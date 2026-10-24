package ru.maxrubtsoff.bankagg.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashbackShotRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleDepositShotRepository
import ru.maxrubtsoff.bankagg.di.SandboxContainer
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.model.TourAnim
import ru.maxrubtsoff.bankagg.domain.model.TourSample
import ru.maxrubtsoff.bankagg.domain.model.TourScript
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.domain.model.TourStep
import ru.maxrubtsoff.bankagg.domain.model.TourTarget
import ru.maxrubtsoff.bankagg.ui.cashback.percentToText
import ru.maxrubtsoff.bankagg.ui.common.InlineSheetHost
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.TourDrive
import ru.maxrubtsoff.bankagg.ui.common.TourTargets

/** What the sandbox shows: the section's own screen, or the check of the example screenshot over it. */
enum class TourView { SECTION, SHOT }

/**
 * One tour that is running (U-54, F-32, B-56): the sandbox it plays on, the
 * steps, and everything the screen and the coach read to show them. It lives
 * from the tap on «Как это работает» to the end of the fade-out; a new one is
 * made each time, so «Ещё раз» on the tour starts from a clean sandbox.
 *
 * The state here is only what is drawn — which step, which view, where the
 * finger is. The example data itself lives in the sandbox's repositories, and
 * the steps' order is data in [TourScript].
 */
@Stable
class TourSession(
    val section: TourSection,
    val features: Features,
    notificationAccess: Boolean,
    val sandbox: SandboxContainer
) {
    val steps: List<TourStep> = TourScript.steps(section, features, notificationAccess)

    var index by mutableIntStateOf(0)
        private set

    /** Raised by «Ещё раз»: the step plays again from its start. */
    var replay by mutableIntStateOf(0)
        private set

    /** The coach is up and the steps play. */
    var playing by mutableStateOf(false)

    /** The person closed the tour (or went through the last step): it fades out. */
    var closing by mutableStateOf(false)
        private set

    /** The sandbox over the real screen (B-56): 0 — only the person's own data, 1 — only the examples. */
    val sandboxAlpha = Animatable(0f)

    /** The veil, the light and the card, after the sandbox has come in. */
    val coachAlpha = Animatable(0f)

    val targets = TourTargets()
    val drive = TourDrive()
    val sheets = InlineSheetHost()

    var view by mutableStateOf(TourView.SECTION)
    var sheetOpen by mutableStateOf(false)
    var finger by mutableStateOf<Offset?>(null)
    var pressed by mutableStateOf(false)

    /** The eye of the overview was pressed in the example. */
    var hidden by mutableStateOf(false)

    /** Raised each time the example screenshot starts over, so its check starts clean. */
    var shotEpoch by mutableIntStateOf(0)
        private set

    val step: TourStep get() = steps[index.coerceIn(0, steps.lastIndex)]
    val isLast: Boolean get() = index >= steps.lastIndex

    fun next() {
        if (isLast) close() else index++
    }

    fun again() {
        replay++
    }

    fun close() {
        closing = true
    }

    private val today get() = sandbox.today.date.value
    private val monthKey get() = CashbackMonth.keyOf(today)
    private val cashbackShots: SampleCashbackShotRepository get() = sandbox.cashbackShotRepository
    private val depositShots: SampleDepositShotRepository get() = sandbox.depositShotRepository

    // ---- the player ----

    /**
     * Plays the current step on the sandbox. Every step begins by setting right what
     * it changes, so it plays the same whether the person came to it by «Далее» or
     * by «Ещё раз». Nothing here goes on by itself: when the action is done the
     * screen stands, and the next one waits for «Далее» (`waitForRead`, F-32).
     */
    suspend fun play() {
        val step = step
        finger = null
        pressed = false
        drive.fillPercent = null
        when (step.anim) {
            TourAnim.NONE -> settle()
            TourAnim.TAP_ADD -> {
                settle()
                if (step.target == TourTarget.HIDE_EYE) hidden = false
                tap(step.target)
                if (step.target == TourTarget.HIDE_EYE) hidden = true
            }
            TourAnim.OPEN_SHEET -> {
                settle()
                pause(Motion.FAST + 80L)
                sheetOpen = true
            }
            TourAnim.PICK_SHOTS -> {
                view = TourView.SECTION
                sheetOpen = true
                tap(TourTarget.SHOTS_TILE)
            }
            TourAnim.SCAN -> {
                if (sheetOpen && steps.getOrNull(index - 1)?.anim != TourAnim.PICK_SHOTS) tap(TourTarget.SHOTS_TILE)
                restartExample()
            }
            TourAnim.REVEAL_FIELDS -> reveal()
            TourAnim.MARK_CATEGORY -> {
                saveCashbackExample()
            }
            TourAnim.BALANCE_CHANGE -> changeBalance()
        }
    }

    /** «Далее» on a step that does something first: types the missing percent, waits a moment, goes on. */
    suspend fun fillAndNext() {
        drive.fillPercent = percentToText(TourSample.FILLED_PERCENT)
        pause(700)
        next()
    }

    /** The section's screen in plain view: no sheet, no check over it. */
    private fun settle() {
        sheetOpen = false
        view = TourView.SECTION
        if (section != TourSection.GENERAL || step.target != TourTarget.HIDE_EYE) hidden = false
    }

    private fun isCashback() = section == TourSection.CASHBACK

    /** The example screenshot starts over: just picked, being read. */
    private fun restartExample() {
        sheetOpen = false
        shotEpoch++
        if (isCashback()) {
            cashbackShots.startExample(monthKey)
        } else {
            depositShots.startExample()
        }
        view = TourView.SHOT
    }

    private suspend fun exampleReading(): Boolean =
        if (isCashback()) cashbackShots.get(SampleCashbackShotRepository.EXAMPLE_ID)?.status == ShotStatus.PROCESSING
        else depositShots.get(SampleDepositShotRepository.EXAMPLE_ID)?.status == ShotStatus.PROCESSING

    /** The reading ends and the fields rise; a deposit is then saved into the list. */
    private suspend fun reveal() {
        when (section) {
            TourSection.CASHBACK, TourSection.DEPOSITS -> {
                if (view != TourView.SHOT || !exampleReading()) restartExample()
                pause(Motion.SLOW + 500L)
                if (isCashback()) cashbackShots.finishExample() else depositShots.finishExample(today)
                if (section == TourSection.DEPOSITS) {
                    pause(2400)
                    sandbox.depositRepository.add(TourSample.scannedDeposit(today))
                    depositShots.delete(SampleDepositShotRepository.EXAMPLE_ID)
                    view = TourView.SECTION
                }
            }
            else -> settle()
        }
    }

    /** The checked cashback is saved: the example bank joins the month, the best percents light up by themselves. */
    private suspend fun saveCashbackExample() {
        val items = TourSample.scannedCashbackLines().mapIndexed { i, l ->
            CashbackItem(
                "sample-new-$i", monthKey, TourSample.BANK, l.text, l.percent ?: TourSample.FILLED_PERCENT, groupKey = l.groupKey
            )
        }
        sandbox.cashbackRepository.saveBankMonth(monthKey, TourSample.BANK, items, append = false)
        cashbackShots.delete(SampleCashbackShotRepository.EXAMPLE_ID)
        sheetOpen = false
        view = TourView.SECTION
    }

    /** A bank's notification or a new sum: the amount changes in front of the person. */
    private suspend fun changeBalance() {
        settle()
        val now = System.currentTimeMillis()
        when (section) {
            TourSection.ACCOUNTS -> {
                val purchase = TourSample.purchase(today)
                val ledger = sandbox.ledgerRepository
                val original = TourSample.accounts(today).first { it.id == TourSample.ACCOUNT_SAMPLE }
                ledger.deleteTransaction(purchase.id)
                ledger.upsertAccountBalance(original.id, original.brand, Currencies.RUB, 0L, original.balance.minor, original.updatedAt)
                pause(900)
                ledger.addTransaction(purchase)
                ledger.upsertAccountBalance(
                    purchase.accountId, purchase.brand, purchase.currency, purchase.amount.minor, TourSample.BALANCE_AFTER_PURCHASE_MINOR, now
                )
            }
            TourSection.CASH -> {
                sandbox.cashRepository.set(TourSample.cash(today))
                pause(900)
                sandbox.cashRepository.set(CashBalance(Money.ofMinor(TourSample.CASH_AFTER_MINOR), now))
            }
            else -> Unit
        }
    }

    // ---- small pieces ----

    /** A wait that is not a wait with «Убрать анимации». */
    private suspend fun pause(ms: Long) {
        if (Motion.enabled) delay(ms)
    }

    private suspend fun boundsOf(target: TourTarget): Rect? =
        withTimeoutOrNull(2000) { snapshotFlow { targets.bounds[target] }.filterNotNull().first() }

    /** The finger comes to the part and presses it. */
    private suspend fun tap(target: TourTarget) {
        val at = boundsOf(target)?.center ?: return
        finger = at
        pause(Motion.NORMAL + 150L)
        pressed = true
        pause(Motion.FAST.toLong())
        pressed = false
        pause(150)
    }
}
