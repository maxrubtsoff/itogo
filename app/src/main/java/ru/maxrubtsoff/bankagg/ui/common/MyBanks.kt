package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.runtime.compositionLocalOf
import ru.maxrubtsoff.bankagg.domain.model.BankBrand

/** The user's banks (F-22), for every bank picker; set at the app root. */
val LocalMyBanks = compositionLocalOf<List<BankBrand>> { emptyList() }

/** Opens adding a bank (the «+» at the end of a picker). */
val LocalAddBank = compositionLocalOf<() -> Unit> { {} }

/** What a picker offers: the user's banks, plus the chosen one if it isn't among them. */
fun pickable(mine: List<BankBrand>, selected: BankBrand?): List<BankBrand> {
    val base = mine.ifEmpty { BankBrand.known }
    return if (selected != null && selected != BankBrand.UNKNOWN && selected !in base) listOf(selected) + base else base
}
