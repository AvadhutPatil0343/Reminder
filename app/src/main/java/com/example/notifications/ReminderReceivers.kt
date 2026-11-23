package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.repository.ReminderRepository
import com.example.widget.RemindlyWidgetProvider
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Medicine Dose"
        val notes = intent.getStringExtra(NotificationHelper.EXTRA_NOTES) ?: ""
        val priority = intent.getStringExtra(NotificationHelper.EXTRA_PRIORITY) ?: "HIGH"
        val category = intent.getStringExtra(NotificationHelper.EXTRA_CATEGORY) ?: "DAILY_MEDS"
        val recurrence = intent.getStringExtra(NotificationHelper.EXTRA_RECURRENCE) ?: "NONE"
        val isEarly = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_EARLY, false)
        val leadMinutes = intent.getLongExtra(NotificationHelper.EXTRA_LEAD_MINUTES, 0L)
        val urgentAlarm = intent.getBooleanExtra(NotificationHelper.EXTRA_URGENT_ALARM, false)
        val dosage = intent.getStringExtra(NotificationHelper.EXTRA_DOSAGE) ?: ""
        val mealInstruction = intent.getStringExtra(NotificationHelper.EXTRA_MEAL_INSTRUCTION) ?: "AFTER_FOOD"

        NotificationHelper.showReminderNotification(
            context = context,
            reminderId = reminderId,
            title = title,
            notes = notes,
            priorityRaw = priority,
            categoryRaw = category,
            recurrenceRaw = recurrence,
            isEarly = isEarly,
            leadMinutes = leadMinutes,
            urgentAlarm = urgentAlarm,
            dosage = dosage,
            mealInstructionRaw = mealInstruction
        )
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == NotificationHelper.ACTION_STOP_FOCUS) {
            AlarmScheduler(context).cancelFocusTimer()
            return
        }

        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID) ?: return
        NotificationHelper.cancelNotification(context, reminderId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmScheduler(context)
                val localStore = LocalAlarmStore(context)
                val isSignedIn = Firebase.auth.currentUser != null

                when (action) {
                    NotificationHelper.ACTION_COMPLETE -> {
                        if (isSignedIn) {
                            val repo = ReminderRepository(context)
                            val updated = repo.completeFromNotification(reminderId).getOrNull()
                            if (updated != null && updated.isLowStock) {
                                NotificationHelper.showRefillAlertNotification(
                                    context = context,
                                    medicineName = updated.title,
                                    remainingCount = updated.stockCount
                                )
                            }
                            if (updated != null && !updated.completed) {
                                scheduler.scheduleRecord(
                                    ScheduledAlarmRecord(
                                        reminderId = updated.id,
                                        title = updated.title,
                                        notes = updated.notes,
                                        triggerAtMillis = updated.effectiveTriggerMillis,
                                        leadTimeMinutes = updated.leadTimeMinutes,
                                        priority = updated.priority,
                                        category = updated.category,
                                        recurrence = updated.recurrence,
                                        urgentAlarm = updated.urgentAlarm,
                                        locationLabel = updated.locationLabel,
                                        dosage = updated.dosage,
                                        mealInstruction = updated.mealInstruction
                                    )
                                )
                            } else {
                                scheduler.cancelAlarm(reminderId)
                                localStore.removeAlarm(reminderId)
                            }
                        } else {
                            scheduler.cancelAlarm(reminderId)
                            localStore.removeAlarm(reminderId)
                        }
                    }

                    NotificationHelper.ACTION_SNOOZE -> {
                        val snoozeMinutes = intent.getIntExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, 10)
                        val newTrigger = if (isSignedIn) {
                            val repo = ReminderRepository(context)
                            repo.snoozeReminder(reminderId, snoozeMinutes).getOrNull()
                                ?: (System.currentTimeMillis() + snoozeMinutes * 60_000L)
                        } else {
                            System.currentTimeMillis() + snoozeMinutes * 60_000L
                        }
                        localStore.updateSnooze(reminderId, newTrigger)
                        val record = localStore.getStoredAlarms().firstOrNull { it.reminderId == reminderId }
                        if (record != null) {
                            scheduler.scheduleRecord(record.copy(triggerAtMillis = newTrigger))
                        }
                    }
                }
                RemindlyWidgetProvider.requestUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class MorningDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.showMorningDigestNotification(context)
        val (enabled, hour, minute) = LocalAlarmStore(context).getDigestConfig()
        if (enabled) {
            AlarmScheduler(context).scheduleMorningDigest(true, hour, minute)
        }
    }
}

class FocusTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Dose Window"
        NotificationHelper.showFocusCompleteNotification(context, title)
    }
}

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val localStore = LocalAlarmStore(context)
            val stored = localStore.getStoredAlarms().associateBy { it.reminderId }
            event.triggeringGeofences?.forEach { geofence ->
                val record = stored[geofence.requestId]
                if (record != null) {
                    NotificationHelper.showReminderNotification(
                        context = context,
                        reminderId = record.reminderId,
                        title = record.title,
                        notes = record.notes,
                        priorityRaw = record.priority,
                        categoryRaw = record.category,
                        recurrenceRaw = record.recurrence,
                        urgentAlarm = record.urgentAlarm,
                        locationBadge = record.locationLabel.ifBlank { "Pharmacy / Home" },
                        dosage = record.dosage,
                        mealInstructionRaw = record.mealInstruction
                    )
                }
            }
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            NotificationHelper.ensureNotificationChannels(context)
            AlarmScheduler(context).restoreAllFromLocalStore()
        }
    }
}
