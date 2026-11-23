package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderRecurrence
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.MonoHeroTimerStyle
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityUrgentColor
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.UiState

@Composable
fun StatsScreen(
    uiState: UiState<List<Reminder>>,
    selectedProfileId: String,
    activeProfileName: String,
    nowMillis: Long,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onRefillStock: (Reminder) -> Unit,
    onSharePdfReport: () -> Unit,
    onSendInstantTest: () -> Unit,
    onSchedule5sTest: () -> Unit,
    onScheduleUrgentTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    when (uiState) {
        is UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is UiState.Error -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(uiState.message, color = MaterialTheme.colorScheme.error)
        }

        is UiState.Success -> {
            val reminders = uiState.data.filter { it.profileId == selectedProfileId }
            val total = reminders.size
            val completedCount = reminders.count { it.completed }
            val activeCount = reminders.count { !it.completed }
            val overdueCount = reminders.count { it.isOverdue(nowMillis) }
            val lowStockMeds = reminders.filter { !it.completed && it.isLowStock }
            val recurringCount = reminders.count { it.recurrenceEnum != ReminderRecurrence.NONE }

            val totalTaken = reminders.sumOf { it.takenCount }
            val totalSkipped = reminders.sumOf { it.skippedCount }
            val totalLogged = totalTaken + totalSkipped
            val adherenceRatio = when {
                totalLogged > 0 -> totalTaken.toFloat() / totalLogged.toFloat()
                total > 0 -> completedCount.toFloat() / total.toFloat()
                else -> 0f
            }

            val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val ringColor = MaterialTheme.colorScheme.primary

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("Medicine Adherence ($activeProfileName)", style = MaterialTheme.typography.headlineSmall)
                }

                item {
                    Button(
                        onClick = onSharePdfReport,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("stats_share_pdf_button")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.exportPdfBtn)
                    }
                }

                // Adherence Ring Card
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
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(104.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val stroke = 12.dp.toPx()
                                    drawArc(
                                        color = trackColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = ringColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * adherenceRatio,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = "${(adherenceRatio * 100).toInt()}%",
                                    style = MonoHeroTimerStyle.copy(
                                        fontSize = MaterialTheme.typography.titleLarge.fontSize
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Adherence Score", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (totalLogged > 0) {
                                        "$totalTaken doses taken • $totalSkipped skipped"
                                    } else {
                                        "$completedCount of $total courses finished"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$activeCount active meds • $recurringCount scheduled cycles",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // KPI Grid
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        KpiMiniCard(
                            icon = Icons.Rounded.AlarmOn,
                            label = "Active Meds",
                            value = activeCount.toString(),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMiniCard(
                            icon = Icons.Rounded.WarningAmber,
                            label = "Due Now",
                            value = overdueCount.toString(),
                            accent = if (overdueCount > 0) PriorityHighColor else SuccessEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMiniCard(
                            icon = Icons.Rounded.Inventory2,
                            label = "Low Stock",
                            value = lowStockMeds.size.toString(),
                            accent = if (lowStockMeds.isNotEmpty()) PriorityUrgentColor else SuccessEmerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Low-stock refill alerts
                if (lowStockMeds.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = PriorityUrgentColor.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Refill Alerts Needed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PriorityUrgentColor
                                )
                                lowStockMeds.forEach { med ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = med.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = "${med.stockCount} remaining (alert <= ${med.refillThreshold})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onRefillStock(med) },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("stats_refill_${med.id}")
                                        ) {
                                            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Refill +30")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Category Breakdown
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
                            Text("By Medication Type", style = MaterialTheme.typography.titleMedium)
                            ReminderCategory.entries.forEach { cat ->
                                val count = reminders.count { it.categoryEnum == cat }
                                val ratio = if (total == 0) 0f else count.toFloat() / total.toFloat()
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(cat.label, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                // Real-time Notification Engine Diagnostics
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
                                Icon(
                                    imageVector = if (hasNotificationPermission) {
                                        Icons.Rounded.NotificationsActive
                                    } else {
                                        Icons.Rounded.NotificationsOff
                                    },
                                    contentDescription = null,
                                    tint = if (hasNotificationPermission) SuccessEmerald else MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Real-Time Dose Alarm Engine", style = MaterialTheme.typography.titleMedium)
                            }

                            StatusBullet("Notification permission", hasNotificationPermission)
                            StatusBullet("Exact alarm scheduling", canScheduleExactAlarms)
                            StatusBullet("Reboot alarm persistence", true)

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onSendInstantTest,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("instant_test_notification_button")
                                ) {
                                    Text("Instant Alert")
                                }
                                OutlinedButton(
                                    onClick = onSchedule5sTest,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("delayed_test_notification_button")
                                ) {
                                    Text("5s Dose Test")
                                }
                            }
                            OutlinedButton(
                                onClick = onScheduleUrgentTest,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("urgent_test_notification_button")
                            ) {
                                Text("5s Critical Medicine Alarm")
                            }
                        }
                    }
                }

                // Medical scheduling disclaimer
                item {
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
                                text = "Remindly is a medication scheduling assistant and does not provide medical advice. Always follow your doctor's or pharmacist's instructions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMiniCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusBullet(label: String, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (enabled) SuccessEmerald else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (enabled) "Ready" else "Needs Action",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) SuccessEmerald else MaterialTheme.colorScheme.error
            )
        }
    }
}
