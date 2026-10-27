package com.fithall.app.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fithall.app.Analytics
import com.fithall.app.data.AccountStorage
import com.fithall.app.data.AppRepository
import com.fithall.app.data.SqlHistoryStore
import com.fithall.app.db.FitHallDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import java.security.MessageDigest
import android.os.Bundle

/**
 * Each account gets its own preferences file ("fithall_<id>") and database ("fithall_<id>.db").
 * The device-level "fithall" preferences keep only what belongs to the install: the device token,
 * the sign-in session and which account is active. Data from before accounts existed is handed to
 * the first account that signs in.
 */
class AndroidAccountStorage(private val context: Context) : AccountStorage {

    private val device = context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)

    override fun open(accountId: String?): AppRepository {
        val name = accountId?.let(::storageName)
        if (name != null) claimLegacyData(name)
        device.edit().putString(KEY_ACTIVE, name ?: NO_ACCOUNT).apply()
        return repository(context, name).first
    }

    override fun signedOut() {
        device.edit().remove(KEY_ACTIVE).apply()
    }

    private fun claimLegacyData(name: String) {
        if (device.getBoolean(KEY_LEGACY_CLAIMED, false)) return
        val legacy = device.all.filterKeys { it !in DEVICE_KEYS }
        val target = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (legacy.isNotEmpty() && target.all.isEmpty()) {
            target.edit().apply { legacy.forEach { (k, v) -> if (v is String) putString(k, v) } }.commit()
            device.edit().apply { legacy.keys.forEach(::remove) }.commit()
        }
        val oldDb = context.getDatabasePath(DATABASE_NAME)
        val newDb = context.getDatabasePath("$name.db")
        if (oldDb.exists() && !newDb.exists()) {
            listOf("", "-journal", "-wal", "-shm").forEach { suffix ->
                java.io.File(oldDb.path + suffix).takeIf { it.exists() }?.renameTo(java.io.File(newDb.path + suffix))
            }
        }
        device.edit().putBoolean(KEY_LEGACY_CLAIMED, true).apply()
    }

    companion object {
        const val DEVICE_PREFS = "fithall"
        private const val KEY_ACTIVE = "active_account"
        private const val KEY_LEGACY_CLAIMED = "legacy_claimed"
        private const val NO_ACCOUNT = "-"
        /** Device-level keys; everything else in the old shared file was the user's data. */
        private val DEVICE_KEYS = setOf("backend_device_token", "auth_session", "welcome_seen", KEY_ACTIVE, KEY_LEGACY_CLAIMED)

        /** Firebase uids are letters and digits; anything else is dropped so it's always a safe file name. */
        private fun storageName(accountId: String) = "fithall_" + accountId.filter { it.isLetterOrDigit() }.take(64)

        /** The signed-in account's data, for background work; the caller closes the driver. Null when signed out. */
        fun openActive(context: Context): Pair<AppRepository, SqlDriver>? {
            val active = context.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, null) ?: return null
            return repository(context, active.takeIf { it != NO_ACCOUNT })
        }

        private fun repository(context: Context, name: String?): Pair<AppRepository, SqlDriver> {
            val driver = AndroidSqliteDriver(FitHallDatabase.Schema, context, name?.let { "$it.db" } ?: DATABASE_NAME)
            return AppRepository(SharedPreferencesStore(context, name ?: DEVICE_PREFS), SqlHistoryStore(driver)) to driver
        }
    }
}

/** The Firebase Web API key from google-services.json, or null when Firebase isn't set up. */
fun firebaseApiKey(context: Context): String? {
    @Suppress("DiscouragedApi") // The resource only exists when google-services.json does.
    val id = context.resources.getIdentifier("google_api_key", "string", context.packageName)
    return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
}

/**
 * Headers that let Google check the request comes from this app, so the Firebase API key can be
 * restricted to "Android apps: com.fithall.app with our signing certificate" in Google Cloud.
 */
fun androidKeyHeaders(context: Context): Map<String, String> {
    val cert = runCatching {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        signatures?.firstOrNull()?.toByteArray()?.let { bytes ->
            MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
        }
    }.getOrNull()
    return buildMap {
        put("X-Android-Package", context.packageName)
        cert?.let { put("X-Android-Cert", it) }
    }
}

/**
 * Google Analytics for Firebase. Collection is off in the manifest and only turned on when the
 * user opts in; the advertising ID is never collected. Without Firebase set up this does nothing.
 */
class FirebaseAnalyticsTracker(context: Context) : Analytics {

    private val analytics: FirebaseAnalytics? =
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAnalytics.getInstance(context) else null

    override fun setEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    override fun event(name: String, params: Map<String, String>) {
        // Dropped by Firebase while collection is disabled.
        analytics?.logEvent(name, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
    }
}
