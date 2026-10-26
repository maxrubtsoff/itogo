package ru.maxrubtsoff.bankagg.ui.lock

import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SlitokDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.domain.usecase.CheckPin
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.common.errorShake
import ru.maxrubtsoff.bankagg.ui.common.pressScale
import ru.maxrubtsoff.bankagg.ui.common.rememberErrorShake
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded

/**
 * The lock screen (F-10). With the fingerprint on, the system prompt opens by
 * itself ([onBiometric] on show); the keypad is the way back. A wrong PIN
 * shakes the dots; after too many the keypad rests a while (B-27).
 * «Забыли код?» asks the phone's own lock and lets a new code be set without
 * touching the data; with no lock on the phone, only a reset with the data is left.
 */
@Composable
fun LockScreen(
    checkPin: suspend (String) -> CheckPin.Result,
    rest: suspend () -> CheckPin.Rest?,
    biometric: Boolean,
    onBiometric: () -> Unit,
    onUnlocked: () -> Unit,
    /** The phone's own lock check; null — the phone has no screen lock. */
    phoneCheck: ((onSuccess: () -> Unit) -> Unit)?,
    onNewPin: suspend (String) -> Unit,
    /** The phone check was passed earlier in this process: «new code» survives a turn, not a process death. */
    verified: Boolean,
    onVerified: () -> Unit,
    onReset: () -> Unit
) {
    var forgot by remember { mutableStateOf(false) }
    var settingNew by remember { mutableStateOf(verified) }
    var resting by remember { mutableStateOf<Pair<Long, Long>?>(null) }   // until (wall clock), next pause
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    fun restFor(r: CheckPin.Rest?) {
        now = System.currentTimeMillis()
        resting = r?.let { (now + it.leftMs) to it.nextMs }
    }
    LaunchedEffect(Unit) { restFor(rest()) }
    LaunchedEffect(biometric) { if (biometric) onBiometric() }
    LaunchedEffect(resting) {
        val until = resting?.first ?: return@LaunchedEffect
        while (System.currentTimeMillis() < until) {
            delay(250)
            now = System.currentTimeMillis()
        }
        resting = null
    }

    if (settingNew) {
        PinSetup(onDone = { pin -> scope.launch { onNewPin(pin); onUnlocked() } }, onCancel = { settingNew = false })
        return
    }

    val left = resting?.let { (until, _) -> ((until - now + 999) / 1000).coerceAtLeast(0) }
    PinPad(
        title = if (left != null) "Слишком много попыток" else "Введите код",
        check = { pin ->
            when (val r = checkPin(pin)) {
                CheckPin.Result.Ok -> { onUnlocked(); true }
                is CheckPin.Result.Wrong -> { restFor(r.rest); false }
                is CheckPin.Result.Paused -> { restFor(r.rest); false }
            }
        },
        biometric = if (biometric) onBiometric else null,
        footer = "Забыли код?",
        onFooter = { if (phoneCheck != null) phoneCheck { onVerified(); settingNew = true } else forgot = true },
        rest = left?.let { sec -> "%d:%02d".format(sec / 60, sec % 60) to (resting?.second ?: 0L) }
    )
    if (forgot) {
        SlitokDialog(
            title = "Сбросить код и данные?",
            onDismiss = { forgot = false },
            text = "На телефоне нет блокировки экрана, поэтому проверить, что это вы, нечем. " +
                "Код сбросится вместе с данными приложения. Вернуть их можно из копии с паролем."
        ) {
            PlateButton(onClick = { forgot = false }) { Text("Отмена") }
            DangerButton(onClick = { forgot = false; onReset() }, filled = true) { Text("Сбросить") }
        }
    }
}

/** A new PIN: typed twice; a mismatch shakes and starts over. */
@Composable
fun PinSetup(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    PinPad(
        title = if (first == null) "Придумайте код" else "Повторите код",
        check = { pin ->
            val f = first
            when {
                f == null -> { first = pin; true }
                f == pin -> { onDone(pin); true }
                else -> { first = null; false }
            }
        },
        biometric = null,
        footer = "Отмена",
        onFooter = onCancel
    )
}

/**
 * Four dots and a digit keypad. [check] gets the full PIN; false shakes and
 * clears. [biometric], when given, puts the fingerprint key bottom-left.
 */
@Composable
private fun PinPad(
    title: String,
    check: suspend (String) -> Boolean,
    biometric: (() -> Unit)?,
    footer: String,
    onFooter: () -> Unit,
    /** Resting after wrong tries: the countdown («0:27») and the next pause length (B-27). */
    rest: Pair<String, Long>? = null
) {
    var pin by remember(title) { mutableStateOf("") }
    val shake = rememberErrorShake(strong = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun type(d: Char) {
        if (rest != null || pin.length >= AppLock.PIN_LENGTH) return
        pin += d
        if (pin.length == AppLock.PIN_LENGTH) {
            val typed = pin
            scope.launch {
                if (!check(typed)) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    shake.play()
                }
                pin = ""
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // The app underneath gets no touches.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .background(Brush.radialGradient(listOf(Slitok.PanelTop, Slitok.Bg), radius = 1400f))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (rest != null) Brush.linearGradient(listOf(Slitok.Coral.copy(alpha = 0.16f), Slitok.Coral.copy(alpha = 0.16f)))
                        else Brush.linearGradient(listOf(Slitok.Brass2, Slitok.Brass))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (rest != null) Icon(Icons.Filled.Timer, contentDescription = null, tint = Slitok.Coral, modifier = Modifier.size(34.dp))
                else Icon(Icons.Filled.Lock, contentDescription = null, tint = Slitok.OnBrass, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(24.dp))
            if (rest != null) {
                Text(
                    rest.first,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Slitok.Coral
                )
                Spacer(Modifier.height(8.dp))
                Text("Следующая пауза — ${RuPlural.pause(rest.second)}".nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
            } else Row(
                Modifier.errorShake(shake),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                repeat(AppLock.PIN_LENGTH) { i ->
                    val filled = i < pin.length
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (filled) Slitok.Brass2 else Slitok.Bg)
                            .border(2.dp, if (filled) Slitok.Brass2 else Slitok.Faint, CircleShape)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        // Resting: the digits fade and do nothing; the fingerprint still works.
                        Box(Modifier.graphicsLayer { alpha = if (rest != null && k != "bio") 0.25f else 1f }) {
                        when (k) {
                            "bio" -> if (biometric != null) Key(onClick = biometric, accent = true) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = "Войти по отпечатку", tint = Slitok.Brass2, modifier = Modifier.size(30.dp))
                            } else Spacer(Modifier.size(76.dp))
                            "del" -> Key(onClick = { pin = pin.dropLast(1) }, plain = true) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Стереть", tint = Slitok.Muted)
                            }
                            else -> Key(onClick = { type(k[0]) }) {
                                Text(k, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                footer,
                style = MaterialTheme.typography.bodyMedium,
                color = Slitok.Muted,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onFooter).padding(12.dp)
            )
        }
    }
}

@Composable
private fun Key(onClick: () -> Unit, accent: Boolean = false, plain: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .pressScale()
            .clip(CircleShape)
            .background(
                when {
                    plain -> androidx.compose.ui.graphics.Color.Transparent
                    accent -> Slitok.BrassTint
                    else -> Slitok.PanelTop
                }
            )
            .border(1.dp, if (plain) androidx.compose.ui.graphics.Color.Transparent else if (accent) Slitok.BrassLine else Slitok.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
