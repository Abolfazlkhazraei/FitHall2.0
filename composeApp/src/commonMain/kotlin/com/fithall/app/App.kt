package com.fithall.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

private enum class Tab(val label: String, val icon: ImageVector) {
    NUTRITION("Nutrition", Icons.Filled.Restaurant),
    WORKOUTS("Workouts", Icons.Filled.FitnessCenter),
    PROGRESS("Progress", Icons.Filled.Insights),
    COACH("Coach", Icons.AutoMirrored.Filled.Chat),
    PROFILE("Profile", Icons.Filled.Person),
}

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
                    key(mode) { AuthScreen(authViewModel, mode, privacyPolicyUrl, termsUrl) }
                }
            }
            is AuthState.SignedIn -> {
                val user = current.user
                if (auth.isConfigured && !user.emailVerified) {
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

    var tab by rememberSaveable { mutableStateOf(if (openProgress) Tab.PROGRESS else Tab.NUTRITION) }
    LaunchedEffect(tab) { viewModel.trackScreen(tab.label) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // Screens handle the status bar themselves, so hero headers can draw behind it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            val badge = item == Tab.PROGRESS && (state.checkInDue || state.pendingCheckIn != null)
                            BadgedBox(badge = { if (badge) Badge() }) { Icon(item.icon, contentDescription = null) }
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
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
                    when (tab) {
                        Tab.NUTRITION -> NutritionScreen(state, viewModel)
                        Tab.WORKOUTS -> WorkoutScreen(state, viewModel)
                        Tab.PROGRESS -> ProgressScreen(state, viewModel)
                        Tab.COACH -> CoachScreen(state, viewModel)
                        Tab.PROFILE -> ProfileScreen(
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
