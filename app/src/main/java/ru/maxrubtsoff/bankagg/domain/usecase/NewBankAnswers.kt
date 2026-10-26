package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.domain.model.BankSignal
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks
import ru.maxrubtsoff.bankagg.domain.model.NewBanks
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.CandidateRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import java.util.UUID

/**
 * New banks from notifications (F-23): noticing a candidate, and the user's three
 * answers. A linked app's kept notification is recorded right away, so nothing
 * that arrived before the answer is lost.
 */
class NewBankAnswers(
    private val banks: BankRepository,
    private val candidates: CandidateRepository,
    private val settings: SettingsRepository,
    private val record: RecordNotification,
    private val ownApp: String
) {
    /** Called for a notification from an app whose notifications aren't collected. */
    suspend fun notice(pkg: String, appName: String, title: String?, text: String, postedAt: Long): BankCandidate? {
        val d = NewBanks.decide(pkg, appName, title, text, banks.current(), candidates.ignored(), ownApp)
        if (d !is NewBanks.Decision.Ask) return null
        val c = BankCandidate(pkg, appName, title, text, postedAt, d.guessKey)
        candidates.put(c)
        return c
    }

    /** Drops waiting candidates that don't pass today's stricter check (B-25): no question about them. */
    suspend fun sweep() {
        candidates.current().filterNot { BankSignal.looksLikeBank(it.packageName, it.title, it.text) }
            .forEach { candidates.remove(it.packageName) }
    }

    /** «Это мой …»: the app joins that bank (a known bank is added as it is). */
    suspend fun link(c: BankCandidate, bank: BankBrand) {
        // The candidate's package always joins the bank, a known one included:
        // otherwise its notifications keep landing in «unknown» (B-87).
        val linked = bank.copy(packages = bank.packages + c.packageName)
        if (banks.current().any { it.key == bank.key }) banks.update(linked) else banks.add(linked)
        collect(c)
    }

    /** «Новый банк»: one named after the app, with its icon. */
    suspend fun addNew(c: BankCandidate) {
        val known = BankBrand.known.firstOrNull { c.packageName in it.packages }
        val bank = known ?: BankBrand(
            key = BankBrand.USER_PREFIX + UUID.randomUUID(),
            title = c.appName,
            primaryColor = CbrBanks.colorFor(c.appName),
            packages = setOf(c.packageName)
        )
        banks.add(bank)
        collect(c)
    }

    /** «Не банк»: never asked about again. */
    suspend fun ignore(c: BankCandidate) = ignore(c.packageName)

    /**
     * «Не банк» by package: the answer is remembered even when the waiting
     * candidate is already gone — swept away, answered elsewhere or dropped
     * with the app's text (B-57).
     */
    suspend fun ignore(pkg: String) = candidates.ignore(pkg)

    /** The user changed their mind in «Банки» → «Не банки» (B-57). */
    suspend fun unignore(pkg: String) = candidates.unignore(pkg)

    private suspend fun collect(c: BankCandidate) {
        settings.setSelectedPackages(settings.currentSelectedPackages() + c.packageName)
        record(c.packageName, c.title, c.text, c.postedAt)
        candidates.remove(c.packageName)
    }
}
