package ru.maxrubtsoff.bankagg.ui.settings

import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokSwitch
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.data.notify.AppNotifier
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.ReminderSettings
import ru.maxrubtsoff.bankagg.domain.repository.ReminderRepository
import ru.maxrubtsoff.bankagg.ui.common.ChipButton
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * «Уведомления» (F-24): what the app tells about by itself. The system permission
 * is asked when the first switch goes on, not at launch; refused — a red plate
 * with one tap to the phone's settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(reminders: ReminderRepository, notifier: AppNotifier, features: Features, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by reminders.settings.collectAsStateWithLifecycle(initialValue = ReminderSettings())
    var allowed by remember { mutableStateOf(notifier.allowed()) }
    var pending by remember { mutableStateOf<ReminderSettings?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = notifier.allowed()
        if (ok) pending?.let { p -> scope.launch { reminders.set(p) } }
        pending = null
    }
    // Back from the phone's settings: the permission may have changed.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) allowed = notifier.allowed() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    fun change(next: ReminderSettings) {
        if (next.any && !allowed && Build.VERSION.SDK_INT >= 33) {
            pending = next
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else scope.launch { reminders.set(next) }
    }

    Scaffold(
        topBar = {
            ScreenHeader("Уведомления", onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!allowed && s.any) Denied {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Panel(Modifier.fillMaxWidth()) {
                val rows = buildList {
                    if (features.collectsNotifications) add(Triple("Новый банк", Icons.Filled.AccountBalance, Slitok.Brass2))
                    if (features.has(Feature.DEPOSITS)) add(Triple("Вклад заканчивается", Icons.Filled.EventAvailable, Slitok.Violet))
                    if (features.showCashback) add(Triple("Новый месяц кэшбэка", Icons.Filled.Percent, Slitok.Mint))
                }
                rows.forEachIndexed { i, (name, icon, color) ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp), color = Slitok.Line)
                    val on = allowed && when (name) {
                        "Новый банк" -> s.newBank
                        "Вклад заканчивается" -> s.depositEnding
                        else -> s.cashbackMonth
                    }
                    ToggleLine(name, icon, color, on) { v ->
                        change(
                            when (name) {
                                "Новый банк" -> s.copy(newBank = v)
                                "Вклад заканчивается" -> s.copy(depositEnding = v)
                                else -> s.copy(cashbackMonth = v)
                            }
                        )
                    }
                    if (name == "Вклад заканчивается" && on) {
                        Row(Modifier.padding(start = 68.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReminderSettings.DAY_CHOICES.forEach { n -> DayChip(n, n == s.daysBefore) { change(s.copy(daysBefore = n)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleLine(title: String, icon: ImageVector, color: Color, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) color.copy(alpha = 0.14f) else Slitok.Inset), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (on) color else Slitok.Faint)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        SlitokSwitch(checked = on, onChange = onChange)
    }
}

@Composable
private fun DayChip(n: Int, selected: Boolean, onClick: () -> Unit) {
    SlitokChip("за " + ru.maxrubtsoff.bankagg.domain.model.RuPlural.days(n), selected, onClick)
}

@Composable
private fun Denied(onAllow: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Slitok.Coral.copy(alpha = 0.14f), Slitok.PanelTop)))
            .border(1.dp, Slitok.Coral.copy(alpha = 0.45f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = Slitok.Coral)
        Text("Уведомления запрещены в телефоне", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        ChipButton("Разрешить", onClick = onAllow)
    }
}
