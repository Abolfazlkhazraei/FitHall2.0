package com.fithall.app.platform

import android.content.Context
import com.fithall.app.CrashReporter
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Crashlytics. Collection is off in the manifest and only turned on when the user opts
 * in. Without google-services.json (Firebase not set up yet) Firebase isn't initialised and this
 * does nothing. No user id or custom data is attached to reports.
 */
class FirebaseCrashReporter(context: Context) : CrashReporter {

    private val crashlytics: FirebaseCrashlytics? =
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseCrashlytics.getInstance() else null

    override fun setEnabled(enabled: Boolean) {
        val crashlytics = crashlytics ?: return
        crashlytics.isCrashlyticsCollectionEnabled = enabled
        if (!enabled) crashlytics.deleteUnsentReports()
    }

    override fun recordNonFatal(error: Throwable) {
        // Dropped by Crashlytics while collection is disabled.
        crashlytics?.recordException(error)
    }
}
