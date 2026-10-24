package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

private val FieldShape = RoundedCornerShape(16.dp)

/** Border by state: brass in focus, coral when [error] (only after a save attempt), hint — strong brass. */
private fun fieldBorder(focused: Boolean, error: Boolean, hint: Boolean): Color = when {
    error -> Slitok.Coral.copy(alpha = 0.6f)
    hint -> Slitok.BrassEdge
    focused -> Slitok.BrassLine
    else -> Slitok.Line
}

/**
 * A text field (U-40/A2): one look for every text input. [label] sits above,
 * [error] — a line under it, shown only after the person tried to save.
 * [password] hides the text and adds an eye to show it.
 */
@Composable
fun SlitokField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    autoFocus: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var shown by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(FieldShape)
                .background(Slitok.Inset)
                .border(1.dp, fieldBorder(focused, error != null, false), FieldShape)
                .padding(start = 14.dp, end = if (password) 2.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leading?.invoke()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Slitok.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Slitok.Fg),
                    cursorBrush = SolidColor(Slitok.Brass2),
                    visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
            if (password) {
                IconButton(onClick = { shown = !shown }) {
                    Icon(
                        if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (shown) "Скрыть пароль" else "Показать пароль",
                        tint = Slitok.Muted
                    )
                }
            }
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = Slitok.Coral)
    }
}

/**
 * A sum or a percent (U-40/A2): the figure in Unbounded, its unit after it in
 * a quiet colour. Digits, one comma; groups of three as you type.
 * [compact] — the small percent box of a cashback row; [hint] — highlighted
 * because the screen asks to fill it (never red).
 */
@Composable
fun AmountField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    autoFocus: Boolean = false,
    compact: Boolean = false,
    hint: Boolean = false,
    error: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    placeholder: String? = null,
    allowFraction: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val style = if (compact) Amounts.Row.copy(fontSize = Amounts.Row.fontSize * 0.94f) else Amounts.Large
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        Row(
            Modifier
                .then(if (compact) Modifier.width(COMPACT_WIDTH) else Modifier.fillMaxWidth())
                .heightIn(min = if (compact) 40.dp else 56.dp)
                .clip(if (compact) RoundedCornerShape(12.dp) else FieldShape)
                .background(Slitok.Inset)
                .border(1.dp, fieldBorder(focused, error != null, hint), if (compact) RoundedCornerShape(12.dp) else FieldShape)
                .padding(horizontal = if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.weight(1f), contentAlignment = if (compact) Alignment.CenterEnd else Alignment.CenterStart) {
            if (value.text.isEmpty() && placeholder != null) {
                Text(placeholder, style = style, color = Slitok.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            BasicTextField(
                value = value,
                onValueChange = { v ->
                    val clean = v.text.filter { it.isDigit() || (allowFraction && (it == ',' || it == '.')) }.take(16)
                    onChange(if (clean == v.text) v else TextFieldValue(clean, TextRange(clean.length)))
                },
                singleLine = true,
                textStyle = style.copy(color = Slitok.Fg, textAlign = if (compact) TextAlign.End else TextAlign.Start),
                cursorBrush = SolidColor(Slitok.Brass2),
                visualTransformation = AmountGrouping,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onNext = { onDone?.invoke() }),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            }
            Text(unit, style = style.copy(fontSize = style.fontSize * 0.85f), color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = Slitok.Coral)
    }
}

private val COMPACT_WIDTH: Dp = 76.dp

/** [AmountField] for a plain string value (forms that keep the text themselves). */
@Composable
fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "",
    label: String? = null,
    placeholder: String? = null,
    allowFraction: Boolean = true
) {
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (tfv.text != value) tfv = TextFieldValue(value, TextRange(value.length))
    AmountField(
        tfv,
        { v -> tfv = v; if (v.text != value) onChange(v.text) },
        unit,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        allowFraction = allowFraction
    )
}
