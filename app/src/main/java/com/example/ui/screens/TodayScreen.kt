package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FamilyProfile
import com.example.data.model.MealInstruction
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderRecurrence
import com.example.data.model.SharedReminder
import com.example.data.parser.SmartReminderParser
import com.example.ui.components.ActiveFocusBanner
import com.example.ui.components.PermissionHealthBanner
import com.example.ui.components.ProfileSwitcherBar
import com.example.ui.components.ReminderCard
import com.example.ui.components.formatExactDateTime
import com.example.ui.components.formatRelativeCountdown
import com.example.ui.components.iconVector
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.MonoHeroTimerStyle
import com.example.ui.theme.PriorityUrgentColor
import com.example.ui.viewmodel.FocusSessionState
import com.example.ui.viewmodel.ReminderFilterTab
import com.example.ui.viewmodel.UiState
import java.util.Calendar
import java.util.Locale

@Composable
fun TodayScreen(
    uiState: UiState<List<Reminder>>,
    sharedRemindersState: UiState<List<SharedReminder>>,
    profilesState: UiState<List<FamilyProfile>>,
    selectedProfileId: String,
    currentUserId: String,
    nowMillis: Long,
    focusSession: FocusSessionState?,
    searchQuery: String,
    selectedFilter: ReminderFilterTab,
    selectedCategory: ReminderCategory?,
    defaultSnoozeMinutes: Int,
    emergencyContactName: String,
    emergencyContactPhone: String,
    highlightedReminderId: String?,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (String?, String, String, String, String, String, () -> Unit) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onOpenPrescriptionScanner: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterTabSelect: (ReminderFilterTab) -> Unit,
    onCategoryToggle: (ReminderCategory?) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onScheduleTestAlert: () -> Unit,
    onQuickAddNaturalLanguage: (String) -> Unit,
    onQuickAddFeedback: (String) -> Unit,
    onStopFocus: () -> Unit,
    onStartFocus: (Reminder) -> Unit,
    onToggleComplete: (Reminder) -> Unit,
    onSkipDose: (Reminder) -> Unit,
    onRefillStock: (Reminder) -> Unit,
    onTogglePin: (Reminder) -> Unit,
    onSnooze: (Reminder, Int) -> Unit,
    onToggleSubtask: (Reminder, String) -> Unit,
    onSpeakDose: (Reminder) -> Unit,
    onAskAiInfo: (Reminder) -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (String) -> Unit,
    onToggleSharedComplete: (SharedReminder) -> Unit,
    onToggleSharedSubtask: (SharedReminder, String) -> Unit,
    onDeleteShared: (String) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val profiles = (profilesState as? UiState.Success)?.data ?: emptyList()

    when (uiState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Unable to sync medicine schedule",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is UiState.Success -> {
            val allReminders = remember(uiState.data, selectedProfileId) {
                uiState.data.filter { it.profileId == selectedProfileId }
            }
            val sharedReminders = (sharedRemindersState as? UiState.Success)?.data ?: emptyList()

            val nextActive = remember(allReminders, nowMillis) {
                allReminders
                    .filter { !it.completed && it.effectiveTriggerMillis >= nowMillis }
                    .minByOrNull { it.effectiveTriggerMillis }
            }
            val overdueCount = remember(allReminders, nowMillis) {
                allReminders.count { it.isOverdue(nowMillis) }
            }
            val lowStockCount = remember(allReminders) {
                allReminders.count { !it.completed && it.isLowStock }
            }
            val activeCount = remember(allReminders) {
                allReminders.count { !it.completed }
            }

            val endOfTodayMillis = remember(nowMillis) {
                Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }

            val filtered = remember(
                allReminders,
                searchQuery,
                selectedFilter,
                selectedCategory,
                nowMillis
            ) {
                allReminders
                    .filter { item ->
                        val matchesQuery = searchQuery.isBlank() ||
                            item.title.contains(searchQuery, ignoreCase = true) ||
                            item.dosage.contains(searchQuery, ignoreCase = true) ||
                            item.prescribedBy.contains(searchQuery, ignoreCase = true) ||
                            item.notes.contains(searchQuery, ignoreCase = true)
                        val matchesCategory = selectedCategory == null || item.categoryEnum == selectedCategory
                        val matchesTab = when (selectedFilter) {
                            ReminderFilterTab.ALL -> !item.completed
                            ReminderFilterTab.OVERDUE -> item.isOverdue(nowMillis)
                            ReminderFilterTab.TODAY -> !item.completed && item.effectiveTriggerMillis <= endOfTodayMillis
                            ReminderFilterTab.REFILL_LOW -> !item.completed && item.isLowStock
                            ReminderFilterTab.PINNED -> !item.completed && item.pinned
                            ReminderFilterTab.SHARED -> false
                            ReminderFilterTab.COMPLETED -> item.completed
                        }
                        matchesQuery && matchesCategory && matchesTab
                    }
                    .sortedWith(
                        compareByDescending<Reminder> { !it.completed && it.pinned }
                            .thenBy { it.completed }
                            .thenBy { it.effectiveTriggerMillis }
                    )
            }

            val filteredShared = remember(sharedReminders, searchQuery, selectedCategory) {
                sharedReminders.filter { shared ->
                    val matchesQuery = searchQuery.isBlank() ||
                        shared.title.contains(searchQuery, ignoreCase = true) ||
                        shared.dosage.contains(searchQuery, ignoreCase = true) ||
                        shared.notes.contains(searchQuery, ignoreCase = true)
                    val matchesCategory = selectedCategory == null ||
                        shared.toReminder().categoryEnum == selectedCategory
                    matchesQuery && matchesCategory
                }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ProfileSwitcherBar(
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onSelectProfile = onSelectProfile,
                        onSaveProfile = onSaveProfile,
                        onDeleteProfile = onDeleteProfile
                    )
                }

                item {
                    NextDoseHeroCard(
                        nextReminder = nextActive,
                        nowMillis = nowMillis,
                        activeCount = activeCount,
                        overdueCount = overdueCount,
                        lowStockCount = lowStockCount,
                        nextDoseTitle = strings.nextDoseLabel,
                        allClearTitle = strings.allClearLabel,
                        onTestAlert = onScheduleTestAlert
                    )
                }

                // AI Prescription & Strip Scanner + SOS Quick Action Row
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onOpenPrescriptionScanner,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("open_prescription_scanner_button")
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.scanRxTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (emergencyContactPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyContactPhone")))
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("today_sos_call_button")
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = strings.emergencySosLabel, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(emergencyContactName.takeIf { it.isNotBlank() }?.take(10) ?: strings.emergencySosLabel)
                            }
                        }
                    }
                }

                if (focusSession != null) {
                    item {
                        ActiveFocusBanner(
                            session = focusSession,
                            nowMillis = nowMillis,
                            onStop = onStopFocus
                        )
                    }
                }

                if (lowStockCount > 0 && selectedFilter != ReminderFilterTab.REFILL_LOW) {
                    item {
                        LowStockAlertBanner(
                            lowStockCount = lowStockCount,
                            onViewRefills = { onFilterTabSelect(ReminderFilterTab.REFILL_LOW) }
                        )
                    }
                }

                if (!hasNotificationPermission || !canScheduleExactAlarms) {
                    item {
                        PermissionHealthBanner(
                            hasNotificationPermission = hasNotificationPermission,
                            canScheduleExactAlarms = canScheduleExactAlarms,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings
                        )
                    }
                }

                item {
                    SmartQuickAddCard(
                        nowMillis = nowMillis,
                        title = strings.quickAddTitle,
                        placeholder = strings.quickAddPlaceholder,
                        submitLabel = strings.scheduleMedicineBtn,
                        onSubmit = onQuickAddNaturalLanguage,
                        onFeedback = onQuickAddFeedback
                    )
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(strings.searchPlaceholder) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_reminders_input")
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReminderFilterTab.entries) { tab ->
                            val label = when {
                                tab == ReminderFilterTab.OVERDUE && overdueCount > 0 ->
                                    "${tab.label} ($overdueCount)"
                                tab == ReminderFilterTab.REFILL_LOW && lowStockCount > 0 ->
                                    "${tab.label} ($lowStockCount)"
                                tab == ReminderFilterTab.SHARED && sharedReminders.isNotEmpty() ->
                                    "${tab.label} (${sharedReminders.size})"
                                else -> tab.label
                            }
                            FilterChip(
                                selected = selectedFilter == tab,
                                onClick = { onFilterTabSelect(tab) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("filter_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReminderCategory.entries) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { onCategoryToggle(cat) },
                                leadingIcon = {
                                    Icon(cat.iconVector(), contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                }

                if (selectedFilter == ReminderFilterTab.SHARED) {
                    when (sharedRemindersState) {
                        is UiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }

                        is UiState.Error -> {
                            item {
                                Text(
                                    text = sharedRemindersState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        is UiState.Success -> {
                            if (filteredShared.isEmpty()) {
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Group,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "No caregiver medicines yet",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tap + and add family or caregiver emails to track doses together in real time.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = onCreateNew,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Text("Share Medicine Schedule")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(filteredShared, key = { it.id }) { shared ->
                                    val asReminder = shared.toReminder()
                                    ReminderCard(
                                        reminder = asReminder,
                                        nowMillis = nowMillis,
                                        defaultSnoozeMinutes = defaultSnoozeMinutes,
                                        sharedParticipantsLabel = "${shared.participantEmails.size} caregivers",
                                        onToggleComplete = { onToggleSharedComplete(shared) },
                                        onStartFocus = { onStartFocus(asReminder) },
                                        onToggleSubtask = { subId -> onToggleSharedSubtask(shared, subId) },
                                        onSpeakDose = { onSpeakDose(asReminder) },
                                        onAskAiInfo = { onAskAiInfo(asReminder) },
                                        onDelete = if (shared.ownerId == currentUserId) {
                                            { onDeleteShared(shared.id) }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else if (filtered.isEmpty()) {
                    item {
                        EmptyRemindersCard(
                            isFiltered = searchQuery.isNotBlank() || selectedCategory != null || selectedFilter != ReminderFilterTab.ALL,
                            onClearFilters = {
                                onSearchQueryChange("")
                                onCategoryToggle(null)
                                onFilterTabSelect(ReminderFilterTab.ALL)
                            },
                            onCreateNew = onCreateNew
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            nowMillis = nowMillis,
                            defaultSnoozeMinutes = defaultSnoozeMinutes,
                            highlighted = reminder.id == highlightedReminderId,
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

@Composable
private fun LowStockAlertBanner(
    lowStockCount: Int,
    onViewRefills: () -> Unit
) {
    Surface(
        color = PriorityUrgentColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = PriorityUrgentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$lowStockCount medicine(s) running low on stock",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            TextButton(
                onClick = onViewRefills,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("view_low_stock_button")
            ) {
                Text("Refill", color = PriorityUrgentColor)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartQuickAddCard(
    nowMillis: Long,
    title: String,
    placeholder: String,
    submitLabel: String,
    onSubmit: (String) -> Unit,
    onFeedback: (String) -> Unit
) {
    var quickInput by remember { mutableStateOf("") }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                quickInput = spoken
            }
        }
    }

    val parsed = remember(quickInput, nowMillis) {
        if (quickInput.isBlank()) null else SmartReminderParser.parse(quickInput, nowMillis)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = quickInput,
                    onValueChange = { quickInput = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("smart_quick_add_input")
                )
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                "Speak your medicine dose (e.g. Metformin 500mg before food 8am)"
                            )
                        }
                        try {
                            voiceLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            onFeedback("Speech recognition is not available on this device")
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("voice_quick_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Speak medicine dose",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (parsed != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickTagChip(parsed.draft.title)
                    QuickTagChip(parsed.draft.dosage)
                    if (parsed.draft.mealInstruction != MealInstruction.ANYTIME) {
                        QuickTagChip(parsed.draft.mealInstruction.label)
                    }
                    QuickTagChip(parsed.detectedTimeLabel)
                    QuickTagChip(parsed.draft.category.label)
                    QuickTagChip(parsed.draft.priority.label)
                    if (parsed.draft.recurrence != ReminderRecurrence.NONE) {
                        QuickTagChip(parsed.draft.recurrence.label)
                    }
                    if (parsed.draft.urgentAlarm) {
                        QuickTagChip("Full Alarm")
                    }
                }

                Button(
                    onClick = {
                        val textToAdd = quickInput.trim()
                        if (textToAdd.isNotEmpty()) {
                            onSubmit(textToAdd)
                            quickInput = ""
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("smart_quick_add_submit")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(submitLabel)
                }
            }
        }
    }
}

@Composable
private fun QuickTagChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun NextDoseHeroCard(
    nextReminder: Reminder?,
    nowMillis: Long,
    activeCount: Int,
    overdueCount: Int,
    lowStockCount: Int,
    nextDoseTitle: String,
    allClearTitle: String,
    onTestAlert: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Medication,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (nextReminder != null) nextDoseTitle else allClearTitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                TextButton(
                    onClick = onTestAlert,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("test_5s_alert_button")
                ) {
                    Text(
                        text = "Test 5s Dose",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (nextReminder != null) {
                Text(
                    text = formatRelativeCountdown(nextReminder.effectiveTriggerMillis, nowMillis)
                        .removePrefix("in "),
                    style = MonoHeroTimerStyle,
                    color = Color.White
                )
                Text(
                    text = "${nextReminder.title} • ${nextReminder.doseDisplaySummary}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusParts = buildList {
                    add(formatExactDateTime(nextReminder.effectiveTriggerMillis))
                    add("$activeCount active")
                    if (overdueCount > 0) add("$overdueCount due")
                    if (lowStockCount > 0) add("$lowStockCount low stock")
                }
                Text(
                    text = statusParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            } else {
                Text(
                    text = if (overdueCount > 0) "$overdueCount Dose(s) Due Now" else "Zero Upcoming Doses",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = "Tap + or speak into Quick Add to schedule pills, syrups, or insulin doses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun EmptyRemindersCard(
    isFiltered: Boolean,
    onClearFilters: () -> Unit,
    onCreateNew: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_med_empty_1795363015457),
                contentDescription = "No medicines scheduled",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isFiltered) "No matching medicines" else "Your medicine schedule is clear",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isFiltered) {
                    "Try clearing your search or medication filter to view all scheduled doses."
                } else {
                    "Add medicines with dosage, meal timing, pill inventory alerts, and caregiver sync."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = if (isFiltered) onClearFilters else onCreateNew,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                if (!isFiltered) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (isFiltered) "Reset Filters" else "Add First Medicine")
            }
        }
    }
}
