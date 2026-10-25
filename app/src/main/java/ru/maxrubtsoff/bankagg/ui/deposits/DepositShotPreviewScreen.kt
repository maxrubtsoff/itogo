package ru.maxrubtsoff.bankagg.ui.deposits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.domain.model.ShotHints
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScanReveal
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ShotHeader
import ru.maxrubtsoff.bankagg.ui.common.aboveKeyboard

/**
 * A deposit screenshot to check. One layout from the first frame to the last
 * (B-51): the head with the screenshot and its state ([ShotHeader], U-53), the
 * fields under it (a skeleton while the app reads, then the recognised values
 * rising one after another, F-34) and the button. A field that was not read is
 * simply empty — no red, the head says what to look at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositShotPreviewScreen(vm: DepositsViewModel, shotId: String, onBack: () -> Unit) {

    val loaded by remember(shotId) { vm.observeShot(shotId).map { listOfNotNull(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val shot = loaded?.firstOrNull()
    var left by remember { mutableStateOf(false) }
    fun leave() { if (!left) { left = true; onBack() } }

    var initialised by remember { mutableStateOf(false) }
    val form = rememberDepositFormState(key = shotId)

    LaunchedEffect(loaded) {
        val s = loaded?.let { it.firstOrNull() ?: run { leave(); return@LaunchedEffect } } ?: return@LaunchedEffect
        vm.resumeIfStuck(s)
        if (!initialised && s.status != ShotStatus.PROCESSING) {
            form.brand = s.brand
            s.draft.principal?.let { form.principal = moneyInput(it) }
            s.draft.rateBp?.let { form.rate = rateInput(it) }
            s.draft.startDate?.let { form.start = it }
            s.draft.endDate?.let { form.end = it }
            s.draft.title?.let { form.title = it }
            s.draft.method?.let { form.method = it }
            val exit = s.draft.exitAmount
            if (exit != null) { form.exit = moneyInput(exit); form.autoExit = false }
            else if (s.draft.rateBp != null) form.autoExit = true
            initialised = true
        }
    }

    val imagePath = shot?.imagePath.orEmpty()
    val status = shot?.status
    // A reading is shown as it is; a result only once it is in the form.
    val revealStatus = if (status == ShotStatus.PROCESSING || initialised) status else null
    val hint = if (status == ShotStatus.NEEDS_INPUT) shot?.draft?.let(ShotHints::deposit) else null

    Scaffold(
        topBar = {
            ScreenHeader("Проверьте вклад", onBack)
        }
    ) { padding ->
      Column(Modifier.fillMaxSize().aboveKeyboard(padding)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            ShotHeader(imagePath, status, hint = hint)
            ScanReveal(revealStatus, skeletonHeight = 360.dp) { play ->
                DepositFormFields(form, reveal = play)
            }

            Spacer(Modifier.height(4.dp))
            BrassButton(
                onClick = {
                    val b = form.brand ?: return@BrassButton
                    vm.saveDeposit(
                        existingId = null,
                        brand = b,
                        title = form.title.ifBlank { null },
                        principal = form.principalMoney ?: return@BrassButton,
                        rateBp = form.rateBp ?: return@BrassButton,
                        startDate = form.start ?: return@BrassButton,
                        endDate = form.end ?: return@BrassButton,
                        exitAmount = form.exitMoney,
                        method = form.method,
                        shotIdToDiscard = shotId,
                        onDone = { leave() }
                    )
                },
                enabled = initialised && form.isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сохранить вклад") }
            Spacer(Modifier.height(16.dp))
        }
        KeyboardExitSum(form)
      }
    }
}
