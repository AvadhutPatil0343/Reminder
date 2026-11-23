package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.MealInstruction
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import com.example.ui.UrgentAlarmActivity
import java.util.Calendar

object NotificationHelper {
    const val CHANNEL_URGENT = "reminders_urgent"
    const val CHANNEL_STANDARD = "reminders_standard"
    const val CHANNEL_EARLY = "reminders_early"
    const val CHANNEL_DIGEST = "reminders_digest"
    const val CHANNEL_FOCUS = "reminders_focus"

    const val FOCUS_NOTIFICATION_ID = 990_001
    const val DIGEST_NOTIFICATION_ID = 990_002

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_NOTES = "extra_notes"
    const val EXTRA_PRIORITY = "extra_priority"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_RECURRENCE = "extra_recurrence"
    const val EXTRA_IS_EARLY = "extra_is_early"
    const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    const val EXTRA_URGENT_ALARM = "extra_urgent_alarm"
    const val EXTRA_DOSAGE = "extra_dosage"
    const val EXTRA_MEAL_INSTRUCTION = "extra_meal_instruction"

    const val ACTION_COMPLETE = "com.example.ACTION_REMINDER_COMPLETE"
    const val ACTION_SNOOZE = "com.example.ACTION_REMINDER_SNOOZE"
    const val ACTION_STOP_FOCUS = "com.example.ACTION_STOP_FOCUS"

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val urgentChannel = NotificationChannel(
            CHANNEL_URGENT,
            "Critical Dose & Full-Screen Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time heads-up and full-screen alarms for critical medicine doses"
            enableLights(true)
            lightColor = Color.rgb(15, 118, 110)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 320, 140, 320)
            setSound(
                alarmSound ?: notificationSound,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            )
        }

        val standardChannel = NotificationChannel(
            CHANNEL_STANDARD,
            "Scheduled Medicine Doses",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time notifications when a medicine dose is due"
            enableLights(true)
            lightColor = Color.rgb(15, 118, 110)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 120, 200)
        }

        val earlyChannel = NotificationChannel(
            CHANNEL_EARLY,
            "Pre-Dose, Refill & Pharmacy Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Upcoming dose reminders, low-stock refill warnings, and pharmacy geofence alerts"
            enableVibration(true)
        }

        val digestChannel = NotificationChannel(
            CHANNEL_DIGEST,
            "Daily Medication Schedule",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning summary of today's medicine schedule and overdue doses"
        }

        val focusChannel = NotificationChannel(
            CHANNEL_FOCUS,
            "Dose Window Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing countdown for post-dose or pre-meal waiting window"
        }

        manager.createNotificationChannels(
            listOf(urgentChannel, standardChannel, earlyChannel, digestChannel, focusChannel)
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun notificationIdFor(reminderId: String, isEarly: Boolean): Int {
        val base = reminderId.hashCode() and 0x0FFF_FFFF
        return if (isEarly) base + 100_000 else base
    }

    fun showReminderNotification(
        context: Context,
        reminderId: String,
        title: String,
        notes: String,
        priorityRaw: String,
        categoryRaw: String,
        recurrenceRaw: String,
        isEarly: Boolean = false,
        leadMinutes: Long = 0L,
        urgentAlarm: Boolean = false,
        locationBadge: String = "",
        dosage: String = "",
        mealInstructionRaw: String = MealInstruction.AFTER_FOOD.name
    ) {
        ensureNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val store = LocalAlarmStore(context)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isTest = reminderId.startsWith("test_")
        val inQuietHours = !isTest && !urgentAlarm && store.isQuietHoursActive(currentHour)
        val tone = store.getAlertTone()
        val vibrationOn = store.isVibrationEnabled()

        val priority = ReminderPriority.fromRaw(priorityRaw)
        val category = ReminderCategory.fromRaw(categoryRaw)
        val recurrence = ReminderRecurrence.fromRaw(recurrenceRaw)
        val meal = MealInstruction.fromRaw(mealInstructionRaw)

        val channelId = when {
            inQuietHours || isEarly -> CHANNEL_EARLY
            urgentAlarm || priority == ReminderPriority.URGENT || priority == ReminderPriority.HIGH -> CHANNEL_URGENT
            else -> CHANNEL_STANDARD
        }

        val notificationId = notificationIdFor(reminderId, isEarly)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doseSummary = buildList {
            if (dosage.isNotBlank()) add(dosage)
            if (meal != MealInstruction.ANYTIME) add(meal.label)
        }.joinToString(" • ")

        val subtitleParts = buildList {
            if (isEarly && leadMinutes > 0) add("In ${leadMinutes}m")
            if (locationBadge.isNotBlank()) add("Near $locationBadge")
            add(category.label)
            if (recurrence != ReminderRecurrence.NONE) add(recurrence.label)
        }
        val headerTag = subtitleParts.joinToString(" • ")
        val contentTitle = if (isEarly) "Upcoming Dose: $title" else "Take Medicine: $title"
        val bodyLine = listOfNotNull(
            doseSummary.takeIf { it.isNotBlank() },
            notes.takeIf { it.isNotBlank() }
        ).joinToString(" — ").ifBlank { headerTag }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(Color.rgb(15, 118, 110))
            .setContentTitle(contentTitle)
            .setContentText(bodyLine)
            .setSubText(headerTag)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(contentTitle)
                    .bigText("$bodyLine\n\n$headerTag")
            )
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setSilent(inQuietHours || tone == "SILENT")
            .setCategory(if (urgentAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(
                when {
                    inQuietHours || isEarly -> NotificationCompat.PRIORITY_DEFAULT
                    urgentAlarm || priority == ReminderPriority.URGENT -> NotificationCompat.PRIORITY_MAX
                    else -> NotificationCompat.PRIORITY_HIGH
                }
            )

        if (vibrationOn && !inQuietHours && tone != "SILENT") {
            builder.setVibrate(longArrayOf(0, 250, 120, 250))
        }

        if (!inQuietHours && tone != "SILENT") {
            val soundUri = when (tone) {
                "ALARM" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (soundUri != null) builder.setSound(soundUri)
        }

        if (urgentAlarm && !isEarly && !inQuietHours) {
            val fullScreenIntent = Intent(context, UrgentAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_NOTES, notes)
                putExtra(EXTRA_CATEGORY, category.label)
                putExtra(EXTRA_DOSAGE, dosage)
                putExtra(EXTRA_MEAL_INSTRUCTION, meal.label)
            }
            val fullScreenPi = PendingIntent.getActivity(
                context,
                notificationId + 700_000,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenPi, true)
        }

        if (!isTest) {
            val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val completePi = PendingIntent.getBroadcast(
                context,
                notificationId * 3 + 1,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snooze10Intent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_SNOOZE_MINUTES, 10)
            }
            val snooze10Pi = PendingIntent.getBroadcast(
                context,
                notificationId * 3 + 2,
                snooze10Intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snooze30Intent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_SNOOZE_MINUTES, 30)
            }
            val snooze30Pi = PendingIntent.getBroadcast(
                context,
                notificationId * 3 + 3,
                snooze30Intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder
                .addAction(0, "Take Dose", completePi)
                .addAction(0, "Snooze 10m", snooze10Pi)
                .addAction(0, "30m", snooze30Pi)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showRefillAlertNotification(context: Context, medicineName: String, remainingCount: Int) {
        ensureNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val openPi = PendingIntent.getActivity(
            context,
            880_900,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EARLY)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(Color.rgb(225, 29, 72))
            .setContentTitle("Low Medicine Stock: $medicineName")
            .setContentText("Only $remainingCount left. Time to refill your prescription.")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(880_900 + medicineName.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun showMorningDigestNotification(context: Context) {
        ensureNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val alarms = LocalAlarmStore(context).getStoredAlarms()
        val now = System.currentTimeMillis()
        val endOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        val overdue = alarms.filter { it.triggerAtMillis < now }
        val dueToday = alarms.filter { it.triggerAtMillis in now..endOfToday }

        if (overdue.isEmpty() && dueToday.isEmpty()) return

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Today's Medication Schedule")
            .setSummaryText("${dueToday.size} doses today • ${overdue.size} overdue")

        (overdue + dueToday).take(5).forEach { record ->
            val prefix = if (record.triggerAtMillis < now) "[Due Now] " else "• "
            val doseTag = record.dosage.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            inboxStyle.addLine("$prefix${record.title}$doseTag")
        }

        val openPi = PendingIntent.getActivity(
            context,
            DIGEST_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DIGEST)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(Color.rgb(15, 118, 110))
            .setContentTitle("Good Morning! ${dueToday.size + overdue.size} doses scheduled today")
            .setContentText("${dueToday.size} upcoming doses, ${overdue.size} due now")
            .setStyle(inboxStyle)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showOngoingFocusNotification(context: Context, title: String, endTimeMillis: Long) {
        ensureNotificationChannels(context)
        if (!hasNotificationPermission(context)) return

        val openPi = PendingIntent.getActivity(
            context,
            FOCUS_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            context,
            FOCUS_NOTIFICATION_ID + 1,
            Intent(context, ReminderActionReceiver::class.java).apply { action = ACTION_STOP_FOCUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(Color.rgb(15, 118, 110))
            .setContentTitle("Dose Gap Timer: $title")
            .setContentText("Waiting window before meal / next dose")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endTimeMillis)
            .setContentIntent(openPi)
            .addAction(0, "Stop Timer", stopPi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(FOCUS_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showFocusCompleteNotification(context: Context, title: String) {
        ensureNotificationChannels(context)
        NotificationManagerCompat.from(context).cancel(FOCUS_NOTIFICATION_ID)
        if (!hasNotificationPermission(context)) return

        val openPi = PendingIntent.getActivity(
            context,
            FOCUS_NOTIFICATION_ID + 2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_STANDARD)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(Color.rgb(5, 150, 105))
            .setContentTitle("Dose Window Complete!")
            .setContentText("Waiting interval finished for $title. Ready for your meal or next step.")
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(FOCUS_NOTIFICATION_ID + 2, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelFocusNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(FOCUS_NOTIFICATION_ID)
    }

    fun cancelNotification(context: Context, reminderId: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notificationIdFor(reminderId, false))
        manager.cancel(notificationIdFor(reminderId, true))
    }
}
