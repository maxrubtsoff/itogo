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
import ru.maxrubtsoff.bankagg.domain.model.DepositBatch
import ru.maxrubtsoff.bankagg.domain.model.ShotHints
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScanReveal
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ShotHeader
import ru.maxrubtsoff.bankagg.ui.common.ShotThumbs
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
fun DepositShotPreviewScreen(vm: DepositsViewModel, shotIds: List<String>, onBack: () -> Unit) {
    val key = shotIds.joinToString()

    val loaded by remember(key) { vm.observeShots(shotIds) }
        .collectAsStateWithLifecycle(initialValue = null)
    val shot = loaded?.firstOrNull()
    var left by remember { mutableStateOf(false) }
    fun leave() { if (!left) { left = true; onBack() } }

    var initialised by remember { mutableStateOf(false) }
    val form = rememberDepositFormState(key = key)

    LaunchedEffect(loaded) {
        val items = loaded?.let { it.ifEmpty { vm.announceDuplicate(shotIds); leave(); return@LaunchedEffect } } ?: return@LaunchedEffect
        items.forEach { vm.resumeIfStuck(it) }
        if (!initialised && items.none { it.status == ShotStatus.PROCESSING }) {
            // B-70: the form shows what all the shots of the group add up to.
            val g = DepositBatch.merge(items.map { DepositBatch.Item(it.id, it.brand, it.draft) })
            val d = g.draft
            form.brand = g.brand
            d.principal?.let { form.principal = moneyInput(it) }
            g.rateBp?.let { form.rate = rateInput(it) }
            d.startDate?.let { form.start = it }
            d.endDate?.let { form.end = it }
            d.title?.let { form.title = it }
            d.method?.let { form.method = it }
            val exit = d.exitAmount
            if (exit != null) { form.exit = moneyInput(exit); form.autoExit = false }
            else if (g.rateBp != null) form.autoExit = true
            initialised = true
        }
    }

    val imagePath = shot?.imagePath.orEmpty()
    val status = loaded?.let { l -> if (l.any { it.status == ShotStatus.PROCESSING }) ShotStatus.PROCESSING else shot?.status }
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
            if (shotIds.size > 1) {
                ShotThumbs(loaded.orEmpty().map { it.id to it.imagePath }, onOpen = {})
            }
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
                        shotIds = shotIds,
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
