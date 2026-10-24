package ru.maxrubtsoff.bankagg.data.rates

import org.w3c.dom.Element
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses the CBR daily rates response (XML_daily.asp). Pure function, no
 * Android: the bytes are windows-1251 (declared in the XML header, so the
 * parser decodes them), decimals use a comma, and some currencies are quoted
 * per 10/100/… units — the per-unit rate is VunitRate when present, otherwise
 * Value / Nominal.
 */
object CbrXml {

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** A real daily answer is ~10 KB; anything this big isn't one (U-34). */
    const val MAX_BYTES = 1024 * 1024

    fun parse(bytes: ByteArray): List<FxRate> {
        require(bytes.size <= MAX_BYTES) { "ответ ЦБ слишком большой" }
        // The answer has no DTD: one in it means it isn't the CBR's, so no entities are expanded (U-34).
        require(!String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1).contains("<!DOCTYPE", ignoreCase = true)) { "в ответе ЦБ DTD" }
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isExpandEntityReferences = false }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))
        val root = doc.documentElement
        val date = LocalDate.parse(root.getAttribute("Date"), DATE)
        val nodes = root.getElementsByTagName("Valute")
        return (0 until nodes.length).mapNotNull { i ->
            val v = nodes.item(i) as? Element ?: return@mapNotNull null
            val code = v.text("CharCode") ?: return@mapNotNull null
            val perUnit = v.text("VunitRate")?.let(::decimal)
                ?: run {
                    val value = v.text("Value")?.let(::decimal) ?: return@mapNotNull null
                    val nominal = v.text("Nominal")?.let(::decimal) ?: BigDecimal.ONE
                    value.divide(nominal, 10, RoundingMode.HALF_UP)
                }
            FxRate(code, date, perUnit.movePointRight(6).setScale(0, RoundingMode.HALF_UP).toLong())
        }
    }

    private fun Element.text(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun decimal(s: String): BigDecimal = BigDecimal(s.replace(',', '.').replace(" ", ""))
}
