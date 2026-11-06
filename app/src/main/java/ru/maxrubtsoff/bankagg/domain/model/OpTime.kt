package ru.maxrubtsoff.bankagg.domain.model

/** Time of an operation as the push text gives it: a date part may be missing (B-123). */
data class OpTime(val day: Int?, val month: Int?, val year: Int?, val hour: Int, val minute: Int)
