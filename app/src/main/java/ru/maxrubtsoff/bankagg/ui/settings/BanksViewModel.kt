package ru.maxrubtsoff.bankagg.ui.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import ru.maxrubtsoff.bankagg.domain.model.BankSummary
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashbackRepository
import ru.maxrubtsoff.bankagg.domain.repository.SubscriptionRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.domain.repository.CandidateRepository
import ru.maxrubtsoff.bankagg.domain.usecase.NewBankAnswers
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.data.icons.LogoMaker
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import java.util.UUID

/** The user's banks (F-22): the list, adding from hints or the CBR list, own banks, collecting notifications. */
class BanksViewModel(
    private val banks: BankRepository,
    private val settings: SettingsRepository,
    installedIcons: StateFlow<Map<String, Bitmap>>,
    private val logos: LogoMaker,
    private val candidateRepo: CandidateRepository,
    private val answers: NewBankAnswers,
    private val ledger: LedgerRepository,
    private val deposits: DepositRepository,
    private val cashback: CashbackRepository,
    private val subscriptions: SubscriptionRepository,
    private val today: Today,
    private val undo: UndoCenter
) : ViewModel() {

    /** Apps that look like banks, waiting for an answer (F-23). */
    val candidates: StateFlow<List<BankCandidate>> = candidateRepo.candidates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun link(c: BankCandidate, bank: BankBrand) = viewModelScope.launch { answers.link(c, bank) }
    fun addNew(c: BankCandidate) = viewModelScope.launch { answers.addNew(c) }
    fun ignore(c: BankCandidate) = viewModelScope.launch { answers.ignore(c) }

    /** Apps answered «Не банк» — shown at the bottom of «Банки» to change one's mind (B-57). */
    val notBanks: StateFlow<Set<String>> =
        candidateRepo.ignoredApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun unignore(pkg: String) = viewModelScope.launch { answers.unignore(pkg) }


    val mine: StateFlow<List<BankBrand>> = banks.myBanks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Packages whose notifications are collected. */
    val collected: StateFlow<Set<String>> = settings.selectedPackages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Known banks with an app on this phone that aren't added yet. */
    val onPhone: StateFlow<List<BankBrand>> = combine(installedIcons, banks.myBanks) { icons, my ->
        BankBrand.known.filter { it.key in icons.keys && it !in my }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The largest banks not added yet (business apps aren't "popular"). */
    val popular: StateFlow<List<BankBrand>> = combine(banks.myBanks, onPhone) { my, phone ->
        BankBrand.known.filter { !it.isBusiness && it !in my && it !in phone }.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _results = MutableStateFlow<List<BankBrand>>(emptyList())
    val results: StateFlow<List<BankBrand>> = _results

    fun search(query: String) = viewModelScope.launch {
        _results.value = CbrBanks.search(query, banks.cbrEntries())
    }

    /** Adds a bank; one with an app starts collecting its notifications right away. */
    fun add(bank: BankBrand) = viewModelScope.launch {
        banks.add(bank)
        if (bank.packages.isNotEmpty()) settings.setSelectedPackages(settings.currentSelectedPackages() + bank.packages)
    }

    fun addAll(list: List<BankBrand>) = list.forEach { add(it) }

    /** Takes the bank out of «Мои банки»: its data hides, nothing is deleted (B-21); «Вернуть» brings it back. */
    fun remove(bank: BankBrand) = viewModelScope.launch {
        val wasCollecting = bank.packages.filter { it in settings.currentSelectedPackages() }
        val held = summary(bank.key).first()
        banks.remove(bank.key)
        settings.setSelectedPackages(settings.currentSelectedPackages() - bank.packages)
        if (held.offersUndo) undo.offer("${bank.title} убран", restore = {
            banks.add(bank)
            settings.setSelectedPackages(settings.currentSelectedPackages() + wasCollecting)
        })
    }

    /** What the bank holds, for its card and the remove question (U-24). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun summary(bankKey: String): Flow<BankSummary> {
        val month = today.date.flatMapLatest { cashback.observeMonth(CashbackMonth.keyOf(it)) }
        return combine(ledger.observeAccounts(), deposits.observeAll(), month, subscriptions.observeAll(), today.date) { a, d, c, s, day ->
            BankSummary.of(bankKey, a, d, c, s, day)
        }
    }

    fun toggleCollect(bank: BankBrand) = viewModelScope.launch {
        val now = settings.currentSelectedPackages()
        val on = bank.packages.any { it in now }
        settings.setSelectedPackages(if (on) now - bank.packages else now + bank.packages)
    }

    /** A new own bank or changes to one; [picture] — a gallery image to cut into the logo. */
    fun saveOwn(existing: BankBrand?, title: String, letters: String, color: Long, textColor: Long, picture: Uri?, usePicture: Boolean, onDone: (BankBrand) -> Unit) =
        viewModelScope.launch {
            val key = existing?.key ?: (BankBrand.USER_PREFIX + UUID.randomUUID())
            val logo = when {
                !usePicture -> null
                picture != null -> logos.make(picture, key)
                else -> existing?.logoFile
            }
            val bank = (existing ?: BankBrand(key, title.trim(), color)).copy(
                title = title.trim(),
                primaryColor = color,
                letters = letters.trim().uppercase().take(3).ifEmpty { BankBrand.lettersOf(title) },
                textColor = textColor,
                logoFile = logo
            )
            if (existing == null) banks.add(bank) else banks.update(bank)
            onDone(bank)
        }
}
