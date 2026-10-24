package ru.maxrubtsoff.bankagg.data.rates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.repository.RatesSource
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The only network call in the app: the Bank of Russia's public daily rates.
 * The date is always passed explicitly (otherwise in the afternoon the CBR
 * serves tomorrow's rates). No user data is sent. Plain [HttpURLConnection] —
 * no network library.
 */
class CbrRatesSource : RatesSource {

    override suspend fun fetch(date: LocalDate): List<FxRate> = withContext(Dispatchers.IO) {
        val url = URL("https://www.cbr.ru/scripts/XML_daily.asp?date_req=${date.format(REQ)}")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "BankAggregator/1.0 (Android)")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("ЦБ ответил ${conn.responseCode}")
            CbrXml.parse(conn.inputStream.use { it.readNBytesCompat(CbrXml.MAX_BYTES + 1) })
        } finally {
            conn.disconnect()
        }
    }

    /** Reads at most [max] bytes: an endless answer can't fill the memory (U-34). */
    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        val REQ: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        const val TIMEOUT_MS = 10_000
    }
}
