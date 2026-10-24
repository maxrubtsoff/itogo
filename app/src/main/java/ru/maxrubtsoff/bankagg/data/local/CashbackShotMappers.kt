package ru.maxrubtsoff.bankagg.data.local

import org.json.JSONArray
import org.json.JSONObject
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.ShotLine
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus

fun CashbackShotEntity.toDomain(): CashbackShot = CashbackShot(
    id = id,
    monthKey = monthKey,
    imagePath = imagePath,
    brand = if (brandKey.isBlank()) null else BankBrand.fromKey(brandKey),
    status = runCatching { ShotStatus.valueOf(status) }.getOrDefault(ShotStatus.NEEDS_INPUT),
    createdAt = createdAt,
    fileName = fileName,
    lines = linesFromJson(linesJson)
)

fun CashbackShot.toEntity(): CashbackShotEntity = CashbackShotEntity(
    id = id,
    monthKey = monthKey,
    imagePath = imagePath,
    brandKey = brand?.key ?: "",
    status = status.name,
    createdAt = createdAt,
    fileName = fileName,
    linesJson = linesToJson(lines)
)

/** Draft rows as a JSON array of {text, groupKey, percent|null, sourceText}. */
private fun linesToJson(lines: List<ShotLine>): String = JSONArray().apply {
    lines.forEach { l ->
        put(JSONObject().apply {
            put("text", l.text); put("groupKey", l.groupKey)
            put("percent", l.percent ?: JSONObject.NULL); put("sourceText", l.sourceText)
        })
    }
}.toString()

private fun linesFromJson(json: String): List<ShotLine> = runCatching {
    val arr = JSONArray(json)
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ShotLine(
            text = o.optString("text"),
            groupKey = o.optString("groupKey", CategoryGroups.OTHER),
            percent = if (o.isNull("percent")) null else o.getDouble("percent"),
            sourceText = o.optString("sourceText")
        )
    }
}.getOrDefault(emptyList())
