package ru.maxrubtsoff.bankagg.ui.subscriptions

import ru.maxrubtsoff.bankagg.domain.model.LogoChoice
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.data.icons.LogoMaker
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Finding
import ru.maxrubtsoff.bankagg.domain.model.Payment
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.model.SubscriptionStatus
import ru.maxrubtsoff.bankagg.domain.model.Subscriptions
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.SubscriptionRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Subscriptions (F-27): the list with its monthly total, the runs of payments
 * that look like one, and the draft the form edits. Payments come from the
 * operations the bank pushes already filled; a subscription with a payee keeps
 * itself up to date from them.
 */
class SubscriptionsViewModel(
    private val repo: SubscriptionRepository,
    ledger: LedgerRepository,
    banks: BankRepository,
    private val today: Today,
    /** Makes the picture of a subscription's look; null where nothing is saved (the tours' sandbox). */
    private val logos: LogoMaker?
) : ViewModel() {

    private val payments = ledger.observeTransactions(PAYMENT_WINDOW).map { list ->
        list.filter { it.amount.minor < 0 && !it.counterparty.isNullOrBlank() }.map { t ->
            Payment(t.counterparty!!, -t.amount.minor, LocalDate.ofInstant(java.time.Instant.ofEpochMilli(t.postedAt), ZoneId.systemDefault()), t.brand)
        }
    }

    private val all = repo.observeAll()

    data class State(
        val active: List<Subscription> = emptyList(),
        val perMonth: Long = 0,
        val findings: List<Finding> = emptyList(),
        val payments: List<Payment> = emptyList(),
        val today: LocalDate = LocalDate.now()
    )

    val state: StateFlow<State> = combine(all, payments, today.date) { subs, pays, day ->
        // New payments of known subscriptions move their dates and prices (idempotent).
        subs.filter { it.status == SubscriptionStatus.ACTIVE }.forEach { s ->
            val synced = Subscriptions.sync(s, pays)
            if (synced != s) viewModelScope.launch { repo.save(synced) }
        }
        val active = subs.filter { it.status == SubscriptionStatus.ACTIVE }.sortedBy { Subscriptions.upcoming(it, day) }
        State(
            active = active,
            perMonth = Subscriptions.perMonth(active),
            findings = Subscriptions.find(pays, Subscriptions.knownPayees(subs), day),
            payments = pays,
            today = day
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    val myBanks: StateFlow<List<BankBrand>> = banks.myBanks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draft = MutableStateFlow<Subscription?>(null)
    /** What the form edits: a new one (blank or from a finding) or an existing one. */
    val draft: StateFlow<Subscription?> = _draft.asStateFlow()

    fun newBlank() {
        _draft.value = Subscription(UUID.randomUUID().toString(), "", 0, Period.MONTHLY, today.date.value.plusMonths(1), createdAt = System.currentTimeMillis())
    }

    fun newFrom(f: Finding) {
        _draft.value = Subscriptions.fromFinding(f, UUID.randomUUID().toString(), System.currentTimeMillis())
    }

    fun edit(s: Subscription) { _draft.value = s }

    fun save(s: Subscription, onDone: () -> Unit) = viewModelScope.launch {
        repo.save(s)
        onDone()
    }

    /**
     * Saves [s] with its look (F-33): [picked] is a gallery image to cut into the
     * subscription's picture; without [usePicture] the picture goes, letters stay.
     */
    fun saveWithPicture(s: Subscription, picked: Uri?, usePicture: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        val look = s.look
        val made = if (usePicture && picked != null) logos?.make(picked, s.id) else null
        val logo = LogoChoice.resolve(usePicture, made, picked != null, look.logoFile)
        repo.save(s.copy(decor = look.copy(logoFile = logo)))
        onDone()
    }

    /** «Это …»: the run's payee joins [s]; its payments move it on. */
    fun link(f: Finding, s: Subscription) = viewModelScope.launch {
        repo.save(Subscriptions.sync(s.copy(payee = f.payee), f.payments))
    }

    /** «Не подписка»: the payee isn't offered again. */
    fun notSubscription(f: Finding) = viewModelScope.launch {
        repo.save(
            Subscription(UUID.randomUUID().toString(), f.title, f.priceMinor, f.period, f.nextDate, payee = f.payee,
                status = SubscriptionStatus.IGNORED, createdAt = System.currentTimeMillis())
        )
    }

    /** «Больше не плачу»: off the list and the total; its payee stays known, so it isn't found again. */
    fun stop(s: Subscription) = viewModelScope.launch { repo.save(s.copy(status = SubscriptionStatus.ARCHIVED)) }

    fun setRemind(s: Subscription, on: Boolean) = viewModelScope.launch { repo.save(s.copy(remind = on)) }

    private companion object {
        /** A year of operations is enough to see a yearly subscription twice. */
        const val PAYMENT_WINDOW = 5_000
    }
}
