package ru.maxrubtsoff.bankagg.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.App

/**
 * U-55: `adb shell am broadcast -n <package>/ru.maxrubtsoff.bankagg.benchmark.BenchmarkSeedReceiver`
 * fills the app with [BenchmarkData] so Macrobenchmark scrolls real-sized lists.
 * Exists only in the benchmark build and answers only the shell (permission DUMP).
 * It never touches an app that already has banks — a phone with real data is safe.
 */
class BenchmarkSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val container = (context.applicationContext as App).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (container.banks.current().isNotEmpty()) {
                    Log.i(TAG, "skipped: the app already has banks")
                    return@launch
                }
                val today = container.today.date.value
                val now = System.currentTimeMillis()
                BenchmarkData.banks.forEach { container.banks.add(it) }
                BenchmarkData.accounts().forEach {
                    container.ledgerRepository.upsertAccountBalance(it.id, it.brand, it.currency, 0L, it.balanceMinor, now)
                }
                BenchmarkData.deposits(today).forEach { container.depositRepository.add(it) }
                BenchmarkData.cashback(today).forEach { (month, byBank) ->
                    byBank.forEach { (brand, items) -> container.cashbackRepository.saveBankMonth(month, brand, items, append = false) }
                }
                BenchmarkData.subscriptions(today).forEach { container.subscriptionRepository.save(it) }
                Log.i(TAG, "seeded")
            } catch (e: Exception) {
                Log.e(TAG, "seeding failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BenchmarkSeed"
    }
}
