package ru.maxrubtsoff.bankagg.ui.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor
import ru.maxrubtsoff.bankagg.domain.model.ServiceBrands
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** Backgrounds the editor offers; the look's current colour is added first if it isn't among them. */
private val BACKGROUNDS = listOf(
    0xFFFC3F1D, 0xFF2E6BF0, 0xFF7B5CF0, 0xFF1DB954, 0xFFF07A2B, 0xFFFFDD2D, 0xFF1E1033, 0xFF3A3F52,
    0xFF14387F, 0xFFD7A74A
)
private const val LETTERS_LIGHT = 0xFFFFFFFFL
private const val LETTERS_DARK = 0xFF14120EL
private val LETTER_COLOURS = listOf(LETTERS_LIGHT, LETTERS_DARK, 0xFFF0C878, 0xFF59E2A6)

/**
 * What the look editor holds (F-33): the name, and the look as letters on a
 * colour or a picture. Letters (and, for a new one, the colour) follow the name
 * until the person sets their own. [existingLogoFile] — the saved picture,
 * [pickedUri] — one just chosen from the gallery, not saved yet.
 */
@Stable
class BrandDecorState(
    title: String,
    letters: String,
    color: Long,
    textColor: Long?,
    usePicture: Boolean,
    val existingLogoFile: String?,
    pickedUri: String?,
    lettersTouched: Boolean,
    colorTouched: Boolean
) {
    var title by mutableStateOf(title)
    var letters by mutableStateOf(letters)
    var color by mutableStateOf(color)
    /** Null — black or white, whichever reads on [color]. */
    var textColor by mutableStateOf(textColor)
    var usePicture by mutableStateOf(usePicture)
    var pickedUri by mutableStateOf(pickedUri)
    private var lettersTouched by mutableStateOf(lettersTouched)
    private var colorTouched by mutableStateOf(colorTouched)

    fun onTitle(value: String) {
        title = value
        if (!lettersTouched) letters = BankBrand.lettersOf(value.trim())
        if (!colorTouched) color = ServiceBrands.colorFor(value)
    }

    fun onLetters(value: String) { letters = value.uppercase().take(3); lettersTouched = true }

    fun onColor(value: Long) { color = value; colorTouched = true; textColor = null }

    fun onPicture(uri: Uri) { pickedUri = uri.toString(); usePicture = true }

    /** The look as it would be saved; the picture itself is made by the ViewModel from [pickedUri]. */
    fun decor(): BrandDecor = BrandDecor(
        colorArgb = color,
        letters = letters.trim().uppercase().take(3).ifEmpty { BankBrand.lettersOf(title.trim()) },
        textColorArgb = textColor,
        logoFile = existingLogoFile.takeIf { usePicture }
    )

    /** A picture is chosen (or already saved) and switched on. */
    val picked: Uri? get() = pickedUri?.takeIf { usePicture }?.let(Uri::parse)

    companion object {
        val Saver: Saver<BrandDecorState, Any> = listSaver(
            save = { s ->
                listOf(
                    s.title, s.letters, s.color, s.textColor ?: -1L, s.textColor != null, s.usePicture,
                    s.existingLogoFile ?: "", s.pickedUri ?: "", s.lettersTouched, s.colorTouched
                )
            },
            restore = { l ->
                BrandDecorState(
                    title = l[0] as String, letters = l[1] as String, color = l[2] as Long,
                    textColor = if (l[4] as Boolean) l[3] as Long else null,
                    usePicture = l[5] as Boolean,
                    existingLogoFile = (l[6] as String).ifEmpty { null },
                    pickedUri = (l[7] as String).ifEmpty { null },
                    lettersTouched = l[8] as Boolean, colorTouched = l[9] as Boolean
                )
            }
        )
    }
}

/**
 * The look editor's state, kept over rotation. [touched] — the look is the
 * person's own already (an existing bank or subscription), so a new name doesn't
 * change its letters and colour.
 */
@Composable
fun rememberBrandDecorState(initialTitle: String, initialDecor: BrandDecor, touched: Boolean = false, key: Any? = null): BrandDecorState =
    rememberSaveable(key, saver = BrandDecorState.Saver) {
        BrandDecorState(
            title = initialTitle, letters = initialDecor.letters, color = initialDecor.colorArgb, textColor = initialDecor.textColorArgb,
            usePicture = initialDecor.logoFile != null, existingLogoFile = initialDecor.logoFile, pickedUri = null,
            lettersTouched = touched, colorTouched = touched
        )
    }

/**
 * «Оформление» (F-33): one editor for a bank and a subscription. A preview of
 * the face beside the name, the switch «Буквы / Картинка», then letters with
 * their colour and the background, or «Другая картинка». [shape] — a circle for
 * a bank, a rounded square for a subscription.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrandDecorEditor(state: BrandDecorState, shape: Shape, modifier: Modifier = Modifier) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) state.onPicture(uri) }
    val ask = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    // The colour the look came with stays on offer even if it isn't in the palette (a service's own, a bank's).
    val startColor = remember { state.color }
    val backgrounds = remember { if (startColor in BACKGROUNDS) BACKGROUNDS else listOf(startColor) + BACKGROUNDS }

    Panel(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BrandAvatar(state.decor().copy(letters = state.letters.ifBlank { "?" }), shape, 64.dp, picked = state.picked)
                SlitokField(state.title, state::onTitle, modifier = Modifier.weight(1f), label = "Название")
            }
            PagerTabsStatic(listOf("Буквы", "Картинка"), if (state.usePicture) 1 else 0) { i ->
                if (i == 1 && state.pickedUri == null && state.existingLogoFile == null) ask() else state.usePicture = i == 1
            }
            if (!state.usePicture) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SlitokField(state.letters, state::onLetters, modifier = Modifier.width(96.dp), label = "Буквы")
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Цвет букв", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                        val shown = state.textColor ?: if (Color(state.color).luminance() > 0.6f) LETTERS_DARK else LETTERS_LIGHT
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LETTER_COLOURS.forEach { c -> Swatch(c, c == shown, 32) { state.textColor = c } }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Фон", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        backgrounds.forEach { c -> Swatch(c, c == state.color, 36) { state.onColor(c) } }
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slitok.PanelTop)
                        .border(1.dp, Slitok.Line, RoundedCornerShape(16.dp))
                        .clickable { ask() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = Slitok.Brass2)
                    Text("Другая картинка", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun Swatch(c: Long, selected: Boolean, side: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(side.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) Slitok.Brass2 else Color.Transparent, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Color(c))
            .border(1.dp, Slitok.Line, CircleShape)
            .clickable(onClick = onClick)
    )
}
