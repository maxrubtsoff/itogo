package ru.maxrubtsoff.bankagg.data.local

import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

fun FxOperationEntity.toDomain(): FxOperation = FxOperation(
    id = id,
    currency = currency,
    kind = runCatching { FxKind.valueOf(kind) }.getOrDefault(FxKind.BUY),
    date = LocalDate.ofEpochDay(epochDay),
    amountMinor = amountMinor,
    rubMinor = rubMinor,
    createdAt = createdAt
)

fun FxOperation.toEntity(): FxOperationEntity = FxOperationEntity(
    id = id,
    currency = currency,
    kind = kind.name,
    epochDay = date.toEpochDay(),
    amountMinor = amountMinor,
    rubMinor = rubMinor,
    createdAt = createdAt
)

fun FxRateEntity.toDomain(): FxRate = FxRate(code, LocalDate.ofEpochDay(epochDay), rubPerUnitMicro)

fun FxRate.toEntity(): FxRateEntity = FxRateEntity(currency, date.toEpochDay(), rubPerUnitMicro)

fun CashEntity.toDomain(): CashBalance = CashBalance(Money.ofMinor(amountMinor), updatedAt)
