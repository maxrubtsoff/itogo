package ru.maxrubtsoff.bankagg.ui.subscriptions

import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.common.tourTarget
import ru.maxrubtsoff.bankagg.ui.common.rememberTabScrollState
import ru.maxrubtsoff.bankagg.ui.common.GhostButton
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Finding
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.model.Subscriptions
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate

/**
 * «Подписки» in the «Платежи» tab (F-27): what looks like a subscription on
 * top, the monthly total, then the list by the nearest payment.
 */
@Composable
fun SubscriptionsSection(vm: SubscriptionsViewModel, onOpen: (Subscription) -> Unit, onCreate: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var finding by remember { mutableStateOf<Finding?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberTabScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.findings.firstOrNull()?.let { f ->
            Row(
                Modifier.fillMaxWidth().tourTarget(TourTarget.FINDING, radius = 18.dp).clip(RoundedCornerShape(18.dp)).background(Slitok.BrassTint)
                    .border(1.dp, Slitok.BrassLine, RoundedCornerShape(18.dp))
                    .clickable { finding = f }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Slitok.BrassPill), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Slitok.Brass2, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.findings.size > 1) "Похоже на подписку · ещё ${state.findings.size - 1}" else "Похоже на подписку",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold
                    )
                    MoneyText(
                        "${f.payee} · ${Money.ofMinor(f.priceMinor).format()} · ${f.period.label()}".nbsp(),
                        style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Brass2)
            }
        }

        if (state.active.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Подписок пока нет", style = MaterialTheme.typography.titleMedium)
                    BrassButton(onClick = { vm.newBlank(); onCreate() }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Добавить подписку")
                    }
                }
            }
        } else {
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("В месяц", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                        MoneyText(Money.ofMinor(state.perMonth).format(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    MoneyText(
                        "${Money.ofMinor(state.perMonth * 12).format()} в год",
                        style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, softWrap = false
                    )
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                state.active.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                    Box(Modifier.tourTarget(TourTarget.LIST_ROW, active = i == 0, radius = 16.dp)) { SubscriptionRow(s, state.today) { onOpen(s) } }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    finding?.let { f ->
        FindingSheet(
            f, state.active,
            onLink = { s -> vm.link(f, s); finding = null },
            onCreate = { vm.newFrom(f); finding = null; onCreate() },
            onNot = { vm.notSubscription(f); finding = null },
            onDismiss = { finding = null }
        )
    }
}

@Composable
private fun SubscriptionRow(s: Subscription, today: LocalDate, onClick: () -> Unit) {
    val next = Subscriptions.upcoming(s, today)
    val soon = next.toEpochDay() - today.toEpochDay() <= 3
    val raise = s.raisedBy
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubscriptionAvatar(s.title, s.brand)
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val whenText = Subscriptions.whenText(next, today)
            MoneyText(
                when {
                    raise != null -> "подорожала на ${Money.ofMinor(raise).format()} · $whenText"
                    else -> listOfNotNull(whenText, s.brand?.title).joinToString(" · ")
                }.nbsp(),
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    raise != null -> Slitok.Coral
                    soon -> Slitok.Brass2
                    else -> Slitok.Muted
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        MoneyText(Money.ofMinor(s.priceMinor).format(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** The bank's icon when known, otherwise the first letter on a plate. */
@Composable
internal fun SubscriptionAvatar(title: String, brand: BankBrand?, size: androidx.compose.ui.unit.Dp = 36.dp) {
    if (brand != null) {
        BankIcon(brand, size = size)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Slitok.PanelTop).border(1.dp, Slitok.Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = Slitok.Brass2)
        }
    }
}

/**
 * «Похоже на подписку» (F-27): the payments behind the guess, then the choice —
 * one of the user's subscriptions, a new one, or not a subscription at all.
 */
@Composable
private fun FindingSheet(
    f: Finding,
    active: List<Subscription>,
    onLink: (Subscription) -> Unit,
    onCreate: () -> Unit,
    onNot: () -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    SlitokSheet(onDismiss) {
        Text("Похоже на подписку", style = MaterialTheme.typography.titleLarge)
        MoneyText("${f.payee} · ${Money.ofMinor(f.priceMinor).format()} · ${f.period.label()}".nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Slitok.Inset)) {
            f.payments.sortedByDescending { it.date }.forEachIndexed { i, p ->
                if (i > 0) HorizontalDivider(color = Slitok.Line)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(RuDays.relative(p.date, today).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                        Text(p.brand.title, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                    }
                    MoneyText(Money.ofMinor(p.amountMinor).format(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
        }
        if (active.isNotEmpty()) {
            Text("Это одна из ваших?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            // The closest price first: the likeliest match.
            val ordered = active.sortedBy { kotlin.math.abs(it.priceMinor - f.priceMinor) }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Slitok.Inset)) {
                ordered.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider(color = Slitok.Line)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onLink(s) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SubscriptionAvatar(s.title, s.brand, size = 30.dp)
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MoneyText(Money.ofMinor(s.priceMinor).format(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, softWrap = false)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Muted)
                    }
                }
            }
        }
        BrassButton(onClick = onCreate, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Создать подписку «${f.title}»", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GhostButton("Не подписка", onClick = onNot, modifier = Modifier.fillMaxWidth(), quiet = true)
        Spacer(Modifier.width(1.dp).height(12.dp))
    }
}
