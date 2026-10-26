package ru.maxrubtsoff.bankagg.ui.storage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.data.local.StorageStatus
import ru.maxrubtsoff.bankagg.domain.model.StorageAction
import ru.maxrubtsoff.bankagg.domain.model.StorageDecision
import ru.maxrubtsoff.bankagg.domain.model.StorageGuard
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.GhostButton
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.common.SlitokDialog
import ru.maxrubtsoff.bankagg.ui.common.SlitokField
import ru.maxrubtsoff.bankagg.ui.common.nbsp

/**
 * The last resort when the data does not open (B-91). It says in plain words
 * whether to wait, whether the data is still there, and the few things the
 * person can do. Nothing here deletes anything.
 */
@Composable
fun StorageErrorScreen(vm: StorageErrorViewModel, onClose: () -> Unit) {
    val status by vm.status.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    // Quiet retries first: a busy moment of the phone is not worth a screen.
    if (state.quiet && status is StorageStatus.Waiting) {
        Box(Modifier.fillMaxSize())
        return
    }
    val decision = (status as? StorageStatus.Blocked)?.decision ?: StorageDecision.WAIT
    val actions = StorageGuard.actions(decision)
    val waiting = decision == StorageDecision.WAIT

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.restore(uri, null)
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        (if (waiting) "Данные пока не открылись" else "Данные не открылись").nbsp(),
                        style = MaterialTheme.typography.titleLarge, color = Slitok.Fg
                    )
                    Text(
                        (if (waiting) "Подождите немного и повторите. Ничего не потеряно."
                        else "Они на месте, ничего не удалено, но на этом телефоне приложение их сейчас открыть не может.").nbsp(),
                        style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted
                    )
                    state.message?.let {
                        Text(it.nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Coral)
                    }
                }
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (StorageAction.ENTER_PASSWORD in actions) {
                    var password by remember { mutableStateOf("") }
                    SlitokField(
                        password, { password = it }, label = "Пароль копии", password = true,
                        error = if (state.passwordWrong) "Пароль не подошёл" else null
                    )
                    Text(
                        "Этот пароль вы задали для копий. Он открывает данные.".nbsp(),
                        style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted
                    )
                    BrassButton({ vm.unlock(password.toCharArray()) }, Modifier.fillMaxWidth(), enabled = password.isNotEmpty() && !state.busy) { Text("Открыть") }
                }
                if (StorageAction.RETRY in actions) {
                    BrassButton(vm::retry, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Повторить") }
                }
                if (StorageAction.RESTORE in actions) {
                    PlateButton({ pick.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Восстановить из копии") }
                }
                if (StorageAction.START_OVER in actions) {
                    DangerButton({ vm.askStartOver(true) }, Modifier.fillMaxWidth()) { Text("Начать заново") }
                }
                if (StorageAction.CLOSE in actions) {
                    GhostButton("Закрыть", onClose, Modifier.align(Alignment.CenterHorizontally), quiet = true)
                }
            }
        }
    }

    if (state.confirmStartOver) {
        SlitokDialog(
            title = "Начать заново?",
            onDismiss = { vm.askStartOver(false) },
            text = "Приложение начнёт с пустых данных. Прежние данные сохранятся в отдельном файле на телефоне, но приложение их не откроет.",
            buttons = {
                GhostButton("Отмена", { vm.askStartOver(false) }, quiet = true)
                DangerButton(vm::startOver, filled = true) { Text("Начать заново") }
            }
        )
    }

    state.copy?.let { picked ->
        var password by remember(picked.uri) { mutableStateOf("") }
        SlitokDialog(
            title = "Пароль копии",
            onDismiss = vm::dismissCopy,
            text = "Эта копия закрыта паролем, который вы задали.",
            content = {
                SlitokField(
                    password, { password = it }, label = "Пароль", password = true,
                    error = if (picked.wrongPassword) "Пароль не подошёл" else null
                )
            },
            buttons = {
                GhostButton("Отмена", vm::dismissCopy, quiet = true)
                BrassButton({ vm.restore(picked.uri, password.toCharArray()) }, enabled = password.isNotEmpty() && !state.busy) { Text("Открыть") }
            }
        )
    }
}
