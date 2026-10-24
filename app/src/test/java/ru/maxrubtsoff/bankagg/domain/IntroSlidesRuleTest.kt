package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.IntroSlidesRule

/** «Что такое Itogo» is shown once, at the very first launch (B-50). */
class IntroSlidesRuleTest {

    @Test fun firstLaunch_showsTheSlides() {
        assertTrue(IntroSlidesRule.show(onboarded = false, shown = false))
    }

    @Test fun onceShown_neverAgain() {
        assertFalse(IntroSlidesRule.show(onboarded = false, shown = true))
        assertFalse(IntroSlidesRule.show(onboarded = true, shown = true))
    }

    @Test fun anInstallThatAlreadyHasData_doesNotStartWithSlides() {
        // A restored copy or an install from before the mark: onboarding is done.
        assertFalse(IntroSlidesRule.show(onboarded = true, shown = false))
    }
}
