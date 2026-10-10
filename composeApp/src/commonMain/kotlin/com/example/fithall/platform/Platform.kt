package com.example.fithall.platform

import androidx.compose.runtime.Composable

expect fun nowMillis(): Long

/** Today's local date as yyyy-MM-dd. */
expect fun todayKey(): String

/** Launches the platform camera / gallery and hands back a downscaled JPEG. */
interface MealPhotoLauncher {
    val canTakePhoto: Boolean
    val canPickFromGallery: Boolean
    fun takePhoto()
    fun pickFromGallery()
}

@Composable
expect fun rememberMealPhotoLauncher(onPhoto: (ByteArray) -> Unit): MealPhotoLauncher
