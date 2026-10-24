package ru.maxrubtsoff.bankagg.data.local

import org.json.JSONObject
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.DepositDraft
import ru.maxrubtsoff.bankagg.domain.model.DepositField
import ru.maxrubtsoff.bankagg.domain.model.DepositShot
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import java.time.LocalDate

fun DepositShotEntity.toDomain(): DepositShot = DepositShot(
    id = id,
    imagePath = imagePath,
    brand = if (brandKey.isBlank()) null else BankBrand.fromKey(brandKey),
    status = runCatching { ShotStatus.valueOf(status) }.getOrDefault(ShotStatus.NEEDS_INPUT),
    createdAt = createdAt,
    fileName = fileName,
    draft = draftFromJson(draftJson)
)

fun DepositShot.toEntity(): DepositShotEntity = DepositShotEntity(
    id = id,
    imagePath = imagePath,
    brandKey = brand?.key ?: "",
    status = status.name,
    createdAt = createdAt,
    fileName = fileName,
    draftJson = draftToJson(draft)
)

/** Draft fields as a flat JSON object; missing values are JSON null. */
private fun draftToJson(d: DepositDraft): String = JSONObject().apply {
    put("principalMinor", d.principal?.minor ?: JSONObject.NULL)
    put("exitMinor", d.exitAmount?.minor ?: JSONObject.NULL)
    put("rateBp", d.rateBp ?: JSONObject.NULL)
    put("startEpochDay", d.startDate?.toEpochDay() ?: JSONObject.NULL)
    put("endEpochDay", d.endDate?.toEpochDay() ?: JSONObject.NULL)
    put("incomeMinor", d.income?.minor ?: JSONObject.NULL)
    put("title", d.title ?: JSONObject.NULL)
    put("method", d.method?.name ?: JSONObject.NULL)
    put("guessed", d.guessed.joinToString(",") { it.name })
}.toString()

private fun draftFromJson(json: String): DepositDraft = runCatching {
    val o = JSONObject(json)
    DepositDraft(
        principal = if (o.isNull("principalMinor")) null else Money.ofMinor(o.getLong("principalMinor")),
        exitAmount = if (o.isNull("exitMinor")) null else Money.ofMinor(o.getLong("exitMinor")),
        rateBp = if (o.isNull("rateBp")) null else o.getInt("rateBp"),
        startDate = if (o.isNull("startEpochDay")) null else LocalDate.ofEpochDay(o.getLong("startEpochDay")),
        endDate = if (o.isNull("endEpochDay")) null else LocalDate.ofEpochDay(o.getLong("endEpochDay")),
        // Fields added later: absent in drafts stored by older versions.
        income = if (o.isNull("incomeMinor")) null else Money.ofMinor(o.getLong("incomeMinor")),
        title = if (o.isNull("title")) null else o.getString("title"),
        method = if (o.isNull("method")) null else runCatching { AccrualMethod.valueOf(o.getString("method")) }.getOrNull(),
        guessed = o.optString("guessed").split(',').mapNotNull { n -> DepositField.entries.firstOrNull { it.name == n } }.toSet()
    )
}.getOrDefault(DepositDraft())
