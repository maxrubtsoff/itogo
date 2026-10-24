package ru.maxrubtsoff.bankagg.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.settings.FeaturesGrid
import ru.maxrubtsoff.bankagg.ui.settings.SettingsViewModel
import androidx.compose.runtime.LaunchedEffect
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * First launch: what the app is (F-29), what to keep (F-21), then the user's
 * banks (F-22, only when a kept section needs banks). [banks] draws the last
 * step and calls back when done.
 */
@Composable
fun OnboardingScreen(vm: SettingsViewModel, banks: @Composable (onDone: () -> Unit) -> Unit) {
    val features by vm.features.collectAsStateWithLifecycle()
    // -1: the intro slides; back from a step goes one step back, from the slides — out.
    // They are shown once ever (B-50), so the first step is decided by the mark.
    var step by rememberSaveable { mutableIntStateOf(-2) }
    LaunchedEffect(Unit) {
        if (step == -2) step = if (vm.showIntroSlides()) -1 else 0
    }
    val finish = { vm.finishOnboarding(); Unit }
    BackHandler(enabled = step >= 0) { step-- }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally { w -> dir * w / 4 } + fadeIn()) togetherWith (slideOutHorizontally { w -> -dir * w / 6 } + fadeOut())
        },
        label = "onboarding"
    ) { s ->
        when (s) {
            -2 -> Box(Modifier.fillMaxSize().background(Slitok.Bg))
            -1 -> IntroSlides(onDone = { vm.markIntroShown(); step = 0 })
            0 -> Column(
                Modifier.fillMaxSize().background(Slitok.Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Steps(current = 0, total = if (features.needsBanks) 2 else 1)
                Text("Что храним", style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    FeaturesGrid(features, vm::toggleFeature)
                }
                BrassButton(onClick = { if (features.needsBanks) step = 1 else finish() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (features.needsBanks) "Дальше" else "Готово")
                }
            }
            else -> banks(finish)
        }
    }
}

/** Two short brass bars: where in the setup we are. */
@Composable
fun Steps(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(Modifier.size(width = 28.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= current) Slitok.Brass2 else Slitok.PanelTop))
        }
    }
    Spacer(Modifier.height(0.dp))
}
