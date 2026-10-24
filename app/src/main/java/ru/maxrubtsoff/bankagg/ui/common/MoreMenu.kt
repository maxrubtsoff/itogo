package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** One action of a screen's «⋮» menu; [danger] ones are coral and stand apart at the bottom. */
data class MoreAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * «⋮» in a screen's top bar (U-43): rare and dangerous actions of the screen
 * live here, not as big buttons under the content. Same look as the long-press
 * menu (U-44): compact rows, quiet icons, coral only for the dangerous one.
 */
@Composable
fun MoreMenu(actions: List<MoreAction>) {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Ещё", tint = Slitok.Muted)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Slitok.PanelTop,
            border = BorderStroke(1.dp, Slitok.Line),
            shadowElevation = 16.dp
        ) {
            val regular = actions.filterNot { it.danger }
            val risky = actions.filter { it.danger }
            regular.forEach { MoreItem(it) { open = false } }
            if (regular.isNotEmpty() && risky.isNotEmpty()) HorizontalDivider(color = Slitok.Line)
            risky.forEach { MoreItem(it) { open = false } }
        }
    }
}

@Composable
private fun MoreItem(action: MoreAction, close: () -> Unit) {
    val color = if (action.danger) Slitok.Coral else Slitok.Fg
    DropdownMenuItem(
        text = { Text(action.label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize)) },
        onClick = { close(); action.onClick() },
        leadingIcon = { Icon(action.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        colors = MenuDefaults.itemColors(
            textColor = color,
            leadingIconColor = if (action.danger) Slitok.Coral else Slitok.Muted
        )
    )
}
