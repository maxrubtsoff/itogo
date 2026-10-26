package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.CandidateRepository
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import ru.maxrubtsoff.bankagg.domain.usecase.NewBankAnswers
import ru.maxrubtsoff.bankagg.domain.usecase.RecordNotification
import java.lang.reflect.Proxy

class NewBankAnswersTest {

    private class FakeBanks(initial: List<BankBrand> = emptyList()) : BankRepository {
        // A plain list: BankBrand.equals is by key, a StateFlow would swallow the update.
        var list = initial
        override val myBanks: Flow<List<BankBrand>> get() = MutableStateFlow(list)
        override suspend fun current() = list
        override suspend fun add(bank: BankBrand) { if (list.none { it.key == bank.key }) list = list + bank }
        override suspend fun update(bank: BankBrand) { list = list.map { if (it.key == bank.key) bank else it } }
        override suspend fun remove(key: String) { list = list.filterNot { it.key == key } }
        override suspend fun cbrEntries(): List<CbrBanks.Entry> = emptyList()
    }

    private class FakeCandidates : CandidateRepository {
        override val candidates: Flow<List<BankCandidate>> = MutableStateFlow(emptyList())
        override suspend fun current() = emptyList<BankCandidate>()
        override suspend fun put(candidate: BankCandidate) {}
        override suspend fun remove(packageName: String) {}
        override suspend fun ignore(packageName: String) {}
        override suspend fun ignored() = emptySet<String>()
        override val ignoredApps: Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun unignore(packageName: String) {}
    }

    // Only the selected-packages calls are used; the rest is never reached.
    private fun settings(): SettingsRepository {
        var pkgs = emptySet<String>()
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SettingsRepository::class.java)) { _, m, a ->
            when (m.name) {
                "currentSelectedPackages" -> pkgs
                "setSelectedPackages" -> { @Suppress("UNCHECKED_CAST") run { pkgs = a[0] as Set<String> }; Unit }
                else -> Unit
            }
        } as SettingsRepository
    }

    private fun <T> stub(c: Class<T>): T =
        Proxy.newProxyInstance(c.classLoader, arrayOf(c)) { _, _, _ -> error("not used") } as T

    // A blank text makes RecordNotification return before touching the stores.
    private fun answers(banks: BankRepository) = NewBankAnswers(
        banks, FakeCandidates(), settings(),
        RecordNotification(stub(EventRepository::class.java), stub(LedgerRepository::class.java), stub(TransactionRunner::class.java)),
        "own"
    )

    private fun cand(pkg: String) = BankCandidate(pkg, "App", null, "", 0L, null)

    @Test fun link_known_bank_adds_candidate_package() = runBlocking {
        val banks = FakeBanks()
        answers(banks).link(cand("new.vtb.app"), BankBrand.VTB)
        assertTrue("new.vtb.app" in banks.list.single { it.key == BankBrand.VTB.key }.packages)
    }

    @Test fun link_known_bank_already_mine_updates_instead_of_skipping() = runBlocking {
        val banks = FakeBanks(listOf(BankBrand.VTB))
        answers(banks).link(cand("new.vtb.app"), BankBrand.VTB)
        assertEquals(1, banks.list.size)
        assertTrue("new.vtb.app" in banks.list.single().packages)
    }

    @Test fun link_unknown_bank_keeps_old_behavior() = runBlocking {
        val mine = BankBrand("user:1", "Мой", 0xFF123456)
        val banks = FakeBanks()
        answers(banks).link(cand("my.pkg"), mine)
        assertEquals(setOf("my.pkg"), banks.list.single().packages)
    }
}
