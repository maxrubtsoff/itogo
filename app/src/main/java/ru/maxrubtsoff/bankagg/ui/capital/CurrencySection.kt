package ru.maxrubtsoff.bankagg.ui.capital

import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.common.tourTarget
import ru.maxrubtsoff.bankagg.ui.common.rememberTabScrollState
import ru.maxrubtsoff.bankagg.ui.common.sharedCard
import ru.maxrubtsoff.bankagg.ui.common.cascade
import ru.maxrubtsoff.bankagg.ui.common.rememberCascade
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import androidx.compose.foundation.combinedClickable
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalSnapshot
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.CurrencyHolding
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxMath
import ru.maxrubtsoff.bankagg.domain.model.FxPortfolio
import ru.maxrubtsoff.bankagg.domain.model.FxRates
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate

/** What a sheet is open for: a new purchase/sale (with a currency) or editing an operation. */
data class FxSheetRequest(val kind: FxKind, val currency: String?, val editId: String? = null)

@Composable
fun CurrencySection(vm: CapitalViewModel, snap: CapitalSnapshot, onOpenCurrency: (String) -> Unit) {
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lastCurrency by vm.lastCurrency.collectAsStateWithLifecycle()
    var sheetKind by rememberSaveable { mutableStateOf<String?>(null) }
    // A long press on a currency (U-18) opens the sheet with that currency chosen.
    var sheetCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    val cur = snap.currency
    val cashPositions = cur.positions.filter { it.qtyMinor != 0L || it.realizedMinor != 0L }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberTabScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CurrencyHero(snap, refreshing, onRefresh = vm::refreshRatesNow)

        if (cur.isEmpty) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Валюты пока нет", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Добавьте покупку наличной валюты — посчитаю её в рублях по курсу ЦБ и покажу прирост.",
                        style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted
                    )
                    BrassButton(onClick = { sheetKind = FxKind.BUY.name }, modifier = Modifier.fillMaxWidth()) {
                        Text("Добавить покупку")
                    }
                }
            }
        } else {
            if (cashPositions.isNotEmpty()) {
                GroupTitle("Наличными")
                Panel(Modifier.fillMaxWidth()) {
                    val play = rememberCascade("currency")
                    cashPositions.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                        // U-45: «Купил / Продал» live on the currency's screen.
                        Box(Modifier.cascade(i, play).tourTarget(TourTarget.CURRENCY_ROW, active = i == 0, radius = 16.dp)) { PositionRow(p) { onOpenCurrency(p.currency) } }
                    }
                }
            }
            BuySellButtons(
                canSell = cashPositions.any { it.qtyMinor > 0 },
                onBuy = { sheetKind = FxKind.BUY.name },
                onSell = { sheetKind = FxKind.SELL.name }
            )
            if (cur.holdings.isNotEmpty()) {
                GroupTitle("На счетах")
                Panel(Modifier.fillMaxWidth()) {
                    cur.holdings.forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                        HoldingRow(h)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    sheetKind?.let { k ->
        val kind = FxKind.valueOf(k)
        val held = cashPositions.filter { it.qtyMinor > 0 }.map { it.currency }
        val currency = when {
            sheetCurrency != null -> sheetCurrency
            kind == FxKind.SELL && lastCurrency !in held -> held.firstOrNull() ?: lastCurrency
            else -> lastCurrency
        }
        FxOperationSheet(vm, FxSheetRequest(kind, currency), onDone = { sheetKind = null; sheetCurrency = null })
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun BuySellButtons(canSell: Boolean, onBuy: () -> Unit, onSell: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BrassButton(onClick = onBuy, modifier = Modifier.weight(1f).tourTarget(TourTarget.ADD_BUTTON, radius = 16.dp)) { Text("+ Купил") }
        PlateButton(onClick = onSell, enabled = canSell, modifier = Modifier.weight(1f)) { Text("− Продал") }
    }
}

@Composable
private fun CurrencyHero(snap: CapitalSnapshot, refreshing: Boolean, onRefresh: () -> Unit) {
    val cur = snap.currency
    Panel(Modifier.fillMaxWidth().sharedCard("part-${CapitalPart.CURRENCY.key}"), radius = 22.dp) {
        Box(Modifier.fillMaxWidth().heroGlow(Slitok.Sky)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeroText("Валюта в рублях", Money.ofMinor(snap.part(CapitalPart.CURRENCY).valueMinor).format())
                if (cur.costMinor > 0L) {
                    AmountText(
                        "наличные: ${signedRub(cur.unrealizedMinor)}" +
                            (cur.unrealizedPct?.let { RuText.DOT + signedPct(it) } ?: "") +
                            " за всё время",
                        style = MaterialTheme.typography.bodyMedium,
                        color = gainColor(cur.unrealizedMinor),
                        fontWeight = FontWeight.SemiBold,
                        minSize = 10.sp
                    )
                }
                if (cur.realizedMinor != 0L) {
                    MoneyText(
                        "зафиксировано ${signedRub(cur.realizedMinor)}",
                        style = MaterialTheme.typography.bodySmall, color = gainColor(cur.realizedMinor), maxLines = 1
                    )
                }
                Spacer(Modifier.height(4.dp))
                RateLine(cur.rateDate, cur.missingRates, snap.today, refreshing, onRefresh)
            }
        }
    }
}

/** "курс ЦБ на 21.10 · ↻"; brass with "обновить" when older than three days. */
@Composable
fun RateLine(rateDate: LocalDate?, missing: List<String>, today: LocalDate, refreshing: Boolean, onRefresh: () -> Unit) {
    val stale = rateDate != null && FxRates.isStale(rateDate, today)
    val color = if (stale || missing.isNotEmpty()) Slitok.Brass2 else Slitok.Muted
    Row(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable(enabled = !refreshing, onClick = onRefresh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val text = buildString {
            if (rateDate != null) append("курс ЦБ на %02d.%02d".format(rateDate.dayOfMonth, rateDate.monthValue))
            if (missing.isNotEmpty()) {
                if (isNotEmpty()) append(RuText.DOT)
                append("нет курса для ${missing.joinToString(", ")}")
            }
            if (rateDate == null && missing.isEmpty()) append("курс ЦБ")
            if (stale) append(RuText.DOT + "обновить")
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        if (refreshing) {
            CircularProgressIndicator(Modifier.size(14.dp), color = color, strokeWidth = 1.5.dp)
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = "Обновить курс", tint = color, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PositionRow(p: FxPortfolio.Position, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().sharedCard("fx-${p.currency}").clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CurrencyBadge(p.currency)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Currencies.nameOf(p.currency), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                MoneyText(
                    p.valueMinor?.let { Money.ofMinor(it).format() } ?: "нет курса",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (p.valueMinor == null) Slitok.Brass2 else Slitok.Fg,
                    maxLines = 1, softWrap = false
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountText(
                    FxMath.formatAmount(p.qtyMinor, p.currency) +
                        (p.avgRate?.let { RuText.DOT + "ср. " + FxMath.formatRate(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, minSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                p.unrealizedMinor?.takeIf { p.qtyMinor > 0 }?.let { g ->
                    Spacer(Modifier.width(8.dp))
                    MoneyText(signedRub(g), style = MaterialTheme.typography.bodySmall, color = gainColor(g),
                        fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(h: CurrencyHolding) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        BankIcon(h.account.brand, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Currencies.nameOf(h.account.currency), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                MoneyText(
                    h.valueMinor?.let { Money.ofMinor(it).format() } ?: "нет курса",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (h.valueMinor == null) Slitok.Brass2 else Slitok.Fg,
                    maxLines = 1, softWrap = false
                )
            }
            MoneyText(
                h.account.balance.format(h.account.currency) + RuText.DOT + h.account.brand.title,
                style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
