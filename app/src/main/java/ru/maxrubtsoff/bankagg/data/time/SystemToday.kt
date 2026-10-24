package ru.maxrubtsoff.bankagg.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.repository.Today
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [Today] backed by the system clock: a timer wakes at the next midnight, and
 * date/time/zone change broadcasts cover manual clock changes.
 */
class SystemToday(context: Context) : Today {

    private val state = MutableStateFlow(LocalDate.now())
    override val date: StateFlow<LocalDate> = state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, midnight).toMillis() + 1_000)
                refresh()
            }
        }
    }

    /** Re-reads the clock; cheap, also called when the app comes to the foreground. */
    fun refresh() {
        state.value = LocalDate.now()
    }
}
