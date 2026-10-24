package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The row of a section's actions (U-58): the first line of a section's content,
 * right under the header and the section switch. Left — what is shown
 * («7 вкладов»); right — the list's controls ([controls]: month, view, sort),
 * then the brass «+» ([onAdd]) always last, in the right corner. The header
 * above keeps only the title, «Назад» and the screen's icons.
 *
 * [lead] — a choice that stands first on the left (the cashback's month): then
 * the [summary] moves to a quiet line under the row.
 */
@Composable
fun SectionActions(
    summary: String,
    modifier: Modifier = Modifier,
    controls: @Composable RowScope.() -> Unit = {},
    onAdd: (() -> Unit)? = null,
    addDescription: String = "Добавить",
    lead: (@Composable () -> Unit)? = null
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lead != null) {
                lead()
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = Slitok.Fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            controls()
            if (onAdd != null) AddButton(addDescription, onAdd)
        }
        if (lead != null) {
            Text(summary, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
