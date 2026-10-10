package com.fithall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fithall.app.ai.AiService
import com.fithall.app.ai.BackendAiService
import com.fithall.app.ai.PlayIntegrityAttestation
import com.fithall.app.data.AppRepository
import com.fithall.app.data.OpenFoodFactsDatabase
import com.fithall.app.platform.SharedPreferencesStore
import com.fithall.app.platform.clearAppCaches

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val store = SharedPreferencesStore(applicationContext)
        val repository = AppRepository(store)
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
            )
        }
    }
}
