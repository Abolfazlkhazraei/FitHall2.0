package com.fithall.app.ai

/** Proves to the server that requests come from the genuine app on a genuine device. */
fun interface DeviceAttestation {
    /** Returns an attestation token bound to [nonce], or null if this device can't produce one. */
    suspend fun attest(nonce: String): String?
}

/** For platforms without attestation (iOS and Desktop for now) and for tests. */
val NoAttestation = DeviceAttestation { null }
