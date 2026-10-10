package com.example.fithall

import androidx.compose.ui.window.ComposeUIViewController
import com.example.fithall.ai.BackendAiService
import com.example.fithall.data.AppRepository
import com.example.fithall.data.InMemoryKeyValueStore

// The simulator reaches a server on the same Mac at localhost. iOS blocks plain HTTP by default,
// so local testing needs an App Transport Security exception in Info.plist.
private const val BACKEND_URL = "http://localhost:8080"

fun MainViewController() = ComposeUIViewController {
    val store = InMemoryKeyValueStore()
    App(
        repository = AppRepository(store),
        ai = BackendAiService(BACKEND_URL, store),
    )
}
