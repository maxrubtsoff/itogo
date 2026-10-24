package ru.maxrubtsoff.bankagg.ui.common

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import ru.maxrubtsoff.bankagg.domain.model.TourTarget
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
    // A tour plays inside one window, where its veil and light reach the sheet too (U-54).
    val inline = LocalInlineSheets.current
    if (inline != null) {
        InlineSheet(inline, scroll, content)
        return
    }
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
 * Where a tour draws the sheets of the screens it plays on (U-54): a real sheet
 * is a window of its own, above the tour's veil, so in a tour it is drawn in
 * place — the same content, the same look, in the one window the tour lights.
 */
class InlineSheetHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

/** Set only by a tour; null in the app, where sheets are real windows. */
val LocalInlineSheets = compositionLocalOf<InlineSheetHost?> { null }

@Composable
private fun InlineSheet(host: InlineSheetHost, scroll: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val latest by rememberUpdatedState(content)
    DisposableEffect(host) {
        host.content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) { latest() }
        }
        onDispose { host.content = null }
    }
}

/** The tour's sheet host: the sheet rises from the bottom of the screen and goes back down. */
@Composable
fun InlineSheetLayer(host: InlineSheetHost, modifier: Modifier = Modifier) {
    val shown = host.content
    var last by remember { mutableStateOf(shown) }
    SideEffect { if (shown != null) last = shown }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = shown != null,
            enter = slideInVertically(Motion.tween(Motion.NORMAL)) { it } + fadeIn(Motion.tween(Motion.NORMAL)),
            exit = slideOutVertically(Motion.tween(Motion.FAST)) { it } + fadeOut(Motion.tween(Motion.FAST))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .tourTarget(TourTarget.ADD_SHEET, radius = 28.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Slitok.PanelTop),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.padding(vertical = 12.dp).size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Slitok.Faint))
                (shown ?: last)?.invoke()
                Spacer(Modifier.height(12.dp))
            }
        }
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
