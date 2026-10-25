package ru.maxrubtsoff.bankagg.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.maxrubtsoff.bankagg.R
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.domain.model.Reminder
import ru.maxrubtsoff.bankagg.ui.MainActivity

/**
 * The app's own notifications (F-24), in three channels the user can also tune
 * in the system settings. A tap opens the screen the message is about.
 */
class AppNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = appContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_DEPOSITS, "Вклады", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CH_CASHBACK, "Кэшбэк", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CH_BANKS, "Банки", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CH_SUBS, "Подписки", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    /** The system lets the app show notifications (Android 13+ asks; older — always). */
    fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    fun show(r: Reminder) {
        if (!allowed()) return
        val (channel, open) = when (r.kind) {
            Reminder.Kind.DEPOSIT_ENDING -> CH_DEPOSITS to (OPEN_DEPOSIT + (r.depositId ?: ""))
            Reminder.Kind.CASHBACK_MONTH -> CH_CASHBACK to OPEN_CASHBACK
            Reminder.Kind.SUBSCRIPTION -> CH_SUBS to OPEN_SUBSCRIPTIONS
        }
        val n = NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.title)
            .setContentText(r.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
            // A subscription's own icon is the big one (F-33).
            .apply { r.decor?.let { setLargeIcon(DecorBitmap.of(it)) } }
            .setContentIntent(openApp(open, r.id.hashCode()))
            .setAutoCancel(true)
            .private(channel, when (r.kind) {
                Reminder.Kind.DEPOSIT_ENDING -> "Скоро закончится вклад"
                Reminder.Kind.CASHBACK_MONTH -> "Новый месяц кэшбэка"
                Reminder.Kind.SUBSCRIPTION -> "Скоро списание по подписке"
            })
            .build()
        post(r.id.hashCode(), n)
    }

    /** «Похоже на банк: …» with the bank's own text quoted and three answers (F-23). */
    fun showNewBank(c: BankCandidate, guessTitle: String?) {
        if (!allowed()) return
        val id = c.packageName.hashCode()
        val question = guessTitle?.let { "Это ваш $it?" } ?: "Собирать его уведомления?"
        val quote = "«${c.text}»"
        val b = NotificationCompat.Builder(appContext, CH_BANKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Похоже на банк: ${c.appName}")
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$question\n$quote"))
            .setContentIntent(openApp(OPEN_NEW_BANKS, id))
            .setAutoCancel(true)
            .private(CH_BANKS, "Похоже на новый банк")
        if (guessTitle != null) b.addAction(0, "Да", answer(NewBankActionReceiver.YES, c.packageName, id))
        else b.addAction(0, "Новый банк", answer(NewBankActionReceiver.NEW, c.packageName, id))
        b.addAction(0, "Другой банк", openApp(OPEN_NEW_BANKS, id + 1))
        b.addAction(0, "Не банк", answer(NewBankActionReceiver.IGNORE, c.packageName, id + 2))
        post(id, b.build())
    }

    fun cancelNewBank(pkg: String) = manager.cancel(pkg.hashCode())

    /**
     * On a locked phone only [publicTitle] shows: no sums, no quoted bank text (B-31).
     */
    private fun NotificationCompat.Builder.private(channel: String, publicTitle: String) = apply {
        setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        setPublicVersion(
            NotificationCompat.Builder(appContext, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(publicTitle)
                .build()
        )
    }

    private fun post(id: Int, n: android.app.Notification) {
        runCatching { manager.notify(id, n) }   // the permission can be revoked at any moment
    }

    private fun openApp(route: String, request: Int): PendingIntent = PendingIntent.getActivity(
        appContext, request,
        Intent(appContext, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun answer(action: String, pkg: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
        appContext, request,
        Intent(appContext, NewBankActionReceiver::class.java).setAction(action).putExtra(NewBankActionReceiver.EXTRA_PKG, pkg),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_DEPOSIT = "deposit:"
        const val OPEN_CASHBACK = "cashback"
        const val OPEN_NEW_BANKS = "new_banks"
        const val OPEN_SUBSCRIPTIONS = "subscriptions"

        /** «Что храним» — where a widget of a switched-off section leads (U-52). */
        const val OPEN_FEATURES = "features"
        private const val CH_DEPOSITS = "deposits"
        private const val CH_CASHBACK = "cashback"
        private const val CH_BANKS = "banks"
        private const val CH_SUBS = "subscriptions"
    }
}
