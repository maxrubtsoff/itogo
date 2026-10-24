package ru.maxrubtsoff.bankagg.ui.settings

import androidx.compose.animation.AnimatedVisibility
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Новые банки» (F-23): a card per app — its name, the full text of its last
 * notification, and four equal choices; the likely answer has a brass frame.
 */
@Composable
fun NewBanksScreen(vm: BanksViewModel, onBack: () -> Unit) {
    val list by vm.candidates.collectAsStateWithLifecycle()
    val mine by vm.mine.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<BankCandidate?>(null) }

    Column(
        Modifier.fillMaxSize().background(Slitok.Bg).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Новые банки", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            list.sortedByDescending { it.postedAt }.forEach { c ->
                val guess = c.guessKey?.let { BankBrand.fromKey(it) }
                CandidateCard(c, guess, onYes = { if (guess != null) vm.link(c, guess) }, onOther = { choosing = c }, onNew = { vm.addNew(c) }, onIgnore = { vm.ignore(c) })
            }
        }
    }
    LaunchedNothingLeft(list.isEmpty(), onBack)

    choosing?.let { c ->
        SlitokSheet(onDismiss = { choosing = null }) {
            Text("Это мой", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
            Column {
                mine.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.link(c, b); choosing = null }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BankIcon(b, size = 36.dp)
                        Text(b.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Everything answered: back to where the list was opened from. */
@Composable
private fun LaunchedNothingLeft(empty: Boolean, onBack: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(empty) { if (empty) onBack() }
}

@Composable
private fun CandidateCard(c: BankCandidate, guess: BankBrand?, onYes: () -> Unit, onOther: () -> Unit, onNew: () -> Unit, onIgnore: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Slitok.PanelTop).border(1.dp, Slitok.Line, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BankIcon(BankBrand("app:${c.packageName}", c.appName, 0xFF4A5568, packages = setOf(c.packageName)), size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(c.appName, style = MaterialTheme.typography.titleMedium)
                Text(TIME.format(Date(c.postedAt)), style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
            }
        }
        // The whole text: it's what tells which account this is.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Slitok.Inset).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            c.title?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
            Text(c.text, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (guess != null) Choice(Modifier.weight(1f), "Это ${guess.title}", likely = true, onClick = onYes) { BankIcon(guess, size = 28.dp) }
            else Choice(Modifier.weight(1f), "Новый банк", likely = true, onClick = onNew) { ChoiceIcon(Icons.Filled.Add) }
            Choice(Modifier.weight(1f), "Другой мой", likely = false, onClick = onOther) { ChoiceIcon(Icons.AutoMirrored.Filled.List) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (guess != null) Choice(Modifier.weight(1f), "Новый банк", likely = false, onClick = onNew) { ChoiceIcon(Icons.Filled.Add) }
            Choice(Modifier.weight(1f), "Не банк", likely = false, onClick = onIgnore) { ChoiceIcon(Icons.Filled.Close, Slitok.Muted) }
        }
    }
}

@Composable
private fun Choice(modifier: Modifier, label: String, likely: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(52.dp).clip(shape).background(Slitok.Inset)
            .border(if (likely) 1.5.dp else 1.dp, if (likely) Slitok.BrassEdge else Slitok.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
private fun ChoiceIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = Slitok.Brass2) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Slitok.BrassTint), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

private val TIME = SimpleDateFormat("d MMM, HH:mm", Locale("ru"))
