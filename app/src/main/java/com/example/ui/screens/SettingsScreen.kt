package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AlertTone
import com.example.data.preferences.UserPreferences
import com.example.ui.i18n.LocalAppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    userEmail: String?,
    userDisplayName: String?,
    preferences: UserPreferences,
    completedCount: Int,
    onLanguageChange: (String) -> Unit,
    onVoiceAlertsChange: (Boolean) -> Unit,
    onTestVoiceAlert: () -> Unit,
    onSaveEmergencyContact: (String, String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onDefaultSnoozeChange: (Int) -> Unit,
    onDefaultLeadTimeChange: (Long) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onQuietHoursChange: (Boolean, Int, Int) -> Unit,
    onDigestChange: (Boolean, Int) -> Unit,
    onAlertToneChange: (AlertTone) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onClearCompleted: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var emergencyName by remember(preferences.emergencyContactName) {
        mutableStateOf(preferences.emergencyContactName)
    }
    var emergencyPhone by remember(preferences.emergencyContactPhone) {
        mutableStateOf(preferences.emergencyContactPhone)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.titleSettings, style = MaterialTheme.typography.headlineSmall)
        }

        // Account Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(18.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "User profile",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userDisplayName?.takeIf { it.isNotBlank() } ?: "Patient Profile",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = userEmail ?: "Cloud Sync Enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // App Language & Voice Spoken Dose Alerts
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.languageSectionTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.appLanguage != "mr",
                            onClick = { onLanguageChange("en") },
                            label = { Text("English") },
                            modifier = Modifier.testTag("lang_chip_en")
                        )
                        FilterChip(
                            selected = preferences.appLanguage == "mr",
                            onClick = { onLanguageChange("mr") },
                            label = { Text("मराठी (Marathi)") },
                            modifier = Modifier.testTag("lang_chip_mr")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(strings.voiceAlertsTitle, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = strings.voiceAlertsSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.voiceAlertsEnabled,
                            onCheckedChange = onVoiceAlertsChange,
                            modifier = Modifier.testTag("voice_alerts_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = onTestVoiceAlert,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("test_voice_tts_button")
                    ) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.testVoiceBtn)
                    }
                }
            }
        }

        // Emergency / Doctor Contact
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.emergencySectionTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = emergencyName,
                        onValueChange = { if (it.length <= 60) emergencyName = it },
                        label = { Text("Contact / Doctor Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("emergency_name_input")
                    )
                    OutlinedTextField(
                        value = emergencyPhone,
                        onValueChange = { if (it.length <= 30) emergencyPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("emergency_phone_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onSaveEmergencyContact(emergencyName, emergencyPhone) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_emergency_button")
                        ) {
                            Text("Save Contact")
                        }
                        if (emergencyPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyPhone")))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.callDoctorBtn)
                            }
                        }
                    }
                }
            }
        }

        // Theme Selector
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "SYSTEM" to "System",
                            "LIGHT" to "Light",
                            "DARK" to "Dark"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = preferences.themeMode == key,
                                onClick = { onThemeModeChange(key) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("theme_mode_$key")
                            )
                        }
                    }
                }
            }
        }

        // Daily Morning Medication Digest & Quiet Hours
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Medication Digest & Quiet Hours", style = MaterialTheme.typography.titleMedium)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.WbSunny, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Morning Meds Digest", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Summary of today's medicine doses at ${preferences.digestHour}:00",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.digestEnabled,
                            onCheckedChange = { enabled ->
                                onDigestChange(enabled, preferences.digestHour)
                            },
                            modifier = Modifier.testTag("digest_switch")
                        )
                    }

                    if (preferences.digestEnabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7 to "7 AM", 8 to "8 AM", 9 to "9 AM", 10 to "10 AM").forEach { (hr, label) ->
                                FilterChip(
                                    selected = preferences.digestHour == hr,
                                    onClick = { onDigestChange(true, hr) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("digest_hour_$hr")
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Bedtime, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Quiet Hours (DND)", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "Silence optional alerts (${preferences.quietStartHour}:00 - ${preferences.quietEndHour}:00); critical dose alarms still ring",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = preferences.quietHoursEnabled,
                            onCheckedChange = { enabled ->
                                onQuietHoursChange(
                                    enabled,
                                    preferences.quietStartHour,
                                    preferences.quietEndHour
                                )
                            },
                            modifier = Modifier.testTag("quiet_hours_switch")
                        )
                    }

                    if (preferences.quietHoursEnabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple(22, 7, "10 PM - 7 AM"),
                                Triple(23, 7, "11 PM - 7 AM"),
                                Triple(21, 6, "9 PM - 6 AM")
                            ).forEach { (start, end, label) ->
                                FilterChip(
                                    selected = preferences.quietStartHour == start && preferences.quietEndHour == end,
                                    onClick = { onQuietHoursChange(true, start, end) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Notification Defaults & Alert Tone
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Dose Alarm Defaults & Sounds", style = MaterialTheme.typography.titleMedium)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dose Alarm Sound", style = MaterialTheme.typography.labelLarge)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val currentTone = AlertTone.fromRaw(preferences.alertTone)
                        AlertTone.entries.forEach { tone ->
                            FilterChip(
                                selected = currentTone == tone,
                                onClick = { onAlertToneChange(tone) },
                                label = { Text(tone.label) },
                                modifier = Modifier.testTag("alert_tone_${tone.name.lowercase()}")
                            )
                        }
                    }

                    Text("Default Dose Snooze", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30).forEach { mins ->
                            FilterChip(
                                selected = preferences.defaultSnoozeMinutes == mins,
                                onClick = { onDefaultSnoozeChange(mins) },
                                label = { Text("${mins}m") }
                            )
                        }
                    }

                    Text("Default Pre-Dose Notice", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0L to "At due", 5L to "5m", 15L to "15m", 30L to "30m", 60L to "1h").forEach { (mins, label) ->
                            FilterChip(
                                selected = preferences.defaultLeadTimeMinutes == mins,
                                onClick = { onDefaultLeadTimeChange(mins) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Vibration, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Alarm Vibration", style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(
                            checked = preferences.vibrationEnabled,
                            onCheckedChange = onVibrationChange
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenNotificationSettings,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open System Notification Settings")
                    }
                }
            }
        }

        // Data & Account Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onClearCompleted,
                    enabled = completedCount > 0,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("clear_completed_button")
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Completed Courses ($completedCount)")
                }

                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.signOutLabel)
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Remindly helps track prescribed schedules and refill stock. Always verify dosages with your healthcare provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
