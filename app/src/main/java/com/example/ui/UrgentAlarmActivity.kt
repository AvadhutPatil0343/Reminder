package com.example.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.notifications.DoseSpeaker
import com.example.notifications.LocalAlarmStore
import com.example.notifications.NotificationHelper
import com.example.notifications.ReminderActionReceiver
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PriorityUrgentColor
import com.example.ui.theme.RemindlyTheme
import com.example.ui.theme.SuccessEmerald

class UrgentAlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var doseSpeaker: DoseSpeaker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID).orEmpty()
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE) ?: "Urgent Medicine Dose"
        val notes = intent.getStringExtra(NotificationHelper.EXTRA_NOTES).orEmpty()
        val category = intent.getStringExtra(NotificationHelper.EXTRA_CATEGORY) ?: "Daily Meds"
        val dosage = intent.getStringExtra(NotificationHelper.EXTRA_DOSAGE).orEmpty()
        val mealInstruction = intent.getStringExtra(NotificationHelper.EXTRA_MEAL_INSTRUCTION).orEmpty()

        val localStore = LocalAlarmStore(this)
        val appLanguage = localStore.getAppLanguage()
        val voiceEnabled = localStore.isVoiceAlertsEnabled()

        startAlarmSoundAndVibration()
        doseSpeaker = DoseSpeaker(this)
        if (voiceEnabled) {
            doseSpeaker?.speakDose(
                title = title,
                dosage = dosage.ifBlank { "1 dose" },
                mealInstructionRaw = mealInstruction,
                languageCode = appLanguage
            )
        }

        setContent {
            RemindlyTheme(themeMode = "DARK") {
                UrgentAlarmScreen(
                    title = title,
                    notes = notes,
                    category = category,
                    dosage = dosage,
                    mealInstruction = mealInstruction,
                    onSpeakNow = {
                        doseSpeaker?.speakDose(
                            title = title,
                            dosage = dosage.ifBlank { "1 dose" },
                            mealInstructionRaw = mealInstruction,
                            languageCode = appLanguage
                        )
                    },
                    onComplete = {
                        stopAlarmSoundAndVibration()
                        if (reminderId.isNotBlank() && !reminderId.startsWith("test_")) {
                            sendBroadcast(
                                Intent(this, ReminderActionReceiver::class.java).apply {
                                    action = NotificationHelper.ACTION_COMPLETE
                                    putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
                                }
                            )
                        } else {
                            NotificationHelper.cancelNotification(this, reminderId)
                        }
                        finish()
                    },
                    onSnooze = { mins ->
                        stopAlarmSoundAndVibration()
                        if (reminderId.isNotBlank() && !reminderId.startsWith("test_")) {
                            sendBroadcast(
                                Intent(this, ReminderActionReceiver::class.java).apply {
                                    action = NotificationHelper.ACTION_SNOOZE
                                    putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminderId)
                                    putExtra(NotificationHelper.EXTRA_SNOOZE_MINUTES, mins)
                                }
                            )
                        } else {
                            NotificationHelper.cancelNotification(this, reminderId)
                        }
                        finish()
                    },
                    onDismiss = {
                        stopAlarmSoundAndVibration()
                        NotificationHelper.cancelNotification(this, reminderId)
                        finish()
                    }
                )
            }
        }
    }

    private fun startAlarmSoundAndVibration() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                play()
            }
        } catch (_: Exception) {
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 500, 300, 500, 300)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopAlarmSoundAndVibration() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        doseSpeaker?.shutdown()
        doseSpeaker = null
    }

    override fun onDestroy() {
        stopAlarmSoundAndVibration()
        super.onDestroy()
    }
}

@Composable
private fun UrgentAlarmScreen(
    title: String,
    notes: String,
    category: String,
    dosage: String,
    mealInstruction: String,
    onSpeakNow: () -> Unit,
    onComplete: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val doseBanner = listOfNotNull(
        dosage.takeIf { it.isNotBlank() },
        mealInstruction.takeIf { it.isNotBlank() }
    ).joinToString(" • ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0B1114), IndigoPrimary.copy(alpha = 0.55f), Color(0xFF0B1114))
                )
            )
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                color = PriorityUrgentColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "CRITICAL DOSE ALARM • ${category.uppercase()}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFFB4A3),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse)
                        .background(PriorityUrgentColor.copy(alpha = 0.25f), CircleShape)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(84.dp)
                            .background(PriorityUrgentColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Medication,
                            contentDescription = "Medicine dose alarm ringing",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                if (doseBanner.isNotBlank()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = doseBanner,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF5EEAD4),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                if (notes.isNotBlank()) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onSpeakNow,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Read dose aloud",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Speak Dose", color = Color.White)
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessEmerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("urgent_complete_button")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark Dose Taken", style = MaterialTheme.typography.titleMedium)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onSnooze(10) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("urgent_snooze_10_button")
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Snooze 10m")
                    }
                    OutlinedButton(
                        onClick = { onSnooze(30) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("urgent_snooze_30_button")
                    ) {
                        Text("Snooze 30m")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("urgent_dismiss_button")
                ) {
                    Text("Dismiss", color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}
