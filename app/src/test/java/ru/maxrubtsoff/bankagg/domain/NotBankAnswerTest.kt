package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate
import ru.maxrubtsoff.bankagg.domain.model.NewBanks
import ru.maxrubtsoff.bankagg.domain.repository.CandidateRepository

/**
 * «Не банк» is remembered for good, by package — the waiting candidate may
 * already be gone when the answer arrives from the notification itself (B-57).
 */
class NotBankAnswerTest {

    private val chat = "com.some.wallet"
    private val payment = "Зачисление 1 000 ₽. Баланс 5 000 ₽"
    private val ownApp = "ru.maxrubtsoff.bankagg"

    private class FakeCandidates : CandidateRepository {
        val list = MutableStateFlow<List<BankCandidate>>(emptyList())
        private val ignoredSet = MutableStateFlow(emptySet<String>())
        override val candidates: Flow<List<BankCandidate>> = list
        override suspend fun current(): List<BankCandidate> = list.value
        override suspend fun put(candidate: BankCandidate) {
            list.value = list.value.filterNot { it.packageName == candidate.packageName } + candidate
        }
        override suspend fun remove(packageName: String) {
            list.value = list.value.filterNot { it.packageName == packageName }
        }
        override suspend fun ignore(packageName: String) {
            ignoredSet.value = ignoredSet.value + packageName
            remove(packageName)
        }
        override suspend fun ignored(): Set<String> = ignoredSet.value
        override val ignoredApps: Flow<Set<String>> = ignoredSet
        override suspend fun unignore(packageName: String) {
            ignoredSet.value = ignoredSet.value - packageName
        }
    }

    @Test fun ignored_app_isNeverAskedAbout_again() = runTest {
        val candidates = FakeCandidates()
        candidates.ignore(chat)
        assertEquals(
            NewBanks.Decision.Skip,
            NewBanks.decide(chat, "Кошелёк", null, payment, emptyList(), candidates.ignored(), ownApp)
        )
    }

    @Test fun returning_an_app_asks_about_it_again() = runTest {
        val candidates = FakeCandidates()
        candidates.ignore(chat)
        candidates.unignore(chat)
        assertTrue(
            NewBanks.decide(chat, "Кошелёк", null, payment, emptyList(), candidates.ignored(), ownApp)
                is NewBanks.Decision.Ask
        )
    }

    @Test fun answer_without_a_waiting_candidate_isStillRemembered() = runTest {
        // The candidate was swept away (B-25) or answered elsewhere, and the
        // answer comes from the notification: it must still be stored.
        val candidates = FakeCandidates()
        candidates.ignore(chat)
        assertEquals(setOf(chat), candidates.ignored())
        assertTrue(candidates.current().isEmpty())
    }

    @Test fun ignoring_drops_the_waiting_candidate() = runTest {
        val candidates = FakeCandidates()
        candidates.put(BankCandidate(chat, "Кошелёк", null, payment, 1L, null))
        candidates.ignore(chat)
        assertTrue(candidates.current().isEmpty())
        assertEquals(setOf(chat), candidates.ignored())
    }

    @Test fun ignored_app_isSkipped_evenNextToTheUsersBanks() {
        val d = NewBanks.decide(chat, "Кошелёк", null, payment, listOf(BankBrand.SBER), setOf(chat), ownApp)
        assertEquals(NewBanks.Decision.Skip, d)
    }
}
