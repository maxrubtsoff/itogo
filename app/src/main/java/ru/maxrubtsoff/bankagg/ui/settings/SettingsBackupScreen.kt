package ru.maxrubtsoff.bankagg.ui.settings

import ru.maxrubtsoff.bankagg.ui.common.TagTone
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.SlitokField
import androidx.compose.material.icons.filled.Lock
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.ListRow
import ru.maxrubtsoff.bankagg.ui.common.ButtonRow
import ru.maxrubtsoff.bankagg.ui.common.SlitokDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.data.backup.AutoBackup
import ru.maxrubtsoff.bankagg.data.backup.BackupManager
import ru.maxrubtsoff.bankagg.data.backup.BackupPassword
import ru.maxrubtsoff.bankagg.domain.model.BackupRotation
import ru.maxrubtsoff.bankagg.domain.model.CopyKind
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.ui.common.SheetIconTile
import ru.maxrubtsoff.bankagg.ui.common.SheetList
import ru.maxrubtsoff.bankagg.ui.common.SheetRow
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val RESTORE_SCOPE = "счета, операции, вклады, валюта, наличные, кэшбэк, подписки и настройки"

/** "сегодня в 14:32". */
fun backupMoment(at: Long, today: LocalDate = LocalDate.now()): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    return "${RuDays.relative(t.toLocalDate(), today)} в %02d:%02d".format(t.hour, t.minute)
}

/** "Сегодня, 19:04". */
private fun copyMoment(day: LocalDate, time: LocalTime, today: LocalDate): String =
    RuDays.relative(day, today).replaceFirstChar { it.uppercase() } + ", %02d:%02d".format(time.hour, time.minute)

/**
 * The backup keeps itself (U-30): one panel says when the last copy was made,
 * with «Сохранить сейчас» and «Восстановить» right under it and «Отправить
 * копию» beside. Every copy in the list has its time; restoring first saves
 * what is there now, and the snackbar offers «Вернуть» (B-26).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
    backup: BackupManager,
    auto: AutoBackup,
    password: BackupPassword,
    undo: UndoCenter,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastSaved by auto.lastSaved.collectAsStateWithLifecycle()
    var copies by remember { mutableStateOf<List<BackupManager.Copy>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<BackupManager.Copy?>(null) }
    var pickedFile by remember { mutableStateOf<Uri?>(null) }
    var fileWrong by remember { mutableStateOf(false) }
    // U-46: the password for copies sent elsewhere — set, shown after a check, changed.
    var settingPassword by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var passwordMenu by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf<String?>(null) }
    var hasPassword by remember { mutableStateOf(password.get() != null) }
    val today = LocalDate.now()

    LaunchedEffect(lastSaved) { copies = runCatching { backup.copies() }.getOrDefault(emptyList()) }

    fun run(block: suspend () -> Unit) {
        busy = true; error = null
        scope.launch {
            runCatching { block() }.onFailure {
                error = (it as? BackupManager.RestoreError)?.message ?: "Не получилось: ${it.message}"
            }
            busy = false
        }
    }

    fun restored(before: BackupManager.Copy?) {
        auto.flush()
        if (before != null) undo.offer("Данные восстановлены", restore = { backup.restore(before, keepCurrent = false) })
    }

    fun openFile(uri: Uri, pw: CharArray?) = run {
        when (backup.restoreFrom(uri, pw)) {
            BackupManager.FileResult.Restored -> { pickedFile = null; fileWrong = false; restored(null) }
            BackupManager.FileResult.NeedsPassword -> { pickedFile = uri; fileWrong = false }
            BackupManager.FileResult.WrongPassword -> fileWrong = true
        }
    }

    fun share(pw: CharArray) = run {
        val file = backup.exportForSharing(pw)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Отправить копию"))
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) openFile(uri, null)
    }

    Scaffold(
        topBar = {
            ScreenHeader("Резервная копия", onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = Slitok.Mint, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Копия сохраняется сама", style = MaterialTheme.typography.titleMedium)
                            Text(
                                lastSaved?.let { "Последняя — ${backupMoment(it, today)}" } ?: "Сохраню при первом изменении данных",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Slitok.Muted
                            )
                        }
                    }
                    ButtonRow {
                        BrassButton(onClick = { run { auto.saveNow() } }, enabled = !busy) { Text("Сохранить") }
                        PlateButton(onClick = { listOpen = true }, enabled = !busy) { Text("Восстановить") }
                    }
                }
            }
            Text("НА ДРУГОЙ ТЕЛЕФОН", style = MaterialTheme.typography.labelSmall, color = Slitok.Faint, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    ListRow(
                        "Отправить копию",
                        onClick = { if (!busy) password.get()?.let(::share) ?: run { settingPassword = true } },
                        leading = { Icon(Icons.Filled.Share, contentDescription = null, tint = Slitok.Muted) }
                    )
                    HorizontalDivider(Modifier.padding(start = 52.dp), color = Slitok.Line)
                    ListRow(
                        "Пароль копии",
                        onClick = { if (hasPassword) passwordMenu = true else changingPassword = true },
                        leading = { Icon(Icons.Filled.Lock, contentDescription = null, tint = Slitok.Muted) },
                        trailing = { Text(if (hasPassword) "задан" else "не задан", style = MaterialTheme.typography.bodyLarge, color = Slitok.Muted) }
                    )
                }
            }
            error?.let { Text(it.nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Coral) }
        }
    }

    if (listOpen) {
        SlitokSheet(onDismiss = { listOpen = false }) {
            Text("Восстановить", style = MaterialTheme.typography.titleLarge)
            Text("Копии за ${BackupRotation.KEEP_DAYS} дней".nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
            Spacer(Modifier.height(8.dp))
            if (copies.isEmpty()) {
                Text("Копий на телефоне пока нет.", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                SheetList {
                    copies.forEach { c ->
                        SheetRow(
                            title = copyMoment(c.day, c.time, today),
                            subtitle = RuDays.weekday(c.day),
                            trailing = when (c.kind) {
                                CopyKind.MANUAL -> ({ SlitokTag("вручную", TagTone.Brass) })
                                CopyKind.BEFORE_RESTORE -> ({ SlitokTag("до восстановления", TagTone.Neutral) })
                                CopyKind.AUTO -> null
                            },
                            onClick = { listOpen = false; confirm = c }
                        )
                    }
                }
            }
            // A quiet action, not a brass one: the list above is the usual way.
            SheetList {
                SheetRow(
                    "Из файла…",
                    leading = { SheetIconTile(Icons.Filled.FolderOpen) },
                    arrow = true,
                    onClick = {
                        listOpen = false
                        pickFile.launch(arrayOf("application/octet-stream", "application/json", "text/*", "*/*"))
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    confirm?.let { c ->
        SlitokDialog(
            title = "Вернуть данные на ${copyMoment(c.day, c.time, today).replaceFirstChar { it.lowercase() }}?",
            onDismiss = { confirm = null },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Заменятся $RESTORE_SCOPE.".nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Slitok.Mint.copy(alpha = 0.08f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = Slitok.Mint, modifier = Modifier.size(20.dp))
                        Text("Текущие данные сохранятся отдельной копией".nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Fg)
                    }
                }
            }
        ) {
            PlateButton(onClick = { confirm = null }) { Text("Отмена") }
            BrassButton(onClick = { confirm = null; run { restored(backup.restore(c)) } }) { Text("Восстановить") }
        }
    }

    pickedFile?.let { uri ->
        PasswordSheet(
            title = "Пароль копии",
            action = "Восстановить",
            confirmTwice = false,
            wrong = fileWrong,
            onDismiss = { pickedFile = null; fileWrong = false },
            onDone = { pw -> openFile(uri, pw) }
        )
    }

    if (settingPassword) {
        PasswordSheet(
            title = "Пароль для копии",
            action = "Отправить",
            confirmTwice = true,
            wrong = false,
            onDismiss = { settingPassword = false },
            onDone = { pw -> settingPassword = false; password.set(pw); hasPassword = true; share(pw) }
        )
    }

    if (changingPassword) {
        PasswordSheet(
            title = if (hasPassword) "Новый пароль копии" else "Пароль копии",
            action = "Сохранить",
            confirmTwice = true,
            wrong = false,
            onDismiss = { changingPassword = false },
            onDone = { pw -> changingPassword = false; password.set(pw); hasPassword = true }
        )
    }

    if (passwordMenu) {
        SlitokSheet(onDismiss = { passwordMenu = false; revealed = null }) {
            Text("Пароль копии", style = MaterialTheme.typography.titleLarge)
            revealed?.let {
                Text(it, style = Amounts.Row, color = Slitok.Fg, modifier = Modifier.padding(vertical = 4.dp))
            }
            ButtonRow {
                PlateButton(onClick = {
                    if (revealed != null) revealed = null
                    else confirmOwner(context) { revealed = password.get()?.let { String(it) } }
                }) { Text(if (revealed == null) "Показать" else "Скрыть") }
                PlateButton(onClick = { passwordMenu = false; revealed = null; changingPassword = true }) { Text("Сменить") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * U-46: the copy password is shown only to the phone's owner — after the
 * fingerprint or the phone's screen lock. A phone without either has nothing
 * to ask with, so it shows at once.
 */
private fun confirmOwner(context: android.content.Context, onOk: () -> Unit) {
    val activity = context as? androidx.fragment.app.FragmentActivity ?: return onOk()
    val kinds = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (androidx.biometric.BiometricManager.from(activity).canAuthenticate(kinds) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) return onOk()
    runCatching {
        androidx.biometric.BiometricPrompt(
            activity,
            androidx.core.content.ContextCompat.getMainExecutor(activity),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) = onOk()
            }
        ).authenticate(
            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Пароль копии")
                .setSubtitle("Подтвердите, что это вы")
                .setAllowedAuthenticators(kinds)
                .build()
        )
    }.onFailure { onOk() }
}

/**
 * A password for copies sent elsewhere (F-26). Setting one asks twice; the
 * shortest allowed is [MIN_PASSWORD] characters — a 4-digit code would be
 * guessed in minutes.
 */
@Composable
private fun PasswordSheet(
    title: String,
    action: String,
    confirmTwice: Boolean,
    wrong: Boolean,
    onDismiss: () -> Unit,
    onDone: (CharArray) -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val tooShort = confirmTwice && first.isNotEmpty() && first.length < MIN_PASSWORD
    val mismatch = confirmTwice && second.isNotEmpty() && second != first
    val ready = if (confirmTwice) first.length >= MIN_PASSWORD && second == first else first.isNotEmpty()
    SlitokSheet(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        PasswordField(first, { first = it }, "Пароль", shown, { shown = !shown })
        if (confirmTwice) {
            Spacer(Modifier.height(10.dp))
            PasswordField(second, { second = it }, "Ещё раз", shown, { shown = !shown })
        }
        if (confirmTwice) {
            Text("Без пароля копию не открыть — сохраните его.".nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        }
        val note = when {
            wrong -> "Пароль не подходит"
            tooShort -> "Не короче $MIN_PASSWORD символов"
            mismatch -> "Пароли не совпадают"
            else -> null
        }
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Coral)
        }
        Spacer(Modifier.height(16.dp))
        BrassButton(onClick = { onDone(first.toCharArray()) }, Modifier.fillMaxWidth(), enabled = ready) { Text(action) }
        Spacer(Modifier.height(20.dp))
    }
}

private const val MIN_PASSWORD = 8

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, shown: Boolean, onToggle: () -> Unit) {
    SlitokField(value, onChange, label = label, password = true)
}
