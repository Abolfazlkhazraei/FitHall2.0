package com.fithall.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.fithall.app.ai.BackendAiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "FitHall",
    ) {
        val store = InMemoryKeyValueStore()
        App(
            repository = AppRepository(store),
            ai = BackendAiService(System.getenv("FITHALL_BACKEND_URL") ?: "http://localhost:8080", store),
        )
    }
}
