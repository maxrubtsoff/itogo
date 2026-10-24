package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.TourAction
import ru.maxrubtsoff.bankagg.domain.model.TourAnim
import ru.maxrubtsoff.bankagg.domain.model.TourHighlight
import ru.maxrubtsoff.bankagg.domain.model.TourScript
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.domain.model.TourTarget

class TourScriptTest {

    @Test fun general_asksForAccessOnlyWhenNeededAndMissing() {
        val withAccounts = Features(setOf(Feature.ACCOUNTS, Feature.DEPOSITS))
        assertEquals(TourTarget.ACCESS_TILE, TourScript.steps(TourSection.GENERAL, withAccounts, notificationAccess = false).first().target)
        assertTrue(TourScript.steps(TourSection.GENERAL, withAccounts, notificationAccess = true).none { it.target == TourTarget.ACCESS_TILE })
        val noNotifications = Features(setOf(Feature.DEPOSITS, Feature.CASH))
        assertTrue(TourScript.steps(TourSection.GENERAL, noNotifications, notificationAccess = false).none { it.target == TourTarget.ACCESS_TILE })
    }

    @Test fun cashback_hasTheGentleMissingPercentStep() {
        val step = TourScript.steps(TourSection.CASHBACK, Features.ALL, true).first { it.target == TourTarget.MISSING_PERCENT }
        assertEquals(TourAction.FILL_AND_NEXT, step.action)
        assertFalse(step.text.contains("ошиб", ignoreCase = true))
    }

    @Test fun sections_followTheKeptOnes() {
        val f = Features(setOf(Feature.DEPOSITS, Feature.CASHBACK))
        assertEquals(listOf(TourSection.GENERAL, TourSection.DEPOSITS, TourSection.CASHBACK), TourScript.sections(f))
    }

    @Test fun everySectionHasSteps() {
        TourSection.entries.forEach { s -> assertTrue(s.name, TourScript.steps(s, Features.ALL, false).isNotEmpty()) }
    }

    @Test fun offerInSection_onlyWhileEmptyAndNotTaken() {
        val f = Features(setOf(Feature.DEPOSITS))
        assertTrue(TourScript.offerInSection(TourSection.DEPOSITS, f, hasData = false, done = emptySet()))
        assertFalse(TourScript.offerInSection(TourSection.DEPOSITS, f, hasData = true, done = emptySet()))
        assertFalse(TourScript.offerInSection(TourSection.DEPOSITS, f, hasData = false, done = setOf(TourSection.DEPOSITS)))
        assertFalse(TourScript.offerInSection(TourSection.CASHBACK, f, hasData = false, done = emptySet()))
    }

    @Test fun afterSetup_once() {
        assertTrue(TourScript.startAfterSetup(emptySet()))
        assertFalse(TourScript.startAfterSetup(setOf(TourSection.GENERAL)))
    }

    @Test fun keysRoundTrip() {
        TourSection.entries.forEach { assertEquals(it, TourSection.fromKey(it.key)) }
    }

    @Test fun cashbackPlaysInTheMockupOrder() {
        assertEquals(
            listOf(
                TourAnim.TAP_ADD, TourAnim.OPEN_SHEET, TourAnim.PICK_SHOTS,
                TourAnim.SCAN, TourAnim.REVEAL_FIELDS, TourAnim.MARK_CATEGORY
            ),
            TourScript.steps(TourSection.CASHBACK, Features.ALL, true).map { it.anim }
        )
    }

    @Test fun depositAndAccountsPlayTheirAnimations() {
        assertEquals(
            listOf(TourAnim.TAP_ADD, TourAnim.OPEN_SHEET, TourAnim.SCAN, TourAnim.REVEAL_FIELDS),
            TourScript.steps(TourSection.DEPOSITS, Features.ALL, true).map { it.anim }
        )
        assertTrue(TourScript.steps(TourSection.ACCOUNTS, Features.ALL, true).any { it.anim == TourAnim.BALANCE_CHANGE })
    }

    @Test fun everyAnimatedStepWaitsForTheComment() {
        for (s in TourSection.entries) for (access in listOf(true, false)) {
            TourScript.steps(s, Features.ALL, access).filter { it.anim != TourAnim.NONE }
                .forEach { assertTrue("${s.name}: ${it.title}", it.waitForRead) }
        }
    }

    @Test fun everySectionHasAnAnimatedStep() {
        TourSection.entries.forEach { s ->
            assertTrue(s.name, TourScript.steps(s, Features.ALL, true).any { it.anim != TourAnim.NONE })
        }
    }

    @Test fun fullWidthTargetsAreOnlyDimmed() {
        val fullWidth = setOf(TourTarget.ADD_SHEET, TourTarget.BOTTOM_BAR)
        for (s in TourSection.entries) for (step in TourScript.steps(s, Features.ALL, false)) {
            val expected = if (step.target in fullWidth) TourHighlight.DIM_ONLY else TourHighlight.FRAMED
            assertEquals("${s.name}: ${step.title}", expected, step.highlight)
        }
    }
}
