package ru.maxrubtsoff.bankagg.ui.settings

import ru.maxrubtsoff.bankagg.ui.common.SlitokField
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.PagerTabsStatic
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded
import java.io.File

private val BACKGROUNDS = listOf(
    0xFF14387F, 0xFF21A038, 0xFFEF3124, 0xFFFFDD2D, 0xFF7B61FF, 0xFF005BFF, 0xFFE4002B, 0xFF1E1033, 0xFFD7A74A, 0xFF2A3431
)
private val LETTER_COLOURS = listOf(0xFFFFFFFF, 0xFF14120E, 0xFFF0C878, 0xFF59E2A6)

/**
 * An own bank (F-22): the name, and the logo as up to three letters on a chosen
 * colour, or a picture from the gallery with its plain background cut away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankEditorScreen(vm: BanksViewModel, existing: BankBrand?, initialName: String, onBack: () -> Unit, onDone: (BankBrand) -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: initialName) }
    var letters by rememberSaveable { mutableStateOf(existing?.letters ?: BankBrand.lettersOf(initialName)) }
    var color by rememberSaveable { mutableLongStateOf(existing?.primaryColor ?: CbrBanks.colorFor(initialName.ifBlank { "банк" })) }
    var textColor by rememberSaveable { mutableLongStateOf(existing?.textColor ?: 0xFFFFFFFF) }
    var usePicture by rememberSaveable { mutableStateOf(existing?.logoFile != null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { picked = uri; usePicture = true } }
    // Letters follow the name until the user types their own.
    var lettersTouched by rememberSaveable { mutableStateOf(existing != null) }

    Column(
        Modifier.fillMaxSize().background(Slitok.Bg).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(if (existing == null) "Свой банк" else "Банк", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Preview, the size of the biggest place a logo is drawn.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val shape = CircleShape
                Box(Modifier.size(96.dp).clip(shape).background(Color(color)).border(1.dp, Slitok.Line, shape), contentAlignment = Alignment.Center) {
                    val file = existing?.logoFile?.let { File(it) }?.takeIf { it.exists() }
                    when {
                        usePicture && picked != null -> Image(rememberAsyncImagePainter(picked), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        usePicture && file != null -> Image(rememberAsyncImagePainter(file), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else -> Text(
                            letters.ifBlank { "?" },
                            color = Color(textColor),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = when (letters.length) { 0, 1 -> 40.sp; 2 -> 32.sp; else -> 25.sp }
                        )
                    }
                }
            }
            Field("Название", title) {
                title = it
                if (!lettersTouched) letters = BankBrand.lettersOf(it)
            }
            PagerTabsStatic(listOf("Буквы", "Картинка"), if (usePicture) 1 else 0) { i ->
                if (i == 1 && picked == null && existing?.logoFile == null) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else usePicture = i == 1
            }
            if (!usePicture) {
                Field("Буквы", letters, width = 120) { letters = it.uppercase().take(3); lettersTouched = true }
                Text("Фон", style = MaterialTheme.typography.labelLarge, color = Slitok.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BACKGROUNDS.forEach { c -> Swatch(c, c == color) { color = c; textColor = if (Color(c).luminance() > 0.6f) 0xFF14120E else 0xFFFFFFFF } }
                }
                Text("Буквы", style = MaterialTheme.typography.labelLarge, color = Slitok.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LETTER_COLOURS.forEach { c -> Swatch(c, c == textColor) { textColor = c } }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Slitok.PanelTop).border(1.dp, Slitok.Line, RoundedCornerShape(16.dp))
                        .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = Slitok.Brass2)
                    Text("Другая картинка", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        BrassButton(
            onClick = { vm.saveOwn(existing, title, letters, color, textColor, picked, usePicture, onDone) },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        ) { Text("Сохранить") }
    }
}

@Composable
private fun Field(label: String, value: String, width: Int? = null, onChange: (String) -> Unit) {
    SlitokField(
        value,
        onChange,
        modifier = if (width != null) Modifier.width(width.dp) else Modifier.fillMaxWidth(),
        label = label
    )
}

@Composable
private fun Swatch(c: Long, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .border(2.dp, if (selected) Slitok.Brass2 else Color.Transparent, CircleShape).padding(3.dp)
            .clip(CircleShape).background(Color(c)).clickable(onClick = onClick)
    )
}
