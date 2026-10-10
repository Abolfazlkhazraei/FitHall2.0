package com.fithall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fithall.app.ai.AiService
import com.fithall.app.ai.BackendAiService
import com.fithall.app.ai.PlayIntegrityAttestation
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fithall.app.data.AppRepository
import com.fithall.app.data.SqlHistoryStore
import com.fithall.app.db.FitHallDatabase
import com.fithall.app.data.OpenFoodFactsDatabase
import com.fithall.app.platform.AndroidReminderScheduler
import com.fithall.app.platform.DATABASE_NAME
import com.fithall.app.platform.SharedPreferencesStore
import com.fithall.app.platform.clearAppCaches

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val store = SharedPreferencesStore(applicationContext)
        val database = AndroidSqliteDriver(FitHallDatabase.Schema, applicationContext, DATABASE_NAME)
        val repository = AppRepository(store, SqlHistoryStore(database))
        val ai: AiService = BackendAiService(
            baseUrl = BuildConfig.BACKEND_URL,
            store = store,
            attestation = PlayIntegrityAttestation(applicationContext, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER),
        )

        setContent {
            App(
                repository = repository,
                ai = ai,
                foods = OpenFoodFactsDatabase(),
                privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL,
                clearCaches = { clearAppCaches(applicationContext) },
                reminders = AndroidReminderScheduler(applicationContext),
                openProgress = intent?.getStringExtra(EXTRA_OPEN) == OPEN_PROGRESS,
            )
        }
    }

    companion object {
        /** Set by the check-in reminder notification to open the Progress tab. */
        const val EXTRA_OPEN = "com.fithall.app.OPEN"
        const val OPEN_PROGRESS = "progress"
    }
}
