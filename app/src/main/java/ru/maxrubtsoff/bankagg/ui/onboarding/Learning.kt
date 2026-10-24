package ru.maxrubtsoff.bankagg.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.TourScript
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.ui.common.Dot
import ru.maxrubtsoff.bankagg.ui.common.ListRow
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.SlitokTag
import ru.maxrubtsoff.bankagg.ui.common.TagTone
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * «Как это работает» in an empty section (F-30): given a section and whether it
 * holds anything, the action that opens its tour — or null when the tour isn't
 * offered (already taken, or the section has data).
 */
val LocalTourOffer = compositionLocalOf<(TourSection, Boolean) -> (() -> Unit)?> { { _, _ -> null } }

fun TourSection.label(): String = when (this) {
    TourSection.GENERAL -> "Знакомство с приложением"
    TourSection.ACCOUNTS -> "Счета"
    TourSection.DEPOSITS -> "Вклады"
    TourSection.CURRENCY -> "Валюта"
    TourSection.CASH -> "Наличные"
    TourSection.CASHBACK -> "Кэшбэк"
    TourSection.SUBSCRIPTIONS -> "Подписки"
}

private fun TourSection.dot() = when (this) {
    TourSection.GENERAL -> Slitok.Brass2
    TourSection.ACCOUNTS -> Slitok.Brass
    TourSection.DEPOSITS -> Slitok.Violet
    TourSection.CURRENCY -> Slitok.Sky
    TourSection.CASH -> Slitok.Sand
    TourSection.CASHBACK, TourSection.SUBSCRIPTIONS -> Slitok.Mint
}

/** «Настройки → Обучение» (F-30): the general tour and one per kept section; any can be taken again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(features: Features, done: Set<TourSection>, onOpen: (TourSection) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            ScreenHeader("Обучение", onBack)
        }
    ) { padding ->
        val sections = TourScript.sections(features)
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    // «Что такое Itogo» is not here: the slides are a first-launch
                    // thing only (B-50).
                    sections.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 38.dp), color = Slitok.Line)
                        ListRow(
                            s.label(),
                            onClick = { onOpen(s) },
                            leading = { Dot(s.dot()) },
                            trailing = { if (s in done) SlitokTag("пройдено", TagTone.Mint) }
                        )
                    }
                }
            }
        }
    }
}
