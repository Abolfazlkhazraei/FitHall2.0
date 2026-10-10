package com.example.fithall

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.fithall.ai.UnavailableAiService
import com.example.fithall.data.AppRepository
import com.example.fithall.data.InMemoryKeyValueStore

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "FitHall",
    ) {
        App(
            repository = AppRepository(InMemoryKeyValueStore()),
            ai = UnavailableAiService("AI features are only available in the Android app for now."),
        )
    }
}
