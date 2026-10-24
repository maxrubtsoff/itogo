package ru.maxrubtsoff.bankagg.data.local

import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    brand = BankBrand.fromKey(brandKey),
    title = title,
    balance = Money.ofMinor(balanceMinor),
    currency = currency,
    updatedAt = updatedAt,
    excluded = excluded,
    checkedAt = checkedAt,
    estimated = estimated
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    brand = BankBrand.fromKey(brandKey),
    accountId = accountId,
    amount = Money.ofMinor(amountMinor),
    currency = currency,
    kind = runCatching { TransactionKind.valueOf(kind) }.getOrDefault(TransactionKind.UNKNOWN),
    category = category,
    counterparty = counterparty,
    postedAt = postedAt
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    brandKey = brand.key,
    accountId = accountId,
    amountMinor = amount.minor,
    currency = currency,
    kind = kind.name,
    category = category,
    counterparty = counterparty,
    postedAt = postedAt
)
