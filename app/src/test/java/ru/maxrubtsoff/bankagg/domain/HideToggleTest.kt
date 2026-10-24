package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankSummary
import ru.maxrubtsoff.bankagg.domain.model.HideToggle
import ru.maxrubtsoff.bankagg.domain.model.Money

class HideToggleTest {

    @Test fun noAmountsNoEye() = assertFalse(HideToggle.visible(hasAmounts = false, hidden = false))

    @Test fun amountsShowTheEye() = assertTrue(HideToggle.visible(hasAmounts = true, hidden = false))

    @Test fun hiddenKeepsTheEyeToShowAgain() = assertTrue(HideToggle.visible(hasAmounts = false, hidden = true))

    /** U-48: an empty bank goes quietly; one with data offers «Вернуть». */
    @Test fun undoOnlyForABankWithData() {
        assertFalse(BankSummary(Money.ZERO, 0, Money.ZERO, 0, 0).offersUndo)
        assertTrue(BankSummary(Money.ZERO, 0, Money.ZERO, 0, 3).offersUndo)
        assertTrue(BankSummary(Money.ofMinor(100), 1, Money.ZERO, 0, 0).offersUndo)
    }
}
