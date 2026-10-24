package ru.maxrubtsoff.bankagg.ui.deposits

import ru.maxrubtsoff.bankagg.ui.common.TagTone
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.ui.common.ShotImage
import ru.maxrubtsoff.bankagg.domain.model.DepositShot
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.io.File

/** How many screenshots one pick may take. */
const val MAX_PICK = 20

/**
 * «Со скринов» for deposits (U-38): opens the gallery; one screenshot goes to
 * its check, several to the batch. Kept outside the sheet that calls it, so the
 * answer arrives after the sheet is gone.
 */
@Composable
fun rememberDepositShotsPicker(vm: DepositsViewModel, onOpenShot: (String) -> Unit, onOpenBatch: (List<String>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris: List<Uri> ->
        when (uris.size) {
            0 -> Unit
            1 -> vm.addShot(uris.first(), onOpenShot)
            else -> vm.addShots(uris, onOpenBatch)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositAddScreen(
    vm: DepositsViewModel,
    onBack: () -> Unit,
    onOpenShot: (String) -> Unit,
    onOpenBatch: (List<String>) -> Unit,
    @Suppress("UNUSED_PARAMETER") onManual: () -> Unit
) {
    val shots by vm.shots.collectAsStateWithLifecycle()
    // U-38: this screen only holds screenshots not saved yet; adding goes through «Со скринов / Вручную».
    val pick = rememberDepositShotsPicker(vm, onOpenShot, onOpenBatch)
    LaunchedEffect(shots.isEmpty()) { if (shots.isEmpty()) onBack() }

    Scaffold(
        topBar = {
            ScreenHeader("Скрины вкладов", onBack)
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            ShotRail(shots, onOpenShot = onOpenShot, onAddPhoto = pick, onDeleteShot = { vm.deleteShot(it.id) })
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShotRail(
    shots: List<DepositShot>,
    onOpenShot: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onDeleteShot: (DepositShot) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        shots.forEach { shot ->
            ShotCard(shot, onClick = { onOpenShot(shot.id) }, onDelete = { onDeleteShot(shot) })
        }
        AddShotCard(onAddPhoto)
    }
}

@Composable
private fun ShotCard(shot: DepositShot, onClick: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .width(134.dp)
            .height(150.dp)
            .clip(shape)
            .border(1.dp, Slitok.Line, shape)
            .background(Slitok.Inset)
            .clickable(onClick = onClick)
    ) {
        val exists = remember(shot.imagePath) { shot.imagePath.isNotBlank() && File(shot.imagePath).exists() }
        if (exists) {
            Image(
                painter = rememberAsyncImagePainter(ShotImage(shot.imagePath)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().blur(2.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(Slitok.ScrimLight))
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Slitok.ScrimDeep)))
        )

        DepositStatusPill(shot.status, Modifier.align(Alignment.TopStart).padding(8.dp))

        if (shot.status != ShotStatus.PROCESSING) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Slitok.Scrim)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Удалить скрин", tint = Slitok.Fg, modifier = Modifier.size(14.dp))
            }
        }

        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                shot.brand?.title ?: "Банк не распознан",
                style = MaterialTheme.typography.labelMedium,
                color = Slitok.Fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            shot.draft.exitAmount?.let {
                AmountText(money(it), style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, minSize = 9.sp)
            }
        }
    }
}

@Composable
private fun AddShotCard(onAddPhoto: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .width(96.dp)
            .height(150.dp)
            .clip(shape)
            .background(Slitok.Violet.copy(alpha = 0.12f))
            .border(1.dp, Slitok.Violet.copy(alpha = 0.4f), shape)
            .clickable(onClick = onAddPhoto),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Добавить скрин", tint = Slitok.Violet, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text("Выбрать", style = MaterialTheme.typography.labelMedium, color = Slitok.Violet, textAlign = TextAlign.Center)
    }
}

@Composable
fun DepositStatusPill(status: ShotStatus, modifier: Modifier = Modifier) {
    // U-40/A7: one status tag for screenshots; «не распознано» is grey, not red (B-35).
    val (label, tone) = when (status) {
        ShotStatus.RECOGNIZED -> "Готово" to TagTone.Mint
        ShotStatus.NEEDS_INPUT -> "Проверьте" to TagTone.Brass
        ShotStatus.FAILED -> "Не распознано" to TagTone.Neutral
        ShotStatus.PROCESSING -> "Обработка…" to TagTone.Neutral
    }
    SlitokTag(label, tone, modifier)
}
