package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CashWithdrawal
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.parser.BareOp
import ru.maxrubtsoff.bankagg.domain.parser.OpDirection

class CashWithdrawalTest {

    private val bare = BareOp(amountMinor = 7_000_000L, balanceMinor = 982_166L)

    @Test fun withdrawalWhenBalanceDropsByAmount() {
        val op = CashWithdrawal.resolve(bare, 7_982_166L)!!
        assertEquals(OpDirection.OUT, op.direction)
        assertEquals(TransactionKind.EXPENSE, op.kind)
        assertEquals(-7_000_000L, op.amount.minor)
        assertEquals(982_166L, op.balance!!.minor)
        assertEquals("Снятие наличных", op.counterparty)
    }

    @Test fun bareNullWhenPrevUnknown() = assertNull(CashWithdrawal.resolve(bare, null))

    @Test fun bareNullWhenBalanceRises() = assertNull(CashWithdrawal.resolve(bare, 982_166L - 7_000_000L))

    @Test fun bareNullWhenMismatch() {
        assertNull(CashWithdrawal.resolve(bare, 7_982_167L))
        assertNull(CashWithdrawal.resolve(bare, 982_166L))
    }
}
