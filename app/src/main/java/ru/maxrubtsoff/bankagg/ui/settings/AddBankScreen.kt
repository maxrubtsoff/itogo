package ru.maxrubtsoff.bankagg.ui.settings

import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ButtonRow
import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.SearchField

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ChipButton
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.onboarding.Steps
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Adding banks (F-22): the ones added so far on top, a search over the CBR list,
 * then hints — banks whose app is on the phone and the largest banks — and an
 * own bank for anything else. The same screen is the first launch's second step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBankScreen(vm: BanksViewModel, firstLaunch: Boolean, onBack: (() -> Unit)?, onDone: () -> Unit, onOwn: (String) -> Unit) {
    val mine by vm.mine.collectAsStateWithLifecycle()
    val onPhone by vm.onPhone.collectAsStateWithLifecycle()
    val popular by vm.popular.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { delay(150); vm.search(query) }

    Column(Modifier.fillMaxSize().background(Slitok.Bg).navigationBarsPadding()) {
    ScreenHeader(if (firstLaunch) "Ваши банки" else "Добавить банк", onBack, standalone = true) {
        if (mine.isNotEmpty()) Text("${mine.size}", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, modifier = Modifier.padding(end = 12.dp))
    }
    Column(
        Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (firstLaunch) Steps(current = 1, total = 2)

        // Added so far: one line that scrolls sideways; a tap removes.
        if (mine.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mine.forEach { b -> BankChip(b, added = true) { vm.remove(b) } }
        }

        SearchField(query, { query = it }, "Найти банк")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (query.isNotBlank()) {
                val fresh = results.filter { it !in mine }
                if (fresh.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
                    fresh.forEachIndexed { i, b ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                        BankLine(b, action = "Добавить") { vm.add(b); query = "" }
                    }
                }
                PlateButton(onClick = { onOwn(query.trim()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Свой банк «${query.trim()}»")
                }
            } else {
                if (onPhone.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Есть на телефоне", style = MaterialTheme.typography.titleSmall, color = Slitok.Muted, modifier = Modifier.weight(1f))
                        ChipButton("Добавить все") { vm.addAll(onPhone) }
                    }
                    Panel(Modifier.fillMaxWidth()) {
                        onPhone.forEachIndexed { i, b ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                            BankLine(b, action = "Добавить") { vm.add(b) }
                        }
                    }
                }
                if (popular.isNotEmpty()) {
                    Text("Популярные", style = MaterialTheme.typography.titleSmall, color = Slitok.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        popular.forEach { b -> BankChip(b, added = false) { vm.add(b) } }
                    }
                }
            }
        }

        // B-38: the two buttons are halves of one height.
        if (query.isBlank()) ButtonRow {
            PlateButton(onClick = { onOwn("") }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Свой банк")
            }
            BrassButton(onClick = onDone) { Text("Готово") }
        } else BrassButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
    }
    }
}

@Composable
private fun BankLine(bank: BankBrand, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BankIcon(bank, size = 36.dp)
        Text(bank.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(action, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Slitok.Brass2)
    }
}

@Composable
private fun BankChip(bank: BankBrand, added: Boolean, onClick: () -> Unit) {
    // U-40/A7: the app's selectable plate, with the bank's logo.
    SlitokChip(bank.title, added, onClick, leading = { BankIcon(bank, size = 36.dp) })
}
