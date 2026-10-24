package ru.maxrubtsoff.bankagg.ui.capital

import ru.maxrubtsoff.bankagg.ui.common.StatTile
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.sharedCard
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxMath
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxPortfolio
import ru.maxrubtsoff.bankagg.domain.model.FxRates
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** One cash currency: value, gain, tiles and the full history of purchases and sales. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDetailScreen(vm: CapitalViewModel, code: String, onBack: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val ops by vm.operations.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }      // "BUY", "SELL" or "edit:<id>"

    val position = snap?.currency?.positions?.firstOrNull { it.currency == code }
    val rate = position?.rate
    val mine = remember(ops, code) {
        ops.filter { it.currency == code }.sortedWith(compareByDescending<FxOperation> { it.date }.thenByDescending { it.createdAt })
    }
    val results = remember(ops, code, rate) { FxPortfolio.results(code, ops, rate) }

    Scaffold(
        topBar = {
            ScreenHeader(Currencies.nameOf(code), onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Panel(Modifier.fillMaxWidth().sharedCard("fx-$code"), radius = 22.dp) {
                Box(Modifier.fillMaxWidth().heroGlow(Slitok.Sky)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionHeroText(
                            "Стоимость в рублях",
                            position?.valueMinor?.let { Money.ofMinor(it).format() } ?: "нет курса"
                        )
                        position?.unrealizedMinor?.takeIf { position.qtyMinor > 0 }?.let { g ->
                            AmountText(
                                signedRub(g) + (position.unrealizedPct?.let { RuText.DOT + signedPct(it) } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, color = gainColor(g),
                                fontWeight = FontWeight.SemiBold, minSize = 11.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        snap?.let { s ->
                            RateLine(rate?.date, if (rate == null && position != null) listOf(code) else emptyList(),
                                s.today, refreshing, vm::refreshRatesNow)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Остаток", position?.let { FxMath.formatAmount(it.qtyMinor, code) } ?: "—", Modifier.weight(1f))
                Tile("Средний курс", position?.avgRate?.let { FxMath.formatRate(it) } ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(
                    "Курс ЦБ сегодня",
                    rate?.let { FxMath.formatRate(it.rubPerUnit) } ?: "—",
                    Modifier.weight(1f),
                    color = if (rate != null && snap != null && FxRates.isStale(rate.date, snap!!.today)) Slitok.Brass2 else Slitok.Fg
                )
                val realized = position?.realizedMinor ?: 0L
                Tile("Зафиксировано", signedRub(realized), Modifier.weight(1f), color = if (realized == 0L) Slitok.Fg else gainColor(realized))
            }

            BuySellButtons(
                canSell = (position?.qtyMinor ?: 0L) > 0L,
                onBuy = { sheet = "BUY" },
                onSell = { sheet = "SELL" }
            )

            Text("История", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            if (mine.isEmpty()) {
                Text("Операций нет", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
            } else {
                Panel(Modifier.fillMaxWidth()) {
                    mine.forEachIndexed { i, op ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = Slitok.Line)
                        OperationRow(op, results[op.id]) { sheet = "edit:${op.id}" }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    sheet?.let { s ->
        val request = when {
            s.startsWith("edit:") -> FxSheetRequest(FxKind.BUY, code, editId = s.removePrefix("edit:"))
            else -> FxSheetRequest(FxKind.valueOf(s), code)
        }
        FxOperationSheet(vm, request, onDone = { sheet = null })
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = Slitok.Fg) {
    StatTile(label, modifier) {
        AmountText(value, style = Amounts.Row, color = color, minSize = 11.sp, hideAll = true)
    }
}

/** «Купил · 21.10.2025 · 1 000 $ по 76,50 = 76 500 ₽» with «сейчас +4 700 ₽» / «прибыль +2 250 ₽». */
@Composable
private fun OperationRow(op: FxOperation, result: FxPortfolio.OpResult?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (op.kind == FxKind.BUY) "Купил" else "Продал") + RuText.DOT + op.date.format(DATE_FMT),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            val (caption, value) = when (result) {
                is FxPortfolio.OpResult.Buy -> "сейчас" to result.gainNowMinor
                is FxPortfolio.OpResult.Sell -> "прибыль" to result.profitMinor
                null -> "" to null
            }
            if (value != null) {
                MoneyText(
                    "$caption ${signedRub(value)}",
                    style = MaterialTheme.typography.bodySmall, color = gainColor(value),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = TextAlign.End
                )
            }
        }
        AmountText(
            "${FxMath.formatAmount(op.amountMinor, op.currency)} по ${FxMath.formatRate(op.rate)} = ${Money.ofMinor(op.rubMinor).format()}",
            style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, minSize = 10.sp
        )
    }
}
