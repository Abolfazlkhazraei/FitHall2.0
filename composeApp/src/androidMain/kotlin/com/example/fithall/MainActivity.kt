package com.example.fithall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.fithall.ai.AiService
import com.example.fithall.ai.BackendAiService
import com.example.fithall.ai.PlayIntegrityAttestation
import com.example.fithall.data.AppRepository
import com.example.fithall.data.OpenFoodFactsDatabase
import com.example.fithall.platform.SharedPreferencesStore

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
            App(repository, ai, OpenFoodFactsDatabase())
        }
    }
}
