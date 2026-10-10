package com.example.fithall

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.fithall.ai.BackendAiService
import com.example.fithall.data.AppRepository
import com.example.fithall.data.InMemoryKeyValueStore

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
