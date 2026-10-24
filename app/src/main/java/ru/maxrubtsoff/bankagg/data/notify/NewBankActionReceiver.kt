package ru.maxrubtsoff.bankagg.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.domain.model.BankBrand

/** «Да» / «Новый банк» / «Не банк» right from the notification, without opening the app (F-23). */
class NewBankActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val container = (context.applicationContext as App).container
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val c = container.bankCandidates.current().firstOrNull { it.packageName == pkg } ?: return@launch
                when (intent.action) {
                    YES -> c.guessKey?.let { container.newBankAnswers.link(c, BankBrand.fromKey(it)) }
                    NEW -> container.newBankAnswers.addNew(c)
                    IGNORE -> container.newBankAnswers.ignore(c)
                }
                container.notifier.cancelNewBank(pkg)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val YES = "ru.maxrubtsoff.bankagg.NEW_BANK_YES"
        const val NEW = "ru.maxrubtsoff.bankagg.NEW_BANK_NEW"
        const val IGNORE = "ru.maxrubtsoff.bankagg.NEW_BANK_IGNORE"
        const val EXTRA_PKG = "pkg"
    }
}
