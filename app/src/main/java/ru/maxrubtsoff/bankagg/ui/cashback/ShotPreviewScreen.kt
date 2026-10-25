package ru.maxrubtsoff.bankagg.ui.cashback

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackBatch
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.ShotHints
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScanReveal
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.ShotHeader
import ru.maxrubtsoff.bankagg.ui.common.ShotThumbs
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.aboveKeyboard
import ru.maxrubtsoff.bankagg.ui.common.cascade
import ru.maxrubtsoff.bankagg.ui.common.keyboardOpen

/**
 * A cashback screenshot to check. One layout from the first frame to the last
 * (B-51): the head with the screenshot and its state ([ShotHeader] — big with
 * the scan line while the app reads, compact after), the fields under it (a
 * skeleton, then the recognised rows rising one after another, F-34) and the
 * button. Nothing is swapped for another screen when reading ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShotPreviewScreen(vm: CashbackViewModel, shotIds: List<String>, onBack: () -> Unit) {
    val key = shotIds.joinToString()

    // Live: the screen opens right after upload, while OCR may still be running.
    // null = still loading; empty = no such shot (deleted elsewhere); else the shot.
    val loaded by remember(key) { vm.observeShots(shotIds) }
        .collectAsStateWithLifecycle(initialValue = null)
    val shot = loaded?.firstOrNull()
    var left by remember { mutableStateOf(false) }
    fun leave() { if (!left) { left = true; onBack() } }

    var initialised by rememberSaveable { mutableStateOf(false) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var brandKey by rememberSaveable { mutableStateOf<String?>(null) }
    // The shot's own bank first: an example bank is not in the directory by its key.
    val brand: BankBrand? = brandKey?.let { k -> shot?.brand?.takeIf { it.key == k } ?: BankBrand.fromKey(k) }
    val lines = rememberDraftLines()

    val save = rememberSaveCashback(vm) { vm.discardShots(shotIds); leave() }

    LaunchedEffect(loaded) {
        val items = loaded?.let { it.ifEmpty { leave(); return@LaunchedEffect } } ?: return@LaunchedEffect
        items.forEach { vm.resumeIfStuck(it) }
        // Copy the recognised draft into the editor once; later emissions don't overwrite edits.
        if (!initialised && items.none { it.status == ShotStatus.PROCESSING }) {
            // B-70: rows and bank of all the shots of the group.
            val g = CashbackBatch.merge(items.map { CashbackBatch.Item(it.id, it.brand, it.monthKey, it.lines) })
            brandKey = g.brand?.key
            lines.clear()
            lines.addAll(vm.draftLines(g.lines))
            initialised = true
        }
    }
    val imagePath = shot?.imagePath.orEmpty()
    val monthKey = shot?.monthKey.orEmpty()
    val status = loaded?.let { l -> if (l.any { it.status == ShotStatus.PROCESSING }) ShotStatus.PROCESSING else shot?.status }
    // A reading is shown as it is; a result only once it is in the form.
    val revealStatus = if (status == ShotStatus.PROCESSING || initialised) status else null
    val hint = if (status == ShotStatus.NEEDS_INPUT) ShotHints.cashback(shot?.lines.orEmpty()) else null

    Scaffold(
        topBar = {
            ScreenHeader("Кэшбэк", onBack = ::leave) {
                if (monthKey.isNotBlank()) SlitokTag(CashbackMonth.shortLabel(monthKey), modifier = Modifier.padding(end = 12.dp))
            }
        }
    ) { padding ->
      val typing = keyboardOpen()
      // B-35: «Сохранить» works once there is a line; an unfinished line turns red only after it's pressed.
      val canSave = initialised && lines.isNotEmpty()
      fun trySave() {
          if (lines.all { it.isComplete }) save.start(monthKey, brand, lines.toList()) else tried = true
      }
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

            // U-53: the screenshot small, with what was read; a tap opens it whole.
            ShotHeader(imagePath, status, hint = hint)
            if (shotIds.size > 1) {
                ShotThumbs(loaded.orEmpty().map { it.id to it.imagePath }, onOpen = {})
            }
            // The fields wait as a skeleton until the draft is in the editor, then rise in turn (F-34).
            ScanReveal(revealStatus) { play ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BankPickerRow(selected = brand, onSelect = { brandKey = it.key }, modifier = Modifier.cascade(0, play))
                    CashbackLinesEditor(lines, vm, tried = tried, reveal = play)
                }
            }

            Spacer(Modifier.height(4.dp))
            // While typing, the button rides on top of the keyboard instead.
            if (!typing) {
                BrassButton(
                    onClick = ::trySave,
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить") }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (typing) {
            BrassButton(
                onClick = ::trySave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Сохранить") }
        }
      }
    }

    SaveCashbackDialogs(save)
}
