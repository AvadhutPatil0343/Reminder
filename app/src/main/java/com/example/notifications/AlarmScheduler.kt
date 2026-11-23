package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.DoctorAppointment
import com.example.data.model.MealInstruction
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import com.example.widget.RemindlyWidgetProvider
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val localStore = LocalAlarmStore(appContext)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun syncAlarms(reminders: List<Reminder>) {
        val existingIds = localStore.getStoredAlarms().map { it.reminderId }.toSet()
        val incomingIds = reminders.filter { !it.completed }.map { it.id }.toSet()

        (existingIds - incomingIds).forEach { removedId ->
            cancelAlarm(removedId)
        }
        reminders.filter { it.completed }.forEach { completed ->
            cancelAlarm(completed.id)
        }

        val now = System.currentTimeMillis()
        reminders
            .filter { !it.completed }
            .forEach { reminder ->
                val triggerAt = reminder.effectiveTriggerMillis
                if (triggerAt > now - 15_000L) {
                    scheduleRecord(
                        ScheduledAlarmRecord(
                            reminderId = reminder.id,
                            title = reminder.title,
                            notes = reminder.notes,
                            triggerAtMillis = maxOf(triggerAt, now + 1_000L),
                            leadTimeMinutes = reminder.leadTimeMinutes,
                            priority = reminder.priority,
                            category = reminder.category,
                            recurrence = reminder.recurrence,
                            urgentAlarm = reminder.urgentAlarm,
                            locationLabel = reminder.locationLabel,
                            dosage = reminder.dosage,
                            mealInstruction = reminder.mealInstruction
                        )
                    )
                }
            }

        localStore.saveActiveReminders(reminders, now)
        RemindlyWidgetProvider.requestUpdate(appContext)
    }

    fun syncAppointments(appointments: List<DoctorAppointment>) {
        val now = System.currentTimeMillis()
        appointments.forEach { appt ->
            val apptAlarmId = "appt_${appt.id}"
            if (appt.completed || appt.appointmentAtMillis < now - 15_000L) {
                cancelAlarm(apptAlarmId)
            } else {
                val subParts = listOf(appt.specialty, appt.clinicName).filter { it.isNotBlank() }
                val record = ScheduledAlarmRecord(
                    reminderId = apptAlarmId,
                    title = "Dr. ${appt.doctorName.removePrefix("Dr. ").removePrefix("Dr.")}",
                    notes = if (subParts.isNotEmpty()) subParts.joinToString(" • ") else "Doctor Visit",
                    triggerAtMillis = appt.appointmentAtMillis,
                    leadTimeMinutes = 30L,
                    priority = ReminderPriority.HIGH.name,
                    category = ReminderCategory.HEART_BP.name,
                    recurrence = ReminderRecurrence.NONE.name,
                    urgentAlarm = false,
                    dosage = "Visit",
                    mealInstruction = MealInstruction.ANYTIME.name
                )
                scheduleRecord(record)
            }
        }
    }

    fun restoreAllFromLocalStore() {
        val now = System.currentTimeMillis()
        localStore.getStoredAlarms().forEach { record ->
            if (record.triggerAtMillis > now - 60_000L) {
                scheduleRecord(
                    record.copy(triggerAtMillis = maxOf(record.triggerAtMillis, now + 2_000L))
                )
            }
        }
        val (digestEnabled, digestHour, digestMinute) = localStore.getDigestConfig()
        scheduleMorningDigest(digestEnabled, digestHour, digestMinute)
        RemindlyWidgetProvider.requestUpdate(appContext)
    }

    fun scheduleRecord(record: ScheduledAlarmRecord) {
        val now = System.currentTimeMillis()

        if (record.triggerAtMillis > now - 15_000L) {
            val pendingIntent = buildAlarmPendingIntent(record, isEarly = false)
            scheduleExactOrFallback(maxOf(record.triggerAtMillis, now + 500L), pendingIntent)
        }

        if (record.leadTimeMinutes > 0) {
            val earlyMillis = record.triggerAtMillis - (record.leadTimeMinutes * 60_000L)
            if (earlyMillis > now + 2_000L) {
                val earlyPendingIntent = buildAlarmPendingIntent(record, isEarly = true)
                scheduleExactOrFallback(earlyMillis, earlyPendingIntent)
            }
        }
    }

    fun scheduleMorningDigest(enabled: Boolean, hour: Int, minute: Int = 0) {
        localStore.saveDigestSchedule(enabled, hour, minute)
        val pi = PendingIntent.getBroadcast(
            appContext,
            DIGEST_REQUEST_CODE,
            Intent(appContext, MorningDigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
        if (!enabled) return

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        scheduleExactOrFallback(cal.timeInMillis, pi)
    }

    fun scheduleFocusEnd(title: String, endTimeMillis: Long) {
        NotificationHelper.showOngoingFocusNotification(appContext, title, endTimeMillis)
        val intent = Intent(appContext, FocusTimerReceiver::class.java).apply {
            putExtra(NotificationHelper.EXTRA_TITLE, title)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            FOCUS_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactOrFallback(endTimeMillis, pi)
    }

    fun cancelFocusTimer() {
        NotificationHelper.cancelFocusNotification(appContext)
        val pi = PendingIntent.getBroadcast(
            appContext,
            FOCUS_REQUEST_CODE,
            Intent(appContext, FocusTimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    fun scheduleTestNotification(delaySeconds: Int = 5, urgentAlarm: Boolean = false) {
        val triggerAt = System.currentTimeMillis() + (delaySeconds * 1000L)
        val record = ScheduledAlarmRecord(
            reminderId = "test_${System.currentTimeMillis()}",
            title = if (urgentAlarm) "Dolo 650mg (Test Alarm)" else "Metformin 500mg (Test Dose)",
            notes = "Take with warm water • Real-time medicine alarm verified.",
            triggerAtMillis = triggerAt,
            leadTimeMinutes = 0L,
            priority = ReminderPriority.URGENT.name,
            category = ReminderCategory.DAILY_MEDS.name,
            recurrence = ReminderRecurrence.NONE.name,
            urgentAlarm = urgentAlarm,
            dosage = "1 Tablet",
            mealInstruction = MealInstruction.AFTER_FOOD.name
        )
        val pendingIntent = buildAlarmPendingIntent(record, isEarly = false)
        scheduleExactOrFallback(triggerAt, pendingIntent)
    }

    fun cancelNotification(reminderId: String) {
        NotificationHelper.cancelNotification(appContext, reminderId)
    }

    fun cancelAlarm(reminderId: String) {
        val mainPi = buildCancelPendingIntent(reminderId, isEarly = false)
        val earlyPi = buildCancelPendingIntent(reminderId, isEarly = true)
        alarmManager.cancel(mainPi)
        alarmManager.cancel(earlyPi)
        NotificationHelper.cancelNotification(appContext, reminderId)
    }

    private fun scheduleExactOrFallback(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun buildAlarmPendingIntent(
        record: ScheduledAlarmRecord,
        isEarly: Boolean
    ): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            putExtra(NotificationHelper.EXTRA_REMINDER_ID, record.reminderId)
            putExtra(NotificationHelper.EXTRA_TITLE, record.title)
            putExtra(NotificationHelper.EXTRA_NOTES, record.notes)
            putExtra(NotificationHelper.EXTRA_PRIORITY, record.priority)
            putExtra(NotificationHelper.EXTRA_CATEGORY, record.category)
            putExtra(NotificationHelper.EXTRA_RECURRENCE, record.recurrence)
            putExtra(NotificationHelper.EXTRA_IS_EARLY, isEarly)
            putExtra(NotificationHelper.EXTRA_LEAD_MINUTES, record.leadTimeMinutes)
            putExtra(NotificationHelper.EXTRA_URGENT_ALARM, record.urgentAlarm)
            putExtra(NotificationHelper.EXTRA_DOSAGE, record.dosage)
            putExtra(NotificationHelper.EXTRA_MEAL_INSTRUCTION, record.mealInstruction)
        }
        val requestCode = requestCodeFor(record.reminderId, isEarly)
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCancelPendingIntent(reminderId: String, isEarly: Boolean): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeFor(reminderId, isEarly),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCodeFor(reminderId: String, isEarly: Boolean): Int {
        val base = reminderId.hashCode() and 0x0FFF_FFFF
        return if (isEarly) base + 500_000 else base
    }

    companion object {
        private const val DIGEST_REQUEST_CODE = 880_001
        private const val FOCUS_REQUEST_CODE = 880_002
    }
}
