package ru.maxrubtsoff.bankagg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.maxrubtsoff.bankagg.ui.common.LocalNavScope
import ru.maxrubtsoff.bankagg.ui.common.LocalSharedScope
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import ru.maxrubtsoff.bankagg.ui.common.sharedCard

/** B-28: a long press on a row that opens with a shared transition (deposit, currency) shows the menu, no crash. */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class LongPressMenuTest {

    @get:Rule val rule = createComposeRule()

    @Test fun sharedRow_longPress_opensMenu() {
        rule.setContent {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalNavScope provides this) {
                        LongPressMenu(items = listOf("Изменить" to {}, "Удалить" to {}), danger = setOf("Удалить")) { onLong ->
                            Text(
                                "Вклад Т-Банк",
                                Modifier.fillMaxWidth().sharedCard("deposit-1")
                                    .combinedClickable(onClick = {}, onLongClick = onLong)
                                    .padding(24.dp).testTag("row")
                            )
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("row").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Изменить").assertIsDisplayed()
    }
}
