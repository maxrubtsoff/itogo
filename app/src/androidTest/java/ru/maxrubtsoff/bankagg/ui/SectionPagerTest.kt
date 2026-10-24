package ru.maxrubtsoff.bankagg.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.maxrubtsoff.bankagg.ui.common.SectionPager

/** B-24: a swipe past the first or last section leaves the section's card where it is. */
@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class SectionPagerTest {

    @get:Rule val rule = createComposeRule()

    private fun setPages(count: Int, start: Int) {
        rule.setContent {
            val state = rememberPagerState(initialPage = start) { count }
            Box(Modifier.fillMaxSize().testTag("pager")) {
                SectionPager(state, Modifier.fillMaxSize()) { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Box(Modifier.fillMaxWidth().height(120.dp).testTag("card$page")) { Text("Раздел $page") }
                    }
                }
            }
        }
    }

    private fun assertStaysAfter(tag: String, swipe: () -> Unit) {
        val before = rule.onNodeWithTag(tag).getBoundsInRoot()
        swipe()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(before.left, rule.onNodeWithTag(tag).getBoundsInRoot().left)
        rule.waitForIdle()
        assertEquals(before, rule.onNodeWithTag(tag).getBoundsInRoot())
    }

    @Test fun pastTheLastSection_nothingMoves() {
        setPages(count = 4, start = 3)
        assertStaysAfter("card3") { rule.onNodeWithTag("pager").performTouchInput { swipeLeft(durationMillis = 400) } }
    }

    @Test fun pastTheFirstSection_nothingMoves() {
        setPages(count = 4, start = 0)
        assertStaysAfter("card0") { rule.onNodeWithTag("pager").performTouchInput { swipeRight(durationMillis = 400) } }
    }

    @Test fun singleSection_doesNotSwipe() {
        setPages(count = 1, start = 0)
        assertStaysAfter("card0") { rule.onNodeWithTag("pager").performTouchInput { swipeLeft(durationMillis = 400) } }
    }

    @Test fun aSwipeInTheMiddle_stillTurnsTheSection() {
        setPages(count = 4, start = 1)
        rule.onNodeWithTag("pager").performTouchInput { swipeLeft(durationMillis = 300) }
        rule.waitForIdle()
        rule.onNodeWithTag("card2").getBoundsInRoot().let { assertEquals(0f, it.left.value, 1f) }
    }
}
