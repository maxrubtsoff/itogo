package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * The current date as a live value. Emits again at midnight (and when the
 * user changes the clock or time zone), so deposits, capital and the cashback
 * month recompute without the app being restarted.
 */
interface Today {
    val date: StateFlow<LocalDate>
}
