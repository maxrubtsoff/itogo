package ru.maxrubtsoff.bankagg.ui.deposits

import androidx.compose.ui.text.withStyle
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Delete
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.StatTile
import ru.maxrubtsoff.bankagg.ui.common.MoreAction
import ru.maxrubtsoff.bankagg.ui.common.MoreMenu
import ru.maxrubtsoff.bankagg.ui.common.sharedCard
import ru.maxrubtsoff.bankagg.ui.common.RollingAmount
import ru.maxrubtsoff.bankagg.domain.model.Money
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.RuText
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositDetailScreen(
    vm: DepositsViewModel,
    depositId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    openTopUp: Boolean = false
) {
    val item by remember(depositId) { vm.observeItem(depositId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val today by vm.todayDate.collectAsStateWithLifecycle()
    val d = item?.deposit
    var toppingUp by remember { mutableStateOf(openTopUp) }

    Scaffold(
        topBar = {
            ScreenHeader(
                onBack,
                // B-33: the deposit's name is the title — two lines, then «…»; the bank and rate under it.
                title = {
                    if (d != null) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(depositName(d), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BankIcon(d.brand, size = 20.dp)
                            Text(
                                d.brand.title + RuText.DOT + ratePercent(d.rateBp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Slitok.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // U-45: changing and deleting live here, not in a long press on the list.
                    MoreMenu(
                        listOf(
                            MoreAction("Изменить", Icons.Filled.Edit) { onEdit(depositId) },
                            MoreAction("Удалить", Icons.Filled.Delete, danger = true) { vm.delete(depositId); onBack() }
                        )
                    )
                }
            )
        }
    ) { padding ->
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Вклад не найден", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
            }
            return@Scaffold
        }
        val proj = item!!.projection

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Hero(d, proj, today)
            Tiles(proj)
            MonthsCard(d, today)
            FactsCard(d)
            TopUpsBlock(d, today, onTopUp = { toppingUp = true }, onDelete = { vm.deleteTopUp(d.id, it) })
            Spacer(Modifier.height(16.dp))
        }
    }
    if (toppingUp && d != null) {
        TopUpSheet(d, today, onSave = { minor, date -> vm.addTopUp(d.id, minor, date); toppingUp = false }, onDismiss = { toppingUp = false })
    }
}

@Composable
private fun Hero(d: Deposit, proj: DepositMath.Projection, today: LocalDate) {
    val accruedLabel = if (d.method == AccrualMethod.PAYOUT) "получено" else "накоплено"
    Panel(modifier = Modifier.fillMaxWidth().sharedCard("deposit-${d.id}"), radius = 22.dp) {
        Box(Modifier.fillMaxWidth().heroGlow(Slitok.Violet)) {
            Column(Modifier.padding(20.dp)) {
                // U-42: the main figure stands alone, no «Стоимость сейчас» over it.
                RollingAmount(proj.currentValue.minor) { v ->
                    AmountText(money(Money.ofMinor(v)), style = Amounts.Hero, color = Slitok.Fg, minSize = 20.sp)
                }
                Spacer(Modifier.height(6.dp))
                AmountText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Slitok.Mint, fontWeight = FontWeight.SemiBold)) { append(signedMoney(proj.accruedIncome)) }
                        append(" $accruedLabel за ${RuPlural.days(proj.elapsedDays)}".nbsp())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slitok.Muted,
                    minSize = 11.sp
                )
                Spacer(Modifier.height(16.dp))
                ProgressBar(proj.progress.toFloat())
                Spacer(Modifier.height(8.dp))
                // U-37: no «N% срока» — the bar already shows it.
                Text(
                    (remainingLabel(d.endDate, today) + RuText.DOT + "до ${formatDate(d.endDate)}").nbsp(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slitok.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One chart for the whole term (B-7, F-15): the next accrual, the picked month, the ribbon. */
@Composable
private fun MonthsCard(d: Deposit, today: LocalDate) {
    val next = remember(d, today) { DepositMath.nextAccrual(d, today) }
    Panel(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("По месяцам", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Legend()
            }
            if (next != null) {
                val verb = if (d.method == AccrualMethod.PAYOUT) "выплатим" else "начислим"
                AmountText(
                    "${dayMonth(next.date)} $verb ещё ${money(next.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slitok.Mint,
                    minSize = 11.sp
                )
            }
            MonthsChart(d, today)
        }
    }
}

/** What comes at the end (U-36): the payout and the term's income; amounts in text colour, income in mint (B-34). */
@Composable
private fun Tiles(proj: DepositMath.Projection) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("К выдаче", Modifier.weight(1f)) {
            AmountText(MoneyCompact.format(proj.payoutAmount), style = Amounts.Large, color = Slitok.Fg, minSize = 14.sp, hideAll = true)
        }
        StatTile("Доход за срок", Modifier.weight(1f)) {
            AmountText(MoneyCompact.format(proj.totalIncome, signed = true), style = Amounts.Large, color = Slitok.Mint, minSize = 14.sp, hideAll = true)
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(Slitok.Violet, "на вкладе")
        LegendDot(Slitok.Mint, "начислено")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.height(8.dp).width(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
    }
}

/** The rest, quietly (U-36): what went in, the term, how interest comes. No repeats of the tiles. */
@Composable
private fun FactsCard(d: Deposit) {
    Panel(modifier = Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Fact("Вложено", money(d.investedTotal), Slitok.Fg, divider = true)
            Fact("Срок", "${formatDate(d.startDate)} → ${formatDate(d.endDate)}", Slitok.Muted, divider = true)
            Fact("Начисление", methodLabel(d.method), Slitok.Muted, divider = false)
        }
    }
}

@Composable
private fun Fact(label: String, value: String, valueColor: Color, divider: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Spacer(Modifier.width(12.dp))
            AmountText(
                value,
                style = if (MoneyMask.hasMoney(value)) Amounts.Row else MaterialTheme.typography.bodyLarge,
                color = valueColor,
                minSize = 11.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Slitok.Line))
    }
}
