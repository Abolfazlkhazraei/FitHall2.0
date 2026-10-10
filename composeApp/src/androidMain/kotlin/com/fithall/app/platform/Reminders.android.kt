package com.fithall.app.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fithall.app.MainActivity
import com.fithall.app.R
import com.fithall.app.data.AppRepository
import com.fithall.app.data.SqlHistoryStore
import com.fithall.app.db.FitHallDatabase
import com.fithall.app.domain.CheckInReminder
import com.fithall.app.domain.ReminderScheduler
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Runs once a day (around 10:00) and posts a reminder if the weekly check-in has become due. */
class CheckInReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!canNotify(context)) return Result.success()
        val driver = AndroidSqliteDriver(FitHallDatabase.Schema, context, DATABASE_NAME)
        try {
            val repository = AppRepository(SharedPreferencesStore(context), SqlHistoryStore(driver))
            val key = CheckInReminder.reminderToSend(repository, todayKey()) ?: return Result.success()
            postNotification(context)
            repository.lastReminderKey = key
        } finally {
            driver.close()
        }
        return Result.success()
    }

    private fun postNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weekly check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when your weekly check-in is ready"
            }
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_PROGRESS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your weekly check-in is ready")
            .setContentText("See how your week went and get your adjusted plan.")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // canNotify() checked the permission just before.
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "weekly_check_in"
        const val NOTIFICATION_ID = 1
    }
}

const val DATABASE_NAME = "fithall.db"

private fun canNotify(context: Context): Boolean {
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    override fun schedule() {
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(10, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<CheckInReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        // KEEP: calling this on every app start doesn't push the next run back.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "weekly-check-in-reminder"
    }
}

@Composable
actual fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.invoke(granted && canNotify(context))
        pending[0] = null
    }
    return remember(context) {
        object : NotificationPermission {
            override val isSupported = true
            override fun request(onResult: (Boolean) -> Unit) {
                val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needsAsking) {
                    pending[0] = onResult
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onResult(canNotify(context))
                }
            }
        }
    }
}
