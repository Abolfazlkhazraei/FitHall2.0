package com.fithall.app

import androidx.compose.ui.window.ComposeUIViewController
import com.fithall.app.ai.BackendAiService
import com.fithall.app.data.InMemoryAccountStorage
import com.fithall.app.data.InMemoryKeyValueStore
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/**
 * From FITHALL_BACKEND_URL in iosApp/Configuration/Config.xcconfig (via Info.plist). Falls back to
 * a server on the same Mac, which is what the simulator reaches at localhost.
 */
private val BACKEND_URL: String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("FitHallBackendURL") as? String)
        ?.takeIf { it.startsWith("http") }
        ?: "http://localhost:8080"

fun MainViewController(): UIViewController {
    // Created once, outside composition. iOS keeps data in memory only for now.
    val store = InMemoryKeyValueStore()
    val accounts = InMemoryAccountStorage()
    val ai = BackendAiService(BACKEND_URL, store)
    return ComposeUIViewController { App(accounts = accounts, ai = ai, deviceStore = store) }
}
