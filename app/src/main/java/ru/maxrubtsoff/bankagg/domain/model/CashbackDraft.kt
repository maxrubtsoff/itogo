package ru.maxrubtsoff.bankagg.domain.model

/** Rules for a cashback row being edited (name + percent typed as text). */
object CashbackDraft {

    /** The typed percent as a number in (0, 100], or null if it isn't one. */
    fun percent(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 }

    /** A row can be saved only with a name and a valid percent. */
    fun isComplete(name: String, percentText: String): Boolean =
        name.isNotBlank() && percent(percentText) != null
}
