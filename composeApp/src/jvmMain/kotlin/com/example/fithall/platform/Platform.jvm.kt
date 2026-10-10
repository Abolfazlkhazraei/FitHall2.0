package com.example.fithall.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.LocalDate

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun todayKey(): String = LocalDate.now().toString()

// Desktop is not a target yet: photo capture is disabled.
@Composable
actual fun rememberMealPhotoLauncher(onPhoto: (ByteArray) -> Unit): MealPhotoLauncher = remember {
    object : MealPhotoLauncher {
        override val canTakePhoto = false
        override val canPickFromGallery = false
        override fun takePhoto() = Unit
        override fun pickFromGallery() = Unit
    }
}
