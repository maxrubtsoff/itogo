package ru.maxrubtsoff.bankagg.ui.deposits

import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
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
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.aboveKeyboard
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.io.File

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
    val status = shot?.status ?: ShotStatus.PROCESSING

    Scaffold(
        topBar = {
            ScreenHeader("Проверьте вклад", onBack)
        }
    ) { padding ->
        if (!initialised) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                if (imagePath.isNotBlank()) UserPhoto(imagePath)
                RecognitionBanner(ShotStatus.PROCESSING)
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Slitok.Violet)
                }
            }
            return@Scaffold
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
            UserPhoto(imagePath)
            RecognitionBanner(status)

            DepositFormFields(form)

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
                enabled = form.isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сохранить вклад") }
            Spacer(Modifier.height(16.dp))
        }
        KeyboardExitSum(form)
      }
    }
}

@Composable
private fun UserPhoto(path: String) {
    val exists = remember(path) { path.isNotBlank() && File(path).exists() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
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
            Text(
                "Фото недоступно",
                style = MaterialTheme.typography.bodyMedium,
                color = Slitok.Faint,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun RecognitionBanner(status: ShotStatus) {
    val (label, hint, accent) = when (status) {
        ShotStatus.RECOGNIZED -> Triple("Распознано", "Сверьте со скрином и сохраните.", Slitok.Mint)
        ShotStatus.NEEDS_INPUT -> Triple("Проверьте", "Не всё распозналось — проверьте и дополните поля.", Slitok.Brass2)
        ShotStatus.FAILED -> Triple("Не распознано", "Заполните поля вручную по фото.", Slitok.Coral)
        ShotStatus.PROCESSING -> Triple("Обработка…", "Распознаём текст со скрина.", Slitok.Muted)
    }
    Panel(modifier = Modifier.fillMaxWidth(), radius = 14.dp) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.SemiBold)
            }
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        }
    }
}
