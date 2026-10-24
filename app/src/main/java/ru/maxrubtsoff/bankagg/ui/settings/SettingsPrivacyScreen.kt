package ru.maxrubtsoff.bankagg.ui.settings

import androidx.compose.material.icons.filled.VisibilityOff
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokSwitch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.ScreenRotation
import ru.maxrubtsoff.bankagg.ui.common.SlitokIcons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.lock.PinSetup
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** Настройки → Приватность: the app lock (F-10) and hiding amounts (F-11). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(vm: SettingsViewModel, biometricAvailable: Boolean, onBack: () -> Unit) {
    val pinSet by vm.pinSet.collectAsStateWithLifecycle()
    val biometric by vm.biometric.collectAsStateWithLifecycle()
    val flip by vm.hideOnFlip.collectAsStateWithLifecycle()
    val swipe by vm.hideBySwipe.collectAsStateWithLifecycle()
    val recents by vm.hideInRecents.collectAsStateWithLifecycle()
    // null — no PIN entry on screen; false — turning the lock on; true — changing the PIN.
    var settingPin by rememberSaveable { mutableStateOf<Boolean?>(null) }

    settingPin?.let { changing ->
        PinSetup(
            onDone = { pin ->
                vm.setPin(pin) {
                    // Right after a new lock: offer the fingerprint at once.
                    if (!changing && biometricAvailable) vm.setBiometric(true)
                }
                settingPin = null
            },
            onCancel = { settingPin = null }
        )
        return
    }

    Scaffold(
        topBar = {
            ScreenHeader("Приватность", onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Caption("Вход в приложение")
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow(Icons.Filled.Lock, "Код-пароль", "При запуске и после 10 минут в фоне", pinSet) { on ->
                        if (on) settingPin = false else vm.turnLockOff()
                    }
                    if (pinSet) {
                        if (biometricAvailable) {
                            HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
                            ToggleRow(Icons.Filled.Fingerprint, "Входить по отпечатку", null, biometric) { vm.setBiometric(it) }
                        }
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
                        LinkRow(Icons.Filled.Password, "Сменить код") { settingPin = true }
                    }
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
                    ToggleRow(Icons.Filled.VisibilityOff, "Скрывать в недавних", null, recents) { vm.setHideInRecents(it) }
                }
            }
            Caption("Скрытие сумм")
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow(Icons.Filled.ScreenRotation, "При перевороте телефона", "Экраном вниз — скрыть или показать", flip) { vm.setHideOnFlip(it) }
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = Slitok.Line)
                    ToggleRow(SlitokIcons.TwoFingerSwipeDown, "Свайпом", "Двумя пальцами вниз — скрыть или показать", swipe) { vm.setHideBySwipe(it) }
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Slitok.Muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Slitok.Brass2)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        }
        SlitokSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Slitok.Brass2)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint)
    }
}
