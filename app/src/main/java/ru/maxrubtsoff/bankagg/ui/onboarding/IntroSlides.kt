package ru.maxrubtsoff.bankagg.ui.onboarding

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.bleed
import ru.maxrubtsoff.bankagg.ui.common.GhostButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

private class Slide(val title: String, val text: String, val picture: @Composable () -> Unit)

private val slides = listOf(
    Slide("Все деньги — в одном итоге", "Счета во всех банках, вклады, валюта и наличные складываются в общий капитал.") { CapitalPicture() },
    Slide("Банки сами рассказывают", "Itogo читает уведомления банков о покупках и пополнениях. Без логинов, паролей и подключения к банкам.") { PushPicture() },
    Slide("Кэшбэк и подписки", "Скрины условий кэшбэка разбираются сами — видно, какой картой платить. Подписки находятся по списаниям.") { CashbackPicture() },
    Slide("Всё остаётся на телефоне", "Счета, суммы и операции хранятся только у вас и никуда не отправляются.") { LockPicture() }
)

/**
 * What the app is, before anything is asked (F-29): four slides, swiped or
 * stepped with «Дальше»; «Пропустить» goes straight on. The pictures are drawn
 * from the app's own pieces, so they look like what comes next.
 */
@Composable
fun IntroSlides(onDone: () -> Unit, doneLabel: String = "Начать") {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Column(
        Modifier.fillMaxSize().background(Slitok.Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!last) GhostButton("Пропустить", onClick = onDone, quiet = true)
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().bleed(24.dp)) { i ->
            val s = slides[i]
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)) {
                s.picture()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.title.nbsp(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(s.text.nbsp(), style = MaterialTheme.typography.bodyLarge, color = Slitok.Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            repeat(slides.size) { i ->
                Box(
                    Modifier.height(8.dp).width(if (i == pager.currentPage) 22.dp else 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) Slitok.Brass else Slitok.Faint)
                )
            }
        }
        BrassButton(onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth()) {
            Text(if (last) doneLabel else "Дальше")
        }
    }
}

@Composable
private fun CapitalPicture() {
    Panel(Modifier.fillMaxWidth(), radius = 22.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Общий капитал", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
            Text("2 847 300 ₽", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val parts = listOf(0.38f to Slitok.Brass, 0.34f to Slitok.Violet, 0.18f to Slitok.Sky, 0.10f to Slitok.Sand)
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                parts.forEach { (w, c) -> Box(Modifier.weight(w).fillMaxHeight().background(c)) }
            }
            listOf("Счета" to Slitok.Brass, "Вклады" to Slitok.Violet, "Валюта" to Slitok.Sky, "Наличные" to Slitok.Sand).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { (t, c) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                            Text(t, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PushPicture() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Slitok.PanelTop).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BankIcon(BankBrand.TBANK, size = 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Т-Банк · сейчас", style = MaterialTheme.typography.labelMedium, color = Slitok.Muted)
                Text("Покупка 631,89 ₽, PYATEROCHKA. Доступно 48 210 ₽", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Slitok.Brass)
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BankIcon(BankBrand.TBANK, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text("Т-Банк", style = MaterialTheme.typography.titleMedium)
                    Text("−631,89 ₽ · PYATEROCHKA", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                }
                Text("48 210 ₽", style = Amounts.Row)
            }
        }
    }
}

@Composable
private fun CashbackPicture() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Лучший кэшбэк", style = MaterialTheme.typography.labelMedium, color = Slitok.Brass2)
                listOf(Triple("Кафе и рестораны", BankBrand.ALFA, "10%"), Triple("АЗС", BankBrand.TBANK, "7%"), Triple("Аптеки", BankBrand.OZON, "5%"))
                    .forEach { (cat, bank, pct) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(cat, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            BankIcon(bank, size = 24.dp)
                            Text(pct, style = MaterialTheme.typography.titleMedium, color = Slitok.Mint, fontWeight = FontWeight.Bold)
                        }
                    }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Slitok.Violet.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = Slitok.VioletText, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Кинопоиск", style = MaterialTheme.typography.bodyLarge)
                    Text("через 3 дня", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                }
                Text("399 ₽", style = Amounts.Small)
            }
        }
    }
}

@Composable
private fun LockPicture() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(
            Modifier.size(120.dp).clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(listOf(Slitok.Brass2, Slitok.Brass))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Lock, contentDescription = null, tint = Slitok.OnBrass, modifier = Modifier.size(52.dp)) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Без сервера и регистрации", "Данные и копии зашифрованы").forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Slitok.Mint)
                    Text(t.nbsp(), style = MaterialTheme.typography.bodyLarge, color = Color.Unspecified)
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}
