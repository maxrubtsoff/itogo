package ru.maxrubtsoff.bankagg.ui.deposits

import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.ui.common.aboveKeyboard
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositManualScreen(vm: DepositsViewModel, depositId: String?, onBack: () -> Unit, onDeleted: () -> Unit = onBack) {
    val editing = depositId != null
    val existingState = if (depositId == null) null
        else remember(depositId) { vm.observeDeposit(depositId) }.collectAsStateWithLifecycle(initialValue = null)
    val existing: Deposit? = existingState?.value

    val form = rememberDepositFormState(key = depositId ?: "new")
    var initialised by remember { mutableStateOf(depositId == null) }
    var left by remember { mutableStateOf(false) }
    fun leave() { if (!left) { left = true; onBack() } }

    LaunchedEffect(existing) {
        val d = existing
        if (!initialised && d != null) {
            form.brand = d.brand
            form.title = d.title ?: ""
            form.principal = moneyInput(d.principal)
            form.rate = rateInput(d.rateBp)
            form.start = d.startDate
            form.end = d.endDate
            form.method = d.method
            val exit = d.exitAmount
            if (exit != null) { form.exit = moneyInput(exit); form.autoExit = false } else form.autoExit = true
            initialised = true
        }
    }

    Scaffold(
        topBar = {
            ScreenHeader(
                if (editing) "Изменить вклад" else "Новый вклад", onBack,
                actions = {
                    if (editing && depositId != null) {
                        IconButton(onClick = { vm.delete(depositId); if (!left) { left = true; onDeleted() } }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить вклад", tint = Slitok.Coral)
                        }
                    }
                }
            )
        }
    ) { padding ->
      Column(Modifier.fillMaxSize().aboveKeyboard(padding)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            DepositFormFields(form)

            Spacer(Modifier.height(4.dp))
            BrassButton(
                onClick = {
                    val b = form.brand ?: return@BrassButton
                    vm.saveDeposit(
                        existingId = depositId,
                        brand = b,
                        title = form.title.ifBlank { null },
                        principal = form.principalMoney ?: return@BrassButton,
                        rateBp = form.rateBp ?: return@BrassButton,
                        startDate = form.start ?: return@BrassButton,
                        endDate = form.end ?: return@BrassButton,
                        exitAmount = form.exitMoney,
                        method = form.method,
                        onDone = { leave() }
                    )
                },
                enabled = form.isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (editing) "Сохранить" else "Сохранить вклад") }
            Spacer(Modifier.height(16.dp))
        }
        KeyboardExitSum(form)
      }
    }
}
