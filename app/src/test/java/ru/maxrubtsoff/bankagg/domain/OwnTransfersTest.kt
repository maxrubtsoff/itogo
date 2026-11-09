package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.OwnTransfers
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import java.time.LocalDateTime
import java.time.ZoneOffset

class OwnTransfersTest {

    private val tb = Account("tb", BankBrand.TBANK, "Т-Банк", Money.ofMinor(0), "RUB", 0L)
    private val al = Account("al", BankBrand.ALFA, "Альфа", Money.ofMinor(0), "RUB", 0L)
    private val accounts = listOf(tb, al)

    private fun ms(day: Int, h: Int, m: Int) =
        LocalDateTime.of(2026, 10, day, h, m).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun op(id: String, acc: String, minor: Long, kind: TransactionKind, at: Long = ms(10, 12, 0)) =
        Transaction(id, BankBrand.TBANK, acc, Money.ofMinor(minor), "RUB", kind, null, null, at)

    private fun ids(vararg t: Transaction, marked: Set<String> = emptySet(), acc: List<Account> = accounts) =
        OwnTransfers.ids(t.toList(), acc, marked, ZoneOffset.UTC)

    @Test fun pairAcrossBanksTransferToIncome() {
        val r = ids(op("o", "tb", -5000, TransactionKind.TRANSFER), op("i", "al", 5000, TransactionKind.INCOME, ms(10, 12, 10)))
        assertEquals(setOf("o", "i"), r)
    }

    @Test fun purchaseNeverPairs() {
        assertTrue(ids(op("o", "tb", -5000, TransactionKind.EXPENSE), op("i", "al", 5000, TransactionKind.INCOME)).isEmpty())
    }

    @Test fun differentSumsNoPair() {
        assertTrue(ids(op("o", "tb", -5000, TransactionKind.TRANSFER), op("i", "al", 4000, TransactionKind.INCOME)).isEmpty())
    }

    @Test fun sameAccountNoPair() {
        assertTrue(ids(op("o", "tb", -5000, TransactionKind.TRANSFER), op("i", "tb", 5000, TransactionKind.INCOME)).isEmpty())
    }

    @Test fun unknownAccountNoPair() {
        assertTrue(ids(op("o", "tb", -5000, TransactionKind.TRANSFER), op("i", "al", 5000, TransactionKind.INCOME), acc = listOf(tb)).isEmpty())
    }

    @Test fun pairAcrossMidnightWithinWindow() {
        val r = ids(op("o", "tb", -5000, TransactionKind.TRANSFER, ms(10, 23, 50)), op("i", "al", 5000, TransactionKind.INCOME, ms(11, 0, 20)))
        assertEquals(2, r.size)
    }

    @Test fun farApartNextDayNoPair() {
        val r = ids(op("o", "tb", -5000, TransactionKind.TRANSFER, ms(10, 23, 0)), op("i", "al", 5000, TransactionKind.INCOME, ms(11, 3, 30)))
        assertTrue(r.isEmpty())
    }

    @Test fun oneToOne() {
        val r = ids(
            op("o", "tb", -5000, TransactionKind.TRANSFER, ms(10, 12, 0)),
            op("i1", "al", 5000, TransactionKind.INCOME, ms(10, 12, 30)),
            op("i2", "al", 5000, TransactionKind.INCOME, ms(10, 12, 5))
        )
        assertEquals(setOf("o", "i2"), r)
    }

    @Test fun textMarkedSingleLeg() {
        assertEquals(setOf("o"), ids(op("o", "tb", -5000, TransactionKind.TRANSFER), marked = setOf("o", "gone")))
    }

    @Test fun isOwnTextMarkers() {
        assertTrue(OwnTransfers.isOwnText(null, "Перевод себе в Альфа-Банк"))
        assertFalse(OwnTransfers.isOwnText(null, "Перевод Ивану И."))
        assertTrue(OwnTransfers.isOwnText("Т-Банк", "между своими счетами"))
    }
}
