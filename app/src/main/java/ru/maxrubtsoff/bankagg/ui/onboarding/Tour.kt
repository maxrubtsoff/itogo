package ru.maxrubtsoff.bankagg.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.AppTab
import ru.maxrubtsoff.bankagg.domain.model.TourAction
import ru.maxrubtsoff.bankagg.domain.model.TourAnim
import ru.maxrubtsoff.bankagg.domain.model.TourHighlight
import ru.maxrubtsoff.bankagg.domain.model.TourSample
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.ui.capital.CapitalScreen
import ru.maxrubtsoff.bankagg.ui.capital.CapitalViewModel
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackScreen
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackViewModel
import ru.maxrubtsoff.bankagg.ui.cashback.ShotPreviewScreen
import ru.maxrubtsoff.bankagg.ui.common.AddSheet
import ru.maxrubtsoff.bankagg.ui.common.FingerPointer
import ru.maxrubtsoff.bankagg.ui.common.InlineSheetLayer
import ru.maxrubtsoff.bankagg.ui.common.LocalAmountsHidden
import ru.maxrubtsoff.bankagg.ui.common.LocalInlineSheets
import ru.maxrubtsoff.bankagg.ui.common.LocalMyBanks
import ru.maxrubtsoff.bankagg.ui.common.LocalRevealAmounts
import ru.maxrubtsoff.bankagg.ui.common.LocalTourDrive
import ru.maxrubtsoff.bankagg.ui.common.LocalTourTargets
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.SandboxViewModelFactory
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.deposits.DepositShotPreviewScreen
import ru.maxrubtsoff.bankagg.ui.deposits.DepositsViewModel
import ru.maxrubtsoff.bankagg.ui.overview.OverviewScreen
import ru.maxrubtsoff.bankagg.ui.subscriptions.PaymentsScreen
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionsSection
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionsViewModel
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashbackShotRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleDepositShotRepository
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/*
 * The tour (F-30, F-32, U-54, B-56). It plays on the app's own screens: the
 * screen the person stands on is drawn again over itself from the sandbox's
 * example data, and the veil, the light and the card come over that. Nothing is
 * rebuilt or moved — the same place on the same screen — so nothing jumps; the
 * person's own data goes out and the examples come in (a cross-fade), then the
 * coach appears, and at the end it all goes back the same way.
 *
 * Two layers, in two places of the app's root: [TourSandboxLayer] sits in the
 * content area (the bottom bar stays the real one), [TourCoach] over everything.
 */

/** The sandbox's view-models live exactly as long as the tour. */
private class SandboxStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Which of the app's screens the tour plays a section on. */
private sealed interface Stage {
    data object Overview : Stage
    data class Capital(val part: CapitalPart?) : Stage
    data class Payments(val section: Int) : Stage
}

private fun stageOf(session: TourSession): Stage = when (session.section) {
    TourSection.GENERAL -> when (session.features.firstTab) {
        AppTab.CAPITAL -> Stage.Capital(null)
        AppTab.PAYMENTS -> Stage.Payments(0)
        else -> Stage.Overview
    }
    TourSection.ACCOUNTS -> Stage.Capital(CapitalPart.ACCOUNTS)
    TourSection.DEPOSITS -> Stage.Capital(CapitalPart.DEPOSITS)
    TourSection.CURRENCY -> Stage.Capital(CapitalPart.CURRENCY)
    TourSection.CASH -> Stage.Capital(CapitalPart.CASH)
    TourSection.CASHBACK -> Stage.Payments(0)
    TourSection.SUBSCRIPTIONS -> Stage.Payments(1)
}

/**
 * The sandbox over the real screen of the tour's section: the same `Screen`
 * functions, run by view-models over the example data. It is as opaque as the
 * real screen it covers, so the cross-fade is between two complete pictures.
 * [padding] is the content area's, as for the real tab.
 */
@Composable
fun TourSandboxLayer(session: TourSession, padding: PaddingValues) {
    val owner = remember(session) { SandboxStoreOwner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val factory = remember(session) { SandboxViewModelFactory(session.sandbox) }
    val banks = remember(session) { TourSample.data(session.sandbox.today.date.value).banks }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalTourTargets provides session.targets,
        LocalTourDrive provides session.drive,
        LocalInlineSheets provides session.sheets,
        LocalAmountsHidden provides session.hidden,
        LocalRevealAmounts provides {},
        LocalMyBanks provides banks,
        LocalTourOffer provides { _, _ -> null }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = session.sandboxAlpha.value }
                .background(Slitok.Bg)
        ) {
            Crossfade(targetState = session.view, animationSpec = Motion.tween(Motion.NORMAL), label = "tourView") { view ->
                when (view) {
                    TourView.SECTION -> SectionScreen(session, factory, padding)
                    TourView.SHOT -> ShotScreen(session, factory)
                }
            }
            InlineSheetLayer(session.sheets)
            // «Добавить …»: the same sheet of the screen, opened by the tour from outside.
            if (session.sheetOpen) {
                AddSheet(
                    title = if (session.section == TourSection.DEPOSITS) "Добавить вклад" else "Добавить кэшбэк",
                    onShots = {}, onManual = {}, onDismiss = {}
                )
            }
            // The sandbox is for looking at: touches don't reach it.
            Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) {})
        }
    }
}

@Composable
private fun SectionScreen(session: TourSession, factory: SandboxViewModelFactory, padding: PaddingValues) {
    // The same wrapper as the real tab's: the bottom bar's padding and the status bar.
    Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
        when (val stage = stageOf(session)) {
            Stage.Overview -> {
                val capitalVm: CapitalViewModel = viewModel(factory = factory)
                val cashbackVm: CashbackViewModel = viewModel(factory = factory)
                OverviewScreen(
                    vm = capitalVm,
                    cashbackVm = cashbackVm,
                    onOpenCapital = {}, onOpenCashback = {}, onOpenCashbackNext = {}, onOpenBanks = {}, onAddDeposit = {},
                    banksSelected = true,
                    features = session.features
                )
            }
            is Stage.Capital -> {
                val capitalVm: CapitalViewModel = viewModel(factory = factory)
                val depositsVm: DepositsViewModel = viewModel(factory = factory)
                CapitalScreen(
                    vm = capitalVm,
                    depositsVm = depositsVm,
                    sectionArg = stage.part?.key,
                    parts = CapitalPart.entries.filter { it in session.features.capitalParts },
                    onOpenDeposit = {}, onOpenDepositShot = {}, onOpenDepositBatch = {}, onManualDeposit = {},
                    onOpenPendingShots = {}, onOpenCurrency = {}, onOpenBanks = {}
                )
            }
            is Stage.Payments -> {
                val cashbackVm: CashbackViewModel = viewModel(factory = factory)
                val subscriptionsVm: SubscriptionsViewModel = viewModel(factory = factory)
                PaymentsScreen(
                    cashback = { monthOffset, byCategory, onMonthOffset, onByCategory ->
                        CashbackScreen(
                            cashbackVm, monthOffset, byCategory, onMonthOffset,
                            onOpenShot = {}, onOpenBatch = {}, onEditBank = { _, _ -> },
                            onByCategory = onByCategory
                        )
                    },
                    subscriptions = { SubscriptionsSection(subscriptionsVm, onOpen = {}, onCreate = {}) },
                    startSection = stage.section,
                    showCashback = session.features.showCashback,
                    showSubscriptions = session.features.showSubscriptions,
                    showNotifAccess = session.features.showNotifAccessOnPayments
                )
            }
        }
    }
}

/** The check of the example screenshot, as the app opens it: the head, the fields, the button. */
@Composable
private fun ShotScreen(session: TourSession, factory: SandboxViewModelFactory) {
    // A new epoch is a new check: the form starts empty and reads the example again.
    androidx.compose.runtime.key(session.shotEpoch) {
        if (session.section == TourSection.CASHBACK) {
            val vm: CashbackViewModel = viewModel(factory = factory)
            ShotPreviewScreen(vm, SampleCashbackShotRepository.EXAMPLE_ID, onBack = {})
        } else {
            val vm: DepositsViewModel = viewModel(factory = factory)
            DepositShotPreviewScreen(vm, SampleDepositShotRepository.EXAMPLE_ID, onBack = {})
        }
    }
}

/**
 * The veil with a window over the target, the frame round it, the finger and the
 * card; and the whole life of the tour's coming and going (B-56): first the
 * sandbox fades in over the real screen, only then the coach appears; on the
 * way out the same, backwards. [onFinished] comes when the real screen is bare
 * again. [onAllowAccess] opens the system's setting (the one real thing in a tour).
 */
@Composable
fun TourCoach(session: TourSession, onFinished: () -> Unit, onAllowAccess: () -> Unit) {
    BackHandler { session.close() }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(session) {
        // The sandbox is composed while still invisible; its first frames are not the ones that move.
        withFrameNanos { }
        withFrameNanos { }
        session.sandboxAlpha.animateTo(1f, Motion.tween(Motion.NORMAL))
        session.coachAlpha.animateTo(1f, Motion.tween(Motion.NORMAL))
        session.playing = true
        snapshotFlow { session.closing }.first { it }
        session.playing = false
        session.finger = null
        session.coachAlpha.animateTo(0f, Motion.tween(Motion.FAST))
        session.sheetOpen = false
        session.view = TourView.SECTION
        session.sandboxAlpha.animateTo(0f, Motion.tween(Motion.NORMAL))
        finished()
    }
    // A step plays when it begins and again on «Ещё раз»; the next one waits for «Далее».
    LaunchedEffect(session, session.index, session.replay, session.playing) {
        if (session.playing) session.play()
    }

    val step = session.step
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInset = WindowInsets.statusBars.getTop(density)
    val bottomInset = WindowInsets.navigationBars.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = session.coachAlpha.value }) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val margin = with(density) { 8.dp.toPx() }
        val pad = with(density) { 6.dp.toPx() }
        val screen = Rect(0f, 0f, width, height)
        val safe = Rect(margin, topInset + margin, width - margin, height - bottomInset - margin)

        // The light: where the target is, kept inside what can be seen, with the target's own rounding.
        val target = session.targets.bounds[step.target]
        val targetRadius = with(density) { (session.targets.radius[step.target] ?: 18.dp).toPx() }
        val framed = step.highlight == TourHighlight.FRAMED
        val window: Rect? = target?.let {
            if (framed) TourFrame.framed(it, pad, safe) else TourFrame.dimmed(it, screen, reach = with(density) { 40.dp.toPx() })
        }
        var last by remember { mutableStateOf(Rect.Zero) }
        SideEffect { if (window != null) last = window }
        val shown by animateValueAsState(window ?: last, Rect.VectorConverter, Motion.tween(Motion.NORMAL, Motion.Standard), label = "tourWindow")
        val shownAlpha by animateFloatAsState(if (window != null) 1f else 0f, Motion.tween(Motion.FAST), label = "tourWindowAlpha")
        val radius = TourFrame.radius(targetRadius, if (framed) pad else 0f, shown)

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clickable(interactionSource = null, indication = null) {}
        ) {
            drawRect(Slitok.Scrim)
            if (shownAlpha > 0f) {
                drawRoundRect(
                    Color.Black.copy(alpha = shownAlpha), Offset(shown.left, shown.top), Size(shown.width, shown.height),
                    CornerRadius(radius), blendMode = BlendMode.DstOut
                )
                if (framed) {
                    drawRoundRect(
                        Slitok.Brass2.copy(alpha = shownAlpha), Offset(shown.left, shown.top), Size(shown.width, shown.height),
                        CornerRadius(radius), style = Stroke(2.dp.toPx())
                    )
                }
            }
        }

        FingerPointer(session.finger, pressed = session.pressed)

        // The card goes under the target when there's room, otherwise over it.
        var cardHeight by remember { mutableFloatStateOf(0f) }
        val cardTop = TourFrame.cardTop(window, cardHeight, with(density) { 14.dp.toPx() }, safe)
        val animatedTop by animateFloatAsState(cardTop, Motion.tween(Motion.NORMAL, Motion.Standard), label = "tourCardTop")
        CoachCard(
            session = session,
            onNext = {
                when (step.action) {
                    TourAction.NEXT -> session.next()
                    TourAction.ALLOW_ACCESS -> { onAllowAccess(); session.next() }
                    TourAction.FILL_AND_NEXT -> scope.launch { session.fillAndNext() }
                }
            },
            onLater = if (step.action == TourAction.ALLOW_ACCESS) ({ session.next() }) else null,
            modifier = Modifier
                .offset { IntOffset(0, animatedTop.toInt()) }
                .onSizeChanged { cardHeight = it.height.toFloat() }
        )
    }
}

@Composable
private fun CoachCard(
    session: TourSession,
    onNext: () -> Unit,
    onLater: (() -> Unit)?,
    modifier: Modifier
) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Slitok.CoachBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedContent(
            targetState = session.index,
            transitionSpec = {
                (fadeIn(Motion.tween(Motion.NORMAL)) togetherWith fadeOut(Motion.tween(Motion.FAST)))
                    .using(SizeTransform(clip = false) { _, _ -> Motion.tween(Motion.NORMAL, Motion.Standard) })
            },
            label = "tourCardText"
        ) { i ->
            val step = session.steps[i.coerceIn(0, session.steps.lastIndex)]
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium, color = Slitok.CoachInk, modifier = Modifier.weight(1f))
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = session::close), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть обучение", tint = Slitok.CoachQuiet, modifier = Modifier.size(18.dp))
                    }
                }
                Text(step.text.nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.CoachText)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(session.steps.size) { i ->
                    Box(
                        Modifier.size(width = if (i == session.index) 16.dp else 6.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == session.index) Slitok.CoachInk else Slitok.CoachInk.copy(alpha = 0.2f))
                    )
                }
            }
            if (session.step.anim != TourAnim.NONE) {
                CardAction("Ещё раз", session::again)
            }
            if (onLater != null) CardAction("Позже", onLater)
            Text(
                when {
                    onLater != null -> "Разрешить"
                    session.isLast -> "Готово"
                    else -> "Далее"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Slitok.CoachBg,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slitok.CoachInk)
                    .clickable(onClick = onNext)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

/** A quiet action of the card: «Ещё раз», «Позже». */
@Composable
private fun CardAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Slitok.CoachQuiet,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
