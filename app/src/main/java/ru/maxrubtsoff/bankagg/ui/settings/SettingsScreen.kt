package ru.maxrubtsoff.bankagg.ui.settings

import androidx.compose.material.icons.filled.Palette
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeaderGap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Notifications
import ru.maxrubtsoff.bankagg.ui.common.LocalMyBanks
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.domain.model.Feature
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onOpenBanks: () -> Unit,
    onOpenFeatures: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenIntro: () -> Unit = {},
    onOpenShowcase: () -> Unit = {},
    lastBackup: Long? = null
) {
    val selected by vm.selectedPackages.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        ScreenHeader("Настройки")
        Spacer(Modifier.height(ScreenHeaderGap))
        Panel(Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Filled.Dashboard,
                title = "Что храним",
                subtitle = Feature.entries.filter { features.has(it) }.joinToString(" · ") { it.title() },
                onClick = onOpenFeatures
            )
            if (features.needsBanks) {
            HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
            SettingsRow(
                icon = Icons.Filled.AccountBalance,
                title = "Банки",
                subtitle = LocalMyBanks.current.let { if (it.isEmpty()) "Не добавлены" else RuPlural.banks(it.size) },
                onClick = onOpenBanks
            )
            }
            HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = "Уведомления",
                subtitle = "Новые банки, вклады, кэшбэк",
                onClick = onOpenNotifications
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = "Приватность",
                subtitle = "Код-пароль, скрытие сумм",
                onClick = onOpenPrivacy
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
            SettingsRow(
                icon = Icons.Filled.Save,
                title = "Резервная копия",
                subtitle = lastBackup?.let { "Сохранена ${backupMoment(it)}" } ?: "Сохраняется сама",
                onClick = onOpenBackup
            )
            if (features.collectsNotifications) {
            HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = "Журнал перехвата",
                subtitle = "Диагностика уведомлений",
                onClick = onOpenLog
            )
            }
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            // F-29: the intro slides again, for whoever skipped them.
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "Обучение",
                subtitle = "Как пользоваться разделами",
                onClick = onOpenIntro
            )
        }
        // F-31: the design system's showcase — only in the debug build.
        val debuggable = (LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                SettingsRow(icon = Icons.Filled.Palette, title = "Витрина", subtitle = "Компоненты «Слитка»", onClick = onOpenShowcase)
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Slitok.Brass2)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint)
    }
}
