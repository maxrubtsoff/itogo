package ru.maxrubtsoff.bankagg.data.local

import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

fun DepositEntity.toDomain(): Deposit = Deposit(
    id = id,
    brand = BankBrand.fromKey(brandKey),
    title = title?.takeIf { it.isNotBlank() },
    principal = Money.ofMinor(principalMinor),
    rateBp = rateBp,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    endDate = LocalDate.ofEpochDay(endEpochDay),
    exitAmount = exitMinor?.let(Money::ofMinor),
    method = runCatching { AccrualMethod.valueOf(method) }.getOrDefault(AccrualMethod.LINEAR),
    createdAt = createdAt
)

fun Deposit.toEntity(): DepositEntity = DepositEntity(
    id = id,
    brandKey = brand.key,
    title = title,
    principalMinor = principal.minor,
    rateBp = rateBp,
    startEpochDay = startDate.toEpochDay(),
    endEpochDay = endDate.toEpochDay(),
    exitMinor = exitAmount?.minor,
    method = method.name,
    createdAt = createdAt
)
