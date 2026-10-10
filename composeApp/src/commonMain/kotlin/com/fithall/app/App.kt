package com.fithall.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.fithall.app.data.AppRepository
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
    repository: AppRepository,
    ai: AiService,
    foods: FoodDatabase = UnavailableFoodDatabase("Barcode lookup is not available on this platform."),
    privacyPolicyUrl: String = "",
    clearCaches: () -> Unit = {},
    reminders: ReminderScheduler = NoReminders,
    /** Start on the Progress tab, e.g. when opened from the check-in reminder. */
    openProgress: Boolean = false,
    connectivity: Connectivity = AlwaysOnline,
) {
    val viewModel = viewModel { AppViewModel(repository, ai, foods, clearCaches, reminders, connectivity) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by connectivity.isOnline.collectAsStateWithLifecycle()

    FitHallTheme {
        if (state.needsConsent) {
            ConsentScreen(privacyPolicyUrl, onAccept = viewModel::acceptConsent)
            return@FitHallTheme
        }
        val profile = state.profile
        if (profile == null) {
            OnboardingScreen(onSave = viewModel::saveProfile)
            return@FitHallTheme
        }

        var tab by rememberSaveable { mutableStateOf(if (openProgress) Tab.PROGRESS else Tab.NUTRITION) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.error) {
            state.error?.let {
                snackbar.showSnackbar(it)
                viewModel.dismissError()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                val badge = item == Tab.PROGRESS && (state.checkInDue || state.pendingCheckIn != null)
                                BadgedBox(badge = { if (badge) Badge() }) { Icon(item.icon, contentDescription = null) }
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                if (!online) OfflineBanner()
                Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.NUTRITION -> NutritionScreen(state, viewModel)
                    Tab.WORKOUTS -> WorkoutScreen(state, viewModel)
                    Tab.PROGRESS -> ProgressScreen(state, viewModel)
                    Tab.COACH -> CoachScreen(state, viewModel)
                    Tab.PROFILE -> ProfileScreen(profile, state.adjustment, state.checkInReminders, privacyPolicyUrl, viewModel)
                }
                }
            }
        }
    }
}
