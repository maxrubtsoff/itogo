package ru.maxrubtsoff.bankagg.ui.common

import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The one bottom sheet (U-28): every sheet in the app rises, settles and goes
 * the same way — same veil, shape, handle and motion. Content scrolls above the
 * keyboard; [scroll] = false for a sheet that scrolls a list of its own.
 * The veil reaches the very bottom of the screen, under the gesture bar (B-40).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlitokSheet(onDismiss: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Slitok.PanelTop,
        scrimColor = Slitok.Scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        ClearSystemBars()
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * A confirmation of something that can't be undone (U-40/A3): a «Слиток» panel
 * over the veil, a title, one line of what happens, two buttons.
 */
@Composable
fun SlitokDialog(
    title: String,
    onDismiss: () -> Unit,
    text: String? = null,
    content: (@Composable () -> Unit)? = null,
    buttons: (@Composable () -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ClearSystemBars(dim = true)
        Panel(Modifier.fillMaxWidth().padding(horizontal = 20.dp), radius = 20.dp) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title.nbsp(), style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
                if (text != null) Text(text.nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                content?.invoke()
                if (buttons != null) {
                    Spacer(Modifier.height(4.dp))
                    ButtonRow { buttons() }
                }
            }
        }
    }
}

/** A date in a sheet (U-40/A4), in the palette, with «Готово». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlitokDatePicker(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, latest: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toEpochDay()?.times(86_400_000L),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                latest == null || utcTimeMillis <= latest.toEpochDay() * 86_400_000L
        }
    )
    SlitokSheet(onDismiss) {
        DatePicker(
            state = state,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = Slitok.PanelTop,
                titleContentColor = Slitok.Muted,
                headlineContentColor = Slitok.Fg,
                weekdayContentColor = Slitok.Muted,
                subheadContentColor = Slitok.Muted,
                navigationContentColor = Slitok.Fg,
                yearContentColor = Slitok.Fg,
                currentYearContentColor = Slitok.Brass2,
                selectedYearContentColor = Slitok.OnBrassInk,
                selectedYearContainerColor = Slitok.Brass,
                dayContentColor = Slitok.Fg,
                selectedDayContentColor = Slitok.OnBrassInk,
                selectedDayContainerColor = Slitok.Brass,
                todayContentColor = Slitok.Brass2,
                todayDateBorderColor = Slitok.BrassLine,
                dividerColor = Slitok.Line
            )
        )
        BrassButton(
            onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.selectedDateMillis != null
        ) { Text("Готово") }
    }
}

/**
 * A sheet or dialog lives in its own window; Android paints a light strip over
 * the gesture bar there unless told not to (B-40). The veil then covers it too.
 */
@Composable
private fun ClearSystemBars(dim: Boolean = false) {
    val view = LocalView.current
    SideEffect {
        val window = view.dialogWindow() ?: return@SideEffect
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        if (dim) window.setDimAmount(0.74f)
    }
}

private fun View.dialogWindow(): Window? {
    var v: Any? = this
    while (v != null) {
        if (v is DialogWindowProvider) return v.window
        v = (v as? View)?.parent
    }
    return null
}
