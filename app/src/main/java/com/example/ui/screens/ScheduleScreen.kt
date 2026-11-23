package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Reminder
import com.example.ui.components.ReminderCard
import com.example.ui.components.accentColor
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleScreen(
    uiState: UiState<List<Reminder>>,
    selectedProfileId: String,
    nowMillis: Long,
    defaultSnoozeMinutes: Int,
    onToggleComplete: (Reminder) -> Unit,
    onSkipDose: (Reminder) -> Unit,
    onRefillStock: (Reminder) -> Unit,
    onTogglePin: (Reminder) -> Unit,
    onSnooze: (Reminder, Int) -> Unit,
    onStartFocus: (Reminder) -> Unit,
    onToggleSubtask: (Reminder, String) -> Unit,
    onSpeakDose: (Reminder) -> Unit,
    onAskAiInfo: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDayOffset by remember { mutableIntStateOf(0) }

    val days = remember(nowMillis) {
        val base = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        (0 until 14).map { offset ->
            val dayStart = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dayEnd = (dayStart.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            Triple(offset, dayStart.timeInMillis, dayEnd.timeInMillis)
        }
    }

    when (uiState) {
        is UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is UiState.Error -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(uiState.message, color = MaterialTheme.colorScheme.error)
        }

        is UiState.Success -> {
            val reminders = remember(uiState.data, selectedProfileId) {
                uiState.data.filter { it.profileId == selectedProfileId }
            }
            val (_, selectedStart, selectedEnd) = days[selectedDayOffset]
            val dayReminders = remember(reminders, selectedStart, selectedEnd) {
                reminders
                    .filter { it.effectiveTriggerMillis in selectedStart..selectedEnd }
                    .sortedBy { it.effectiveTriggerMillis }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "14-Day Dose Timeline",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(days) { (offset, start, end) ->
                            val dayItems = reminders.filter {
                                !it.completed && it.effectiveTriggerMillis in start..end
                            }
                            val isSelected = selectedDayOffset == offset
                            val dowFmt = SimpleDateFormat("EEE", Locale.getDefault())
                            val domFmt = SimpleDateFormat("d", Locale.getDefault())

                            Surface(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .width(60.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { selectedDayOffset = offset }
                                    .testTag("timeline_day_$offset")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = if (offset == 0) "Today" else dowFmt.format(Date(start)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = domFmt.format(Date(start)),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        dayItems.take(3).forEach { rem ->
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else rem.priorityEnum.accentColor()
                                                    )
                                            )
                                        }
                                        if (dayItems.isEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val headerFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                    Text(
                        text = headerFmt.format(Date(selectedStart)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (dayReminders.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No medicine doses scheduled for this day.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(dayReminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            nowMillis = nowMillis,
                            defaultSnoozeMinutes = defaultSnoozeMinutes,
                            onToggleComplete = { onToggleComplete(reminder) },
                            onSkipDose = { onSkipDose(reminder) },
                            onRefillStock = { onRefillStock(reminder) },
                            onTogglePin = { onTogglePin(reminder) },
                            onSnooze = { mins -> onSnooze(reminder, mins) },
                            onStartFocus = { onStartFocus(reminder) },
                            onToggleSubtask = { subId -> onToggleSubtask(reminder, subId) },
                            onSpeakDose = { onSpeakDose(reminder) },
                            onAskAiInfo = { onAskAiInfo(reminder) },
                            onEdit = { onEdit(reminder) },
                            onDelete = { onDelete(reminder.id) }
                        )
                    }
                }
            }
        }
    }
}
