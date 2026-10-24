package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.util.Perms

/**
 * Whether the app may read notifications, re-checked every time the screen
 * comes back (the user returns from system settings, or Android revoked it).
 */
@Composable
fun rememberNotificationAccess(): State<Boolean> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(Perms.hasNotificationAccess(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = Perms.hasNotificationAccess(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Balances stopped updating: say so, and open the setting in one tap. */
@Composable
fun NotificationAccessBanner(
    modifier: Modifier = Modifier,
    text: String = "Балансы не обновляются — нет доступа к уведомлениям"
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Slitok.BrassTint)
            .border(1.dp, Slitok.BrassLine, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = Slitok.Brass2, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = Slitok.Fg
            )
        }
        BrassButton(onClick = { Perms.openNotificationAccessSettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Включить")
        }
    }
}
