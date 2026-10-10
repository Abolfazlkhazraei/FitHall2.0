package com.fithall.app.domain

import com.fithall.app.data.AppRepository
import com.fithall.app.data.PrivacyPolicy
import com.fithall.app.model.CheckInStatus

/**
 * Decides whether the background job should post "your weekly check-in is ready". At most one
 * reminder per check-in period: the period is identified by the latest check-in (or "first"
 * before there's been one), and the key of the last reminder sent is remembered.
 */
object CheckInReminder {

    /** The key to remember after notifying, or null if no reminder should be sent now. */
    fun reminderToSend(repository: AppRepository, today: String): String? {
        if (!repository.checkInReminders) return null
        if (repository.profile == null || repository.consent?.version != PrivacyPolicy.VERSION) return null

        val history = repository.history
        val checkIns = history.checkIns()
        if (checkIns.any { it.status == CheckInStatus.PENDING }) return null // already run, waiting for review
        val due = CheckInCalculator.isDue(checkIns, today, history.workouts(), history.weights(), history.meals())
        if (!due) return null

        val period = checkIns.maxByOrNull { it.createdAtMillis }?.id ?: "first"
        return period.takeIf { it != repository.lastReminderKey }
    }
}

/** Schedules the background reminder job. Android uses WorkManager; other platforms do nothing yet. */
interface ReminderScheduler {
    fun schedule()
    fun cancel()
}

object NoReminders : ReminderScheduler {
    override fun schedule() = Unit
    override fun cancel() = Unit
}
