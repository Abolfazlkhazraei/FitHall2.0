package com.fithall.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fithall.app.ai.AiService
import com.fithall.app.auth.AuthService
import com.fithall.app.auth.AuthState
import com.fithall.app.auth.AuthViewModel
import com.fithall.app.auth.NoAccounts
import com.fithall.app.data.AccountStorage
import com.fithall.app.data.InMemoryKeyValueStore
import com.fithall.app.data.KeyValueStore
import com.fithall.app.ui.AccountSection
import com.fithall.app.ui.AuthMode
import com.fithall.app.ui.AuthScreen
import com.fithall.app.ui.VerifyEmailScreen
import com.fithall.app.ui.WelcomeScreen
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import com.fithall.app.data.FoodDatabase
import com.fithall.app.domain.NoReminders
import com.fithall.app.domain.ReminderScheduler
import com.fithall.app.data.UnavailableFoodDatabase
import com.fithall.app.ui.CoachScreen
import com.fithall.app.ui.ConsentScreen
import com.fithall.app.ui.FitHallTheme
import com.fithall.app.ui.OfflineBanner
import com.fithall.app.ui.NutritionScreen
import com.fithall.app.ui.OnboardingScreen
import com.fithall.app.ui.ProfileScreen
import com.fithall.app.ui.ProgressScreen
import com.fithall.app.ui.WorkoutScreen
import com.fithall.app.ui.Destination
import com.fithall.app.ui.FloatingNavBar
import com.fithall.app.ui.MealInput
import com.fithall.app.ui.QuickAddSheet
import com.fithall.app.ui.TodayScreen
import com.fithall.app.ui.formatWeight
import com.fithall.app.platform.SystemBackHandler
import com.fithall.app.platform.rememberSocialSignIn
import com.fithall.app.auth.NoSocialSignIn

@Composable
fun App(
    accounts: AccountStorage,
    ai: AiService,
    auth: AuthService = NoAccounts,
    /** Device-level settings that don't belong to an account (e.g. whether the welcome was seen). */
    deviceStore: KeyValueStore = InMemoryKeyValueStore(),
    foods: FoodDatabase = UnavailableFoodDatabase("Barcode lookup is not available on this platform."),
    privacyPolicyUrl: String = "",
    termsUrl: String = "",
    clearCaches: () -> Unit = {},
    reminders: ReminderScheduler = NoReminders,
    /** Start on the Progress tab, e.g. when opened from the check-in reminder. */
    openProgress: Boolean = false,
    connectivity: Connectivity = AlwaysOnline,
    crashReporter: CrashReporter = NoCrashReporter,
    analytics: Analytics = NoAnalytics,
) {
    val authViewModel = viewModel { AuthViewModel(auth) }
    val authState by auth.state.collectAsStateWithLifecycle()
    // Only offered when accounts are on; Google and Facebook sign into the same Firebase project.
    val social = if (auth.isConfigured) rememberSocialSignIn() else NoSocialSignIn

    FitHallTheme {
        when (val current = authState) {
            AuthState.SignedOut -> {
                var welcomeSeen by rememberSaveable { mutableStateOf(deviceStore.get(KEY_WELCOME_SEEN) != null) }
                var mode by rememberSaveable { mutableStateOf(AuthMode.LOG_IN) }
                if (!welcomeSeen) {
                    fun done(next: AuthMode) {
                        deviceStore.put(KEY_WELCOME_SEEN, "1")
                        mode = next
                        welcomeSeen = true
                    }
                    WelcomeScreen(onGetStarted = { done(AuthMode.SIGN_UP) }, onLogIn = { done(AuthMode.LOG_IN) })
                } else {
                    key(mode) { AuthScreen(authViewModel, mode, privacyPolicyUrl, termsUrl, social) }
                }
            }
            is AuthState.SignedIn -> {
                val user = current.user
                if (auth.isConfigured && !user.verified) {
                    VerifyEmailScreen(user, authViewModel, onUseAnotherAccount = auth::signOut)
                    return@FitHallTheme
                }
                val accountId = if (auth.isConfigured) user.uid else null
                // A separate view model, and so separate data, for each account.
                val viewModel = viewModel(key = "account-$accountId") {
                    AppViewModel(accounts.open(accountId), ai, foods, clearCaches, reminders, connectivity, crashReporter, analytics)
                }
                fun logOut() {
                    // Stops anything acting on this account's data while nobody is signed in.
                    reminders.cancel()
                    crashReporter.setEnabled(false)
                    analytics.setEnabled(false)
                    accounts.signedOut()
                    auth.signOut()
                }
                SignedInApp(
                    viewModel = viewModel,
                    privacyPolicyUrl = privacyPolicyUrl,
                    termsUrl = termsUrl,
                    openProgress = openProgress,
                    connectivity = connectivity,
                    account = if (!auth.isConfigured) null else {
                        {
                            AccountSection(
                                email = user.email,
                                authViewModel = authViewModel,
                                onLogOut = ::logOut,
                                wipeLocalData = { viewModel.deleteAllData(); accounts.signedOut() },
                                provider = user.socialProvider,
                                social = social,
                            )
                        }
                    },
                )
            }
        }
    }
}

private const val KEY_WELCOME_SEEN = "welcome_seen"

@Composable
private fun SignedInApp(
    viewModel: AppViewModel,
    privacyPolicyUrl: String,
    termsUrl: String,
    openProgress: Boolean,
    connectivity: Connectivity,
    account: (@Composable () -> Unit)?,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by connectivity.isOnline.collectAsStateWithLifecycle()

    if (state.needsConsent) {
        ConsentScreen(privacyPolicyUrl, termsUrl, onAccept = viewModel::acceptConsent)
        return
    }
    val profile = state.profile
    if (profile == null) {
        OnboardingScreen(onSave = viewModel::saveProfile)
        return
    }

    var screen by rememberSaveable { mutableStateOf(if (openProgress) Destination.PROGRESS else Destination.TODAY) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    var mealRequest by remember { mutableStateOf<MealInput?>(null) }
    LaunchedEffect(screen) { viewModel.trackScreen(screen.label) }
    // Back from Coach or Profile returns home rather than leaving the app.
    SystemBackHandler(enabled = screen != Destination.TODAY) { screen = Destination.TODAY }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            lastWeight = state.weights.maxByOrNull { it.dayKey }?.let { formatWeight(it.weightKg, profile.units) },
            onMeal = { input -> quickAdd = false; mealRequest = input; screen = Destination.EAT },
            onWorkout = { quickAdd = false; screen = Destination.TRAIN },
            onWeight = { quickAdd = false; screen = Destination.PROGRESS },
            onCoach = { quickAdd = false; screen = Destination.COACH },
            onDismiss = { quickAdd = false },
        )
    }

    // The live workout and the coach chat have their own controls along the bottom.
    val showBar = !(screen == Destination.TRAIN && state.activeWorkout != null) && screen != Destination.COACH
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // Screens handle the status bar themselves.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                FloatingNavBar(
                    current = screen,
                    progressBadge = state.checkInDue || state.pendingCheckIn != null,
                    onSelect = { screen = it },
                    onQuickAdd = { quickAdd = true },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            if (!online) OfflineBanner(Modifier.statusBarsPadding())
            // When the banner is showing it already covers the status bar. On tablets and in
            // landscape the content keeps a readable width instead of stretching edge to edge.
            Box(
                Modifier.weight(1f).fillMaxWidth().then(if (!online) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH)) {
                    when (screen) {
                        Destination.TODAY -> TodayScreen(state, viewModel, navigate = { screen = it }, onQuickAdd = { quickAdd = true })
                        Destination.TRAIN -> WorkoutScreen(state, viewModel, onMinimize = { screen = Destination.TODAY })
                        Destination.EAT -> NutritionScreen(state, viewModel, mealRequest, onRequestHandled = { mealRequest = null })
                        Destination.PROGRESS -> ProgressScreen(state, viewModel)
                        Destination.COACH -> CoachScreen(state, viewModel, onBack = { screen = Destination.TODAY })
                        Destination.PROFILE -> ProfileScreen(
                            profile, state.adjustment, state.checkInReminders,
                            crashReportsOn = state.consent?.crashReports == true,
                            analyticsOn = state.consent?.analytics == true,
                            privacyPolicyUrl = privacyPolicyUrl,
                            termsUrl = termsUrl,
                            viewModel = viewModel,
                            account = account ?: {},
                        )
                    }
                }
            }
        }
    }
}

private val MAX_CONTENT_WIDTH = 720.dp
