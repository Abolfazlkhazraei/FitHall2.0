package com.example.fithall.platform

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
