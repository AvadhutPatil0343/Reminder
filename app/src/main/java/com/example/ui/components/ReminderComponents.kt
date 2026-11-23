package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MealInstruction
import com.example.data.model.MedicationForm
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.MonoCountdownStyle
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.PriorityUrgentColor
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.FocusSessionState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun ReminderPriority.accentColor(): Color = when (this) {
    ReminderPriority.URGENT -> PriorityUrgentColor
    ReminderPriority.HIGH -> PriorityHighColor
    ReminderPriority.MEDIUM -> PriorityMediumColor
    ReminderPriority.LOW -> PriorityLowColor
}

fun ReminderCategory.iconVector(): ImageVector = when (this) {
    ReminderCategory.DAILY_MEDS -> Icons.Rounded.Medication
    ReminderCategory.VITAMINS -> Icons.Rounded.WbSunny
    ReminderCategory.HEART_BP -> Icons.Rounded.FavoriteBorder
    ReminderCategory.DIABETES -> Icons.Rounded.Bloodtype
    ReminderCategory.ANTIBIOTIC -> Icons.Rounded.Vaccines
    ReminderCategory.EYES_SKIN -> Icons.Rounded.Visibility
}

fun MedicationForm.iconVector(): ImageVector = when (this) {
    MedicationForm.TABLET -> Icons.Rounded.Medication
    MedicationForm.CAPSULE -> Icons.Rounded.Medication
    MedicationForm.SYRUP -> Icons.Rounded.LocalPharmacy
    MedicationForm.INJECTION -> Icons.Rounded.Vaccines
    MedicationForm.DROPS -> Icons.Rounded.Visibility
    MedicationForm.INHALER -> Icons.Rounded.Healing
    MedicationForm.OTHER -> Icons.Rounded.MedicalServices
}

fun formatExactDateTime(millis: Long): String {
    val nowCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { timeInMillis = millis }
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val sameDay = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
        nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today, ${timeFormat.format(Date(millis))}"

    val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val isTomorrow = tomorrowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
        tomorrowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
    if (isTomorrow) return "Tomorrow, ${timeFormat.format(Date(millis))}"

    val dateTimeFormat = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
    return dateTimeFormat.format(Date(millis))
}

fun formatRelativeCountdown(targetMillis: Long, nowMillis: Long): String {
    val diff = targetMillis - nowMillis
    val absSec = abs(diff) / 1000L
    val days = absSec / 86_400L
    val hours = (absSec % 86_400L) / 3_600L
    val minutes = (absSec % 3_600L) / 60L
    val seconds = absSec % 60L

    val core = when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
    return if (diff < 0) "-$core overdue" else "in $core"
}

@Composable
fun ActiveFocusBanner(
    session: FocusSessionState,
    nowMillis: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remainingSec = ((session.endTimeMillis - nowMillis).coerceAtLeast(0L) / 1000L)
    val mins = remainingSec / 60L
    val secs = remainingSec % 60L
    val formatted = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Dose Gap Timer • $formatted",
                        style = MonoCountdownStyle.copy(
                            fontSize = MaterialTheme.typography.titleSmall.fontSize
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OutlinedButton(
                onClick = onStop,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("stop_focus_button")
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
fun PermissionHealthBanner(
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasNotificationPermission && canScheduleExactAlarms) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Enable Real-Time Dose Alarms",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = if (!hasNotificationPermission) {
                    "Grant notification permission so Remindly can deliver dose alerts with Take Dose & Snooze buttons."
                } else {
                    "Allow exact alarms in system settings for second-accurate pill reminders even in battery saver."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasNotificationPermission) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_notifications_button")
                    ) {
                        Text("Allow Notifications")
                    }
                }
                if (!canScheduleExactAlarms) {
                    OutlinedButton(
                        onClick = onOpenExactAlarmSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_exact_alarm_button")
                    ) {
                        Text("Allow Exact Alarms")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderCard(
    reminder: Reminder,
    nowMillis: Long,
    defaultSnoozeMinutes: Int,
    highlighted: Boolean = false,
    sharedParticipantsLabel: String? = null,
    onToggleComplete: () -> Unit,
    onSkipDose: (() -> Unit)? = null,
    onRefillStock: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onSnooze: ((Int) -> Unit)? = null,
    onStartFocus: (() -> Unit)? = null,
    onToggleSubtask: (String) -> Unit,
    onSpeakDose: (() -> Unit)? = null,
    onAskAiInfo: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var expanded by remember { mutableStateOf(highlighted) }
    val priority = reminder.priorityEnum
    val category = reminder.categoryEnum
    val recurrence = reminder.recurrenceEnum
    val medForm = reminder.medFormEnum
    val mealInstruction = reminder.mealInstructionEnum
    val isOverdue = reminder.isOverdue(nowMillis)
    val accent = if (reminder.completed) SuccessEmerald else priority.accentColor()

    val containerColor by animateColorAsState(
        targetValue = when {
            highlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            reminder.completed -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardContainer"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (reminder.completed) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (reminder.completed) 0.72f else 1f)
            .clickable { expanded = !expanded }
            .testTag("reminder_card_${reminder.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleComplete)
                        .testTag("complete_toggle_${reminder.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (reminder.completed) SuccessEmerald else accent.copy(alpha = 0.14f)
                            )
                    ) {
                        Icon(
                            imageVector = if (reminder.completed) Icons.Rounded.Check else medForm.iconVector(),
                            contentDescription = if (reminder.completed) "Dose completed" else "Mark dose taken",
                            tint = if (reminder.completed) Color.White else accent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (reminder.completed) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = reminder.doseDisplaySummary +
                                if (reminder.prescribedBy.isNotBlank()) " • ${reminder.prescribedBy}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onTogglePin != null) {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pin_toggle_${reminder.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = if (reminder.pinned) "Unpin medicine" else "Pin medicine",
                                tint = if (reminder.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (reminder.notes.isNotBlank()) {
                    Text(
                        text = reminder.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 6 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isOverdue) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Alarm,
                                contentDescription = null,
                                tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatExactDateTime(reminder.effectiveTriggerMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (!reminder.completed) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = formatRelativeCountdown(reminder.effectiveTriggerMillis, nowMillis),
                                    style = MonoCountdownStyle.copy(
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                                    ),
                                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (mealInstruction != MealInstruction.ANYTIME) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mealInstruction.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }

                    if (reminder.stockCount >= 0) {
                        val low = reminder.isLowStock
                        Surface(
                            color = if (low) PriorityUrgentColor.copy(alpha = 0.14f) else SuccessEmerald.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Inventory2,
                                    contentDescription = null,
                                    tint = if (low) PriorityUrgentColor else SuccessEmerald,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (low) "Low: ${reminder.stockCount} left" else "${reminder.stockCount} left",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (low) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (low) PriorityUrgentColor else SuccessEmerald
                                )
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = category.iconVector(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (recurrence != ReminderRecurrence.NONE) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = recurrence.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    if (reminder.urgentAlarm) {
                        Surface(
                            color = PriorityUrgentColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RingVolume,
                                    contentDescription = null,
                                    tint = PriorityUrgentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Full Alarm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PriorityUrgentColor
                                )
                            }
                        }
                    }

                    if (reminder.locationTriggerEnabled) {
                        Surface(
                            color = SuccessEmerald.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = SuccessEmerald,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = reminder.locationLabel.ifBlank { "Pharmacy Alert" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessEmerald
                                )
                            }
                        }
                    }

                    if (sharedParticipantsLabel != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sharedParticipantsLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    if (reminder.leadTimeMinutes > 0L) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${reminder.leadTimeMinutes}m early",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (reminder.courseDays > 0) {
                    val dayNum = reminder.currentCourseDay(nowMillis)
                    val fraction = reminder.courseProgressFraction(nowMillis)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = if (reminder.completed) {
                                "${reminder.courseDays}/${reminder.courseDays}d Done"
                            } else {
                                "Day $dayNum of ${reminder.courseDays}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (reminder.subtasks.isNotEmpty()) {
                    val doneCount = reminder.subtasks.count { it.completed }
                    val total = reminder.subtasks.size
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { doneCount.toFloat() / total.toFloat() },
                            color = SuccessEmerald,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = "$doneCount/$total checks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        if (reminder.takenCount > 0 || reminder.skippedCount > 0) {
                            Text(
                                text = "History: ${reminder.takenCount} taken • ${reminder.skippedCount} skipped",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (reminder.subtasks.isNotEmpty()) {
                            reminder.subtasks.forEach { step ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onToggleSubtask(step.id) }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Checkbox(
                                        checked = step.completed,
                                        onCheckedChange = { onToggleSubtask(step.id) }
                                    )
                                    Text(
                                        text = step.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            textDecoration = if (step.completed) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!reminder.completed) {
                                Button(
                                    onClick = onToggleComplete,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuccessEmerald,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("take_dose_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(strings.takeDose)
                                }
                            }
                            if (!reminder.completed && onSkipDose != null) {
                                OutlinedButton(
                                    onClick = onSkipDose,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("skip_dose_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.skipDose)
                                }
                            }
                            if (!reminder.completed && onSnooze != null) {
                                OutlinedButton(
                                    onClick = { onSnooze(defaultSnoozeMinutes) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("snooze_default_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${strings.snoozeLabel} ${defaultSnoozeMinutes}m")
                                }
                            }
                            if (reminder.isLowStock && onRefillStock != null) {
                                OutlinedButton(
                                    onClick = onRefillStock,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("refill_stock_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.refillStockLabel)
                                }
                            }
                            if (onSpeakDose != null) {
                                OutlinedButton(
                                    onClick = onSpeakDose,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("speak_dose_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.speakButton)
                                }
                            }
                            if (onAskAiInfo != null) {
                                OutlinedButton(
                                    onClick = onAskAiInfo,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("ai_info_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.aiInfoButton)
                                }
                            }
                            if (!reminder.completed && onStartFocus != null) {
                                OutlinedButton(
                                    onClick = onStartFocus,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("start_focus_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("30m Gap")
                                }
                            }
                            if (onEdit != null) {
                                OutlinedButton(
                                    onClick = onEdit,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("edit_reminder_${reminder.id}")
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Edit")
                                }
                            }
                            if (onDelete != null) {
                                IconButton(
                                    onClick = onDelete,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("delete_reminder_${reminder.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete medicine",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
