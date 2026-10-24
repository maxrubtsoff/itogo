package ru.maxrubtsoff.bankagg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.rates.CbrXml
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import java.time.LocalDate

class CbrXmlTest {

    private val bytes: ByteArray =
        javaClass.classLoader!!.getResourceAsStream("cbr/XML_daily_2026-10-21.xml")!!.use { it.readBytes() }

    @Test fun parses_saved_cbr_response() {
        val rates = CbrXml.parse(bytes)
        assertTrue("ожидались все валюты, пришло ${rates.size}", rates.size >= 40)
        assertTrue(rates.all { it.date == LocalDate.of(2026, 10, 21) })
        val byCode = rates.associateBy { it.currency }
        assertEquals(57_888_900L, byCode.getValue("AUD").rubPerUnitMicro)     // 57,8889
        assertEquals(27_737_500L, byCode.getValue("BYN").rubPerUnitMicro)     // 27,7375
    }

    @Test fun per_unit_rate_for_nominal_above_one() {
        val amd = CbrXml.parse(bytes).first { it.currency == "AMD" }          // 100 драмов = 22,9321
        assertEquals(229_321L, amd.rubPerUnitMicro)
    }

    @Test fun every_quoted_currency_is_in_the_catalogue() {
        val missing = CbrXml.parse(bytes).map { it.currency }.filter { Currencies.of(it) == null }
        assertTrue("нет в справочнике: $missing", missing.isEmpty())
    }

    @Test fun refuses_an_answer_with_a_dtd() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "aaaa">]><ValCurs Date="22.10.2026">&a;</ValCurs>"""
        assertTrue(runCatching { CbrXml.parse(evil.toByteArray()) }.isFailure)
    }

    @Test fun refuses_a_huge_answer() {
        assertTrue(runCatching { CbrXml.parse(ByteArray(CbrXml.MAX_BYTES + 1)) }.isFailure)
    }
}
