package ru.maxrubtsoff.bankagg.ui.log

import ru.maxrubtsoff.bankagg.ui.common.TagTone
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.Panel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: LogViewModel, onBack: () -> Unit) {
    val events by vm.recent.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            ScreenHeader("Журнал перехвата", onBack)
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Последние перехваченные уведомления",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (events.isEmpty()) {
                Text(
                    "Пока ничего не перехвачено. Сделайте операцию или дождитесь уведомления банка.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(events, key = { it.id }) { EventRow(it, fmt) }
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: CapturedEvent, fmt: SimpleDateFormat) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            BankIcon(e.brand, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.brand.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(e.parsed)
                }
                Text(
                    fmt.format(Date(e.postedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                MoneyText(e.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
        }
    }
}

@Composable
private fun StatusChip(parsed: Boolean) {
    if (parsed) SlitokTag("разобрано", TagTone.Mint) else SlitokTag("не разобрано")
}
