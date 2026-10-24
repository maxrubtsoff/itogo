package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesSource
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import java.time.LocalDate

/**
 * Keeps CBR rates fresh. [ifNeeded] runs quietly on start and on return to the
 * app: only when today's rates haven't been fetched yet and the last attempt
 * was over an hour ago. [now] (the ↻ button) always fetches. Only public rates
 * travel — the request carries no user data.
 */
class RefreshRates(
    private val source: RatesSource,
    private val rates: RatesRepository,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    sealed interface Result {
        data class Updated(val date: LocalDate) : Result
        data object Skipped : Result
        data class Failed(val error: Throwable) : Result
    }

    /** [moscowToday] — today's date in Moscow: the CBR sets rates by Moscow time. */
    suspend fun ifNeeded(moscowToday: LocalDate): Result {
        if (settings.ratesLastSuccessDay() == moscowToday.toEpochDay()) return Result.Skipped
        if (clock() - settings.ratesLastAttemptAt() < RETRY_AFTER_MS) return Result.Skipped
        return now(moscowToday)
    }

    suspend fun now(moscowToday: LocalDate): Result {
        settings.setRatesAttempt(clock())
        return try {
            val fetched = source.fetch(moscowToday)
            if (fetched.isEmpty()) return Result.Failed(IllegalStateException("пустой ответ ЦБ"))
            rates.save(fetched)
            settings.setRatesSuccess(moscowToday.toEpochDay())
            Result.Updated(fetched.maxOf { it.date })
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }

    /**
     * The CBR rate of [currency] on [date] for the purchase form's chip: from
     * the cache when that day is there, otherwise one request for that day.
     * Null offline — the chip simply stays hidden.
     */
    suspend fun rateOn(currency: String, date: LocalDate): FxRate? {
        if (currency == Currencies.RUB) return null
        rates.latestOnOrBefore(currency, date)?.takeIf { it.date == date }?.let { return it }
        return runCatching {
            val fetched = source.fetch(date)
            rates.save(fetched)
            fetched.filter { it.currency == currency && !it.date.isAfter(date) }.maxByOrNull { it.date }
        }.getOrNull() ?: rates.latestOnOrBefore(currency, date)?.takeIf { !it.date.isBefore(date.minusDays(4)) }
    }

    private companion object {
        const val RETRY_AFTER_MS = 60 * 60_000L
    }
}
