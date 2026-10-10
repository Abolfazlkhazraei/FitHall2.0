package com.example.fithall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.fithall.ai.AiService
import com.example.fithall.ai.ClaudeAiService
import com.example.fithall.ai.UnavailableAiService
import com.example.fithall.data.AppRepository
import com.example.fithall.data.OpenFoodFactsDatabase
import com.example.fithall.platform.SharedPreferencesStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = AppRepository(SharedPreferencesStore(applicationContext))
        val ai: AiService = if (BuildConfig.ANTHROPIC_API_KEY.isNotBlank()) {
            ClaudeAiService(BuildConfig.ANTHROPIC_API_KEY)
        } else {
            UnavailableAiService("No AI key configured. Add ANTHROPIC_API_KEY to local.properties and rebuild.")
        }

        setContent {
            App(repository, ai, OpenFoodFactsDatabase())
        }
    }
}
