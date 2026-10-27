package com.fithall.app

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fithall.app.ai.AiService
import com.fithall.app.ai.BackendAiService
import com.fithall.app.ai.PlayIntegrityAttestation
import com.fithall.app.api.Api
import com.fithall.app.auth.AuthService
import com.fithall.app.auth.FirebaseAuthService
import com.fithall.app.auth.NoAccounts
import com.fithall.app.data.OpenFoodFactsDatabase
import com.fithall.app.platform.AndroidConnectivity
import com.fithall.app.platform.AndroidReminderScheduler
import com.fithall.app.platform.AndroidAccountStorage
import com.fithall.app.platform.FirebaseAnalyticsTracker
import com.fithall.app.platform.androidKeyHeaders
import com.fithall.app.platform.firebaseApiKey
import com.fithall.app.platform.FirebaseCrashReporter
import com.fithall.app.platform.SharedPreferencesStore
import com.fithall.app.platform.clearAppCaches

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // The app is always dark, so keep light system-bar icons even when the phone is in light mode.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        // Device-level settings; each account's data is kept apart by AndroidAccountStorage.
        val store = SharedPreferencesStore(applicationContext)
        val auth: AuthService = firebaseApiKey(applicationContext)
            ?.let { FirebaseAuthService(it, store, androidKeyHeaders(applicationContext)) }
            ?: NoAccounts
        val ai: AiService = BackendAiService(
            baseUrl = BuildConfig.BACKEND_URL,
            store = store,
            attestation = PlayIntegrityAttestation(applicationContext, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER),
            userToken = auth::idToken,
        )
        val server = BuildConfig.BACKEND_URL.trimEnd('/')

        setContent {
            App(
                accounts = AndroidAccountStorage(applicationContext),
                ai = ai,
                auth = auth,
                deviceStore = store,
                foods = OpenFoodFactsDatabase(),
                privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL.ifBlank { server + Api.PRIVACY_PAGE },
                termsUrl = server + Api.TERMS_PAGE,
                clearCaches = { clearAppCaches(applicationContext) },
                reminders = AndroidReminderScheduler(applicationContext),
                openProgress = intent?.getStringExtra(EXTRA_OPEN) == OPEN_PROGRESS,
                connectivity = AndroidConnectivity.get(applicationContext),
                crashReporter = FirebaseCrashReporter(applicationContext),
                analytics = FirebaseAnalyticsTracker(applicationContext),
            )
        }
    }

    companion object {
        /** Set by the check-in reminder notification to open the Progress tab. */
        const val EXTRA_OPEN = "com.fithall.app.OPEN"
        const val OPEN_PROGRESS = "progress"
    }
}
