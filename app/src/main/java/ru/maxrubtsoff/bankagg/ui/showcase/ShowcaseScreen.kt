package ru.maxrubtsoff.bankagg.ui.showcase

import androidx.compose.animation.core.animateDpAsState
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import androidx.compose.foundation.shape.CircleShape
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor
import ru.maxrubtsoff.bankagg.ui.common.BrandAvatar
import ru.maxrubtsoff.bankagg.ui.common.BrandDecorEditor
import ru.maxrubtsoff.bankagg.ui.common.rememberBrandDecorState
import ru.maxrubtsoff.bankagg.ui.common.squircle
import ru.maxrubtsoff.bankagg.ui.common.FingerPointer
import ru.maxrubtsoff.bankagg.ui.common.ScanReveal
import ru.maxrubtsoff.bankagg.ui.common.ScanningThumb
import ru.maxrubtsoff.bankagg.ui.common.ShotHeader
import ru.maxrubtsoff.bankagg.ui.common.cascade
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.ui.common.AddButton
import ru.maxrubtsoff.bankagg.ui.common.AmountField
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.ButtonRow
import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.Dot
import ru.maxrubtsoff.bankagg.ui.common.HistoryLine
import ru.maxrubtsoff.bankagg.domain.model.RowKind
import ru.maxrubtsoff.bankagg.ui.common.EmptyState
import ru.maxrubtsoff.bankagg.ui.common.GhostButton
import ru.maxrubtsoff.bankagg.ui.common.ListRow
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.errorShake
import ru.maxrubtsoff.bankagg.ui.common.rememberErrorShake
import ru.maxrubtsoff.bankagg.ui.common.MoreAction
import ru.maxrubtsoff.bankagg.ui.common.MoreMenu
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.AddShotTile
import ru.maxrubtsoff.bankagg.ui.common.PendingShotsPlate
import ru.maxrubtsoff.bankagg.ui.common.DeltaLine
import ru.maxrubtsoff.bankagg.ui.common.PeriodChips
import ru.maxrubtsoff.bankagg.ui.common.CapitalChart
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.common.ShotDragLayer
import ru.maxrubtsoff.bankagg.ui.common.ShotDropZone
import ru.maxrubtsoff.bankagg.ui.common.ShotThumbs
import ru.maxrubtsoff.bankagg.ui.common.rememberShotDragState
import ru.maxrubtsoff.bankagg.ui.common.shotDropTarget
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SelectRow
import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import ru.maxrubtsoff.bankagg.ui.common.SlitokField
import ru.maxrubtsoff.bankagg.ui.common.SlitokSwitch
import ru.maxrubtsoff.bankagg.ui.common.SheetIconTile
import ru.maxrubtsoff.bankagg.ui.common.SheetList
import ru.maxrubtsoff.bankagg.ui.common.SheetRow
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.BankStack
import ru.maxrubtsoff.bankagg.ui.common.StatTile
import ru.maxrubtsoff.bankagg.ui.common.SectionActions
import ru.maxrubtsoff.bankagg.ui.common.ChipButton
import ru.maxrubtsoff.bankagg.ui.common.StatusLine
import ru.maxrubtsoff.bankagg.ui.common.SwitchRow
import ru.maxrubtsoff.bankagg.ui.common.TagTone
import ru.maxrubtsoff.bankagg.ui.common.TileButton
import ru.maxrubtsoff.bankagg.ui.common.ValueChip
import ru.maxrubtsoff.bankagg.ui.overview.CapitalPartArt
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * «Витрина» (F-31): every «Слиток» component as the app draws it, in its
 * states — to check the code against docs/design-system on a phone. Debug builds only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShowcaseScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            ScreenHeader("Витрина", onBack) {
                MoreMenu(listOf(MoreAction("Изменить", Icons.Filled.Edit) {}, MoreAction("Удалить", Icons.Filled.Delete, danger = true) {}))
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Caption("Суммы")
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MoneyText("4 522 208,94 ₽", style = Amounts.Hero)
                    MoneyText("1 000 000 ₽", style = Amounts.Large)
                    MoneyText("26 148,55 ₽", style = Amounts.Row)
                    MoneyText("+18 083,24 ₽", style = Amounts.Small, color = Slitok.Mint)
                }
            }

            Caption("Копейки по ступени")
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyText("98 262,55 ₽", style = Amounts.Large)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Операции после", color = Slitok.Muted)
                        MoneyText("−5 000,50 ₽", style = Amounts.Row)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Доход за месяц", color = Slitok.Muted)
                        MoneyText("+1 713,92 ₽", style = Amounts.Small, color = Slitok.Mint)
                    }
                    MoneyText("Покупка 1 073,40 ₽ в FAST-FUD", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                }
            }

            Caption("Движение")
            var moved by remember { mutableStateOf(false) }
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("FAST" to Motion.FAST, "NORMAL" to Motion.NORMAL, "SLOW" to Motion.SLOW).forEach { (name, ms) ->
                        MotionTrack(name, ms, moved)
                    }
                    GhostButton("Запустить", onClick = { moved = !moved })
                    // The «no» shake: one implementation for every refusal, still with animations off.
                    val shake = rememberErrorShake()
                    val scope = rememberCoroutineScope()
                    Box(Modifier.errorShake(shake)) {
                        PlateButton(onClick = { scope.launch { shake.play() } }, modifier = Modifier.fillMaxWidth()) { Text("Ошибка: встряхнуть") }
                    }
                }
            }

            Caption("Оформление и аватар бренда")
            BrandDemo()

            Caption("Распознавание (скан)")
            ScanDemo()

            Caption("Шапка скрина")
            ShotHeaderDemo()

            Caption("Обучение")
            TourDemo()

            Caption("Кнопки")
            BrassButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
            ButtonRow {
                PlateButton(onClick = {}) { Text("Вручную") }
                BrassButton(onClick = {}, enabled = false) { Text("Выключено") }
            }
            ButtonRow {
                BrassButton(onClick = {}) { Text("Сохранить сейчас") }
                PlateButton(onClick = {}) { Text("Восстановить из файла") }
            }
            DangerButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Удалить") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("Восстановить из копии", onClick = {})
                GhostButton("Пропустить", onClick = {}, quiet = true)
            }
            AddButton("Добавить") {}
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TileButton(Icons.Filled.AddPhotoAlternate, "Со скринов", Modifier.weight(1f)) {}
                TileButton(Icons.Filled.Edit, "Вручную", Modifier.weight(1f)) {}
            }

            Caption("Поля")
            var text by remember { mutableStateOf("") }
            SlitokField(text, { text = it }, label = "Название", placeholder = "Например, «Подушка»")
            SlitokField("", {}, label = "Ошибка — после попытки сохранить", error = "Впишите название")
            SlitokField("секретный", {}, label = "Пароль", password = true)
            var sum by remember { mutableStateOf(TextFieldValue("1000000")) }
            AmountField(sum, { sum = it }, "₽", label = "Сумма")
            SelectRow(null, "Выберите банк", onClick = {})

            Caption("Выбор")
            var on by remember { mutableStateOf(true) }
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    SwitchRow("Собирать уведомления", on, { on = it }) { Icon(Icons.Filled.Notifications, null, tint = Slitok.Muted) }
                    HorizontalDivider(color = Slitok.Line)
                    SwitchRow("Выключено", false, {}, enabled = false)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlitokSwitch(true, null)
                SlitokSwitch(false, null)
            }
            var picked by remember { mutableStateOf(true) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SlitokChip("за 1 день", picked, { picked = !picked })
                SlitokChip("за 3 дня", !picked, { picked = !picked })
                ValueChip("Искусство", "25%")
                ValueChip("На всё", "1%", dashed = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SlitokTag("Готово", TagTone.Mint)
                SlitokTag("Не распознано")
                SlitokTag("вручную", TagTone.Brass)
                SlitokTag("11,45%", TagTone.Violet)
                SlitokTag("Ошибка", TagTone.Coral)
            }
            StatusLine("Распознано 3 из 4", Slitok.Mint)

            Caption("Строки и плитки")
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    ListRow("Счёт", onClick = {}, leading = { Dot(Slitok.Brass) }, trailing = { MoneyText("26 148,55 ₽", style = Amounts.Row) })
                    HorizontalDivider(color = Slitok.Line)
                    ListRow("Очень длинное название строки, которое не помещается", onClick = {}, leading = { Dot(Slitok.Violet) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("К выдаче", Modifier.weight(1f)) { MoneyText("1,33 млн", style = Amounts.Large) }
                StatTile("Доход за срок", Modifier.weight(1f)) { MoneyText("+229 600", style = Amounts.Large, color = Slitok.Mint) }
            }
            // U-59: tiles of the Settings — the whole tile opens something; «Банки» holds a small stack and a count.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Что храним", Modifier.weight(1f), onClick = {}) {
                    Text("4 из 6", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatTile("Банки", Modifier.weight(1f), onClick = {}) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BankStack(listOf(BankBrand.SBER, BankBrand.TBANK, BankBrand.ALFA), max = 2, size = 28.dp, overlap = 8.dp)
                        Text("3", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Caption("Список в шторке")
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SheetList {
                        SheetRow("Сегодня, 19:04", subtitle = "четверг", onClick = {})
                        SheetRow("Вчера, 21:42", subtitle = "среда", trailing = { SlitokTag("вручную", TagTone.Brass) }, onClick = {})
                        SheetRow("20 октября, 08:15", subtitle = "вторник", selected = true, onClick = {})
                    }
                    SheetList {
                        SheetRow("Из файла…", leading = { SheetIconTile(Icons.Filled.Edit) }, arrow = true, onClick = {})
                    }
                }
            }

            Caption("Строка действий раздела")
            SectionActions(summary = "7 вкладов", controls = { ChipButton("по закрытию") {} }, onAdd = {}, addDescription = "Добавить вклад")
            SectionActions(summary = "6 банков · 23 категории", lead = { ChipButton("Октябрь") {} }, onAdd = {}, addDescription = "Добавить кэшбэк")

            Caption("Скрины ждут проверки")
            PendingShotsPlate(shots = 4, thumbs = listOf("", "", ""), ready = 2, check = 1, unread = 1, onOpen = {})

            Caption("История капитала")
            DeltaLine(Money.ofMinor(4_830_000), "за месяц", listOf(10L, 12, 11, 15, 14, 18, 20), onClick = {})
            DeltaLine(Money.ofMinor(177_000_000), "за месяц", listOf(10L, 12, 11, 15, 14, 18, 20), onClick = {}, modifier = Modifier.width(260.dp))
            DeltaLine(Money.ofMinor(-400_000), "за неделю", null, onClick = {})
            DeltaLine(Money.ZERO, "за месяц", null, onClick = {})
            var period by remember { mutableStateOf(HistoryPeriod.MONTH) }
            PeriodChips(period, onPick = { period = it })
            PeriodChips(period, onPick = { period = it }, compact = true)
            var marked by remember { mutableStateOf<Int?>(null) }
            CapitalChart(
                points = listOf(10L, 12, 11, 15, 14, 18, 17, 21, 20, 24), axis = listOf("1 окт", "8 окт", "15 окт", "25 окт"),
                selected = marked, onScrub = { marked = it }, onRelease = {},
                range = marked?.takeIf { it > 0 }?.let { (it - 1)..it }
            )

            Caption("Строка истории")
            HistoryLine("Новый вклад", "Сбер", 10_000_000, RowKind.GO)
            HistoryLine("Проценты", "2 вклада", 34_000, RowKind.OPEN, expanded = true)
            HistoryLine("Сбер", "Проценты", 21_000, RowKind.GO, nested = true)
            HistoryLine("Списания", "4 операции", -320_000, RowKind.SUM)
            HistoryLine("Пополнение", "Иван", 500_000, RowKind.SUM, highlighted = true)

            Caption("Перенос скрина пальцем")
            ShotDragDemo()
            ShotThumbs(listOf("a" to "", "b" to ""), onOpen = {}, trailing = { AddShotTile {} })

            Caption("Пустое состояние")
            EmptyState("Пока нет вкладов", art = { CapitalPartArt(CapitalPart.DEPOSITS, Modifier.size(84.dp, 64.dp)) }) {
                BrassButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("Добавить вклад")
                }
            }
            Caption("")
        }
    }
}

/** U-65: a thumbnail carried between two cards; the zone opens under them while it is in the air. */
@Composable
private fun ShotDragDemo() {
    var inSecond by remember { mutableStateOf(false) }
    val drag = rememberShotDragState { _, target -> inSecond = target == "b" }
    Box(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (key in listOf("a", "b")) {
                Box(Modifier.shotDropTarget(drag, key)) {
                    Panel(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (key == "a") "Альфа-Вклад" else "Лучший %", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if ((key == "b") == inSecond) ShotThumbs(listOf("demo" to ""), onOpen = {}, drag = drag)
                        }
                    }
                }
            }
            ShotDropZone(drag, "Отдельный вклад")
        }
        ShotDragLayer(drag)
    }
}

/** F-33: the avatar of a bank and of a subscription, and the editor of a look. */
@Composable
private fun BrandDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandAvatar(BrandDecor.defaultFor("Совкомбанк"), CircleShape, 48.dp)
            BrandAvatar(BrandDecor.defaultFor("Яндекс Плюс"), squircle(48.dp), 48.dp, overlay = BankBrand.TBANK)
            BrandAvatar(BrandDecor.defaultFor("Netflix"), squircle(36.dp), 36.dp)
        }
        val state = rememberBrandDecorState("Яндекс Плюс", BrandDecor.defaultFor("Яндекс Плюс"))
        BrandDecorEditor(state, squircle(64.dp))
    }
}

/** F-34: the scan line over a screenshot, then the fields rising one after another. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanDemo() {
    var status by remember { mutableStateOf(ShotStatus.PROCESSING) }
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScanningThumb(status, Modifier.size(width = 130.dp, height = 220.dp), foundText = "Нашли банк и 3 категории") {
                Column(Modifier.fillMaxSize().background(Slitok.Inset).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ozon Банк", style = MaterialTheme.typography.labelMedium, color = Slitok.Fg)
                    listOf("Искусство · 25%", "Отели · 5%", "Рестораны · 5%").forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            ScanReveal(status, skeletonHeight = 96.dp) { play ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Искусство" to "25%", "Отели" to "5%", "Рестораны" to "5%").forEachIndexed { i, (name, percent) ->
                        ValueChip(name, percent, Modifier.cascade(i, play))
                    }
                }
            }
            GhostButton(
                if (status == ShotStatus.PROCESSING) "Показать результат" else "Ещё раз",
                onClick = { status = if (status == ShotStatus.PROCESSING) ShotStatus.RECOGNIZED else ShotStatus.PROCESSING }
            )
        }
    }
}

/** U-53: the head over a recognised screenshot, through its states; a tap on «Дальше» moves to the next one. */
@Composable
private fun ShotHeaderDemo() {
    var step by remember { mutableIntStateOf(0) }
    val states = listOf(ShotStatus.PROCESSING, ShotStatus.NEEDS_INPUT, ShotStatus.RECOGNIZED, ShotStatus.FAILED)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShotHeader("", states[step], hint = if (states[step] == ShotStatus.NEEDS_INPUT) "Не прочиталось: дата закрытия" else null)
        GhostButton("Дальше", onClick = { step = (step + 1) % states.size })
    }
}

/** F-32: the finger of a tour moves to a button and presses it. */
@Composable
private fun TourDemo() {
    var step by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(96.dp)) {
                val w = with(density) { maxWidth.toPx() }
                val h = with(density) { 96.dp.toPx() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TileButton(Icons.Filled.AddPhotoAlternate, "Со скринов", Modifier.weight(1f)) {}
                    TileButton(Icons.Filled.Edit, "Вручную", Modifier.weight(1f)) {}
                }
                FingerPointer(
                    target = when (step) {
                        0 -> null
                        1 -> Offset(w * 0.25f, h * 0.5f)
                        else -> Offset(w * 0.75f, h * 0.5f)
                    },
                    pressed = step == 2
                )
            }
            GhostButton(if (step == 2) "Ещё раз" else "Дальше", onClick = { step = (step + 1) % 3 })
        }
    }
}

/** A brass dot running across a track in [ms] from the motion scale. */
@Composable
private fun MotionTrack(name: String, ms: Int, moved: Boolean) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(10.dp)).background(Slitok.Inset)
    ) {
        val travel = maxWidth - 26.dp
        val x by animateDpAsState(if (moved) travel else 0.dp, Motion.tween(ms), label = name)
        Box(Modifier.padding(5.dp).offset(x = x).size(16.dp).clip(RoundedCornerShape(6.dp)).background(Slitok.Brass))
        Text("$name · $ms", style = MaterialTheme.typography.labelSmall, color = Slitok.Faint,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
    }
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Slitok.Faint, modifier = Modifier.padding(top = 8.dp))
}
