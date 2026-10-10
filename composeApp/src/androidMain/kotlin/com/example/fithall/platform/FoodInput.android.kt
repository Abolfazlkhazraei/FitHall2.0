package com.example.fithall.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Uses the system speech recognizer screen, so the app needs no microphone permission: the
 * recognizer app records the audio and hands back the transcript.
 */
@Composable
actual fun rememberVoiceInput(prompt: String, onText: (String) -> Unit, onError: (String) -> Unit): VoiceInput {
    val context = LocalContext.current
    val currentOnText by rememberUpdatedState(onText)
    val currentOnError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) currentOnText(text)
    }
    return remember(context) {
        object : VoiceInput {
            override val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            override fun start() {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                try {
                    launcher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    currentOnError("Voice input isn't available on this device. You can type instead.")
                }
            }
        }
    }
}

/**
 * Google's code scanner: Play services shows its own camera screen and returns the barcode,
 * so the app needs no camera permission or camera code of its own.
 */
@Composable
actual fun rememberBarcodeScanner(onBarcode: (String) -> Unit, onError: (String) -> Unit): BarcodeScanner {
    val context = LocalContext.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnError by rememberUpdatedState(onError)
    return remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .enableAutoZoom()
            .build()
        object : BarcodeScanner {
            override val isAvailable = true
            override fun scan() {
                GmsBarcodeScanning.getClient(context, options).startScan()
                    .addOnSuccessListener { barcode -> barcode.rawValue?.let(currentOnBarcode) }
                    .addOnFailureListener {
                        currentOnError("The barcode scanner couldn't start. It needs Google Play services; try again in a moment.")
                    }
            }
        }
    }
}
