package ru.maxrubtsoff.bankagg.data.local

import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups

fun CashbackEntity.toDomain(): CashbackItem = CashbackItem(
    id = id,
    monthKey = monthKey,
    brand = BankBrand.fromKey(brandKey),
    category = category,
    percent = percent,
    createdAt = createdAt,
    // Rows saved before the catalogue knew them ("Искусство", "Hoff") get their group now.
    groupKey = CategoryGroups.regroup(category, groupKey)
)

fun CashbackItem.toEntity(): CashbackEntity = CashbackEntity(
    id = id,
    monthKey = monthKey,
    brandKey = brand.key,
    category = category,
    percent = percent,
    createdAt = createdAt,
    groupKey = groupKey
)
