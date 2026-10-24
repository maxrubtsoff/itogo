package ru.maxrubtsoff.bankagg.ui

import ru.maxrubtsoff.bankagg.ui.showcase.ShowcaseScreen
import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.onboarding.LocalTourOffer
import ru.maxrubtsoff.bankagg.ui.onboarding.TourScreen
import ru.maxrubtsoff.bankagg.ui.onboarding.LearningScreen
import ru.maxrubtsoff.bankagg.domain.model.TourSection
import ru.maxrubtsoff.bankagg.domain.model.TourScript
import ru.maxrubtsoff.bankagg.ui.common.AddSheet
import ru.maxrubtsoff.bankagg.ui.deposits.rememberDepositShotsPicker
import androidx.compose.runtime.mutableIntStateOf
import ru.maxrubtsoff.bankagg.ui.common.LocalTabTaps
import androidx.compose.material.icons.filled.Savings
import ru.maxrubtsoff.bankagg.ui.common.TileButton
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.SlitokDialog
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import ru.maxrubtsoff.bankagg.ui.common.CalmEasing
import ru.maxrubtsoff.bankagg.ui.common.CLOSE_MS
import ru.maxrubtsoff.bankagg.ui.common.OPEN_MS
import androidx.compose.animation.SharedTransitionLayout
import ru.maxrubtsoff.bankagg.ui.common.LocalSharedScope
import ru.maxrubtsoff.bankagg.ui.common.LocalNavScope
import ru.maxrubtsoff.bankagg.data.notify.AppNotifier
import ru.maxrubtsoff.bankagg.data.notify.checkReminders
import ru.maxrubtsoff.bankagg.ui.settings.NotificationsScreen
import ru.maxrubtsoff.bankagg.ui.settings.NewBanksScreen
import ru.maxrubtsoff.bankagg.ui.common.LocalMyBanks
import ru.maxrubtsoff.bankagg.ui.common.LocalAddBank
import ru.maxrubtsoff.bankagg.ui.settings.BanksViewModel
import ru.maxrubtsoff.bankagg.ui.settings.AddBankScreen
import ru.maxrubtsoff.bankagg.ui.settings.BankEditorScreen
import ru.maxrubtsoff.bankagg.ui.settings.BankCardScreen
import androidx.compose.runtime.saveable.rememberSaveable
import ru.maxrubtsoff.bankagg.domain.model.AppTab
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.ui.onboarding.OnboardingScreen
import ru.maxrubtsoff.bankagg.ui.settings.FeaturesScreen
import ru.maxrubtsoff.bankagg.ui.common.ProvideDustClock
import ru.maxrubtsoff.bankagg.ui.settings.SettingsPrivacyScreen
import ru.maxrubtsoff.bankagg.ui.lock.LockScreen
import ru.maxrubtsoff.bankagg.ui.lock.Biometrics
import ru.maxrubtsoff.bankagg.ui.subscriptions.PaymentsScreen
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionCardScreen
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionFormScreen
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionsSection
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionsViewModel
import ru.maxrubtsoff.bankagg.ui.lock.PhoneCheck
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
import ru.maxrubtsoff.bankagg.ui.common.twoFingerSwipeDown
import ru.maxrubtsoff.bankagg.ui.common.LocalRevealAmounts
import ru.maxrubtsoff.bankagg.ui.common.LocalAmountsHidden
import ru.maxrubtsoff.bankagg.domain.model.FlipDetector
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import android.view.HapticFeedbackConstants
import android.hardware.SensorManager
import android.hardware.SensorEventListener
import android.hardware.SensorEvent
import android.hardware.Sensor
import android.app.ActivityManager
import androidx.fragment.app.FragmentActivity
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.ui.capital.CapitalScreen
import ru.maxrubtsoff.bankagg.ui.capital.CapitalViewModel
import ru.maxrubtsoff.bankagg.ui.capital.CurrencyDetailScreen
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.di.AppContainer
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackScreen
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.cashback.BankCashbackEditScreen
import ru.maxrubtsoff.bankagg.ui.cashback.ShotPreviewScreen
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackViewModel
import ru.maxrubtsoff.bankagg.ui.deposits.DepositAddScreen
import ru.maxrubtsoff.bankagg.ui.deposits.DepositDetailScreen
import ru.maxrubtsoff.bankagg.ui.deposits.DepositManualScreen
import ru.maxrubtsoff.bankagg.ui.deposits.DepositShotPreviewScreen
import ru.maxrubtsoff.bankagg.ui.deposits.DepositsViewModel
import ru.maxrubtsoff.bankagg.ui.common.ViewModelFactory
import ru.maxrubtsoff.bankagg.ui.log.LogScreen
import ru.maxrubtsoff.bankagg.ui.log.LogViewModel
import ru.maxrubtsoff.bankagg.ui.overview.OverviewScreen
import ru.maxrubtsoff.bankagg.ui.settings.SettingsBackupScreen
import ru.maxrubtsoff.bankagg.ui.settings.SettingsBanksScreen
import ru.maxrubtsoff.bankagg.ui.settings.SettingsScreen
import ru.maxrubtsoff.bankagg.ui.settings.SettingsViewModel
import ru.maxrubtsoff.bankagg.ui.theme.BankAggTheme
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.ShotKind
import ru.maxrubtsoff.bankagg.ui.deposits.MAX_PICK
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackBatchScreen
import ru.maxrubtsoff.bankagg.ui.deposits.DepositBatchScreen
import ru.maxrubtsoff.bankagg.ui.common.UndoSnackbarEffect
import androidx.compose.material3.Snackbar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState

private object Routes {
    const val OVERVIEW = "overview"
    const val SETTINGS = "settings"
    const val CASHBACK = "cashback?offset={offset}&section={section}"
    const val CASHBACK_BASE = "cashback"
    const val CASHBACK_SHOT = "cashback/shot/{shotId}"
    fun cashbackShot(shotId: String) = "cashback/shot/$shotId"
    const val CASHBACK_BATCH = "cashback/batch/{ids}"
    fun cashbackBatch(ids: List<String>) = "cashback/batch/${ids.joinToString(",")}"
    const val CASHBACK_BANK_EDIT = "cashback/bank/{monthKey}/{brandKey}"
    fun cashbackBankEdit(monthKey: String, brandKey: String) = "cashback/bank/$monthKey/$brandKey"
    const val NEW_CASHBACK = "new"
    const val SUBSCRIPTION_EDIT = "subscriptions/edit"
    const val SUBSCRIPTION = "subscriptions/{id}"
    fun subscription(id: String) = "subscriptions/$id"
    const val CAPITAL = "capital?section={section}"
    const val CAPITAL_BASE = "capital"
    fun capital(part: CapitalPart) = "capital?section=${part.key}"
    const val CURRENCY = "currency/{code}"
    fun currency(code: String) = "currency/$code"
    const val SETTINGS_BANKS = "settings/banks"
    const val SETTINGS_FEATURES = "settings/features"
    const val BANKS_ADD = "settings/banks/add"
    const val NEW_BANKS = "settings/banks/new"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val BANK_EDIT = "settings/bank/{key}"
    fun bankEdit(key: String) = "settings/bank/${Uri.encode(key)}"
    const val BANK_CARD = "settings/bank-card/{key}"
    fun bankCard(key: String) = "settings/bank-card/${Uri.encode(key)}"
    const val BANK_NEW = "settings/bank-new?name={name}"
    fun bankNew(name: String) = "settings/bank-new?name=${Uri.encode(name)}"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_LOG = "settings/log"
    const val LEARNING = "settings/learning"
    const val SHOWCASE = "debug/showcase"
    const val TOUR = "tour/{section}"
    fun tour(section: TourSection) = "tour/${section.key}"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val DEPOSITS_ADD = "deposits/add"
    const val DEPOSITS_SHOT = "deposits/shot/{shotId}"
    fun depositShot(shotId: String) = "deposits/shot/$shotId"
    const val DEPOSITS_BATCH = "deposits/batch/{ids}"
    fun depositBatch(ids: List<String>) = "deposits/batch/${ids.joinToString(",")}"
    const val DEPOSITS_MANUAL = "deposits/manual"
    const val DEPOSITS_MANUAL_EDIT = "deposits/manual/{depositId}"
    fun depositManualEdit(depositId: String) = "deposits/manual/$depositId"
    const val DEPOSITS_DETAIL = "deposits/detail/{depositId}?topup={topup}"
    fun depositDetail(depositId: String, topUp: Boolean = false) = "deposits/detail/$depositId?topup=$topUp"
}

/** A bottom-bar tab: [pattern] is the registered route, [route] what the bar navigates to. */
private data class TopDest(val tab: AppTab, val pattern: String, val route: String, val label: String, val icon: ImageVector)

private val topDests = listOf(
    TopDest(AppTab.OVERVIEW, Routes.OVERVIEW, Routes.OVERVIEW, "Обзор", Icons.Filled.Dashboard),
    TopDest(AppTab.CAPITAL, Routes.CAPITAL, Routes.CAPITAL_BASE, "Капитал", Icons.Filled.AccountBalanceWallet),
    TopDest(AppTab.PAYMENTS, Routes.CASHBACK, Routes.CASHBACK_BASE, "Платежи", Icons.Filled.Percent),
    TopDest(AppTab.SETTINGS, Routes.SETTINGS, Routes.SETTINGS, "Настройки", Icons.Filled.Settings)
)

/** The tab the app opens on: the first one the kept sections leave visible (U-50). */
private fun startRouteOf(features: Features): String =
    topDests.first { it.tab == features.firstTab }.route

/** U-31: icons, widget, reminders and rates on return — not more often than this. */
private const val CHORES_EVERY_MS = 5 * 60_000L

private val tabTween = tween<androidx.compose.ui.unit.IntOffset>(320, easing = FastOutSlowInEasing)

/** Slide direction between two bottom-bar tabs, 0 when either side is not a tab. */
private fun tabDirection(from: NavDestination, to: NavDestination): Int =
    TabMotion.direction(topDests.indexOfFirst { it.pattern == from.route }, topDests.indexOfFirst { it.pattern == to.route })

class MainActivity : FragmentActivity() {
    /** Screenshots shared into the app ("Поделиться"), waiting to be placed. */
    private val shared = mutableStateOf<List<Uri>>(emptyList())
    /** A screen a notification asked to open (F-24). */
    private val openRoute = mutableStateOf<String?>(null)

    /** The lock screen is up (F-10). */
    private val locked = mutableStateOf(false)

    /** When the return-to-app chores last ran (U-31). */
    private var lastChores = 0L

    /** Face-down detection for hiding amounts (F-11). */
    private val flip = FlipDetector()
    private val gravity = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Each flip face down toggles: hide, then show on the next one.
            if (flip.onGravity(e.values[2], e.timestamp / 1_000_000)) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                lifecycleScope.launch {
                    val settings = (application as App).container.settings
                    settings.setAmountsHidden(!settings.amountsHidden.first())
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        val factory = ViewModelFactory(container)
        // B-39: «Недавние» show the app unless «Скрывать в недавних» is on (Android 13+;
        // FLAG_SECURE is not used — it would forbid screenshots too).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lifecycleScope.launch { container.settings.hideInRecents.collect { setRecentsScreenshotEnabled(!it) } }
        }
        watchFlip()
        if (savedInstanceState == null) shared.value = sharedImages(intent)
        if (savedInstanceState == null) openRoute.value = intent?.getStringExtra(AppNotifier.EXTRA_OPEN)
        // A quick prefs read: the lock must be up before the first frame.
        val session = container.lockSession
        val pinOn = runBlocking { container.settings.lockPin() != null }
        // Which tab the app opens on is decided before the first frame (U-50).
        val startFeatures = runBlocking { container.settings.currentFeatures() }
        val coldStart = AppLock.isColdStart(freshScreen = savedInstanceState == null, unlockedInProcess = session.unlocked)
        if (coldStart) session.unlocked = false
        locked.value = AppLock.needsUnlock(pinOn, coldStart = coldStart, backgroundedAt = null, now = System.currentTimeMillis())
        setContent {
            BankAggTheme {
                Surface {
                    // Behind the lock the app is neither readable by accessibility services
                    // nor fed shared pictures or notification routes: they wait for the code (B-31).
                    val isLocked = locked.value
                    Box(if (isLocked) Modifier.clearAndSetSemantics { } else Modifier) {
                        AppRoot(
                            container, factory, startFeatures,
                            if (isLocked) emptyList() else shared.value,
                            if (isLocked) null else openRoute.value,
                            { openRoute.value = null }
                        ) { shared.value = emptyList() }
                    }
                    if (isLocked) LockGate(container)
                }
            }
        }
    }

    @Composable
    private fun LockGate(container: AppContainer) {
        val biometric by container.settings.biometricEnabled.collectAsStateWithLifecycle(initialValue = false)
        LockScreen(
            checkPin = { pin -> container.checkPin(pin) },
            rest = { container.checkPin.rest() },
            biometric = biometric && Biometrics.available(this),
            onBiometric = { Biometrics.prompt(this) { lifecycleScope.launch { container.checkPin.forgive() }; unlock() } },
            onUnlocked = ::unlock,
            phoneCheck = if (PhoneCheck.available(this)) { ok -> PhoneCheck.prompt(this, ok) } else null,
            onNewPin = { pin -> container.checkPin.setNew(pin) },
            onReset = {
                // No screen lock to prove the owner: the code goes with the data (B-27). The phone-bound
                // keys go first, so copies sealed with them can't be opened after the reset.
                PhoneKeys.deleteAll()
                (getSystemService(ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
            }
        )
    }

    private fun unlock() {
        val session = (application as App).container.lockSession
        session.unlocked = true
        session.backgroundedAt = null
        locked.value = false
    }

    override fun onStart() {
        super.onStart()
        val container = (application as App).container
        val session = container.lockSession
        val pinOn = runBlocking { container.settings.lockPin() != null }
        if (!locked.value && AppLock.needsUnlock(pinOn, !session.unlocked, session.backgroundedAt, System.currentTimeMillis())) {
            locked.value = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedImages(intent).takeIf { it.isNotEmpty() }?.let { shared.value = it }
        intent.getStringExtra(AppNotifier.EXTRA_OPEN)?.let { openRoute.value = it }
    }

    private fun sharedImages(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }.take(MAX_PICK)

    override fun onStop() {
        super.onStop()
        val session = (application as App).container.lockSession
        if (!locked.value) session.backgroundedAt = System.currentTimeMillis()
        // Leaving the app: don't wait for the backup's quiet period.
        (application as App).container.autoBackup.flush()
    }

    /**
     * The gravity sensor runs exactly while the app is on screen and «При
     * перевороте телефона» is on (B-55). It used to be registered from a
     * coroutine inside [onResume], guarded by
     * `lifecycle.currentState.isAtLeast(RESUMED)` — but during `onResume` the
     * lifecycle is still STARTED, so the guard was false and the sensor never
     * started. Tied to the lifecycle it also follows the switch at once,
     * without leaving the screen, and comes back after the lock screen.
     */
    private fun watchFlip() {
        val container = (application as App).container
        val sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    container.settings.hideOnFlip.collect { on ->
                        val sensor = if (on) {
                            sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
                                ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                        } else null
                        if (sensor != null) {
                            sensors.registerListener(gravity, sensor, SensorManager.SENSOR_DELAY_GAME)
                        } else {
                            sensors.unregisterListener(gravity)
                        }
                    }
                } finally {
                    sensors.unregisterListener(gravity)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as App).container
        // The midnight timer may sleep in Doze; re-read the date on return.
        container.today.refresh()
        // A return is when the tab animates: the background chores run at most every few
        // minutes, not at each one (U-31).
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastChores < CHORES_EVERY_MS && lastChores != 0L) return
        lastChores = now
        // Deposits may have changed: a reminder due today shouldn't wait for the morning run.
        lifecycleScope.launch { container.checkReminders() }
        // A bank app may have been installed or removed meanwhile.
        container.bankIcons.refresh()
        // The widget may be stale (restored backup, a new day): redraw it on return.
        container.refreshWidget()
        // Quietly fetch today's CBR rates if they are missing (at most once an hour).
        lifecycleScope.launch {
            container.refreshRates.ifNeeded(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow")))
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun AppRoot(container: AppContainer, factory: ViewModelFactory, startFeatures: Features, shared: List<Uri>, openRoute: String?, onOpened: () -> Unit, onSharedTaken: () -> Unit) {
    val nav = rememberNavController()
    val capitalVm: CapitalViewModel = viewModel(factory = factory)
    val logVm: LogViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val cashbackVm: CashbackViewModel = viewModel(factory = factory)
    val subscriptionsVm: SubscriptionsViewModel = viewModel(factory = factory)
    val depositsVm: DepositsViewModel = viewModel(factory = factory)

    val snackbar = remember { SnackbarHostState() }
    UndoSnackbarEffect(container.undo, snackbar)

    val scope = rememberCoroutineScope()
    val hidden by container.settings.amountsHidden.collectAsStateWithLifecycle(initialValue = false)
    val swipeOn by container.settings.hideBySwipe.collectAsStateWithLifecycle(initialValue = true)
    val toggleHidden = { scope.launch { container.settings.setAmountsHidden(!hidden) }; Unit }
    val reveal = { scope.launch { container.settings.setAmountsHidden(false) }; Unit }
    val swipeDistance = with(LocalDensity.current) { 120.dp.toPx() }

    // Shared screenshots: look at what they show, then open the cashback or deposit check.
    var placing by remember { mutableStateOf<List<Uri>?>(null) }
    var askKind by remember { mutableStateOf(false) }
    fun place(kind: ShotKind, uris: List<Uri>) {
        placing = null; askKind = false
        when (kind) {
            ShotKind.CASHBACK -> {
                val month = CashbackMonth.currentKey()
                if (uris.size == 1) cashbackVm.addShot(uris.first(), month) { nav.navigate(Routes.cashbackShot(it)) }
                else cashbackVm.addShots(uris, month) { nav.navigate(Routes.cashbackBatch(it)) }
            }
            ShotKind.DEPOSIT ->
                if (uris.size == 1) depositsVm.addShot(uris.first()) { nav.navigate(Routes.depositShot(it)) }
                else depositsVm.addShots(uris) { nav.navigate(Routes.depositBatch(it)) }
        }
    }
    LaunchedEffect(shared) {
        if (shared.isEmpty()) return@LaunchedEffect
        placing = shared
        onSharedTaken()
    }
    // Keyed on the batch itself, so taking it off the intent doesn't cancel the look.
    LaunchedEffect(placing) {
        val uris = placing ?: return@LaunchedEffect
        val saved = uris.take(3).mapNotNull { container.shotImageStore.save(it) }
        if (saved.isEmpty()) {
            placing = null
            snackbar.showSnackbar("Не получилось открыть скрины")
            return@LaunchedEffect
        }
        val kind = try { container.classifyScreenshot(saved.map { it.path }) } finally { saved.forEach { File(it.path).delete() } }
        if (kind != null) place(kind, uris) else askKind = true
    }

    val features by container.settings.features.collectAsStateWithLifecycle(initialValue = Features.ALL)
    val onboarded by container.settings.onboarded.collectAsStateWithLifecycle(initialValue = true)
    // Only the tabs of sections the user keeps (F-21), and «Обзор» only while it
    // has something to show (U-50).
    val tabs = topDests.filter { it.tab in features.tabs }
    // The root of the graph: the first visible tab at the moment the app starts
    // (U-50). A section switched off later redirects instead (below).
    val startRoute = remember { startRouteOf(startFeatures) }
    val banksVm: BanksViewModel = viewModel(factory = factory)
    // F-30: the tours taken; the general one opens once, right after the first setup.
    val tourKeys by container.settings.toursDone.collectAsStateWithLifecycle(initialValue = emptySet())
    val toursDone = tourKeys.mapNotNull { TourSection.fromKey(it) }.toSet()
    var setupSeen by rememberSaveable { mutableStateOf(false) }
    if (!onboarded) setupSeen = true
    if (!onboarded) {
        OnboardingScreen(settingsVm) { done ->
            // Second step: the user's banks; an own bank opens its editor in place.
            var own by rememberSaveable { mutableStateOf<String?>(null) }
            val name = own
            if (name == null) AddBankScreen(banksVm, firstLaunch = true, onBack = null, onDone = done, onOwn = { own = it })
            else BankEditorScreen(banksVm, existing = null, initialName = name, onBack = { own = null }, onDone = { own = null })
        }
        return
    }

    val lastBackup by container.autoBackup.lastSaved.collectAsStateWithLifecycle()
    LaunchedEffect(onboarded, setupSeen) {
        if (onboarded && setupSeen && TourScript.startAfterSetup(toursDone)) nav.navigate(Routes.tour(TourSection.GENERAL))
    }
    val tourScope = rememberCoroutineScope()
    val tourOffer: (TourSection, Boolean) -> (() -> Unit)? = { section, hasData ->
        if (TourScript.offerInSection(section, features, hasData, toursDone)) ({ nav.navigate(Routes.tour(section)) }) else null
    }

    // A tap on one of the app's notifications opens what it is about.
    LaunchedEffect(openRoute) {
        val r = openRoute ?: return@LaunchedEffect
        onOpened()
        when {
            r.startsWith(AppNotifier.OPEN_DEPOSIT) -> nav.navigate(Routes.depositDetail(r.removePrefix(AppNotifier.OPEN_DEPOSIT)))
            r == AppNotifier.OPEN_CASHBACK -> nav.navigate(Routes.CASHBACK_BASE)
            r == AppNotifier.OPEN_SUBSCRIPTIONS -> nav.navigate("cashback?section=1")
            r == AppNotifier.OPEN_NEW_BANKS -> nav.navigate(Routes.NEW_BANKS)
            r == AppNotifier.OPEN_FEATURES -> nav.navigate(Routes.SETTINGS_FEATURES)
        }
    }
    val selectedPackages by settingsVm.selectedPackages.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // A section switched off while the app is open: its tab is gone, so whoever
    // stands on it moves to the first visible one (U-50, U-52).
    val visibleRoutes = tabs.map { it.pattern }
    LaunchedEffect(features, currentRoute) {
        val onAHiddenTab = currentRoute in topDests.map { it.pattern } && currentRoute !in visibleRoutes
        if (onAHiddenTab) {
            val home = startRouteOf(features)
            nav.navigate(home) { popUpTo(nav.graph.findStartDestination().id); launchSingleTop = true }
        }
    }
    val showBottomBar = currentRoute in topDests.map { it.pattern }

    // U-38: «Добавить вклад» anywhere opens the same «Со скринов / Вручную» sheet.
    var addingDeposit by remember { mutableStateOf(false) }
    val pickDepositShots = rememberDepositShotsPicker(
        depositsVm,
        onOpenShot = { id -> nav.navigate(Routes.depositShot(id)) },
        onOpenBatch = { ids -> nav.navigate(Routes.depositBatch(ids)) }
    )

    // B-36: every tap on the bottom bar opens its tab from the top.
    var tabTaps by remember { mutableIntStateOf(0) }

    fun openTab(route: String) {
        // The start tab is the root of every stack: just return to it. Navigating
        // to it with restoreState would bring back the tab saved on top of it.
        if (route == startRoute) {
            nav.popBackStack(startRoute, inclusive = false)
            return
        }
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // Opening a given section/month is an explicit jump: no stale saved state.
    fun jumpTo(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }

    /** A capital part on the Overview opens its section of «Капитал» — the same slide as every move deeper (B-41). */
    fun openPart(part: CapitalPart) = jumpTo(Routes.capital(part))

    val myBanks by container.banks.myBanks.collectAsStateWithLifecycle(initialValue = emptyList())
    val newBanks by container.bankCandidates.candidates.collectAsStateWithLifecycle(initialValue = emptyList())
    ProvideDustClock(running = hidden) {
    CompositionLocalProvider(LocalMyBanks provides myBanks, LocalAddBank provides { nav.navigate(Routes.BANKS_ADD) }) {
    CompositionLocalProvider(LocalAmountsHidden provides hidden, LocalRevealAmounts provides reveal) {
    Scaffold(
        modifier = Modifier.twoFingerSwipeDown(swipeOn, swipeDistance, toggleHidden),
        // Edge-to-edge: the outer Scaffold adds no insets; top-level screens add
        // the status-bar inset themselves, and nested-Scaffold detail screens
        // manage their own, so insets are never applied twice.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            // Above the bottom bar on tabs; above the system bar on detail screens.
            SnackbarHost(snackbar, if (showBottomBar) Modifier else Modifier.navigationBarsPadding()) { data ->
                Snackbar(data, containerColor = Slitok.PanelTop, contentColor = Slitok.Fg, actionColor = Slitok.Brass2)
            }
        },
        bottomBar = {
            if (showBottomBar) {
                SlitokBottomBar(tabs, current = backStack?.destination, onSelect = { tabTaps++; openTab(it.route) })
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalTabTaps provides tabTaps, LocalTourOffer provides tourOffer) {
        NavHost(
            navController = nav,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize(),
            // One motion everywhere (B-41): deeper — the new screen flies in from the
            // right, back — it flies out to the right; between tabs — towards the tab.
            enterTransition = {
                val dir = tabDirection(initialState.destination, targetState.destination)
                slideInHorizontally(tabTween) { (if (dir == 0) 1 else dir) * it }
            },
            exitTransition = {
                when (val dir = tabDirection(initialState.destination, targetState.destination)) {
                    0 -> slideOutHorizontally(tabTween) { -it / 4 } + fadeOut(tween(260))
                    else -> slideOutHorizontally(tabTween) { -dir * it }
                }
            },
            popEnterTransition = {
                when (val dir = tabDirection(initialState.destination, targetState.destination)) {
                    0 -> slideInHorizontally(tabTween) { -it / 4 } + fadeIn(tween(260))
                    else -> slideInHorizontally(tabTween) { dir * it }
                }
            },
            popExitTransition = {
                val dir = tabDirection(initialState.destination, targetState.destination)
                slideOutHorizontally(tabTween) { -(if (dir == 0) -1 else dir) * it }
            }
        ) {
            // Top-level screens: apply the bottom-bar padding and the status-bar inset.
            composable(Routes.OVERVIEW) {
                TopLevel(padding) {
                    CompositionLocalProvider(LocalNavScope provides this) { OverviewScreen(
                        vm = capitalVm,
                        cashbackVm = cashbackVm,
                        onOpenCapital = { openPart(it) },
                        onOpenCashback = { openTab(Routes.CASHBACK_BASE) },
                        onOpenCashbackNext = { jumpTo("cashback?offset=1") },
                        onOpenBanks = { nav.navigate(Routes.SETTINGS_BANKS) },
                        onAddDeposit = { addingDeposit = true },
                        onRestore = { nav.navigate(Routes.SETTINGS_BACKUP) },
                        banksSelected = selectedPackages.isNotEmpty(),
                        selectedPackages = selectedPackages,
                        features = features,
                        newBanks = newBanks,
                        onOpenNewBanks = { nav.navigate(Routes.NEW_BANKS) },
                        onToggleHidden = toggleHidden
                    ) }
                }
            }
            composable(
                Routes.CASHBACK,
                arguments = listOf(
                    navArgument("offset") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("section") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { entry ->
                TopLevel(padding) {
                    // «Платежи» (F-27): cashback and subscriptions.
                    PaymentsScreen(
                        cashback = { monthOffset, byCategory, onMonthOffset ->
                            CashbackScreen(
                                cashbackVm,
                                monthOffset = monthOffset,
                                byCategory = byCategory,
                                onMonthOffset = onMonthOffset,
                                onOpenShot = { id -> nav.navigate(Routes.cashbackShot(id)) },
                                onOpenBatch = { ids -> nav.navigate(Routes.cashbackBatch(ids)) },
                                onEditBank = { month, brand -> nav.navigate(Routes.cashbackBankEdit(month, brand.key)) },
                                onAddManual = { month -> nav.navigate(Routes.cashbackBankEdit(month, Routes.NEW_CASHBACK)) }
                            )
                        },
                        subscriptions = {
                            SubscriptionsSection(
                                subscriptionsVm,
                                onOpen = { nav.navigate(Routes.subscription(it.id)) },
                                onCreate = { nav.navigate(Routes.SUBSCRIPTION_EDIT) }
                            )
                        },
                        onAddSubscription = { subscriptionsVm.newBlank(); nav.navigate(Routes.SUBSCRIPTION_EDIT) },
                        startSection = entry.arguments?.getInt("section") ?: 0,
                        initialOffset = entry.arguments?.getInt("offset") ?: 0,
                        showCashback = features.showCashback,
                        showSubscriptions = features.showSubscriptions
                    )
                }
            }
            composable(Routes.SUBSCRIPTION_EDIT) {
                SubscriptionFormScreen(subscriptionsVm, onBack = { nav.popBackStack() })
            }
            composable(Routes.SUBSCRIPTION) { entry ->
                SubscriptionCardScreen(
                    subscriptionsVm, entry.arguments?.getString("id").orEmpty(),
                    onEdit = { nav.navigate(Routes.SUBSCRIPTION_EDIT) },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.CAPITAL,
                arguments = listOf(navArgument("section") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                TopLevel(padding) {
                    CompositionLocalProvider(LocalNavScope provides this) { CapitalScreen(
                        vm = capitalVm,
                        depositsVm = depositsVm,
                        sectionArg = entry.arguments?.getString("section"),
                        parts = CapitalPart.entries.filter { it in features.capitalParts },
                        onOpenDeposit = { id -> nav.navigate(Routes.depositDetail(id)) },
                        onOpenDepositShot = { id -> nav.navigate(Routes.depositShot(id)) },
                        onOpenDepositBatch = { ids -> nav.navigate(Routes.depositBatch(ids)) },
                        onManualDeposit = { nav.navigate(Routes.DEPOSITS_MANUAL) },
                        onOpenPendingShots = { nav.navigate(Routes.DEPOSITS_ADD) },
                        onOpenCurrency = { code -> nav.navigate(Routes.currency(code)) },
                        selectedPackages = selectedPackages,
                        onOpenBanks = { nav.navigate(Routes.SETTINGS_BANKS) }
                    ) }
                }
            }
            composable(Routes.SETTINGS) {
                TopLevel(padding) {
                    SettingsScreen(
                        vm = settingsVm,
                        onOpenBanks = { nav.navigate(Routes.SETTINGS_BANKS) },
                        onOpenFeatures = { nav.navigate(Routes.SETTINGS_FEATURES) },
                        onOpenBackup = { nav.navigate(Routes.SETTINGS_BACKUP) },
                        onOpenLog = { nav.navigate(Routes.SETTINGS_LOG) },
                        onOpenPrivacy = { nav.navigate(Routes.SETTINGS_PRIVACY) },
                        onOpenNotifications = { nav.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                        onOpenIntro = { nav.navigate(Routes.LEARNING) },
                        onOpenShowcase = { nav.navigate(Routes.SHOWCASE) },
                        lastBackup = lastBackup
                    )
                }
            }
            // Detail screens have their own Scaffold and ScreenHeader that handle insets.
            composable(
                Routes.CURRENCY
            ) { entry ->
                CompositionLocalProvider(LocalNavScope provides this) { CurrencyDetailScreen(capitalVm, entry.arguments?.getString("code").orEmpty(), onBack = { nav.popBackStack() }) }
            }
            composable(Routes.CASHBACK_SHOT) { entry ->
                val shotId = entry.arguments?.getString("shotId").orEmpty()
                ShotPreviewScreen(cashbackVm, shotId, onBack = { nav.popBackStack() })
            }
            composable(Routes.CASHBACK_BATCH) { entry ->
                val ids = entry.arguments?.getString("ids").orEmpty().split(',').filter(String::isNotBlank)
                CashbackBatchScreen(
                    cashbackVm, ids,
                    onBack = { nav.popBackStack() },
                    onOpenShot = { id -> nav.navigate(Routes.cashbackShot(id)) },
                    onDone = { nav.popBackStack() }
                )
            }
            composable(
                Routes.CASHBACK_BANK_EDIT
            ) { entry ->
                val month = entry.arguments?.getString("monthKey").orEmpty()
                // «Вручную» opens the same form with no bank yet (B-22).
                val brand = entry.arguments?.getString("brandKey").orEmpty().takeIf { it != Routes.NEW_CASHBACK }?.let(BankBrand::fromKey)
                BankCashbackEditScreen(cashbackVm, month, brand, onBack = { nav.popBackStack() })
            }
            // Deposit flow: capital → add (shot/manual) → preview/form → detail.
            composable(Routes.DEPOSITS_ADD) {
                DepositAddScreen(
                    depositsVm,
                    onBack = { nav.popBackStack() },
                    onOpenShot = { id -> nav.navigate(Routes.depositShot(id)) },
                    onOpenBatch = { ids -> nav.navigate(Routes.depositBatch(ids)) },
                    onManual = { nav.navigate(Routes.DEPOSITS_MANUAL) }
                )
            }
            composable(Routes.DEPOSITS_SHOT) { entry ->
                val id = entry.arguments?.getString("shotId").orEmpty()
                // Opened from a batch check: back to it, so the rest can be saved too.
                val fromBatch = nav.previousBackStackEntry?.destination?.route == Routes.DEPOSITS_BATCH
                DepositShotPreviewScreen(depositsVm, id, onBack = { if (fromBatch) nav.popBackStack() else backToDeposits(nav) })
            }
            composable(Routes.DEPOSITS_BATCH) { entry ->
                val ids = entry.arguments?.getString("ids").orEmpty().split(',').filter(String::isNotBlank)
                DepositBatchScreen(
                    depositsVm, ids,
                    onBack = { nav.popBackStack() },
                    onOpenShot = { id -> nav.navigate(Routes.depositShot(id)) },
                    onDone = { backToDeposits(nav) }
                )
            }
            composable(Routes.DEPOSITS_MANUAL) {
                DepositManualScreen(depositsVm, depositId = null, onBack = { backToDeposits(nav) })
            }
            composable(Routes.DEPOSITS_MANUAL_EDIT) { entry ->
                val id = entry.arguments?.getString("depositId")
                // Deleted: its detail screen has nothing to show — back to the list.
                DepositManualScreen(depositsVm, depositId = id, onBack = { nav.popBackStack() }, onDeleted = { backToDeposits(nav) })
            }
            composable(
                Routes.DEPOSITS_DETAIL,
                arguments = listOf(navArgument("topup") { type = NavType.BoolType; defaultValue = false })
            ) { entry ->
                val id = entry.arguments?.getString("depositId").orEmpty()
                CompositionLocalProvider(LocalNavScope provides this) { DepositDetailScreen(
                    depositsVm, id,
                    onBack = { nav.popBackStack() },
                    onEdit = { depId -> nav.navigate(Routes.depositManualEdit(depId)) },
                    openTopUp = entry.arguments?.getBoolean("topup") ?: false
                ) }
            }
            composable(Routes.SETTINGS_FEATURES) {
                FeaturesScreen(settingsVm, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS_BANKS) {
                SettingsBanksScreen(
                    banksVm,
                    collectsNotifications = features.collectsNotifications,
                    onBack = { nav.popBackStack() },
                    onAdd = { nav.navigate(Routes.BANKS_ADD) },
                    onOpenNew = { nav.navigate(Routes.NEW_BANKS) },
                    onEdit = { nav.navigate(Routes.bankEdit(it.key)) },
                    onOpen = { nav.navigate(Routes.bankCard(it.key)) }
                )
            }
            composable(Routes.BANK_CARD) { entry ->
                BankCardScreen(
                    banksVm,
                    bankKey = entry.arguments?.getString("key").orEmpty(),
                    features = features,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.bankEdit(it.key)) },
                    // B-37: on top of the bank card, so «Назад» comes back to it.
                    onOpenCapital = { nav.navigate(Routes.capital(it)) },
                    onOpenCashback = { nav.navigate(Routes.CASHBACK_BASE) }
                )
            }
            composable(Routes.SETTINGS_NOTIFICATIONS) {
                NotificationsScreen(container.reminders, container.notifier, features, onBack = { nav.popBackStack() })
            }
            composable(Routes.NEW_BANKS) {
                NewBanksScreen(banksVm, onBack = { nav.popBackStack() })
            }
            composable(Routes.BANKS_ADD) {
                AddBankScreen(
                    banksVm, firstLaunch = false,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() },
                    onOwn = { nav.navigate(Routes.bankNew(it)) }
                )
            }
            composable(Routes.BANK_EDIT) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                BankEditorScreen(banksVm, existing = BankBrand.fromKey(key), initialName = "", onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
            }
            composable(
                Routes.BANK_NEW,
                arguments = listOf(navArgument("name") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                BankEditorScreen(
                    banksVm, existing = null, initialName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { nav.popBackStack() },
                    // Back to wherever the bank was being added from.
                    onDone = { nav.popBackStack(Routes.BANKS_ADD, inclusive = true) || nav.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_BACKUP) {
                SettingsBackupScreen(container.backupManager, container.autoBackup, container.backupPassword, container.undo, onBack = { nav.popBackStack() })
            }
            composable(Routes.SHOWCASE) { ShowcaseScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.LEARNING) {
                LearningScreen(
                    features, toursDone,
                    onOpen = { nav.navigate(Routes.tour(it)) },
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.TOUR) { entry ->
                val section = TourSection.fromKey(entry.arguments?.getString("section")) ?: TourSection.GENERAL
                TourScreen(section, features, onDone = {
                    tourScope.launch { container.settings.markTourDone(section.key) }
                    nav.popBackStack()
                })
            }
            composable(Routes.SETTINGS_LOG) {
                // Accounts switched off while the log was open: it closes (U-51).
                LaunchedEffect(features.showCaptureLog) {
                    if (!features.showCaptureLog) nav.popBackStack()
                }
                LogScreen(logVm, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS_PRIVACY) {
                val context = LocalContext.current
                SettingsPrivacyScreen(settingsVm, Biometrics.available(context), onBack = { nav.popBackStack() })
            }
        }
        }
    }
    }
    }
    }
    if (addingDeposit) {
        AddSheet(
            title = "Добавить вклад",
            onShots = pickDepositShots,
            onManual = { nav.navigate(Routes.DEPOSITS_MANUAL) },
            onDismiss = { addingDeposit = false }
        )
    }
    placing?.let { uris ->
        if (askKind) {
            SlitokSheet(onDismiss = { placing = null; askKind = false }) {
                Text("Что на скринах?", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TileButton(Icons.Filled.Percent, "Кэшбэк", Modifier.weight(1f)) { place(ShotKind.CASHBACK, uris) }
                    TileButton(Icons.Filled.Savings, "Вклад", Modifier.weight(1f)) { place(ShotKind.DEPOSIT, uris) }
                }
            }
        } else {
            SlitokDialog(
                title = "Смотрю, что на скринах…",
                onDismiss = {},
                content = { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Slitok.Brass, trackColor = Slitok.Line) }
            )
        }
    }
}

/**
 * After saving a deposit: back to the deposits section of Capital. When the
 * flow started on the overview (empty state), Capital isn't on the stack —
 * open it on the deposits section instead.
 */
private fun backToDeposits(nav: NavHostController) {
    if (!nav.popBackStack(Routes.CAPITAL, inclusive = false)) {
        nav.navigate(Routes.capital(CapitalPart.DEPOSITS)) {
            popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
}

/** Floating, rounded bottom bar in the Слиток style with a brass active pill. */
@Composable
private fun SlitokBottomBar(tabs: List<TopDest>, current: NavDestination?, onSelect: (TopDest) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, shape, clip = false, spotColor = Color.Black)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Slitok.PanelTop, Slitok.PanelBottom)))
                .border(1.dp, Slitok.Line, shape)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { dest ->
                val selected = current?.hierarchy?.any { it.route == dest.pattern } == true
                BarItem(dest, selected, Modifier.weight(1f)) { onSelect(dest) }
            }
        }
    }
}

@Composable
private fun BarItem(dest: TopDest, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Slitok.Brass2 else Slitok.Muted, label = "tint")
    val pill by animateColorAsState(if (selected) Slitok.BrassPill else Color.Transparent, label = "pill")
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(pill)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(dest.icon, contentDescription = dest.label, tint = tint, modifier = Modifier.size(22.dp))
        Text(dest.label, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Wraps a top-level screen with the bottom-bar padding and status-bar inset. */
@Composable
private fun TopLevel(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .statusBarsPadding()
    ) { content() }
}
