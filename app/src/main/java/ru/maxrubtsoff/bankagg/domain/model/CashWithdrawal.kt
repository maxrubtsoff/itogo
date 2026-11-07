package ru.maxrubtsoff.bankagg.domain.model

import ru.maxrubtsoff.bankagg.domain.parser.BareOp
import ru.maxrubtsoff.bankagg.domain.parser.OpDirection
import ru.maxrubtsoff.bankagg.domain.parser.ParsedOp

/**
 * «100 000 ₽ — Баланс: 79 821,66 ₽» (B-126): a push with no word of direction.
 * It is a cash withdrawal only when the balance fell by exactly the amount since
 * the last known one. Anything else — no earlier balance, a balance that rose
 * (a deposit is not guessed), a figure that does not add up — is not an operation.
 */
object CashWithdrawal {

    const val COUNTERPARTY = "Снятие наличных"

    fun resolve(bare: BareOp, prevBalanceMinor: Long?): ParsedOp? {
        if (prevBalanceMinor == null) return null
        if (prevBalanceMinor - bare.amountMinor != bare.balanceMinor) return null
        return ParsedOp(
            amount = Money.ofMinor(-bare.amountMinor),
            currency = bare.currency,
            kind = TransactionKind.EXPENSE,
            direction = OpDirection.OUT,
            balance = Money.ofMinor(bare.balanceMinor),
            counterparty = COUNTERPARTY,
            balanceCurrency = bare.currency
        )
    }

    /** True for an operation made by [resolve]: its amount also goes into the cash. */
    fun isWithdrawal(op: ParsedOp): Boolean = op.counterparty == COUNTERPARTY && op.kind == TransactionKind.EXPENSE
}
