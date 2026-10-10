package com.example.fithall

import androidx.compose.ui.window.ComposeUIViewController
import com.example.fithall.ai.UnavailableAiService
import com.example.fithall.data.AppRepository
import com.example.fithall.data.InMemoryKeyValueStore

fun MainViewController() = ComposeUIViewController {
    App(
        repository = AppRepository(InMemoryKeyValueStore()),
        ai = UnavailableAiService("AI features are only available in the Android app for now."),
    )
}
