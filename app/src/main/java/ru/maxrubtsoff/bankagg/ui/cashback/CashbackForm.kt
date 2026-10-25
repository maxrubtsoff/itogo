package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BankPickSheet
import ru.maxrubtsoff.bankagg.ui.common.CategoryIcon
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SelectRow
import ru.maxrubtsoff.bankagg.ui.common.keepVisibleWhenFocused
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Shared cashback-form pieces, used by the screenshot screen, the bank editor
 * and the batch, so they stay visually and behaviourally identical (U-39).
 */

/**
 * The bank of a form (U-39): one row like a form field — the picked bank or
 * «Выберите банк»; a tap opens the banks in a sheet. One bank, so no «+».
 */
@Composable
fun BankPickerRow(
    selected: BankBrand?,
    onSelect: (BankBrand) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SelectRow(
        value = selected?.title,
        placeholder = "Выберите банк",
        onClick = { open = true },
        modifier = modifier,
        leading = selected?.let { b -> { BankIcon(b, size = 36.dp) } }
    )
    if (open) BankPickSheet(selected, onPick = onSelect, onDismiss = { open = false })
}

/** How a row's percent box looks: as usual, asked for (brass), or wrong after a save attempt (coral). */
enum class PercentState { Normal, Asked, Error }

/**
 * One cashback line (U-39): the category (tap — the picker), a compact percent,
 * a quiet «×». Lines sit in one panel, divided; no red until a save attempt (B-35).
 */
@Composable
fun CashbackLineRow(
    iconKey: String,
    categoryName: String,
    percentText: String,
    onPickCategory: () -> Unit,
    onPercentChange: (String) -> Unit,
    onRemove: () -> Unit,
    percentState: PercentState = PercentState.Normal,
    note: String? = null,
    percentFocus: FocusRequester? = null,
    onPercentDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .keepVisibleWhenFocused()
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = onPickCategory),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryIcon(iconKey, size = 36.dp)
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    // B-54: wrap only between words (a hyphen is a break point too), never inside one.
                    categoryName.ifBlank { "Выберите категорию" }.breakAfterHyphens(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (categoryName.isBlank()) Slitok.Muted else Slitok.Fg,
                    // Long names ("Связь, интернет и ТВ") wrap instead of losing their end (B-15).
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (percentState == PercentState.Error) Slitok.Coral else Slitok.Brass2
                    )
                }
            }
        }
        PercentField(percentText, onPercentChange, percentState, percentFocus, onPercentDone)
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Убрать категорию", tint = Slitok.Faint, modifier = Modifier.size(18.dp))
        }
    }
}

/** A zero-width space after each hyphen: "Онлайн-покупки" may wrap at the hyphen, not between letters. */
private fun String.breakAfterHyphens(): String = replace("-", "-​")

/** Compact "NN %" box; the number and its sign stay together. */
@Composable
private fun PercentField(
    value: String,
    onChange: (String) -> Unit,
    state: PercentState,
    focus: FocusRequester? = null,
    onDone: () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    val border = when (state) {
        PercentState.Error -> Slitok.Coral.copy(alpha = 0.6f)
        PercentState.Asked -> Slitok.BrassEdge
        PercentState.Normal -> Slitok.Line
    }
    Row(
        Modifier
            .width(76.dp)
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(Slitok.Inset)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5)) },
            singleLine = true,
            textStyle = Amounts.Row.copy(color = Slitok.Fg, textAlign = TextAlign.End),
            cursorBrush = SolidColor(Slitok.Brass2),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f).let { if (focus != null) it.focusRequester(focus) else it }
        )
        Spacer(Modifier.width(3.dp))
        Text("%", style = Amounts.Row, color = Slitok.Muted)
    }
}

/** «+ Категория»: one more line (several categories — so this one has a «+»). */
@Composable
fun AddLineButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PlateButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text("Категория")
    }
}
