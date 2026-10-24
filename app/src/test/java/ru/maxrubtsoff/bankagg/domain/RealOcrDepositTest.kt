package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.DepositBatch
import ru.maxrubtsoff.bankagg.domain.model.DepositField
import ru.maxrubtsoff.bankagg.domain.model.DepositParser
import java.time.LocalDate

/**
 * What Tesseract actually read from the six deposit screenshots (2026-10-21; the
 * contract number replaced). It misses display-size figures on coloured banners
 * and the fine print of the ЦФА document, so the parser must cope with halves.
 */
class RealOcrDepositTest {

    private val today = LocalDate.of(2026, 10, 21)
    private fun ocr(name: String) = javaClass.getResource("/ocr/$name.txt")!!.readText()
    private fun item(name: String) = DepositParser.parse(ocr(name), today = today).let { DepositBatch.Item(name, it.brand, it.draft) }

    @Test fun tbank_main_reads_rate_and_term_not_a_principal() {
        val d = DepositParser.parse(ocr("deposit_tbank_main"), today = today).draft
        assertEquals(1600, d.rateBp)
        assertEquals(LocalDate.of(2026, 11, 30), d.endDate)
        assertEquals(LocalDate.of(2025, 10, 30), d.startDate)
        // The balance under the "Вклад" heading is at most a guess, never a sure principal.
        assertTrue(d.principal == null || DepositField.PRINCIPAL in d.guessed)
    }

    @Test fun tbank_events_reads_principal_income_and_payout() {
        val d = DepositParser.parse(ocr("deposit_tbank_events"), today = today).draft
        assertEquals(100_000_000L, d.principal?.minor)
        assertEquals(118_514_231L, d.exitAmount?.minor)
        assertEquals(LocalDate.of(2026, 11, 30), d.endDate)   // "Прогноз на дату закрытия / 30 ноября"
    }

    @Test fun alfa_screens() {
        val main = DepositParser.parse(ocr("deposit_alfa_main"), today = today)
        assertEquals(BankBrand.ALFA, main.brand)
        assertEquals("Альфа-Вклад Максимальный", main.draft.title)
        assertEquals(1351, main.draft.rateBp)
        assertEquals(LocalDate.of(2027, 2, 2), main.draft.endDate)
        assertEquals(null, main.draft.principal)              // "::4493" is an account, not money

        val f = DepositParser.parse(ocr("deposit_alfa_forecast"), today = today).draft
        assertEquals(130_000_000L, f.principal?.minor)
        assertEquals(135_917_120L, f.exitAmount?.minor)
        assertEquals(LocalDate.of(2026, 10, 2), f.startDate)
        assertEquals(AccrualMethod.CAPITALIZATION, f.method)
    }

    @Test fun ozon_asset_reads_rate_and_end() {
        val d = DepositParser.parse(ocr("deposit_ozon_asset"), today = today).draft
        assertEquals(1800, d.rateBp)
        assertEquals(LocalDate.of(2027, 6, 8), d.endDate)
    }

    @Test fun six_real_screens_group_into_deposits() {
        val items = listOf("deposit_tbank_main", "deposit_tbank_events", "deposit_alfa_main",
            "deposit_alfa_forecast", "deposit_ozon_asset", "deposit_ozon_document").map(::item)
        val groups = DepositBatch.group(items)
        assertTrue(listOf("deposit_tbank_main", "deposit_tbank_events") in groups)
        assertTrue(listOf("deposit_alfa_main", "deposit_alfa_forecast") in groups)

        // Neither T-Bank screen names the bank in its text: the user picks it in one tap.
        val t = DepositBatch.merge(items.filter { it.id.startsWith("deposit_tbank") }.map { it.copy(brand = BankBrand.TBANK) })
        assertEquals(100_000_000L, t.draft.principal?.minor)
        assertTrue(t.isSavable)

        val a = DepositBatch.merge(items.filter { it.id.startsWith("deposit_alfa") })
        assertEquals(BankBrand.ALFA, a.brand)
        assertNotNull(a.rateBp)
        assertTrue(a.isSavable)
    }
}
