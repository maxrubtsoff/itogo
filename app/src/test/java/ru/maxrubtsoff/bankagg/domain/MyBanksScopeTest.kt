package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.usecase.MyBanksScope

class MyBanksScopeTest {

    private val tbank = BankBrand("tbank", "Т-Банк", 0xFFFFDD2D)
    private val sber = BankBrand("sber", "Сбербанк", 0xFF21A038)

    @Test fun removedBank_isHidden_andAddingItBack_bringsItBack() {
        val items = listOf(sber, tbank, sber)
        assertEquals(listOf(sber, sber), MyBanksScope.only(items, setOf("sber")) { it })
        assertEquals(items, MyBanksScope.only(items, setOf("sber", "tbank")) { it })
    }

    @Test fun recordsWithoutABank_stayVisible() {
        assertTrue(MyBanksScope.keeps(BankBrand.UNKNOWN, emptySet()))
        assertFalse(MyBanksScope.keeps(tbank, emptySet()))
    }

    private fun tx(id: String, brand: BankBrand, accountId: String) = Transaction(
        id = id, brand = brand, accountId = accountId, amount = Money.ofMinor(-100L), currency = "RUB",
        kind = TransactionKind.UNKNOWN, category = null, counterparty = null, postedAt = 1L
    )

    private fun acc(id: String, brand: BankBrand, excluded: Boolean = false) = Account(
        id = id, brand = brand, title = id, balance = Money.ZERO, currency = "RUB", updatedAt = 1L, excluded = excluded
    )

    @Test fun ops_drop_operations_of_removed_bank() {
        val t = listOf(tx("a", BankBrand.TBANK, "t"), tx("b", BankBrand.SBER, "s"))
        val kept = MyBanksScope.ops(t, emptyList(), setOf(BankBrand.SBER.key))
        assertEquals(listOf("b"), kept.map { it.id })
    }

    @Test fun ops_keep_unknown_brand() {
        val kept = MyBanksScope.ops(listOf(tx("a", BankBrand.UNKNOWN, "u")), emptyList(), emptySet())
        assertEquals(1, kept.size)
    }

    @Test fun ops_drop_operations_of_excluded_account() {
        val t = listOf(tx("a", BankBrand.SBER, "x"), tx("b", BankBrand.SBER, "y"))
        val accounts = listOf(acc("x", BankBrand.SBER, excluded = true), acc("y", BankBrand.SBER))
        assertEquals(listOf("b"), MyBanksScope.ops(t, accounts, setOf(BankBrand.SBER.key)).map { it.id })
    }

    @Test fun ops_keep_operation_of_unknown_account() {
        val kept = MyBanksScope.ops(listOf(tx("a", BankBrand.SBER, "zz")), listOf(acc("x", BankBrand.SBER, true)), setOf(BankBrand.SBER.key))
        assertEquals(1, kept.size)
    }
}
