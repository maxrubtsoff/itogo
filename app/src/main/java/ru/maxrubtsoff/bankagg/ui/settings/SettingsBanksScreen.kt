package ru.maxrubtsoff.bankagg.ui.settings

import androidx.compose.foundation.clickable
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.runtime.setValue
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.common.SectionActions
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.ChipButton
import ru.maxrubtsoff.bankagg.ui.common.ListRow
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import ru.maxrubtsoff.bankagg.ui.common.NotificationAccessBanner
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.rememberNotificationAccess
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.util.InstalledApps
import ru.maxrubtsoff.bankagg.util.Perms

/**
 * The user's banks (F-22): a list, «+» to add more. A bank with an app has a
 * bell — collect its notifications or not; an own bank opens its editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBanksScreen(vm: BanksViewModel, features: Features, onBack: () -> Unit, onAdd: () -> Unit, onOpenNew: () -> Unit, onEdit: (BankBrand) -> Unit, onOpen: (BankBrand) -> Unit) {
    var removing by remember { mutableStateOf<BankBrand?>(null) }
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mine by vm.mine.collectAsStateWithLifecycle()
    val notBanks by vm.notBanks.collectAsStateWithLifecycle()
    val collected by vm.collected.collectAsStateWithLifecycle()
    val notifAccess by rememberNotificationAccess()
    val collectsNotifications = features.collectsNotifications
    val unrestricted by rememberOnResume { Perms.isBatteryUnrestricted(context) }

    Scaffold(
        topBar = { ScreenHeader("Банки", onBack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionActions(
                summary = if (mine.isEmpty()) "Банков пока нет" else RuPlural.banks(mine.size),
                onAdd = onAdd,
                addDescription = "Добавить банк"
            )
            // The words follow what the notifications feed: balances with accounts, else subscriptions (U-52).
            if (collectsNotifications && !notifAccess) {
                if (features.showNotifAccessOnOverview) NotificationAccessBanner()
                else NotificationAccessBanner(text = "Подписки не находятся — нет доступа к уведомлениям")
            }
            if (collectsNotifications && !unrestricted) BatteryBanner { Perms.openBatterySettings(context) }
            if (collectsNotifications && candidates.isNotEmpty()) {
                PlateButton(onClick = onOpenNew, modifier = Modifier.fillMaxWidth()) { Text("Новые банки · ${candidates.size}") }
            }

            if (mine.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
                mine.forEachIndexed { i, b ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = Slitok.Line)
                    val on = b.packages.any { it in collected }
                    BankRow(
                        bank = b,
                        collecting = on,
                        showBell = collectsNotifications && b.packages.isNotEmpty(),
                        onBell = { vm.toggleCollect(b) },
                        onOpen = { onOpen(b) },
                        onEdit = { onEdit(b) },
                        onRemove = { removing = b }
                    )
                }
            }

            if (collectsNotifications && notBanks.isNotEmpty()) {
                NotBanksPanel(notBanks, onReturn = vm::unignore)
            }
        }
    }
    removing?.let { b -> RemoveBankSheet(b, onConfirm = { removing = null; vm.remove(b) }, onDismiss = { removing = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BankRow(bank: BankBrand, collecting: Boolean, showBell: Boolean, onBell: () -> Unit, onOpen: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    // U-45: no long press — the name and «Убрать банк» live in the bank's card.
    run {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BankIcon(bank, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(bank.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        !showBell -> if (bank.isUser) "свой банк" else "без приложения"
                        collecting -> "уведомления собираются"
                        else -> "уведомления не собираются"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (showBell && collecting) Slitok.Mint else Slitok.Muted
                )
            }
            if (showBell) {
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(shape)
                        .background(if (collecting) Slitok.Mint.copy(alpha = 0.14f) else Slitok.Inset)
                        .border(1.dp, if (collecting) Slitok.Mint.copy(alpha = 0.45f) else Slitok.Line, shape)
                        .combinedClickable(onClick = onBell),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (collecting) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                        contentDescription = if (collecting) "Не собирать уведомления" else "Собирать уведомления",
                        tint = if (collecting) Slitok.Mint else Slitok.Faint
                    )
                }
            }
        }
    }
}

/**
 * Apps the user answered «Не банк» about (B-57): folded away at the bottom,
 * one tap returns an app to the questions.
 */
@Composable
private fun NotBanksPanel(packages: Set<String>, onReturn: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Panel(Modifier.fillMaxWidth()) {
        ListRow(
            title = "Не банки · ${packages.size}",
            onClick = { open = !open },
            trailing = {
                Text(if (open) "скрыть" else "показать", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
            }
        )
        if (open) {
            packages.sorted().forEach { pkg ->
                HorizontalDivider(Modifier.padding(start = 16.dp), color = Slitok.Line)
                ListRow(
                    title = InstalledApps.labelOf(context, pkg) ?: pkg,
                    trailing = { ChipButton("Вернуть", onClick = { onReturn(pkg) }) }
                )
            }
        }
    }
}

/** Shown only while the system may freeze the app; gone once the limit is lifted. */
@Composable
private fun BatteryBanner(onFix: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Slitok.Coral.copy(alpha = 0.16f), Slitok.PanelTop)))
            .border(1.dp, Slitok.Coral.copy(alpha = 0.45f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Slitok.Coral.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = Slitok.Coral)
        }
        Column(Modifier.weight(1f)) {
            Text("Фоновая работа ограничена", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Уведомления банков могут теряться", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        }
        ChipButton("Снять", onClick = onFix)
    }
}

/** [check] on the first frame and again every time the screen comes back. */
@Composable
private fun rememberOnResume(check: () -> Boolean): State<Boolean> {
    val owner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(check()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = check()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}
