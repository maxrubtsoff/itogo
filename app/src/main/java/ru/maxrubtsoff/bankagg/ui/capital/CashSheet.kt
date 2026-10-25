package ru.maxrubtsoff.bankagg.ui.capital

import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.AmountField
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.CashEdit
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.common.AmountGrouping
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded

/** A selectable chip. */
@Composable
fun ModeChip(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SlitokChip(text, active, onClick, modifier)
}

/** Cash editor: set the amount, or add / take some; the result is shown live and never negative. */
@Composable
fun CashSheet(currentMinor: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(CashEdit.Mode.SET) }
    val start = if (currentMinor > 0) amountInput(currentMinor) else ""
    var text by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }

    val input = parseAmountMinor(text.text)
    val result = input?.let { CashEdit.result(currentMinor, mode, it) }

    SlitokSheet(onDismiss, imeField = true) {
        Text("Наличные", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(CashEdit.Mode.SET to "Задать", CashEdit.Mode.ADD to "+ Добавить", CashEdit.Mode.TAKE to "− Взять")
                .forEach { (m, label) ->
                    ModeChip(label, mode == m, Modifier.weight(1f)) {
                        if (mode != m) {
                            mode = m
                            // Switching to + / − starts from an empty field; "Задать" from the current sum.
                            val t = if (m == CashEdit.Mode.SET) start else ""
                            text = TextFieldValue(t, TextRange(0, t.length))
                        }
                    }
                }
        }
        AmountField(text, { text = it }, "₽", autoFocus = true)
        if (mode != CashEdit.Mode.SET) {
            MoneyText(
                when {
                    input == null -> "сейчас ${Money.ofMinor(currentMinor).format()}"
                    result == null -> "столько нет: сейчас ${Money.ofMinor(currentMinor).format()}"
                    else -> "станет ${Money.ofMinor(result).format()}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (input != null && result == null) Slitok.Coral else Slitok.Muted
            )
        }
        BrassButton(
            onClick = { result?.let(onSave) },
            enabled = result != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Сохранить") }
    }
}
