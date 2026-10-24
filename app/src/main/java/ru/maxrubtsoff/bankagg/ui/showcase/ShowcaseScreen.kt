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
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SelectRow
import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import ru.maxrubtsoff.bankagg.ui.common.SlitokField
import ru.maxrubtsoff.bankagg.ui.common.SlitokSwitch
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.StatTile
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
