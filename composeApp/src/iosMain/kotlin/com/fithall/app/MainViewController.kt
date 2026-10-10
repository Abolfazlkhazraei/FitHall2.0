package com.fithall.app

import androidx.compose.ui.window.ComposeUIViewController
import com.fithall.app.ai.BackendAiService
import com.fithall.app.data.AppRepository
import com.fithall.app.data.InMemoryKeyValueStore

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
