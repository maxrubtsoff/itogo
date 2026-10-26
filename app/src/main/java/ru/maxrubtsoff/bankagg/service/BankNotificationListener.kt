package ru.maxrubtsoff.bankagg.service

import ru.maxrubtsoff.bankagg.util.InstalledApps
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSignal

/**
 * Reads notifications posted by the banks the user selected and hands them to
 * the domain layer. Only processes selected packages; when nothing is selected
 * yet it falls back to all known bank packages. Group summaries (the collapsed
 * "3 new messages" header) repeat their children and are skipped.
 */
class BankNotificationListener : NotificationListenerService() {

    private var scope = newScope()

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        if (!scope.isActive) scope = newScope()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val container = (applicationContext as App).container

        scope.launch {
            // Accounts switched off (F-21): bank notifications aren't collected at all.
            if (!container.settings.currentFeatures().collectsNotifications) return@launch
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = listOfNotNull(
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ).maxByOrNull { it.length } ?: return@launch
            if (text.isBlank()) return@launch

            val selected = container.settings.currentSelectedPackages()
            val allowed = selected.ifEmpty { BankBrand.allPackages }
            if (pkg !in allowed) {
                // Not one of the user's banks: maybe a new one (F-23) — ask, never collect silently.
                // Most of these are messengers: the cheap text check goes before any PackageManager call (U-34).
                if (!BankSignal.looksLikeBank(pkg, title, text)) return@launch
                val appName = InstalledApps.labelOf(applicationContext, pkg) ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                val c = container.newBankAnswers.notice(pkg, appName, title, text, sbn.postTime)
                if (c != null && container.reminders.current().newBank) {
                    container.notifier.showNewBank(c, c.guessKey?.let { BankBrand.fromKey(it).title })
                }
                return@launch
            }

            container.recordNotification(
                packageName = pkg,
                title = title,
                text = text,
                postedAt = sbn.postTime,
                notificationKey = sbn.key
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }

    override fun onListenerDisconnected() {
        scope.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
