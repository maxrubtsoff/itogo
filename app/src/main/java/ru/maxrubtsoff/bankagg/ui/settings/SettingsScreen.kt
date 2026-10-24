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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.pm.PackageInfoCompat
import ru.maxrubtsoff.bankagg.ui.common.BankStack
import ru.maxrubtsoff.bankagg.ui.common.StatTile
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
import ru.maxrubtsoff.bankagg.domain.model.ReminderKinds
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Settings in groups (U-59): two tiles on top — what is kept and the banks —
 * then notifications, privacy and the backup in one panel, then the tour and,
 * quietly, the version. The capture log lives in «⋮» of the Capital's accounts.
 */
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onOpenBanks: () -> Unit,
    onOpenFeatures: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenIntro: () -> Unit = {},
    onOpenShowcase: () -> Unit = {},
    lastBackup: Long? = null
) {
    val features by vm.features.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            ?.let { "${it.versionName} · сборка ${PackageInfoCompat.getLongVersionCode(it)}" }
    }

    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        ScreenHeader("Настройки")
        Spacer(Modifier.height(ScreenHeaderGap))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Что храним", Modifier.weight(1f).fillMaxHeight(), onClick = onOpenFeatures) {
                    Text(features.keptLabel, style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Without a bank-tied section there are no banks to keep (U-59).
                if (features.needsBanks) {
                    val mine = LocalMyBanks.current
                    StatTile("Банки", Modifier.weight(1f).fillMaxHeight(), onClick = onOpenBanks) {
                        if (mine.isEmpty()) {
                            Text("Не добавлены", style = MaterialTheme.typography.titleMedium, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BankStack(mine, max = 2, size = 28.dp, overlap = 8.dp)
                                Text(mine.size.toString(), style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = "Уведомления",
                    // Built from what the user keeps (U-52).
                    subtitle = ReminderKinds.subtitle(features),
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
            }
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
            val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (debuggable) {
                Panel(Modifier.fillMaxWidth()) {
                    SettingsRow(icon = Icons.Filled.Palette, title = "Витрина", subtitle = "Компоненты «Слитка»", onClick = onOpenShowcase)
                }
            }
            if (version != null) {
                Text(
                    version,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slitok.Muted,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
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
