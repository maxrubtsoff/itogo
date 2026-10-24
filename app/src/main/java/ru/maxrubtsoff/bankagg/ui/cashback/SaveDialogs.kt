package ru.maxrubtsoff.bankagg.ui.cashback

import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SlitokDialog
import ru.maxrubtsoff.bankagg.ui.common.BankPickSheet
import ru.maxrubtsoff.bankagg.ui.common.LocalMyBanks
import ru.maxrubtsoff.bankagg.ui.common.pickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** Asked on "Сохранить" when no bank is chosen yet: one tap picks it and the save continues. */
@Composable
fun BankPickDialog(onPick: (BankBrand) -> Unit, onDismiss: () -> Unit) {
    BankPickSheet(selected = null, onPick = onPick, onDismiss = onDismiss)
}

/** The bank already has other offers this month: add these to them, or replace them. */
@Composable
fun AddOrReplaceDialog(
    bank: BankBrand,
    existingCount: Int,
    onAppend: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    SlitokDialog(
        title = "У банка уже есть кэшбэк",
        onDismiss = onDismiss,
        text = "У «${bank.title}» за этот месяц уже ${RuPlural.categories(existingCount)}."
    ) {
        PlateButton(onClick = onReplace) { Text("Заменить") }
        BrassButton(onClick = onAppend) { Text("Дополнить") }
    }
}

/**
 * The shared "Сохранить" flow for a set of rows (screenshot, bank editor):
 * no bank yet → [BankPickDialog]; bank already has different offers this month
 * → [AddOrReplaceDialog]; otherwise it just saves. Render [SaveCashbackDialogs].
 */
@Stable
class SaveCashbackState internal constructor(
    private val vm: CashbackViewModel,
    private val scope: CoroutineScope,
    private val onSaved: (BankBrand) -> Unit
) {
    internal var askBank by mutableStateOf(false)
    internal var askMode by mutableStateOf<Pair<BankBrand, Int>?>(null)
    /** True from the first "Сохранить" until the write is done: repeated taps are ignored. */
    var saving by mutableStateOf(false)
        private set
    private var monthKey = ""
    private var lines: List<CashbackViewModel.DraftLine> = emptyList()

    fun start(monthKey: String, brand: BankBrand?, lines: List<CashbackViewModel.DraftLine>) {
        if (saving) return
        this.monthKey = monthKey
        this.lines = lines
        if (brand == null) askBank = true else proceed(brand)
    }

    internal fun proceed(brand: BankBrand) {
        askBank = false
        scope.launch {
            when (vm.decideSave(monthKey, brand, lines)) {
                CashbackMerge.Decision.SAVE -> write(brand, CashbackViewModel.SaveMode.APPEND)
                CashbackMerge.Decision.ASK -> askMode = brand to vm.existingCountForBank(monthKey, brand)
            }
        }
    }

    internal fun write(brand: BankBrand, mode: CashbackViewModel.SaveMode) {
        if (saving) return
        askMode = null
        saving = true
        vm.commit(monthKey, brand, lines, mode) { onSaved(brand) }
    }
}

@Composable
fun rememberSaveCashback(vm: CashbackViewModel, onSaved: (BankBrand) -> Unit): SaveCashbackState {
    val scope = rememberCoroutineScope()
    val saved by rememberUpdatedState(onSaved)
    return remember(vm) { SaveCashbackState(vm, scope) { saved(it) } }
}

@Composable
fun SaveCashbackDialogs(state: SaveCashbackState) {
    if (state.askBank) {
        BankPickDialog(onPick = state::proceed, onDismiss = { state.askBank = false })
    }
    state.askMode?.let { (bank, count) ->
        AddOrReplaceDialog(
            bank = bank,
            existingCount = count,
            onAppend = { state.write(bank, CashbackViewModel.SaveMode.APPEND) },
            onReplace = { state.write(bank, CashbackViewModel.SaveMode.REPLACE) },
            onDismiss = { state.askMode = null }
        )
    }
}
