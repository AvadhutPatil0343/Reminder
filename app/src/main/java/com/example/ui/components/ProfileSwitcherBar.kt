package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.FamilyProfile
import com.example.ui.i18n.LocalAppStrings

@Composable
fun ProfileSwitcherBar(
    profiles: List<FamilyProfile>,
    selectedProfileId: String,
    onSelectProfile: (String) -> Unit,
    onSaveProfile: (
        existingId: String?,
        name: String,
        relation: String,
        doctorName: String,
        doctorPhone: String,
        emergencyPhone: String,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
    val dialNumber = selectedProfile?.doctorPhone?.takeIf { it.isNotBlank() }
        ?: selectedProfile?.emergencyPhone?.takeIf { it.isNotBlank() }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    FilterChip(
                        selected = selectedProfileId == "self",
                        onClick = { onSelectProfile("self") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(strings.profileSelf) },
                        modifier = Modifier.testTag("profile_chip_self")
                    )
                }
                items(profiles, key = { it.id }) { profile ->
                    FilterChip(
                        selected = selectedProfileId == profile.id,
                        onClick = { onSelectProfile(profile.id) },
                        label = {
                            Text(
                                if (profile.relation.isNotBlank() && profile.relation != "Family") {
                                    "${profile.name} (${profile.relation})"
                                } else {
                                    profile.name
                                }
                            )
                        },
                        trailingIcon = if (selectedProfileId == profile.id) {
                            {
                                IconButton(
                                    onClick = { onDeleteProfile(profile.id) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove ${profile.name}",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier.testTag("profile_chip_${profile.id}")
                    )
                }
                item {
                    AssistChip(
                        onClick = { showAddDialog = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(strings.addMemberLabel) },
                        modifier = Modifier.testTag("add_family_profile_chip")
                    )
                }
            }

            if (!dialNumber.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialNumber"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("call_profile_doctor_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "Call ${selectedProfile?.doctorName?.ifBlank { selectedProfile.name } ?: "Doctor"}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddFamilyProfileDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, relation, doctorName, doctorPhone, emergencyPhone ->
                onSaveProfile(null, name, relation, doctorName, doctorPhone, emergencyPhone) {
                    showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFamilyProfileDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        relation: String,
        doctorName: String,
        doctorPhone: String,
        emergencyPhone: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Mother") }
    var doctorName by remember { mutableStateOf("") }
    var doctorPhone by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Family Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 60) {
                            name = it
                            nameError = null
                        }
                    },
                    label = { Text("Member Name (e.g., Aai, Baba)") },
                    isError = nameError != null,
                    supportingText = nameError?.let { err -> { Text(err) } },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("family_name_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Mother", "Father", "Spouse", "Child", "Grandparent").forEach { rel ->
                        FilterChip(
                            selected = relation == rel,
                            onClick = { relation = rel },
                            label = { Text(rel) }
                        )
                    }
                }
                OutlinedTextField(
                    value = doctorName,
                    onValueChange = { if (it.length <= 80) doctorName = it },
                    label = { Text("Doctor Name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = doctorPhone,
                    onValueChange = { if (it.length <= 30) doctorPhone = it },
                    label = { Text("Doctor Phone (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emergencyPhone,
                    onValueChange = { if (it.length <= 30) emergencyPhone = it },
                    label = { Text("Emergency Phone (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = "Please enter member name"
                        return@Button
                    }
                    onSave(trimmed, relation, doctorName.trim(), doctorPhone.trim(), emergencyPhone.trim())
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_family_profile_button")
            ) {
                Text("Save Member")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
