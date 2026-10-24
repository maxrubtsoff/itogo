package ru.maxrubtsoff.bankagg.ui.cashback

import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.aboveKeyboard
import ru.maxrubtsoff.bankagg.ui.common.keyboardOpen
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The one cashback form (B-22): a screenshot's form without the photo. With no
 * [brand] it adds cashback by hand — pick the bank, fill the rows; with a bank it
 * edits all of that bank's cashback for the month at once. "Сохранить" writes exactly what is on screen
 * (removed rows are removed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankCashbackEditScreen(vm: CashbackViewModel, monthKey: String, brand: BankBrand?, onBack: () -> Unit) {
    val lines = rememberDraftLines()
    var pickedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val picked: BankBrand? = pickedKey?.let(BankBrand::fromKey)
    var loaded by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    // Back and "save → back" may both fire; leave the screen only once.
    var left by remember { mutableStateOf(false) }
    fun leave() { if (!left) { left = true; onBack() } }

    // A new entry saves like a screenshot: no bank → ask, bank with other offers → add or replace.
    val save = rememberSaveCashback(vm) { leave() }

    LaunchedEffect(monthKey, brand) {
        if (loaded) return@LaunchedEffect   // restored edits win over the database
        lines.clear()
        if (brand != null) {
            val items = vm.monthItems(monthKey).first().filter { it.brand == brand }
            lines.addAll(items.map { CashbackViewModel.DraftLine(it.category, it.groupKey, percentToText(it.percent)) })
        } else {
            lines.add(CashbackViewModel.DraftLine("", CategoryGroups.OTHER, ""))
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            ScreenHeader("${brand?.title ?: "Кэшбэк"} · ${CashbackMonth.shortLabel(monthKey)}", onBack = ::leave) {
                if (brand != null) Box(Modifier.padding(end = 12.dp)) { BankIcon(brand, size = 32.dp) }
            }
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Slitok.Brass)
            }
            return@Scaffold
        }
        val typing = keyboardOpen()
        @Composable
        fun SaveButton(modifier: Modifier) = BrassButton(
            onClick = {
                if (brand != null) {
                    saving = true
                    vm.commit(monthKey, brand, lines.toList(), CashbackViewModel.SaveMode.REPLACE, ::leave)
                } else {
                    save.start(monthKey, picked, lines.toList())
                }
            },
            enabled = !saving && !save.saving && lines.all { it.isComplete } && (brand != null || lines.isNotEmpty()),
            modifier = modifier.fillMaxWidth()
        ) { Text("Сохранить") }
        Column(Modifier.fillMaxSize().aboveKeyboard(padding)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                if (brand == null) {
                    Text("Банк", style = MaterialTheme.typography.labelMedium, color = Slitok.Muted)
                    BankPickerRow(selected = picked, onSelect = { pickedKey = it.key })
                }
                CashbackLinesEditor(lines, vm)
                Spacer(Modifier.height(4.dp))
                // While typing, the button rides on top of the keyboard instead.
                if (!typing) SaveButton(Modifier)
                Spacer(Modifier.height(16.dp))
            }
            if (typing) SaveButton(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }

    SaveCashbackDialogs(save)
}
