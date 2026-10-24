package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.foundation.layout.size
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.foundation.clickable
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.StatusLine
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.ui.common.ShotImage
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.aboveKeyboard
import ru.maxrubtsoff.bankagg.ui.common.keyboardOpen
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import kotlinx.coroutines.flow.map
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShotPreviewScreen(vm: CashbackViewModel, shotId: String, onBack: () -> Unit) {

    // Live: the screen opens right after upload, while OCR may still be running.
    // null = still loading; empty = no such shot (deleted elsewhere); else the shot.
    val loaded by remember(shotId) { vm.observeShot(shotId).map { listOfNotNull(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val shot = loaded?.firstOrNull()
    var left by remember { mutableStateOf(false) }
    fun leave() { if (!left) { left = true; onBack() } }

    var initialised by rememberSaveable { mutableStateOf(false) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var photoOpen by remember { mutableStateOf(false) }
    var brandKey by rememberSaveable { mutableStateOf<String?>(null) }
    val brand: BankBrand? = brandKey?.let(BankBrand::fromKey)
    val lines = rememberDraftLines()

    val save = rememberSaveCashback(vm) { vm.discardShot(shotId); leave() }

    LaunchedEffect(loaded) {
        val s = loaded?.let { it.firstOrNull() ?: run { leave(); return@LaunchedEffect } } ?: return@LaunchedEffect
        vm.resumeIfStuck(s)
        // Copy the recognised draft into the editor once; later emissions don't overwrite edits.
        if (!initialised && s.status != ShotStatus.PROCESSING) {
            brandKey = s.brand?.key
            lines.clear()
            lines.addAll(vm.draftLines(s))
            initialised = true
        }
    }
    val imagePath = shot?.imagePath.orEmpty()
    val monthKey = shot?.monthKey.orEmpty()
    val status = shot?.status ?: ShotStatus.PROCESSING

    Scaffold(
        topBar = {
            ScreenHeader("Кэшбэк", onBack = ::leave) {
                if (monthKey.isNotBlank()) SlitokTag(CashbackMonth.shortLabel(monthKey), modifier = Modifier.padding(end = 12.dp))
            }
        }
    ) { padding ->
        if (!initialised) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                ShotSummary(imagePath, ShotStatus.PROCESSING, lines.size, onOpenPhoto = { photoOpen = true })
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Slitok.Brass)
                }
            }
            return@Scaffold
        }

      val typing = keyboardOpen()
      // B-35: «Сохранить» works once there is a line; an unfinished line turns red only after it's pressed.
      val canSave = lines.isNotEmpty()
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

            // U-39: the screenshot small, with what was read; a tap opens it whole.
            ShotSummary(imagePath, status, lines.count { it.isComplete }, onOpenPhoto = { photoOpen = true })
            BankPickerRow(selected = brand, onSelect = { brandKey = it.key })
            CashbackLinesEditor(lines, vm, tried = tried)

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
    if (photoOpen) SlitokSheet(onDismiss = { photoOpen = false }) { UserPhoto(imagePath) }
}

/**
 * The screenshot as a thumbnail and what came of it (U-39): a dot and a word
 * for the state, one quiet line under it — both from the same edge. Not
 * recognised is not an error of the person, so it's grey, not red (B-35).
 */
@Composable
private fun ShotSummary(path: String, status: ShotStatus, found: Int, onOpenPhoto: () -> Unit) {
    val (label, hint, color) = when (status) {
        ShotStatus.RECOGNIZED -> Triple("Распознано", if (found > 0) "Сверьте со скрином и сохраните" else "Сверьте со скрином", Slitok.Mint)
        ShotStatus.NEEDS_INPUT -> Triple("Распознано не всё", "Допишите, чего не хватает", Slitok.Brass2)
        ShotStatus.FAILED -> Triple("Скрин не прочитался", "Заполните по скрину", Slitok.Muted)
        ShotStatus.PROCESSING -> Triple("Читаю скрин…", "Это несколько секунд", Slitok.Muted)
    }
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ShotThumb(path, onOpenPhoto)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusLine(label, color)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
            }
        }
    }
}

@Composable
private fun ShotThumb(path: String, onClick: () -> Unit) {
    val exists = remember(path) { path.isNotBlank() && File(path).exists() }
    Box(
        Modifier
            .size(width = 52.dp, height = 92.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Slitok.Line, RoundedCornerShape(10.dp))
            .background(Slitok.Inset)
            .clickable(enabled = exists, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (exists) {
            Image(
                painter = rememberAsyncImagePainter(ShotImage(path)),
                contentDescription = "Скрин целиком",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun UserPhoto(path: String) {
    val exists = remember(path) { path.isNotBlank() && File(path).exists() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Slitok.Line, RoundedCornerShape(20.dp))
            .background(Slitok.Inset),
        contentAlignment = Alignment.Center
    ) {
        if (exists) {
            Image(
                painter = rememberAsyncImagePainter(ShotImage(path)),
                contentDescription = "Загруженный скрин",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text("Фото недоступно", style = MaterialTheme.typography.bodyMedium, color = Slitok.Faint,
                modifier = Modifier.padding(24.dp))
        }
    }
}

