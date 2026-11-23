package com.example.notifications

import android.content.Context
import com.example.data.model.Reminder
import org.json.JSONArray
import org.json.JSONObject

data class ScheduledAlarmRecord(
    val reminderId: String,
    val title: String,
    val notes: String,
    val triggerAtMillis: Long,
    val leadTimeMinutes: Long,
    val priority: String,
    val category: String,
    val recurrence: String,
    val urgentAlarm: Boolean = false,
    val locationLabel: String = "",
    val dosage: String = "",
    val mealInstruction: String = "AFTER_FOOD"
)

class LocalAlarmStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveActiveReminders(reminders: List<Reminder>, nowMillis: Long = System.currentTimeMillis()) {
        val array = JSONArray()
        reminders
            .filter { !it.completed && (it.effectiveTriggerMillis > nowMillis - GRACE_WINDOW_MS || it.isOverdue(nowMillis)) }
            .sortedBy { it.effectiveTriggerMillis }
            .forEach { reminder ->
                array.put(
                    JSONObject().apply {
                        put("id", reminder.id)
                        put("title", reminder.title)
                        put("notes", reminder.notes)
                        put("triggerAtMillis", reminder.effectiveTriggerMillis)
                        put("leadTimeMinutes", reminder.leadTimeMinutes)
                        put("priority", reminder.priority)
                        put("category", reminder.category)
                        put("recurrence", reminder.recurrence)
                        put("urgentAlarm", reminder.urgentAlarm)
                        put("locationLabel", reminder.locationLabel)
                        put("dosage", reminder.dosage)
                        put("mealInstruction", reminder.mealInstruction)
                    }
                )
            }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    fun getStoredAlarms(): List<ScheduledAlarmRecord> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    if (id.isNotBlank()) {
                        add(
                            ScheduledAlarmRecord(
                                reminderId = id,
                                title = obj.optString("title"),
                                notes = obj.optString("notes"),
                                triggerAtMillis = obj.optLong("triggerAtMillis"),
                                leadTimeMinutes = obj.optLong("leadTimeMinutes"),
                                priority = obj.optString("priority", "MEDIUM"),
                                category = obj.optString("category", "DAILY_MEDS"),
                                recurrence = obj.optString("recurrence", "NONE"),
                                urgentAlarm = obj.optBoolean("urgentAlarm", false),
                                locationLabel = obj.optString("locationLabel", ""),
                                dosage = obj.optString("dosage", ""),
                                mealInstruction = obj.optString("mealInstruction", "AFTER_FOOD")
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDigestSchedule(enabled: Boolean, hour: Int, minute: Int) {
        prefs.edit()
            .putBoolean(KEY_DIGEST_ENABLED, enabled)
            .putInt(KEY_DIGEST_HOUR, hour)
            .putInt(KEY_DIGEST_MINUTE, minute)
            .apply()
    }

    fun getDigestConfig(): Triple<Boolean, Int, Int> = Triple(
        prefs.getBoolean(KEY_DIGEST_ENABLED, true),
        prefs.getInt(KEY_DIGEST_HOUR, 8),
        prefs.getInt(KEY_DIGEST_MINUTE, 0)
    )

    fun saveAlertSettings(
        quietEnabled: Boolean,
        quietStartHour: Int,
        quietEndHour: Int,
        alertTone: String,
        vibrationEnabled: Boolean,
        appLanguage: String = "en",
        voiceAlertsEnabled: Boolean = true
    ) {
        prefs.edit()
            .putBoolean(KEY_QUIET_ENABLED, quietEnabled)
            .putInt(KEY_QUIET_START, quietStartHour)
            .putInt(KEY_QUIET_END, quietEndHour)
            .putString(KEY_ALERT_TONE, alertTone)
            .putBoolean(KEY_VIBRATION, vibrationEnabled)
            .putString(KEY_APP_LANGUAGE, appLanguage)
            .putBoolean(KEY_VOICE_ALERTS, voiceAlertsEnabled)
            .apply()
    }

    fun isQuietHoursActive(hourOfDay: Int): Boolean {
        if (!prefs.getBoolean(KEY_QUIET_ENABLED, false)) return false
        val start = prefs.getInt(KEY_QUIET_START, 22)
        val end = prefs.getInt(KEY_QUIET_END, 7)
        return if (start > end) {
            hourOfDay >= start || hourOfDay < end
        } else {
            hourOfDay in start until end
        }
    }

    fun getAlertTone(): String = prefs.getString(KEY_ALERT_TONE, "DEFAULT") ?: "DEFAULT"

    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION, true)

    fun getAppLanguage(): String = prefs.getString(KEY_APP_LANGUAGE, "en") ?: "en"

    fun isVoiceAlertsEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_ALERTS, true)

    fun updateSnooze(reminderId: String, newTriggerMillis: Long) {
        val updated = getStoredAlarms().map {
            if (it.reminderId == reminderId) it.copy(triggerAtMillis = newTriggerMillis) else it
        }
        writeRecords(updated)
    }

    fun removeAlarm(reminderId: String) {
        val filtered = getStoredAlarms().filterNot { it.reminderId == reminderId }
        writeRecords(filtered)
    }

    private fun writeRecords(records: List<ScheduledAlarmRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.reminderId)
                    put("title", record.title)
                    put("notes", record.notes)
                    put("triggerAtMillis", record.triggerAtMillis)
                    put("leadTimeMinutes", record.leadTimeMinutes)
                    put("priority", record.priority)
                    put("category", record.category)
                    put("recurrence", record.recurrence)
                    put("urgentAlarm", record.urgentAlarm)
                    put("locationLabel", record.locationLabel)
                    put("dosage", record.dosage)
                    put("mealInstruction", record.mealInstruction)
                }
            )
        }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "remindly_local_alarms"
        private const val KEY_ALARMS = "active_alarms_json"
        private const val KEY_DIGEST_ENABLED = "digest_enabled"
        private const val KEY_DIGEST_HOUR = "digest_hour"
        private const val KEY_DIGEST_MINUTE = "digest_minute"
        private const val KEY_QUIET_ENABLED = "quiet_enabled"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_ALERT_TONE = "alert_tone"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_VOICE_ALERTS = "voice_alerts_enabled"
        private const val GRACE_WINDOW_MS = 60_000L
    }
}
