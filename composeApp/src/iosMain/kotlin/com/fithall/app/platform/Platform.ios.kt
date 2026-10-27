package com.fithall.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.timeIntervalSince1970

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun todayKey(): String = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd" }.stringFromDate(NSDate())

// iOS is not a target yet: photo capture is disabled.
@Composable
actual fun rememberMealPhotoLauncher(onPhoto: (ByteArray) -> Unit): MealPhotoLauncher = remember {
    object : MealPhotoLauncher {
        override val canTakePhoto = false
        override val canPickFromGallery = false
        override fun takePhoto() = Unit
        override fun pickFromGallery() = Unit
    }
}

@Composable
actual fun rememberVoiceInput(prompt: String, onText: (String) -> Unit, onError: (String) -> Unit): VoiceInput = remember {
    object : VoiceInput {
        override val isAvailable = false
        override fun start() = Unit
    }
}

@Composable
actual fun rememberBarcodeScanner(onBarcode: (String) -> Unit, onError: (String) -> Unit): BarcodeScanner = remember {
    object : BarcodeScanner {
        override val isAvailable = false
        override fun scan() = Unit
    }
}

@Composable
actual fun rememberFileExporter(): FileExporter = remember {
    object : FileExporter {
        override val isAvailable = false
        override fun export(fileName: String, mimeType: String, content: String) = Unit
    }
}

@Composable
actual fun rememberNotificationPermission(): NotificationPermission = remember {
    object : NotificationPermission {
        override val isSupported = false
        override fun request(onResult: (Boolean) -> Unit) = onResult(false)
    }
}

@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
