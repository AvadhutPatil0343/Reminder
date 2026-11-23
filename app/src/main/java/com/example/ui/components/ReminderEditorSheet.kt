package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyProfile
import com.example.data.model.MealInstruction
import com.example.data.model.MedicationForm
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderDraft
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import com.example.data.model.SubtaskItem
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorSheet(
    editingReminder: Reminder?,
    profiles: List<FamilyProfile>,
    initialProfileId: String,
    defaultLeadTimeMinutes: Long,
    isSaving: Boolean,
    onCaptureLocation: suspend () -> Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var profileId by remember(editingReminder, initialProfileId) {
        mutableStateOf(editingReminder?.profileId ?: initialProfileId.ifBlank { "self" })
    }
    var courseDays by remember(editingReminder) {
        mutableIntStateOf(editingReminder?.courseDays ?: 0)
    }
    var title by remember(editingReminder) { mutableStateOf(editingReminder?.title ?: "") }
    var dosage by remember(editingReminder) { mutableStateOf(editingReminder?.dosage ?: "1 Tablet") }
    var medForm by remember(editingReminder) {
        mutableStateOf(editingReminder?.medFormEnum ?: MedicationForm.TABLET)
    }
    var mealInstruction by remember(editingReminder) {
        mutableStateOf(editingReminder?.mealInstructionEnum ?: MealInstruction.AFTER_FOOD)
    }
    var prescribedBy by remember(editingReminder) {
        mutableStateOf(editingReminder?.prescribedBy ?: "")
    }
    var trackInventory by remember(editingReminder) {
        mutableStateOf((editingReminder?.stockCount ?: 30) >= 0)
    }
    var stockCountText by remember(editingReminder) {
        mutableStateOf((editingReminder?.stockCount?.takeIf { it >= 0 } ?: 30).toString())
    }
    var dosePerIntake by remember(editingReminder) {
        mutableIntStateOf(editingReminder?.dosePerIntake ?: 1)
    }
    var refillThreshold by remember(editingReminder) {
        mutableIntStateOf(editingReminder?.refillThreshold ?: 5)
    }

    var notes by remember(editingReminder) { mutableStateOf(editingReminder?.notes ?: "") }
    var dueAtMillis by remember(editingReminder) {
        mutableLongStateOf(
            editingReminder?.effectiveTriggerMillis ?: (System.currentTimeMillis() + 15 * 60_000L)
        )
    }
    var leadTimeMinutes by remember(editingReminder) {
        mutableLongStateOf(editingReminder?.leadTimeMinutes ?: defaultLeadTimeMinutes)
    }
    var priority by remember(editingReminder) {
        mutableStateOf(editingReminder?.priorityEnum ?: ReminderPriority.HIGH)
    }
    var category by remember(editingReminder) {
        mutableStateOf(editingReminder?.categoryEnum ?: ReminderCategory.DAILY_MEDS)
    }
    var recurrence by remember(editingReminder) {
        mutableStateOf(editingReminder?.recurrenceEnum ?: ReminderRecurrence.DAILY)
    }
    var pinned by remember(editingReminder) { mutableStateOf(editingReminder?.pinned ?: false) }
    var urgentAlarm by remember(editingReminder) { mutableStateOf(editingReminder?.urgentAlarm ?: false) }

    var locationTriggerEnabled by remember(editingReminder) {
        mutableStateOf(editingReminder?.locationTriggerEnabled ?: false)
    }
    var locationLabel by remember(editingReminder) {
        mutableStateOf(editingReminder?.locationLabel ?: "")
    }
    var latitude by remember(editingReminder) {
        mutableDoubleStateOf(editingReminder?.latitude ?: 0.0)
    }
    var longitude by remember(editingReminder) {
        mutableDoubleStateOf(editingReminder?.longitude ?: 0.0)
    }
    var radiusMeters by remember(editingReminder) {
        mutableLongStateOf(editingReminder?.radiusMeters ?: 250L)
    }
    var locationStatus by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            scope.launch {
                val coords = onCaptureLocation()
                if (coords != null) {
                    latitude = coords.first
                    longitude = coords.second
                    locationTriggerEnabled = true
                    locationStatus = String.format(
                        Locale.getDefault(),
                        "Pinned GPS: %.4f, %.4f",
                        coords.first,
                        coords.second
                    )
                } else {
                    locationStatus = "Could not fetch GPS fix. Try again outdoors."
                }
            }
        } else {
            locationStatus = "Location permission is required to pin GPS coordinates."
        }
    }

    val sharedEmails = remember { mutableStateListOf<String>() }
    var newEmailInput by remember { mutableStateOf("") }

    val subtasks = remember(editingReminder) {
        mutableStateListOf<SubtaskItem>().apply {
            if (editingReminder != null) addAll(editingReminder.subtasks)
        }
    }
    var newSubtaskTitle by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (editingReminder == null) "Add Medicine" else "Edit Medicine",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close editor")
                }
            }

            if (profiles.isNotEmpty()) {
                Text("For Family Member", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = profileId == "self",
                        onClick = { profileId = "self" },
                        label = { Text("Self") }
                    )
                    profiles.forEach { member ->
                        FilterChip(
                            selected = profileId == member.id,
                            onClick = { profileId = member.id },
                            label = { Text(member.name) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= 120) {
                        title = it
                        titleError = null
                    }
                },
                label = { Text("Medicine Name") },
                placeholder = { Text("e.g., Dolo 650, Metformin 500mg, Vitamin D3") },
                isError = titleError != null,
                supportingText = { Text(titleError ?: "${title.length}/120") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_title_input")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { if (it.length <= 60) dosage = it },
                    label = { Text("Dosage") },
                    placeholder = { Text("1 Tablet / 10 ml") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("medicine_dosage_input")
                )
                OutlinedTextField(
                    value = prescribedBy,
                    onValueChange = { if (it.length <= 80) prescribedBy = it },
                    label = { Text("Doctor (optional)") },
                    placeholder = { Text("Dr. Kulkarni") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("medicine_doctor_input")
                )
            }

            Text("Medicine Form", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MedicationForm.entries.forEach { form ->
                    FilterChip(
                        selected = medForm == form,
                        onClick = {
                            medForm = form
                            if (dosage.isBlank() || dosage == "1 Tablet" || dosage == "10 ml") {
                                dosage = when (form) {
                                    MedicationForm.SYRUP -> "10 ml"
                                    MedicationForm.DROPS -> "2 Drops"
                                    MedicationForm.INHALER -> "2 Puffs"
                                    MedicationForm.CAPSULE -> "1 Capsule"
                                    MedicationForm.INJECTION -> "1 Dose"
                                    else -> "1 Tablet"
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(form.iconVector(), contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(form.label) }
                    )
                }
            }

            Text("Meal / Food Timing", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MealInstruction.entries.forEach { meal ->
                    FilterChip(
                        selected = mealInstruction == meal,
                        onClick = { mealInstruction = meal },
                        label = { Text(meal.label) }
                    )
                }
            }

            Text("Dose Schedule Time", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pick_date_button")
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date")
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("pick_time_button")
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Time")
                }
            }

            Text(
                text = "Next Dose: ${formatExactDateTime(dueAtMillis)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickPresets = listOf(
                    "Morning 8 AM" to nextOccurrenceAt(8, 0),
                    "Afternoon 1 PM" to nextOccurrenceAt(13, 0),
                    "Night 9 PM" to nextOccurrenceAt(21, 0),
                    "+15 min" to (System.currentTimeMillis() + 15 * 60_000L),
                    "+1 hr" to (System.currentTimeMillis() + 60 * 60_000L)
                )
                quickPresets.forEach { (label, targetMillis) ->
                    FilterChip(
                        selected = false,
                        onClick = { dueAtMillis = targetMillis },
                        label = { Text(label) }
                    )
                }
            }

            Text("Repeat Interval", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderRecurrence.entries.forEach { item ->
                    FilterChip(
                        selected = recurrence == item,
                        onClick = { recurrence = item },
                        label = { Text(item.label) }
                    )
                }
            }

            Text("Course Duration", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0 to "Ongoing",
                    3 to "3 Days",
                    5 to "5 Days",
                    7 to "7 Days",
                    10 to "10 Days",
                    14 to "14 Days",
                    30 to "30 Days"
                ).forEach { (days, label) ->
                    FilterChip(
                        selected = courseDays == days,
                        onClick = { courseDays = days },
                        label = { Text(label) }
                    )
                }
            }

            // Pill Inventory & Refill Alert Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Track Pill Stock & Refill Alert", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Auto-decrements on intake and alerts when low",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = trackInventory,
                    onCheckedChange = { trackInventory = it },
                    modifier = Modifier.testTag("track_stock_switch")
                )
            }

            if (trackInventory) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = stockCountText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                stockCountText = input
                            }
                        },
                        label = { Text("Total Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("medicine_stock_input")
                    )
                    OutlinedTextField(
                        value = dosePerIntake.toString(),
                        onValueChange = { input ->
                            val parsed = input.toIntOrNull()
                            if (parsed != null && parsed in 1..20) {
                                dosePerIntake = parsed
                            } else if (input.isEmpty()) {
                                dosePerIntake = 1
                            }
                        },
                        label = { Text("Per Dose") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Warn when stock drops to:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7, 10).forEach { threshold ->
                        FilterChip(
                            selected = refillThreshold == threshold,
                            onClick = { refillThreshold = threshold },
                            label = { Text("$threshold left") }
                        )
                    }
                }
            }

            Text("Medicine Group", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderCategory.entries.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        leadingIcon = {
                            Icon(item.iconVector(), contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(item.label) }
                    )
                }
            }

            Text("Dose Priority", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReminderPriority.entries.forEach { item ->
                    FilterChip(
                        selected = priority == item,
                        onClick = { priority = item },
                        label = { Text(item.label) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RingVolume,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Full-Screen Critical Alarm", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Rings on lock screen & bypasses Quiet Hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = urgentAlarm,
                    onCheckedChange = { urgentAlarm = it },
                    modifier = Modifier.testTag("urgent_alarm_switch")
                )
            }

            Text("Early Pre-Dose Notice", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0L to "At dose time",
                    5L to "5m early",
                    15L to "15m early",
                    30L to "30m early",
                    60L to "1h early"
                ).forEach { (mins, label) ->
                    FilterChip(
                        selected = leadTimeMinutes == mins,
                        onClick = { leadTimeMinutes = mins },
                        leadingIcon = if (leadTimeMinutes == mins) {
                            {
                                Icon(
                                    Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        } else null,
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 500) notes = it },
                label = { Text("Special Instructions (optional)") },
                placeholder = { Text("e.g., Drink full glass of water, avoid dairy...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reminder_notes_input")
            )

            // Pharmacy / Home Location Alert
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Pharmacy / Home Location Alert", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Remind for refill or dose when arriving at place",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = locationTriggerEnabled,
                    onCheckedChange = { locationTriggerEnabled = it },
                    modifier = Modifier.testTag("location_trigger_switch")
                )
            }

            if (locationTriggerEnabled) {
                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { if (it.length <= 120) locationLabel = it },
                    label = { Text("Place Name (e.g., Wellness Medical, Home)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("capture_gps_button")
                ) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use Current GPS")
                }

                if (locationStatus != null || latitude != 0.0 || longitude != 0.0) {
                    Text(
                        text = locationStatus ?: String.format(
                            Locale.getDefault(),
                            "Coordinates: %.4f, %.4f",
                            latitude,
                            longitude
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100L, 250L, 500L, 1000L).forEach { r ->
                        FilterChip(
                            selected = radiusMeters == r,
                            onClick = { radiusMeters = r },
                            label = { Text("${r}m radius") }
                        )
                    }
                }
            }

            if (editingReminder == null) {
                Text("Share with Caregiver / Family (Optional)", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newEmailInput,
                        onValueChange = { newEmailInput = it },
                        placeholder = { Text("Caregiver Google email...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_email_input")
                    )
                    Button(
                        onClick = {
                            val clean = newEmailInput.trim().lowercase(Locale.ROOT)
                            if (clean.contains("@") && !sharedEmails.contains(clean) && sharedEmails.size < 9) {
                                sharedEmails.add(clean)
                                newEmailInput = ""
                            }
                        },
                        enabled = newEmailInput.contains("@"),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_share_email_button")
                    ) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = "Add caregiver")
                    }
                }
                if (sharedEmails.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sharedEmails.forEach { email ->
                            InputChip(
                                selected = true,
                                onClick = { sharedEmails.remove(email) },
                                label = { Text(email) },
                                trailingIcon = {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove $email", modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Pin medicine to top", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = pinned,
                    onCheckedChange = { pinned = it },
                    modifier = Modifier.testTag("pin_switch")
                )
            }

            Text("Pre-Dose Checks (${subtasks.size}/15)", style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newSubtaskTitle,
                    onValueChange = { if (it.length <= 120) newSubtaskTitle = it },
                    placeholder = { Text("e.g., Check BP or blood sugar first") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtask_input")
                )
                Button(
                    onClick = {
                        val trimmed = newSubtaskTitle.trim()
                        if (trimmed.isNotEmpty() && subtasks.size < 15) {
                            subtasks.add(
                                SubtaskItem(
                                    id = "sub_${UUID.randomUUID().toString().take(8)}",
                                    title = trimmed,
                                    completed = false
                                )
                            )
                            newSubtaskTitle = ""
                        }
                    },
                    enabled = newSubtaskTitle.isNotBlank() && subtasks.size < 15,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_subtask_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add check")
                }
            }

            subtasks.forEachIndexed { idx, step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• ${step.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { subtasks.removeAt(idx) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove check")
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) {
                        titleError = "Please enter a medicine name"
                        return@Button
                    }
                    val parsedStock = if (trackInventory) {
                        (stockCountText.toIntOrNull() ?: 30).coerceIn(0, 10000)
                    } else {
                        -1
                    }
                    val selectedProfileName = if (profileId == "self") {
                        "Self"
                    } else {
                        profiles.firstOrNull { it.id == profileId }?.name ?: "Self"
                    }
                    onSave(
                        ReminderDraft(
                            profileId = profileId,
                            profileName = selectedProfileName,
                            title = trimmed,
                            notes = notes.trim(),
                            dueAtMillis = dueAtMillis,
                            leadTimeMinutes = leadTimeMinutes,
                            priority = priority,
                            category = category,
                            recurrence = recurrence,
                            pinned = pinned,
                            urgentAlarm = urgentAlarm,
                            locationLabel = locationLabel.trim(),
                            latitude = latitude,
                            longitude = longitude,
                            radiusMeters = radiusMeters,
                            locationTriggerEnabled = locationTriggerEnabled,
                            dosage = dosage.trim().ifBlank { medForm.label },
                            medForm = medForm,
                            mealInstruction = mealInstruction,
                            prescribedBy = prescribedBy.trim(),
                            stockCount = parsedStock,
                            dosePerIntake = dosePerIntake,
                            refillThreshold = refillThreshold,
                            courseDays = courseDays,
                            courseStartMillis = if (courseDays > 0) {
                                editingReminder?.courseStartMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
                            } else {
                                0L
                            },
                            subtasks = subtasks.toList(),
                            sharedWithEmails = sharedEmails.toList()
                        )
                    )
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_reminder_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        when {
                            editingReminder != null -> "Update Medicine"
                            sharedEmails.isNotEmpty() -> "Save & Share with Caregiver"
                            else -> "Save & Schedule Dose"
                        }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtcMillis ->
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtcMillis
                            }
                            val currentCal = Calendar.getInstance().apply {
                                timeInMillis = dueAtMillis
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            }
                            dueAtMillis = currentCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pick Dose Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = Calendar.getInstance().apply {
                            timeInMillis = dueAtMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        dueAtMillis = updated.timeInMillis
                        showTimePicker = false
                    }
                ) { Text("Set Time") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}

private fun nextOccurrenceAt(hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= System.currentTimeMillis()) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}
