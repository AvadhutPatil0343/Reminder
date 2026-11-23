package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MedicineAiService
import com.example.ai.ScannedMedicineItem
import com.example.data.model.DoctorAppointment
import com.example.data.model.FamilyProfile
import com.example.data.model.MealInstruction
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderDraft
import com.example.data.model.ReminderPriority
import com.example.data.model.SharedReminder
import com.example.data.model.VitalRecord
import com.example.data.model.VitalType
import com.example.data.parser.SmartReminderParser
import com.example.data.preferences.AlertTone
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CareDataRepository
import com.example.data.repository.ReminderRepository
import com.example.notifications.AlarmScheduler
import com.example.notifications.GeofenceManager
import com.example.notifications.LocalAlarmStore
import com.example.notifications.NotificationHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ReminderFilterTab(val label: String) {
    ALL("All Meds"),
    OVERDUE("Due Now"),
    TODAY("Today"),
    REFILL_LOW("Low Stock"),
    PINNED("Pinned"),
    SHARED("Caregiver"),
    COMPLETED("History")
}

data class FocusSessionState(
    val title: String,
    val endTimeMillis: Long,
    val totalMinutes: Int
)

data class AiInfoRequest(
    val medicineName: String,
    val dosage: String
)

class RemindersViewModel(
    private val repository: ReminderRepository,
    private val careDataRepository: CareDataRepository,
    private val aiService: MedicineAiService,
    private val preferencesRepository: UserPreferencesRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager,
    private val localAlarmStore: LocalAlarmStore,
    private val currentUserId: String,
    private val currentUserEmail: String?
) : ViewModel() {

    val remindersState: StateFlow<UiState<List<Reminder>>> = repository.observeReminders(currentUserId)
        .onEach { reminders ->
            alarmScheduler.syncAlarms(reminders)
            geofenceManager.syncGeofences(reminders)
        }
        .map<List<Reminder>, UiState<List<Reminder>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing reminders", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load medicine schedule"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val sharedRemindersState: StateFlow<UiState<List<SharedReminder>>> = repository
        .observeSharedReminders(currentUserEmail)
        .map<List<SharedReminder>, UiState<List<SharedReminder>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing caregiver shared medicines", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load caregiver medicines"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val familyProfilesState: StateFlow<UiState<List<FamilyProfile>>> = careDataRepository
        .observeFamilyProfiles(currentUserId)
        .map<List<FamilyProfile>, UiState<List<FamilyProfile>>> { list -> UiState.Success(list) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing family profiles", error)
            emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load family profiles"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val vitalsState: StateFlow<UiState<List<VitalRecord>>> = careDataRepository
        .observeVitals(currentUserId)
        .map<List<VitalRecord>, UiState<List<VitalRecord>>> { list -> UiState.Success(list) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing health vitals", error)
            emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load health vitals"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val appointmentsState: StateFlow<UiState<List<DoctorAppointment>>> = careDataRepository
        .observeAppointments(currentUserId)
        .onEach { appts ->
            alarmScheduler.syncAppointments(appts)
        }
        .map<List<DoctorAppointment>, UiState<List<DoctorAppointment>>> { list -> UiState.Success(list) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing doctor appointments", error)
            emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load appointments"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .onEach { prefs ->
            localAlarmStore.saveAlertSettings(
                quietEnabled = prefs.quietHoursEnabled,
                quietStartHour = prefs.quietStartHour,
                quietEndHour = prefs.quietEndHour,
                alertTone = prefs.alertTone,
                vibrationEnabled = prefs.vibrationEnabled,
                appLanguage = prefs.appLanguage,
                voiceAlertsEnabled = prefs.voiceAlertsEnabled
            )
            alarmScheduler.scheduleMorningDigest(
                enabled = prefs.digestEnabled,
                hour = prefs.digestHour,
                minute = prefs.digestMinute
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UserPreferences()
        )

    val nowMillis: StateFlow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            val activeFocus = _focusSession.value
            if (activeFocus != null && now >= activeFocus.endTimeMillis) {
                _focusSession.value = null
            }
            delay(1000L)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = System.currentTimeMillis()
    )

    private val _selectedProfileId = MutableStateFlow("self")
    val selectedProfileId: StateFlow<String> = _selectedProfileId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ReminderFilterTab.ALL)
    val selectedFilter: StateFlow<ReminderFilterTab> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ReminderCategory?>(null)
    val selectedCategory: StateFlow<ReminderCategory?> = _selectedCategory.asStateFlow()

    private val _focusSession = MutableStateFlow<FocusSessionState?>(null)
    val focusSession: StateFlow<FocusSessionState?> = _focusSession.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _aiScanState = MutableStateFlow<UiState<List<ScannedMedicineItem>>?>(null)
    val aiScanState: StateFlow<UiState<List<ScannedMedicineItem>>?> = _aiScanState.asStateFlow()

    private val _aiInfoTarget = MutableStateFlow<AiInfoRequest?>(null)
    val aiInfoTarget: StateFlow<AiInfoRequest?> = _aiInfoTarget.asStateFlow()

    private val _aiInfoState = MutableStateFlow<UiState<String>?>(null)
    val aiInfoState: StateFlow<UiState<String>?> = _aiInfoState.asStateFlow()

    private var aiInfoJob: Job? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun selectProfile(profileId: String) {
        _selectedProfileId.value = profileId.ifBlank { "self" }
    }

    fun activeProfileName(): String {
        val pid = _selectedProfileId.value
        if (pid == "self") return "Self"
        val profiles = (familyProfilesState.value as? UiState.Success)?.data.orEmpty()
        return profiles.firstOrNull { it.id == pid }?.name ?: "Self"
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterTab(tab: ReminderFilterTab) {
        _selectedFilter.value = tab
    }

    fun toggleCategoryFilter(category: ReminderCategory?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun quickAddNaturalLanguage(rawInput: String) {
        if (rawInput.isBlank()) return
        val parsed = SmartReminderParser.parse(rawInput)
        val pid = _selectedProfileId.value
        val pname = activeProfileName()
        saveReminder(
            existing = null,
            draft = parsed.draft.copy(profileId = pid, profileName = pname)
        )
    }

    fun saveReminder(existing: Reminder?, draft: ReminderDraft, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            if (existing == null && draft.sharedWithEmails.isNotEmpty()) {
                val result = repository.createSharedReminder(draft)
                _isMutating.value = false
                result.onSuccess {
                    _messages.tryEmit("Caregiver medicine schedule shared")
                    onSaved()
                }.onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Unable to share medicine")
                }
            } else {
                val result = if (existing == null) {
                    repository.createReminder(draft)
                } else {
                    repository.updateReminder(existing, draft)
                }
                _isMutating.value = false
                result.onSuccess {
                    _messages.tryEmit(if (existing == null) "Medicine dose scheduled" else "Medicine updated")
                    onSaved()
                }.onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Unable to save medicine")
                }
            }
        }
    }

    fun toggleComplete(reminder: Reminder) {
        viewModelScope.launch {
            repository.toggleComplete(reminder)
                .onSuccess { outcome ->
                    alarmScheduler.cancelNotification(reminder.id)
                    when {
                        outcome.courseCompleted -> {
                            alarmScheduler.cancelAlarm(reminder.id)
                            _messages.tryEmit("Full ${reminder.courseDays}-day course completed for ${reminder.title}!")
                        }
                        outcome.lowStockTriggered -> {
                            _messages.tryEmit("Dose taken! Low stock for ${reminder.title} (${outcome.updatedStock} left)")
                        }
                        outcome.rescheduled -> {
                            _messages.tryEmit("Dose taken! Next ${reminder.recurrenceEnum.label.lowercase()} alert scheduled.")
                        }
                        !reminder.completed -> {
                            alarmScheduler.cancelAlarm(reminder.id)
                            _messages.tryEmit("Course completed")
                        }
                    }
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Update failed")
                }
        }
    }

    fun skipDose(reminder: Reminder) {
        viewModelScope.launch {
            repository.skipDose(reminder)
                .onSuccess { rescheduled ->
                    alarmScheduler.cancelNotification(reminder.id)
                    _messages.tryEmit(
                        if (rescheduled) "Dose skipped — next ${reminder.recurrenceEnum.label.lowercase()} dose scheduled"
                        else "Dose marked skipped"
                    )
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Skip failed")
                }
        }
    }

    fun refillStock(reminder: Reminder, addUnits: Int = 30) {
        viewModelScope.launch {
            repository.refillStock(reminder, addUnits)
                .onSuccess { updated ->
                    _messages.tryEmit("${reminder.title} refilled (+$addUnits, total $updated)")
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Refill update failed")
                }
        }
    }

    fun toggleSharedComplete(shared: SharedReminder) {
        viewModelScope.launch {
            repository.toggleSharedComplete(shared)
                .onSuccess { rescheduled ->
                    _messages.tryEmit(if (rescheduled) "Dose taken — next schedule updated" else "Caregiver dose updated")
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Update failed")
                }
        }
    }

    fun toggleSharedSubtask(shared: SharedReminder, subtaskId: String) {
        viewModelScope.launch {
            repository.toggleSharedSubtask(shared, subtaskId).onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Failed to update step")
            }
        }
    }

    fun deleteSharedReminder(sharedId: String) {
        viewModelScope.launch {
            repository.deleteSharedReminder(sharedId)
                .onSuccess { _messages.tryEmit("Caregiver medicine removed") }
                .onFailure { err -> _messages.tryEmit(err.localizedMessage ?: "Only owner can delete") }
        }
    }

    fun togglePin(reminder: Reminder) {
        viewModelScope.launch {
            repository.togglePin(reminder).onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Failed to update pin")
            }
        }
    }

    fun snoozeReminder(reminder: Reminder, minutes: Int) {
        viewModelScope.launch {
            repository.snoozeReminder(reminder.id, minutes)
                .onSuccess {
                    alarmScheduler.cancelNotification(reminder.id)
                    _messages.tryEmit("Dose snoozed for ${minutes}m")
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Failed to snooze")
                }
        }
    }

    fun toggleSubtask(reminder: Reminder, subtaskId: String) {
        viewModelScope.launch {
            repository.toggleSubtask(reminder, subtaskId).onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Failed to update step")
            }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
                .onSuccess {
                    alarmScheduler.cancelAlarm(reminderId)
                    _messages.tryEmit("Medicine removed")
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Delete failed")
                }
        }
    }

    fun clearCompleted(reminders: List<Reminder>) {
        val ids = reminders.filter { it.completed }.map { it.id }
        if (ids.isEmpty()) {
            _messages.tryEmit("No completed courses to clear")
            return
        }
        viewModelScope.launch {
            repository.clearCompleted(ids)
                .onSuccess { count -> _messages.tryEmit("Cleared $count completed medicines") }
                .onFailure { err -> _messages.tryEmit(err.localizedMessage ?: "Clear failed") }
        }
    }

    fun startFocusTimer(title: String, durationMinutes: Int = 25) {
        val endMillis = System.currentTimeMillis() + durationMinutes * 60_000L
        _focusSession.value = FocusSessionState(
            title = title,
            endTimeMillis = endMillis,
            totalMinutes = durationMinutes
        )
        alarmScheduler.scheduleFocusEnd(title, endMillis)
        _messages.tryEmit("Dose gap timer started for ${durationMinutes}m")
    }

    fun stopFocusTimer() {
        _focusSession.value = null
        alarmScheduler.cancelFocusTimer()
        _messages.tryEmit("Dose gap timer stopped")
    }

    suspend fun captureCurrentLocation(): Pair<Double, Double>? = geofenceManager.getCurrentCoordinates()

    fun scheduleTestNotification(delaySeconds: Int = 5, urgentAlarm: Boolean = false) {
        alarmScheduler.scheduleTestNotification(delaySeconds, urgentAlarm)
        _messages.tryEmit(
            if (urgentAlarm) "Critical medicine alarm scheduled in ${delaySeconds}s"
            else "Dose notification scheduled in ${delaySeconds}s"
        )
    }

    fun fireInstantTestNotification(context: Context) {
        NotificationHelper.showReminderNotification(
            context = context,
            reminderId = "test_instant_${System.currentTimeMillis()}",
            title = "Dolo 650mg (Sample Dose)",
            notes = "Take with warm water • Real-time medicine alerts are ready.",
            priorityRaw = ReminderPriority.URGENT.name,
            categoryRaw = ReminderCategory.DAILY_MEDS.name,
            recurrenceRaw = "DAILY",
            dosage = "1 Tablet",
            mealInstructionRaw = MealInstruction.AFTER_FOOD.name
        )
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDefaultSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setDefaultSnoozeMinutes(minutes) }
    }

    fun setDefaultLeadTimeMinutes(minutes: Long) {
        viewModelScope.launch { preferencesRepository.setDefaultLeadTimeMinutes(minutes) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVibrationEnabled(enabled) }
    }

    fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) {
        viewModelScope.launch { preferencesRepository.setQuietHours(enabled, startHour, endHour) }
    }

    fun setDigestSettings(enabled: Boolean, hour: Int) {
        viewModelScope.launch { preferencesRepository.setDigestSettings(enabled, hour, 0) }
    }

    fun setAlertTone(tone: AlertTone) {
        viewModelScope.launch { preferencesRepository.setAlertTone(tone) }
    }

    fun setAppLanguage(languageCode: String) {
        viewModelScope.launch { preferencesRepository.setAppLanguage(languageCode) }
    }

    fun setVoiceAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVoiceAlertsEnabled(enabled) }
    }

    fun setEmergencyContact(name: String, phone: String) {
        viewModelScope.launch {
            preferencesRepository.setEmergencyContact(name, phone)
            _messages.tryEmit("Emergency contact saved")
        }
    }

    // Family Profiles
    fun saveFamilyProfile(
        existingId: String?,
        name: String,
        relation: String,
        doctorName: String,
        doctorPhone: String,
        emergencyPhone: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = careDataRepository.saveFamilyProfile(
                existingId = existingId,
                name = name,
                relation = relation,
                doctorName = doctorName,
                doctorPhone = doctorPhone,
                emergencyPhone = emergencyPhone
            )
            _isMutating.value = false
            res.onSuccess { profile ->
                _selectedProfileId.value = profile.id
                _messages.tryEmit("${profile.name} profile saved")
                onDone()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Failed to save family profile")
            }
        }
    }

    fun deleteFamilyProfile(profileId: String) {
        viewModelScope.launch {
            careDataRepository.deleteFamilyProfile(profileId)
                .onSuccess {
                    if (_selectedProfileId.value == profileId) {
                        _selectedProfileId.value = "self"
                    }
                    _messages.tryEmit("Family profile removed")
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Unable to remove profile")
                }
        }
    }

    // Health Vitals
    fun logVital(
        type: VitalType,
        valuePrimary: Double,
        valueSecondary: Double,
        notes: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = careDataRepository.logVital(
                profileId = _selectedProfileId.value,
                type = type,
                valuePrimary = valuePrimary,
                valueSecondary = valueSecondary,
                notes = notes
            )
            _isMutating.value = false
            res.onSuccess {
                _messages.tryEmit("${type.label} reading logged")
                onDone()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Failed to save vital")
            }
        }
    }

    fun deleteVital(vitalId: String) {
        viewModelScope.launch {
            careDataRepository.deleteVital(vitalId)
                .onSuccess { _messages.tryEmit("Reading deleted") }
                .onFailure { err -> _messages.tryEmit(err.localizedMessage ?: "Delete failed") }
        }
    }

    // Doctor Appointments
    fun saveAppointment(
        doctorName: String,
        specialty: String,
        clinicName: String,
        clinicPhone: String,
        appointmentAtMillis: Long,
        notes: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = careDataRepository.saveAppointment(
                profileId = _selectedProfileId.value,
                doctorName = doctorName,
                specialty = specialty,
                clinicName = clinicName,
                clinicPhone = clinicPhone,
                appointmentAtMillis = appointmentAtMillis,
                notes = notes
            )
            _isMutating.value = false
            res.onSuccess {
                _messages.tryEmit("Doctor appointment scheduled")
                onDone()
            }.onFailure { err ->
                _messages.tryEmit(err.localizedMessage ?: "Failed to save appointment")
            }
        }
    }

    fun toggleAppointmentComplete(appt: DoctorAppointment) {
        viewModelScope.launch {
            careDataRepository.toggleAppointmentComplete(appt)
                .onSuccess { alarmScheduler.cancelAlarm("appt_${appt.id}") }
                .onFailure { err -> _messages.tryEmit(err.localizedMessage ?: "Update failed") }
        }
    }

    fun deleteAppointment(apptId: String) {
        viewModelScope.launch {
            careDataRepository.deleteAppointment(apptId)
                .onSuccess {
                    alarmScheduler.cancelAlarm("appt_$apptId")
                    _messages.tryEmit("Appointment removed")
                }
                .onFailure { err -> _messages.tryEmit(err.localizedMessage ?: "Delete failed") }
        }
    }

    // AI Prescription & Strip Scanner
    fun scanPrescriptionBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _aiScanState.value = UiState.Loading
            try {
                val items = aiService.scanPrescriptionOrStrip(bitmap)
                if (items.isEmpty()) {
                    _aiScanState.value = UiState.Error("No medicine names detected in this photo. Try a closer, well-lit image.")
                } else {
                    _aiScanState.value = UiState.Success(items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiScanState.value = UiState.Error(e.localizedMessage ?: "AI prescription scan failed. Check internet and try again.")
            }
        }
    }

    fun toggleScannedItem(itemId: String) {
        val current = (_aiScanState.value as? UiState.Success)?.data ?: return
        _aiScanState.value = UiState.Success(
            current.map { if (it.id == itemId) it.copy(selected = !it.selected) else it }
        )
    }

    fun importScannedMedicines(onComplete: () -> Unit) {
        val current = (_aiScanState.value as? UiState.Success)?.data ?: return
        val chosen = current.filter { it.selected }
        if (chosen.isEmpty()) return
        val pid = _selectedProfileId.value
        val pname = activeProfileName()

        viewModelScope.launch {
            _isMutating.value = true
            var addedCount = 0
            chosen.forEach { item ->
                val result = repository.createReminder(item.toReminderDraft(pid, pname))
                if (result.isSuccess) addedCount += 1
            }
            _isMutating.value = false
            if (addedCount > 0) {
                _aiScanState.value = null
                _messages.tryEmit("Added $addedCount scanned medicine(s) to $pname")
                onComplete()
            } else {
                _messages.tryEmit("Could not save scanned medicines. Check connection.")
            }
        }
    }

    fun clearAiScan() {
        _aiScanState.value = null
    }

    // AI Medicine Info Guide
    fun explainMedicine(medicineName: String, dosage: String, languageCode: String) {
        aiInfoJob?.cancel()
        _aiInfoTarget.value = AiInfoRequest(medicineName, dosage)
        _aiInfoState.value = UiState.Loading
        aiInfoJob = viewModelScope.launch {
            try {
                aiService.explainMedicineInfo(medicineName, dosage, languageCode).collect { text ->
                    _aiInfoState.value = UiState.Success(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiInfoState.value = UiState.Error(
                    e.localizedMessage ?: "Could not load AI medicine guide. Check internet and try again."
                )
            }
        }
    }

    fun clearAiInfo() {
        aiInfoJob?.cancel()
        _aiInfoTarget.value = null
        _aiInfoState.value = null
    }

    private companion object {
        const val TAG = "RemindersVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
