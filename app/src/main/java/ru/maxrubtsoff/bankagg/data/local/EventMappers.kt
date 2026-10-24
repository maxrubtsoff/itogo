package ru.maxrubtsoff.bankagg.data.local

import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent

fun EventEntity.toDomain(): CapturedEvent = CapturedEvent(
    id = id,
    packageName = packageName,
    brand = BankBrand.fromKey(brandKey),
    title = title,
    text = text,
    postedAt = postedAt,
    dedupHash = dedupHash,
    parsed = parsed,
    contentHash = contentHash
)

fun CapturedEvent.toEntity(): EventEntity = EventEntity(
    id = id,
    packageName = packageName,
    brandKey = brand.key,
    title = title,
    text = text,
    postedAt = postedAt,
    dedupHash = dedupHash,
    parsed = parsed,
    contentHash = contentHash
)
