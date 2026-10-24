package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.SearchField

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroup
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.Resolution
import ru.maxrubtsoff.bankagg.ui.common.CategoryIcon
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The one category picker, for screenshot rows and manual entry alike. The
 * search field starts from what the screen said (or empty); "Похоже" shows the
 * resolver's best guesses as you type; "Оставить" keeps your own text (its
 * group is still guessed for the icon); "Все" is every group, filtered.
 * "Готово" on the keyboard = keep the text as typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySearchSheet(
    initialQuery: String,
    suggest: suspend (String) -> List<CategoryGroup>,
    resolve: suspend (String) -> Resolution,
    onPick: (displayName: String, groupKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var similar by remember { mutableStateOf<List<CategoryGroup>>(emptyList()) }
    val focus = remember { FocusRequester() }

    var keep by remember { mutableStateOf<Resolution?>(null) }

    LaunchedEffect(query) {
        delay(200)   // debounce typing
        similar = if (query.isBlank()) emptyList() else suggest(query)
        keep = if (query.isBlank()) null else resolve(query)
    }

    /** What "Оставить"/"Готово" saves: the text as typed; a known synonym only picks the icon. */
    fun keptName(): String = query.trim()

    var picked by remember { mutableStateOf(false) }
    fun keepTyped() {
        val text = query.trim()
        if (text.isEmpty() || picked) return
        picked = true
        scope.launch {
            val r = resolve(text)
            onPick(text, r.groupKey)
        }
    }

    val all = CategoryGroups.filter(query).filter { g -> similar.none { it.key == g.key } }

    SlitokSheet(onDismiss, scroll = false) {
        // The field lives in the sheet's own window: focus it once that is composed.
        LaunchedEffect(Unit) {
            awaitFrame()
            runCatching { focus.requestFocus() }
        }
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Например, аптеки",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keepTyped() }),
            modifier = Modifier.focusRequester(focus)
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .imePadding()
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            // The typed name first: it is what the bank calls it; the icon is the one it will get.
            if (query.isNotBlank()) {
                item {
                    val k = keep
                    PickRow(
                        icon = {
                            if (k != null && k.groupKey != CategoryGroups.OTHER) CategoryIcon(CategoryGroups.byKey(k.groupKey).iconKey, size = 30.dp)
                            else Icon(Icons.Filled.Edit, contentDescription = null, tint = Slitok.Muted)
                        },
                        label = "Оставить «${keptName()}»",
                        muted = false,
                        onClick = { keepTyped() }
                    )
                }
            }
            if (similar.isNotEmpty()) {
                item { SectionLabel("Похоже") }
                items(similar, key = { "s-" + it.key }) { g -> GroupRow(g) { onPick(g.name, g.key) } }
            }
            if (all.isNotEmpty()) {
                item { SectionLabel("Все") }
                items(all, key = { "a-" + it.key }) { g -> GroupRow(g) { onPick(g.name, g.key) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Slitok.Muted,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun GroupRow(group: CategoryGroup, onClick: () -> Unit) =
    PickRow(icon = { CategoryIcon(group.iconKey, size = 30.dp) }, label = group.name, muted = false, onClick = onClick)

@Composable
private fun PickRow(icon: @Composable () -> Unit, label: String, muted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (muted) Slitok.Muted else Slitok.Fg,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
