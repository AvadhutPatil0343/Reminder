package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DoctorAppointment
import com.example.data.model.FamilyProfile
import com.example.data.model.Reminder
import com.example.data.model.VitalRecord
import com.example.data.model.VitalType
import com.example.ui.components.ProfileSwitcherBar
import com.example.ui.components.formatExactDateTime
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityUrgentColor
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun VitalsAndCareScreen(
    profilesState: UiState<List<FamilyProfile>>,
    selectedProfileId: String,
    activeProfileName: String,
    vitalsState: UiState<List<VitalRecord>>,
    appointmentsState: UiState<List<DoctorAppointment>>,
    remindersState: UiState<List<Reminder>>,
    emergencyContactName: String,
    emergencyContactPhone: String,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (String?, String, String, String, String, String, () -> Unit) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onLogVital: (VitalType, Double, Double, String, () -> Unit) -> Unit,
    onDeleteVital: (String) -> Unit,
    onSaveAppointment: (String, String, String, String, Long, String, () -> Unit) -> Unit,
    onToggleAppointmentComplete: (DoctorAppointment) -> Unit,
    onDeleteAppointment: (String) -> Unit,
    onSharePdfReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    val profiles = (profilesState as? UiState.Success)?.data ?: emptyList()
    val allVitals = (vitalsState as? UiState.Success)?.data ?: emptyList()
    val allAppointments = (appointmentsState as? UiState.Success)?.data ?: emptyList()
    val allReminders = (remindersState as? UiState.Success)?.data ?: emptyList()

    val profileVitals = remember(allVitals, selectedProfileId) {
        allVitals.filter { it.profileId == selectedProfileId }
    }
    val profileAppointments = remember(allAppointments, selectedProfileId) {
        allAppointments.filter { it.profileId == selectedProfileId }
    }
    val profileMedsCount = remember(allReminders, selectedProfileId) {
        allReminders.count { it.profileId == selectedProfileId && !it.completed }
    }

    var showLogVitalDialog by remember { mutableStateOf(false) }
    var showAddApptDialog by remember { mutableStateOf(false) }
    var chartType by remember { mutableStateOf(VitalType.BP) }

    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
    val sosPhone = selectedProfile?.emergencyPhone?.takeIf { it.isNotBlank() }
        ?: selectedProfile?.doctorPhone?.takeIf { it.isNotBlank() }
        ?: emergencyContactPhone
    val sosLabel = selectedProfile?.doctorName?.takeIf { it.isNotBlank() }
        ?: emergencyContactName.ifBlank { strings.emergencySosLabel }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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

        // Top Actions: Export PDF + SOS Call
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "$activeProfileName • $profileMedsCount active meds",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onSharePdfReport,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("export_doctor_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.exportPdfBtn)
                        }
                        if (sosPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sosPhone")))
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("care_sos_call_button")
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(sosLabel.take(14))
                            }
                        }
                    }
                }
            }
        }

        // Vitals Header + Trend Chart
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.vitalsSectionTitle, style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = { showLogVitalDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("log_vital_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.logVitalBtn)
                }
            }
        }

        item {
            val chartReadings = remember(profileVitals, chartType) {
                profileVitals
                    .filter { it.typeEnum == chartType }
                    .sortedBy { it.recordedAtMillis }
                    .takeLast(10)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(VitalType.BP, VitalType.SUGAR_FASTING, VitalType.SUGAR_PP, VitalType.SPO2).forEach { vt ->
                            FilterChip(
                                selected = chartType == vt,
                                onClick = { chartType = vt },
                                label = { Text(vt.label.substringBefore(" ")) }
                            )
                        }
                    }

                    if (chartReadings.size < 2) {
                        Text(
                            text = if (chartReadings.isEmpty()) {
                                "No ${chartType.label} readings logged yet. Tap '${strings.logVitalBtn}' to start tracking."
                            } else {
                                "Latest ${chartType.label}: ${chartReadings.last().formattedValue()} — log one more reading to plot trend chart."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val secondaryColor = PriorityHighColor
                        val gridColor = MaterialTheme.colorScheme.outlineVariant
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            val pad = 16.dp.toPx()
                            val w = size.width - pad * 2
                            val h = size.height - pad * 2
                            val values = chartReadings.flatMap {
                                if (chartType == VitalType.BP) listOf(it.valuePrimary, it.valueSecondary) else listOf(it.valuePrimary)
                            }
                            val minVal = ((values.minOrNull() ?: 60.0) - 10.0).coerceAtLeast(0.0)
                            val maxVal = ((values.maxOrNull() ?: 140.0) + 10.0).coerceAtLeast(minVal + 10.0)
                            val span = maxVal - minVal

                            drawLine(gridColor, Offset(pad, pad), Offset(size.width - pad, pad), strokeWidth = 1.dp.toPx())
                            drawLine(gridColor, Offset(pad, pad + h), Offset(size.width - pad, pad + h), strokeWidth = 1.dp.toPx())

                            val pathPrimary = Path()
                            val pathSecondary = Path()
                            chartReadings.forEachIndexed { idx, rec ->
                                val x = pad + (w * idx / (chartReadings.size - 1).coerceAtLeast(1))
                                val y1 = pad + h - (((rec.valuePrimary - minVal) / span).toFloat() * h)
                                if (idx == 0) pathPrimary.moveTo(x, y1) else pathPrimary.lineTo(x, y1)
                                drawCircle(primaryColor, radius = 4.dp.toPx(), center = Offset(x, y1))

                                if (chartType == VitalType.BP && rec.valueSecondary > 0) {
                                    val y2 = pad + h - (((rec.valueSecondary - minVal) / span).toFloat() * h)
                                    if (idx == 0) pathSecondary.moveTo(x, y2) else pathSecondary.lineTo(x, y2)
                                    drawCircle(secondaryColor, radius = 4.dp.toPx(), center = Offset(x, y2))
                                }
                            }
                            drawPath(pathPrimary, primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                            if (chartType == VitalType.BP) {
                                drawPath(pathSecondary, secondaryColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                        val latest = chartReadings.last()
                        Text(
                            text = "Latest: ${latest.formattedValue()} (${chartReadings.size} readings shown)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (vitalsState is UiState.Loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (profileVitals.isNotEmpty()) {
            val fmt = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
            items(profileVitals.take(12), key = { it.id }) { vital ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${vital.typeEnum.label}: ${vital.formattedValue()}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = fmt.format(Date(vital.recordedAtMillis)) +
                                    if (vital.notes.isNotBlank()) " • ${vital.notes}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDeleteVital(vital.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete vital", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Doctor Appointments Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.appointmentsSectionTitle, style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { showAddApptDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("add_appointment_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(strings.addAppointmentBtn)
                }
            }
        }

        if (profileAppointments.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No upcoming doctor visits for $activeProfileName. Add appointments for clinic alerts & direct calling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(profileAppointments, key = { it.id }) { appt ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (appt.completed) {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appt.doctorName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                val detail = listOf(appt.specialty, appt.clinicName)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = formatExactDateTime(appt.appointmentAtMillis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (appt.completed) SuccessEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = { onToggleAppointmentComplete(appt) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Mark visit complete",
                                        tint = if (appt.completed) SuccessEmerald else MaterialTheme.colorScheme.outline
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteAppointment(appt.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete appointment",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        if (appt.notes.isNotBlank()) {
                            Text(
                                text = appt.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (appt.clinicPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${appt.clinicPhone}")))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${strings.callDoctorBtn}: ${appt.clinicPhone}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogVitalDialog) {
        LogVitalDialog(
            onDismiss = { showLogVitalDialog = false },
            onSave = { type, v1, v2, notes ->
                onLogVital(type, v1, v2, notes) { showLogVitalDialog = false }
            }
        )
    }

    if (showAddApptDialog) {
        AddAppointmentDialog(
            onDismiss = { showAddApptDialog = false },
            onSave = { docName, spec, clinic, phone, millis, notes ->
                onSaveAppointment(docName, spec, clinic, phone, millis, notes) {
                    showAddApptDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogVitalDialog(
    onDismiss: () -> Unit,
    onSave: (VitalType, Double, Double, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(VitalType.BP) }
    var primaryText by remember { mutableStateOf("") }
    var secondaryText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Health Vital") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VitalType.entries.forEach { vt ->
                        FilterChip(
                            selected = selectedType == vt,
                            onClick = {
                                selectedType = vt
                                errorMsg = null
                            },
                            label = { Text(vt.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it; errorMsg = null },
                    label = {
                        Text(
                            if (selectedType == VitalType.BP) "Systolic (Upper, e.g. 120)"
                            else "${selectedType.label} (${selectedType.defaultUnit})"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("vital_primary_input")
                )
                if (selectedType == VitalType.BP) {
                    OutlinedTextField(
                        value = secondaryText,
                        onValueChange = { secondaryText = it; errorMsg = null },
                        label = { Text("Diastolic (Lower, e.g. 80)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("vital_secondary_input")
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 200) notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = PriorityUrgentColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v1 = primaryText.toDoubleOrNull()
                    val v2 = if (selectedType == VitalType.BP) secondaryText.toDoubleOrNull() else 0.0
                    if (v1 == null || v1 <= 0.0 || (selectedType == VitalType.BP && (v2 == null || v2 <= 0.0))) {
                        errorMsg = "Enter valid numeric values"
                        return@Button
                    }
                    onSave(selectedType, v1, v2 ?: 0.0, notes)
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("save_vital_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppointmentDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Long, String) -> Unit
) {
    var doctorName by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var clinicName by remember { mutableStateOf("") }
    var clinicPhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var apptMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 86_400_000L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Doctor Appointment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = doctorName,
                    onValueChange = { if (it.length <= 100) doctorName = it; errorMsg = null },
                    label = { Text("Doctor Name (e.g. Dr. Deshmukh)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("appt_doctor_input")
                )
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { if (it.length <= 80) specialty = it },
                    label = { Text("Specialty (Physician, Cardio...)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clinicName,
                    onValueChange = { if (it.length <= 100) clinicName = it },
                    label = { Text("Hospital / Clinic") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clinicPhone,
                    onValueChange = { if (it.length <= 30) clinicPhone = it },
                    label = { Text("Clinic Phone (for 1-tap call)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Date")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Time")
                    }
                }
                Text(
                    text = formatExactDateTime(apptMillis),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 300) notes = it },
                    label = { Text("Questions / Lab reports to bring") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = doctorName.trim()
                    if (trimmed.isEmpty()) {
                        errorMsg = "Enter doctor name"
                        return@Button
                    }
                    onSave(trimmed, specialty, clinicName, clinicPhone, apptMillis, notes)
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("save_appointment_button")
            ) {
                Text("Schedule Visit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = apptMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { picked ->
                            val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = picked
                            }
                            val target = Calendar.getInstance().apply {
                                timeInMillis = apptMillis
                                set(Calendar.YEAR, utc.get(Calendar.YEAR))
                                set(Calendar.MONTH, utc.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
                            }
                            apptMillis = target.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = apptMillis }
        val timeState = rememberTimePickerState(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Appointment Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = Calendar.getInstance().apply {
                            timeInMillis = apptMillis
                            set(Calendar.HOUR_OF_DAY, timeState.hour)
                            set(Calendar.MINUTE, timeState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        apptMillis = updated.timeInMillis
                        showTimePicker = false
                    }
                ) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        )
    }
}
