package ru.maxrubtsoff.bankagg.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.di.AppContainer
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate

/**
 * Debug builds only (src/debug): adb shell am broadcast -a APPID.DEBUG_SEED --es what cashback|notify|fxstep.
 * Fills the app with example data through the app's own repositories and use-cases. Local only, no network.
 */
class DebugSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val what = intent.getStringExtra("what") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = (context.applicationContext as App).container
                if (!c.isReady) {
                    Log.w(TAG, "data is closed (lock / not opened): unlock the app first")
                } else {
                    when (what) {
                        "cashback" -> cashback(c)
                        "notify" -> notify(c)
                        "fxstep" -> fxstep(c)
                        else -> Log.w(TAG, "unknown what=$what")
                    }
                    Log.i(TAG, "done: $what")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "failed: $what", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun cashback(c: AppContainer) {
        val month = CashbackMonth.currentKey()
        val spec: List<Pair<BankBrand, List<Triple<String, String, Double>>>> = listOf(
            BankBrand.ALFA_BUSINESS to listOf(
                Triple("Пятёрочка", "groceries", 15.0), Triple("Автозапчасти", "auto", 7.0), Triple("Аптеки", "pharmacy", 7.0),
                Triple("Дом и ремонт", "home", 7.0), Triple("Одежда и обувь", "clothing", 7.0), Triple("Фастфуд", "cafe", 7.0)
            ),
            BankBrand.ALFA to listOf(
                Triple("Базовый кэшбэк", "base", 1.0), Triple("Кафе и рестораны", "restaurant", 5.0), Triple("Цветы", "other", 5.0),
                Triple("Цифровые товары", "online", 5.0), Triple("Пятёрочка", "groceries", 4.0)
            ),
            BankBrand.OZON to listOf(
                Triple("Базовый кэшбэк", "base", 1.5), Triple("Автоуслуги", "auto", 5.0), Triple("Топливо и АЗС", "fuel", 5.0),
                Triple("Электроника и техника", "electronics", 5.0)
            ),
            BankBrand.YANDEX to listOf(
                Triple("MAAG, ECRU, VIVIENNE", "clothing", 20.0), Triple("Бристоль", "groceries", 12.0),
                Triple("Яндекс Такси", "taxi", 5.0), Triple("Супермаркеты", "groceries", 3.0)
            ),
            BankBrand.TBANK to listOf(
                Triple("Базовый кэшбэк", "base", 1.0), Triple("Кино", "entertainment", 5.0),
                Triple("Подарки и творчество", "other", 5.0), Triple("Супермаркеты", "groceries", 2.0)
            ),
            BankBrand.SBER to listOf(
                Triple("Парфюмерия и косметика", "beauty", 5.0), Triple("Транспорт", "transport", 5.0),
                Triple("Хобби и развлечения", "entertainment", 4.0)
            )
        )
        val now = System.currentTimeMillis()
        val mine = c.banks.current().map { it.key }.toSet()
        spec.forEach { (brand, rows) ->
            if (brand.key !in mine) c.banks.add(brand)
            val items = rows.mapIndexed { i, (name, group, pct) ->
                CashbackItem("dbg-${brand.key}-$i", month, brand, name, pct, now + i, group)
            }
            c.cashbackRepository.saveBankMonth(month, brand, items, append = false)
        }
        c.refreshWidget()
    }

    private suspend fun notify(c: AppContainer) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val months = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
        val date = "${today.dayOfMonth} ${months[today.monthValue - 1]}"
        fun arrival(second: Int) = today.atTime(17, 43, second).atZone(zone).toInstant().toEpochMilli()
        val mine = c.banks.current().map { it.key }.toSet()
        listOf(BankBrand.ALFA_BUSINESS, BankBrand.ALFA).filter { it.key !in mine }.forEach { c.banks.add(it) }

        c.recordNotification(
            BankBrand.ALFA_BUSINESS.packages.first(), "Альфа-Бизнес",
            "Поступило 322581.00 счет *4863 от ООО \"Бета\" счет *1007. Остаток 325218.58", arrival(0)
        )
        val alfa = BankBrand.ALFA.packages.first()
        // Arrival order scrambled: operation 17:42 first, then 17:39, then 17:43.
        c.recordNotification(alfa, "Альфа-Банк", "Пополнение на 280 000 RUB Баланс 281 764,12 RUB $date 17:42", arrival(1))
        c.recordNotification(alfa, "Альфа-Банк", "Покупка 1 045 RUB Баланс 1 764,12 RUB Додо Пицца $date 17:39", arrival(2))
        c.recordNotification(alfa, "Альфа-Банк", "Пополнение на 131 920,50 RUB Баланс 413 684,62 RUB $date 17:43", arrival(3))
    }

    /**
     * 1000 USD bought 10 days ago at 80 RUB, and a wrong hand edit of +811 107,46 RUB five days ago:
     * the days (currency part only) jump by it, no deal or rate explains it. Today's own point is left to the app.
     */
    private suspend fun fxstep(c: AppContainer) {
        val today = c.today.date.value
        val buyDay = today.minusDays(10)
        c.ratesRepository.save((12L downTo 0L).map { FxRate("USD", today.minusDays(it), 80_000_000L) })
        c.fxRepository.upsert(
            FxOperation("dbg-fx-usd", "USD", FxKind.BUY, buyDay, amountMinor = 100_000L, rubMinor = 8_000_000L, createdAt = System.currentTimeMillis())
        )
        val position = 8_000_000L
        val step = 81_110_746L
        val points = (11L downTo 1L).map { back ->
            val d = today.minusDays(back)
            val cur = when {
                d.isBefore(buyDay) -> 0L
                d.isBefore(today.minusDays(5)) -> position
                else -> position + step
            }
            CapitalPoint(d, cur, CapitalPart.entries.associateWith { if (it == CapitalPart.CURRENCY) cur else 0L })
        }
        c.capitalHistory.upsertAll(points)
    }

    private companion object {
        const val TAG = "DebugSeed"
    }
}
