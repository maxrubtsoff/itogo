package ru.maxrubtsoff.bankagg.ui.settings

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import ru.maxrubtsoff.bankagg.ui.overview.PaymentArt
import ru.maxrubtsoff.bankagg.ui.overview.PaymentPartArt
import ru.maxrubtsoff.bankagg.ui.overview.CapitalPartArt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.ui.common.pressScale
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** «Что храним» in Settings (F-21): the same plates as on the first launch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val features by vm.features.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            ScreenHeader("Что храним", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            FeaturesGrid(features, vm::toggleFeature)
        }
    }
}

/**
 * «Что храним» (F-21, F-30): two groups, as the tabs — «Капитал» with its four
 * parts and «Платежи» with cashback and subscriptions. The last plate on can't go off.
 */
@Composable
fun FeaturesGrid(features: Features, onToggle: (Feature) -> Unit) {
    val capital = listOf(Feature.ACCOUNTS, Feature.DEPOSITS, Feature.CURRENCY, Feature.CASH)
    val payments = listOf(Feature.CASHBACK, Feature.SUBSCRIPTIONS)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("КАПИТАЛ", style = MaterialTheme.typography.labelSmall, color = Slitok.Faint, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        capital.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { f -> FeatureTile(f, features.has(f), features, Modifier.weight(1f), onToggle) }
            }
        }
        Text("ПЛАТЕЖИ", style = MaterialTheme.typography.labelSmall, color = Slitok.Faint, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            payments.forEach { f -> FeatureTile(f, features.has(f), features, Modifier.weight(1f), onToggle) }
        }
    }
}

@Composable
private fun FeatureTile(f: Feature, on: Boolean, features: Features, modifier: Modifier, onToggle: (Feature) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val border by animateColorAsState(if (on) Slitok.BrassLine else Slitok.Line, label = "border")
    Box(
        modifier
            .graphicsLayer { translationX = shake.value }
            .pressScale()
            .clip(shape)
            .background(if (on) Brush.linearGradient(listOf(Slitok.Brass.copy(alpha = 0.14f), Slitok.PanelTop)) else Brush.linearGradient(listOf(Slitok.PanelBottom, Slitok.PanelBottom)))
            .border(1.5.dp, border, shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (features.toggle(f) == features) {
                    scope.launch { for (x in listOf(10f, -8f, 6f, -4f, 0f)) shake.animateTo(x, tween(45)) }
                } else onToggle(f)
            }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FeatureArt(f, Modifier.size(width = 64.dp, height = 50.dp).graphicsLayer { alpha = if (on) 1f else 0.55f })
            Text(f.title(), style = MaterialTheme.typography.titleMedium, color = if (on) Slitok.Fg else Slitok.Muted)
            Text(f.subtitle(), style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // A checked circle in the corner says «kept» at a glance.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (on) Slitok.Brass else Color.Transparent)
                .border(1.5.dp, if (on) Slitok.Brass else Slitok.Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Slitok.OnBrassInk, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FeatureArt(f: Feature, modifier: Modifier) {
    val part = f.capitalPart
    when {
        part != null -> CapitalPartArt(part, modifier)
        f == Feature.CASHBACK -> PaymentPartArt(PaymentArt.CASHBACK, modifier)
        else -> PaymentPartArt(PaymentArt.SUBSCRIPTIONS, modifier)
    }
}

fun Feature.title(): String = when (this) {
    Feature.ACCOUNTS -> "Счета"
    Feature.DEPOSITS -> "Вклады"
    Feature.CURRENCY -> "Валюта"
    Feature.CASH -> "Наличные"
    Feature.CASHBACK -> "Кэшбэк"
    Feature.SUBSCRIPTIONS -> "Подписки"
}

private fun Feature.subtitle(): String = when (this) {
    Feature.ACCOUNTS -> "из уведомлений банков"
    Feature.DEPOSITS -> "доход по дням"
    Feature.CURRENCY -> "по курсу ЦБ"
    Feature.CASH -> "сумма на руках"
    Feature.CASHBACK -> "категории по месяцам"
    Feature.SUBSCRIPTIONS -> "регулярные списания"
}

fun Feature.color(): Color = when (this) {
    Feature.ACCOUNTS -> Slitok.Brass
    Feature.DEPOSITS -> Slitok.Violet
    Feature.CURRENCY -> Slitok.Sky
    Feature.CASH -> Slitok.Sand
    Feature.CASHBACK -> Slitok.Mint
    Feature.SUBSCRIPTIONS -> Slitok.Mint
}
