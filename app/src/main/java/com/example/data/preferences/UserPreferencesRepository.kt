package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "remindly_prefs")

enum class AlertTone(val label: String) {
    DEFAULT("Default"),
    ALARM("Alarm"),
    CHIME("Soft Chime"),
    SILENT("Silent");

    companion object {
        fun fromRaw(raw: String?): AlertTone =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

data class UserPreferences(
    val themeMode: String = "SYSTEM",
    val appLanguage: String = "en",
    val voiceAlertsEnabled: Boolean = true,
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val defaultSnoozeMinutes: Int = 10,
    val defaultLeadTimeMinutes: Long = 0L,
    val vibrationEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val digestEnabled: Boolean = true,
    val digestHour: Int = 8,
    val digestMinute: Int = 0,
    val alertTone: String = AlertTone.DEFAULT.name
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val VOICE_ALERTS_ENABLED = booleanPreferencesKey("voice_alerts_enabled")
        val EMERGENCY_NAME = stringPreferencesKey("emergency_contact_name")
        val EMERGENCY_PHONE = stringPreferencesKey("emergency_contact_phone")
        val DEFAULT_SNOOZE = intPreferencesKey("default_snooze_minutes")
        val DEFAULT_LEAD_TIME = intPreferencesKey("default_lead_time_minutes")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val DIGEST_ENABLED = booleanPreferencesKey("digest_enabled")
        val DIGEST_HOUR = intPreferencesKey("digest_hour")
        val DIGEST_MINUTE = intPreferencesKey("digest_minute")
        val ALERT_TONE = stringPreferencesKey("alert_tone")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: "en",
            voiceAlertsEnabled = prefs[Keys.VOICE_ALERTS_ENABLED] ?: true,
            emergencyContactName = prefs[Keys.EMERGENCY_NAME] ?: "",
            emergencyContactPhone = prefs[Keys.EMERGENCY_PHONE] ?: "",
            defaultSnoozeMinutes = prefs[Keys.DEFAULT_SNOOZE] ?: 10,
            defaultLeadTimeMinutes = (prefs[Keys.DEFAULT_LEAD_TIME] ?: 0).toLong(),
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            quietStartHour = prefs[Keys.QUIET_START_HOUR] ?: 22,
            quietEndHour = prefs[Keys.QUIET_END_HOUR] ?: 7,
            digestEnabled = prefs[Keys.DIGEST_ENABLED] ?: true,
            digestHour = prefs[Keys.DIGEST_HOUR] ?: 8,
            digestMinute = prefs[Keys.DIGEST_MINUTE] ?: 0,
            alertTone = prefs[Keys.ALERT_TONE] ?: AlertTone.DEFAULT.name
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = if (languageCode == "mr") "mr" else "en" }
    }

    suspend fun setVoiceAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_ALERTS_ENABLED] = enabled }
    }

    suspend fun setEmergencyContact(name: String, phone: String) {
        context.dataStore.edit {
            it[Keys.EMERGENCY_NAME] = name.trim().take(60)
            it[Keys.EMERGENCY_PHONE] = phone.trim().take(30)
        }
    }

    suspend fun setDefaultSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_SNOOZE] = minutes }
    }

    suspend fun setDefaultLeadTimeMinutes(minutes: Long) {
        context.dataStore.edit { it[Keys.DEFAULT_LEAD_TIME] = minutes.toInt() }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) {
        context.dataStore.edit {
            it[Keys.QUIET_HOURS_ENABLED] = enabled
            it[Keys.QUIET_START_HOUR] = startHour
            it[Keys.QUIET_END_HOUR] = endHour
        }
    }

    suspend fun setDigestSettings(enabled: Boolean, hour: Int, minute: Int = 0) {
        context.dataStore.edit {
            it[Keys.DIGEST_ENABLED] = enabled
            it[Keys.DIGEST_HOUR] = hour
            it[Keys.DIGEST_MINUTE] = minute
        }
    }

    suspend fun setAlertTone(tone: AlertTone) {
        context.dataStore.edit { it[Keys.ALERT_TONE] = tone.name }
    }
}
