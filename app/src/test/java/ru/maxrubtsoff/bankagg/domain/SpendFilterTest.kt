package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryKind
import ru.maxrubtsoff.bankagg.domain.model.SpendFilter
import java.time.LocalDate

class SpendFilterTest {

    private fun ev(title: String, kind: HistoryKind, source: BankBrand? = null, parts: List<HistoryEvent> = emptyList()) =
        HistoryEvent(LocalDate.of(2026, 11, 5), CapitalPart.ACCOUNTS, title, null, -100L, kind = kind, parts = parts, source = source)

    private val spend = ev("spend", HistoryKind.SPEND, BankBrand.SBER)
    private val transfer = ev("transfer", HistoryKind.TRANSFER, BankBrand.TBANK)
    private val income = ev("income", HistoryKind.INCOME, BankBrand.SBER)
    private val all = listOf(spend, transfer, income)

    @Test fun allKeepsEverything() {
        assertEquals(all, SpendFilter.keep(all, SpendFilter.All))
    }

    @Test fun transfersKeepsOnlyTransferEvents() {
        assertEquals(listOf(transfer), SpendFilter.keep(all, SpendFilter.Transfers))
    }

    @Test fun transfersKeepsGroupWithTransferChild() {
        val group = ev("group", HistoryKind.TRANSACTION, parts = listOf(transfer, transfer))
        val spends = ev("spends", HistoryKind.TRANSACTION, parts = listOf(spend, spend))
        assertEquals(listOf(group), SpendFilter.keep(listOf(group, spends, spend), SpendFilter.Transfers))
    }

    @Test fun bankFilterMatchesSourceAndParts() {
        val group = ev("group", HistoryKind.TRANSACTION, parts = listOf(transfer))
        val kept = SpendFilter.keep(all + group, SpendFilter.Bank(BankBrand.SBER))
        assertEquals(listOf(spend, income), kept)
        assertEquals(listOf(transfer, group), SpendFilter.keep(all + group, SpendFilter.Bank(BankBrand.TBANK)))
    }
}
