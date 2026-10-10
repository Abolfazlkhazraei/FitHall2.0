package com.fithall.app.ai

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Google Play Integrity "classic" request, used once per install at registration. The server
 * decodes the token with Google and checks the app and device verdicts.
 *
 * [cloudProjectNumber] is the Google Cloud project linked to the app in the Play Console
 * (PLAY_CLOUD_PROJECT_NUMBER in local.properties). With 0, attestation is skipped.
 */
class PlayIntegrityAttestation(
    context: Context,
    private val cloudProjectNumber: Long,
) : DeviceAttestation {

    private val manager = IntegrityManagerFactory.create(context.applicationContext)

    override suspend fun attest(nonce: String): String? {
        if (cloudProjectNumber == 0L) return null
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                val request = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .setCloudProjectNumber(cloudProjectNumber)
                    .build()
                manager.requestIntegrityToken(request)
                    .addOnSuccessListener { response -> continuation.resume(response.token()) }
                    .addOnFailureListener { e ->
                        // E.g. no Play Store, outdated Play services, no network. The server decides what that means.
                        Log.w("FitHall", "Play Integrity unavailable: ${e.message}")
                        continuation.resume(null)
                    }
            }
        }
    }
}
