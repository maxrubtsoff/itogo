package ru.maxrubtsoff.bankagg.ui.onboarding

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.TourAction
import ru.maxrubtsoff.bankagg.domain.model.TourAnim
import ru.maxrubtsoff.bankagg.domain.model.TourHighlight
import ru.maxrubtsoff.bankagg.domain.model.TourScript
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.domain.model.TourStep
import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackLineRow
import ru.maxrubtsoff.bankagg.ui.cashback.PercentState
import ru.maxrubtsoff.bankagg.ui.common.AddButton
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ButtonRow
import ru.maxrubtsoff.bankagg.ui.common.Dot
import ru.maxrubtsoff.bankagg.ui.common.GhostButton
import ru.maxrubtsoff.bankagg.ui.common.ListRow
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.StatusLine
import ru.maxrubtsoff.bankagg.ui.common.TagTone
import ru.maxrubtsoff.bankagg.ui.common.TileButton
import ru.maxrubtsoff.bankagg.ui.common.ValueChip
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.common.rememberNotificationAccess
import ru.maxrubtsoff.bankagg.ui.overview.CapitalPartArt
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.util.Perms

/** Where each element a tour points at sits on the screen, by its [TourTarget]. */
private class TourTargets { val bounds = mutableStateMapOf<TourTarget, Rect>() }

private val LocalTourTargets = compositionLocalOf<TourTargets?> { null }

/** Marks an element a tour step can point at; the highlight follows the element itself, not coordinates. */
private fun Modifier.tourTarget(t: TourTarget): Modifier = composed {
    val targets = LocalTourTargets.current
    if (targets == null) this else onGloballyPositioned { targets.bounds[t] = it.boundsInRoot() }
}

/**
 * A tour (F-30): an example screen made of the app's own pieces, marked
 * «Пример»; a light card says what the highlighted element does. Nothing here
 * is saved. The cross closes the whole tour; a closed tour counts as taken.
 */
@Composable
fun TourScreen(section: TourSection, features: Features, onDone: () -> Unit) {
    val access by rememberNotificationAccess()
    // The steps are fixed when the tour starts, so giving access mid-way doesn't reshuffle them.
    // The example scenes below know only the old targets; the steps for the real screens (F-32, U-54) come with their own UI.
    val steps = remember(section) {
        TourScript.steps(section, features, access).filterNot {
            it.target == TourTarget.SHOT_THUMB || it.anim == TourAnim.PICK_SHOTS ||
                (section == TourSection.CASHBACK && it.target == TourTarget.ADD_SHEET)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var filled by rememberSaveable { mutableStateOf(false) }
    val targets = remember { TourTargets() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    fun next() = if (index >= steps.lastIndex) onDone() else index++
    BackHandler(onBack = onDone)

    CompositionLocalProvider(LocalTourTargets provides targets) {
        Box(Modifier.fillMaxSize().background(Slitok.Bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                    SlitokTag("Пример", TagTone.Violet)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        TourSection.GENERAL -> GeneralScene()
                        TourSection.DEPOSITS -> DepositsScene(sheet = step.target == TourTarget.ADD_SHEET)
                        TourSection.CASHBACK -> CashbackScene(step.target, filled)
                        TourSection.ACCOUNTS -> AccountsScene()
                        TourSection.CURRENCY -> CurrencyScene()
                        TourSection.CASH -> CashScene()
                        TourSection.SUBSCRIPTIONS -> SubscriptionsScene()
                    }
                }
            }
            CoachOverlay(
                bounds = targets.bounds[step.target],
                step = step,
                index = index,
                total = steps.size,
                last = index == steps.lastIndex,
                onClose = onDone,
                onNext = {
                    when (step.action) {
                        TourAction.NEXT -> next()
                        TourAction.ALLOW_ACCESS -> { Perms.openNotificationAccessSettings(context); next() }
                        TourAction.FILL_AND_NEXT -> scope.launch { filled = true; delay(700); next() }
                    }
                },
                onLater = if (step.action == TourAction.ALLOW_ACCESS) ({ next() }) else null
            )
        }
    }
}

/** The veil with a window over the target, a brass frame round it, and the card beside it. */
@Composable
private fun CoachOverlay(
    bounds: Rect?,
    step: TourStep,
    index: Int,
    total: Int,
    last: Boolean,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onLater: (() -> Unit)?
) {
    val density = LocalDensity.current
    val pad = with(density) { 6.dp.toPx() }
    val radius = with(density) { 18.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hole = bounds?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clickable(interactionSource = null, indication = null) {}
        ) {
            drawRect(Slitok.Scrim)
            if (hole != null) {
                drawRoundRect(Color.Transparent, Offset(hole.left, hole.top), Size(hole.width, hole.height), CornerRadius(radius), blendMode = BlendMode.Clear)
                if (step.highlight == TourHighlight.FRAMED)
                    drawRoundRect(Slitok.Brass2, Offset(hole.left, hole.top), Size(hole.width, hole.height), CornerRadius(radius), style = Stroke(2.dp.toPx()))
            }
        }
        val screenH = with(density) { maxHeight.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        // The card goes under the target when there's room, otherwise over it.
        val below = hole == null || hole.bottom + gap + with(density) { 220.dp.toPx() } < screenH
        val y = when {
            hole == null -> screenH / 3
            below -> hole.bottom + gap
            else -> hole.top - gap - with(density) { 200.dp.toPx() }
        }
        CoachCard(step, index, total, last, onClose, onNext, onLater, Modifier.offset { IntOffset(0, y.toInt().coerceAtLeast(0)) })
    }
}

@Composable
private fun CoachCard(
    step: TourStep,
    index: Int,
    total: Int,
    last: Boolean,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onLater: (() -> Unit)?,
    modifier: Modifier
) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Slitok.CoachBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(step.title, style = MaterialTheme.typography.titleMedium, color = Slitok.CoachInk, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть обучение", tint = Slitok.CoachQuiet, modifier = Modifier.size(18.dp))
            }
        }
        Text(step.text.nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.CoachText)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(total) { i ->
                    Box(
                        Modifier.size(width = if (i == index) 16.dp else 6.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == index) Slitok.CoachInk else Slitok.CoachInk.copy(alpha = 0.2f))
                    )
                }
            }
            if (onLater != null) {
                Text(
                    "Позже",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Slitok.CoachQuiet,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onLater).padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            Text(
                when {
                    onLater != null -> "Разрешить"
                    last -> "Готово"
                    else -> "Далее"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Slitok.CoachBg,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slitok.CoachInk)
                    .clickable(onClick = onNext)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

// ---- example scenes: the app's own pieces with made-up data ----

@Composable
private fun SceneTitle(title: String, trailing: @Composable () -> Unit = {}) {
    ScreenHeader(title) { trailing() }
}

@Composable
private fun SceneColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun GeneralScene() {
    Box(Modifier.fillMaxSize()) {
        SceneColumn {
            SceneTitle("Обзор") {
                Box(Modifier.size(44.dp).tourTarget(TourTarget.HIDE_EYE), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, tint = Slitok.Muted)
                }
            }
            Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.ACCESS_TILE), radius = 20.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CapitalPartArt(CapitalPart.ACCOUNTS, Modifier.size(width = 72.dp, height = 56.dp))
                    Text("Счета из уведомлений", style = MaterialTheme.typography.titleLarge)
                    BrassButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Разрешить доступ") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f).height(110.dp)) {}
                Panel(Modifier.weight(1f).height(110.dp)) {}
            }
        }
        ExampleBottomBar(Modifier.align(Alignment.BottomCenter).tourTarget(TourTarget.BOTTOM_BAR))
    }
}

@Composable
private fun ExampleBottomBar(modifier: Modifier) {
    Panel(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).then(modifier).fillMaxWidth(), radius = 26.dp) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Icons.Filled.Dashboard to "Обзор",
                Icons.Filled.AccountBalanceWallet to "Капитал",
                Icons.Filled.Percent to "Платежи",
                Icons.Filled.Settings to "Настройки"
            ).forEachIndexed { i, (icon, label) ->
                BarItem(icon, label, selected = i == 0, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Slitok.BrassPill else Color.Transparent).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val tint = if (selected) Slitok.Brass2 else Slitok.Muted
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An example list row: name and sum, a quiet line and its value under them. */
@Composable
private fun ExampleRow(name: String, sum: String, quiet: String, side: String, sideColor: Color = Slitok.Mint, bar: Float? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sum, style = Amounts.Row, color = Slitok.Fg)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(quiet, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, modifier = Modifier.weight(1f))
            Text(side, style = Amounts.Small, color = sideColor)
        }
        if (bar != null) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Slitok.Line)) {
                Box(Modifier.fillMaxWidth(bar).height(3.dp).background(Slitok.Violet))
            }
        }
    }
}

@Composable
private fun DepositsScene(sheet: Boolean) {
    Box(Modifier.fillMaxSize()) {
        SceneColumn {
            SceneTitle("Вклады")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("2 вклада", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(Modifier.tourTarget(TourTarget.ADD_BUTTON)) { AddButton("Добавить вклад") {} }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Box(Modifier.tourTarget(TourTarget.LIST_ROW)) {
                        ExampleRow("Накопительный", "500 000 ₽", "ещё 41 дн.", "+12 340 ₽", bar = 0.7f)
                    }
                    HorizontalDivider(color = Slitok.Line)
                    ExampleRow("Вклад 13,2%", "300 000 ₽", "ещё 120 дн.", "+2 150 ₽", bar = 0.2f)
                }
            }
        }
        if (sheet) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Slitok.PanelTop)
                    .tourTarget(TourTarget.ADD_SHEET)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Добавить вклад", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TileButton(Icons.Filled.AddPhotoAlternate, "Со скринов", Modifier.weight(1f)) {}
                    TileButton(Icons.Filled.Edit, "Вручную", Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashbackScene(target: TourTarget, filled: Boolean) {
    SceneColumn {
        SceneTitle("Кэшбэк")
        when (target) {
            TourTarget.ADD_BUTTON -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Кэшбэка пока нет", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Box(Modifier.tourTarget(TourTarget.ADD_BUTTON)) { AddButton("Добавить кэшбэк") {} }
                }
            }
            TourTarget.MISSING_PERCENT -> {
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusLine("Распознано 3 из 4", Slitok.Mint)
                        Text("Процент одной категории не прочитался", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                    }
                }
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        ExampleLine("Супермаркеты", "5", "groceries")
                        HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                        ExampleLine("Рестораны", "5", "restaurant")
                        HorizontalDivider(Modifier.padding(start = 64.dp), color = Slitok.Line)
                        Box(Modifier.tourTarget(TourTarget.MISSING_PERCENT)) {
                            ExampleLine("Аптеки", if (filled) "5" else "", "pharmacy", asked = !filled)
                        }
                    }
                }
            }
            else -> {
                Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.BANK_PANEL)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Банк-пример", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ValueChip("Супермаркеты", "5%")
                            ValueChip("Рестораны", "5%")
                            ValueChip("Аптеки", "5%")
                            ValueChip("На всё", "1%", dashed = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleLine(name: String, percent: String, icon: String, asked: Boolean = false) {
    CashbackLineRow(
        iconKey = icon,
        categoryName = name,
        percentText = percent,
        onPickCategory = {},
        onPercentChange = {},
        onRemove = {},
        percentState = if (asked) PercentState.Asked else PercentState.Normal,
        note = if (asked) "впишите процент" else null
    )
}

@Composable
private fun AccountsScene() {
    SceneColumn {
        SceneTitle("Счета")
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Box(Modifier.tourTarget(TourTarget.ACCOUNT_ROW)) {
                    ListRow("Банк-пример", leading = { Dot(Slitok.Brass) }, trailing = { Text("84 210 ₽", style = Amounts.Row) })
                }
                HorizontalDivider(Modifier.padding(start = 38.dp), color = Slitok.Line)
                ListRow("Ещё банк", leading = { Dot(Slitok.Brass) }, trailing = { Text("12 600 ₽", style = Amounts.Row) })
            }
        }
    }
}

@Composable
private fun CurrencyScene() {
    SceneColumn {
        SceneTitle("Валюта") { Box(Modifier.tourTarget(TourTarget.ADD_BUTTON)) { AddButton("Купил валюту") {} } }
        Panel(Modifier.fillMaxWidth()) {
            Box(Modifier.tourTarget(TourTarget.CURRENCY_ROW)) {
                ExampleRow("Доллар США", "1 200 $", "по курсу ЦБ", "96 300 ₽", sideColor = Slitok.Fg)
            }
        }
    }
}

@Composable
private fun CashScene() {
    SceneColumn {
        SceneTitle("Наличные")
        Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.CASH_SUM), radius = 22.dp) {
            Box(Modifier.fillMaxWidth().heroGlow(Slitok.Sand)) {
                Text("45 000 ₽", style = Amounts.Hero, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun SubscriptionsScene() {
    SceneColumn {
        SceneTitle("Подписки")
        Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.FINDING)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Музыка-пример?", style = MaterialTheme.typography.bodyLarge)
                Text("199 ₽ каждый месяц, 3 раза", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                ButtonRow {
                    PlateButton(onClick = {}) { Text("Подписка") }
                    GhostButton("Не подписка", onClick = {}, quiet = true)
                }
            }
        }
        Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.LIST_ROW)) {
            ExampleRow("Облако-пример", "149 ₽", "12 ноября", "", sideColor = Slitok.Muted)
        }
        Panel(Modifier.fillMaxWidth().tourTarget(TourTarget.ACCESS_TILE)) {
            ListRow("Доступ к уведомлениям", trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint) })
        }
    }
}

