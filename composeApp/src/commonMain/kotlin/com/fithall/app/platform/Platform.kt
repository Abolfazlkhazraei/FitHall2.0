package com.fithall.app.platform

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

/** Speech-to-text for describing a meal out loud. */
interface VoiceInput {
    val isAvailable: Boolean
    fun start()
}

@Composable
expect fun rememberVoiceInput(prompt: String, onText: (String) -> Unit, onError: (String) -> Unit): VoiceInput

/** Scans a product barcode and returns its digits. */
interface BarcodeScanner {
    val isAvailable: Boolean
    fun scan()
}

@Composable
expect fun rememberBarcodeScanner(onBarcode: (String) -> Unit, onError: (String) -> Unit): BarcodeScanner
