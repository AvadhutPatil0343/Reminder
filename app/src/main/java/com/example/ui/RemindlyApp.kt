package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.MedicineAiService
import com.example.auth.signOut
import com.example.data.model.MealInstruction
import com.example.data.model.Reminder
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CareDataRepository
import com.example.data.repository.ReminderRepository
import com.example.notifications.AlarmScheduler
import com.example.notifications.DoseSpeaker
import com.example.notifications.GeofenceManager
import com.example.notifications.LocalAlarmStore
import com.example.notifications.NotificationHelper
import com.example.report.PdfReportGenerator
import com.example.ui.components.MedicineInfoSheet
import com.example.ui.components.PrescriptionScannerSheet
import com.example.ui.components.ReminderEditorSheet
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.VitalsAndCareScreen
import com.example.ui.theme.RemindlyTheme
import com.example.ui.viewmodel.RemindersViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class RemindlyTab(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TODAY(Icons.Rounded.Medication, Icons.Outlined.Medication),
    SCHEDULE(Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    CARE(Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder),
    STATS(Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    SETTINGS(Icons.Rounded.Settings, Icons.Outlined.Settings);

    fun labelFor(strings: com.example.ui.i18n.AppStrings): String = when (this) {
        TODAY -> strings.tabToday
        SCHEDULE -> strings.tabSchedule
        CARE -> strings.tabCare
        STATS -> strings.tabAdherence
        SETTINGS -> strings.tabSettings
    }
}

@Composable
fun RemindlyRootApp(
    highlightedReminderId: String? = null,
    openQuickAddToken: Int = 0
) {
    val context = LocalContext.current
    val prefsRepo = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val preferences by prefsRepo.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    CompositionLocalProvider(LocalAppStrings provides stringsForLanguage(preferences.appLanguage)) {
        RemindlyTheme(themeMode = preferences.themeMode) {
            val user = currentUser
            if (user == null) {
                AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
            } else {
                key(user.uid) {
                    AuthenticatedRemindlyShell(
                        currentUserId = user.uid,
                        userEmail = user.email,
                        userDisplayName = user.displayName,
                        highlightedReminderId = highlightedReminderId,
                        openQuickAddToken = openQuickAddToken
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedRemindlyShell(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
    highlightedReminderId: String?,
    openQuickAddToken: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val alarmScheduler = remember(context) { AlarmScheduler(context) }
    val doseSpeaker = remember(context) { DoseSpeaker(context) }

    DisposableEffect(doseSpeaker) {
        onDispose { doseSpeaker.shutdown() }
    }

    val viewModel: RemindersViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RemindersViewModel(
                    repository = ReminderRepository(db),
                    careDataRepository = CareDataRepository(db),
                    aiService = MedicineAiService(),
                    preferencesRepository = UserPreferencesRepository(app),
                    alarmScheduler = AlarmScheduler(app),
                    geofenceManager = GeofenceManager(app),
                    localAlarmStore = LocalAlarmStore(app),
                    currentUserId = currentUserId,
                    currentUserEmail = userEmail
                )
            }
        }
    )

    val uiState by viewModel.remindersState.collectAsStateWithLifecycle()
    val sharedRemindersState by viewModel.sharedRemindersState.collectAsStateWithLifecycle()
    val profilesState by viewModel.familyProfilesState.collectAsStateWithLifecycle()
    val vitalsState by viewModel.vitalsState.collectAsStateWithLifecycle()
    val appointmentsState by viewModel.appointmentsState.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val focusSession by viewModel.focusSession.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val aiScanState by viewModel.aiScanState.collectAsStateWithLifecycle()
    val aiInfoTarget by viewModel.aiInfoTarget.collectAsStateWithLifecycle()
    val aiInfoState by viewModel.aiInfoState.collectAsStateWithLifecycle()

    val strings = stringsForLanguage(preferences.appLanguage)
    val profiles = (profilesState as? UiState.Success)?.data ?: emptyList()
    val activeProfileName = remember(selectedProfileId, profiles) {
        if (selectedProfileId == "self") "Self"
        else profiles.firstOrNull { it.id == selectedProfileId }?.name ?: "Self"
    }

    var currentTab by remember { mutableStateOf(RemindlyTab.TODAY) }
    var showEditor by remember { mutableStateOf(false) }
    var showScannerSheet by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openQuickAddToken) {
        if (openQuickAddToken > 0) {
            currentTab = RemindlyTab.TODAY
            editingReminder = null
            showEditor = true
        }
    }

    LaunchedEffect(Unit) {
        NotificationHelper.ensureNotificationChannels(context)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var canScheduleExactAlarms by remember {
        mutableStateOf(alarmScheduler.canScheduleExactAlarms())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = NotificationHelper.hasNotificationPermission(context)
                canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || NotificationHelper.hasNotificationPermission(context)
    }

    LaunchedEffect(currentUserId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val openExactAlarmSettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    val openSystemNotificationSettings = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    val shareDoctorPdfReport = {
        try {
            val profileMeds = ((uiState as? UiState.Success)?.data ?: emptyList())
                .filter { it.profileId == selectedProfileId }
            val profileVitals = ((vitalsState as? UiState.Success)?.data ?: emptyList())
                .filter { it.profileId == selectedProfileId }
            val profileAppts = ((appointmentsState as? UiState.Success)?.data ?: emptyList())
                .filter { it.profileId == selectedProfileId }
            val file = PdfReportGenerator.generateAndShareReport(
                context = context,
                patientName = activeProfileName,
                reminders = profileMeds,
                vitals = profileVitals,
                appointments = profileAppts
            )
            PdfReportGenerator.sharePdfFile(context, file)
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar(e.localizedMessage ?: "Failed to generate PDF report")
            }
        }
    }

    if (currentTab != RemindlyTab.TODAY) {
        BackHandler { currentTab = RemindlyTab.TODAY }
    }

    val completedCount = (uiState as? UiState.Success)?.data?.count { it.completed } ?: 0

    CompositionLocalProvider(LocalAppStrings provides strings) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    NavigationRail {
                        RemindlyTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            val label = tab.labelFor(strings)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (currentTab) {
                                        RemindlyTab.TODAY -> strings.titleToday
                                        RemindlyTab.SCHEDULE -> strings.titleSchedule
                                        RemindlyTab.CARE -> strings.titleCare
                                        RemindlyTab.STATS -> strings.titleAdherence
                                        RemindlyTab.SETTINGS -> strings.titleSettings
                                    },
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    floatingActionButton = {
                        if (currentTab == RemindlyTab.TODAY || currentTab == RemindlyTab.SCHEDULE) {
                            FloatingActionButton(
                                onClick = {
                                    editingReminder = null
                                    showEditor = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("add_reminder_fab")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = strings.addMedicine)
                            }
                        }
                    },
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar {
                                RemindlyTab.entries.forEach { tab ->
                                    val selected = currentTab == tab
                                    val label = tab.labelFor(strings)
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    when (currentTab) {
                        RemindlyTab.TODAY -> TodayScreen(
                            uiState = uiState,
                            sharedRemindersState = sharedRemindersState,
                            profilesState = profilesState,
                            selectedProfileId = selectedProfileId,
                            currentUserId = currentUserId,
                            nowMillis = nowMillis,
                            focusSession = focusSession,
                            searchQuery = searchQuery,
                            selectedFilter = selectedFilter,
                            selectedCategory = selectedCategory,
                            defaultSnoozeMinutes = preferences.defaultSnoozeMinutes,
                            emergencyContactName = preferences.emergencyContactName,
                            emergencyContactPhone = preferences.emergencyContactPhone,
                            highlightedReminderId = highlightedReminderId,
                            hasNotificationPermission = hasNotificationPermission,
                            canScheduleExactAlarms = canScheduleExactAlarms,
                            onSelectProfile = viewModel::selectProfile,
                            onSaveProfile = viewModel::saveFamilyProfile,
                            onDeleteProfile = viewModel::deleteFamilyProfile,
                            onOpenPrescriptionScanner = {
                                viewModel.clearAiScan()
                                showScannerSheet = true
                            },
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onFilterTabSelect = viewModel::setFilterTab,
                            onCategoryToggle = viewModel::toggleCategoryFilter,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openSystemNotificationSettings()
                                }
                            },
                            onOpenExactAlarmSettings = openExactAlarmSettings,
                            onScheduleTestAlert = { viewModel.scheduleTestNotification(5) },
                            onQuickAddNaturalLanguage = viewModel::quickAddNaturalLanguage,
                            onQuickAddFeedback = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            onStopFocus = viewModel::stopFocusTimer,
                            onStartFocus = { rem -> viewModel.startFocusTimer(rem.title, 30) },
                            onToggleComplete = viewModel::toggleComplete,
                            onSkipDose = viewModel::skipDose,
                            onRefillStock = { rem -> viewModel.refillStock(rem, 30) },
                            onTogglePin = viewModel::togglePin,
                            onSnooze = viewModel::snoozeReminder,
                            onToggleSubtask = viewModel::toggleSubtask,
                            onSpeakDose = { rem ->
                                doseSpeaker.speakDose(
                                    title = rem.title,
                                    dosage = rem.dosage,
                                    mealInstructionRaw = rem.mealInstruction,
                                    languageCode = preferences.appLanguage
                                )
                            },
                            onAskAiInfo = { rem ->
                                viewModel.explainMedicine(rem.title, rem.dosage, preferences.appLanguage)
                            },
                            onEdit = { rem ->
                                editingReminder = rem
                                showEditor = true
                            },
                            onDelete = viewModel::deleteReminder,
                            onToggleSharedComplete = viewModel::toggleSharedComplete,
                            onToggleSharedSubtask = viewModel::toggleSharedSubtask,
                            onDeleteShared = viewModel::deleteSharedReminder,
                            onCreateNew = {
                                editingReminder = null
                                showEditor = true
                            },
                            modifier = Modifier.padding(innerPadding)
                        )

                        RemindlyTab.SCHEDULE -> ScheduleScreen(
                            uiState = uiState,
                            selectedProfileId = selectedProfileId,
                            nowMillis = nowMillis,
                            defaultSnoozeMinutes = preferences.defaultSnoozeMinutes,
                            onToggleComplete = viewModel::toggleComplete,
                            onSkipDose = viewModel::skipDose,
                            onRefillStock = { rem -> viewModel.refillStock(rem, 30) },
                            onTogglePin = viewModel::togglePin,
                            onSnooze = viewModel::snoozeReminder,
                            onStartFocus = { rem -> viewModel.startFocusTimer(rem.title, 30) },
                            onToggleSubtask = viewModel::toggleSubtask,
                            onSpeakDose = { rem ->
                                doseSpeaker.speakDose(
                                    title = rem.title,
                                    dosage = rem.dosage,
                                    mealInstructionRaw = rem.mealInstruction,
                                    languageCode = preferences.appLanguage
                                )
                            },
                            onAskAiInfo = { rem ->
                                viewModel.explainMedicine(rem.title, rem.dosage, preferences.appLanguage)
                            },
                            onEdit = { rem ->
                                editingReminder = rem
                                showEditor = true
                            },
                            onDelete = viewModel::deleteReminder,
                            modifier = Modifier.padding(innerPadding)
                        )

                        RemindlyTab.CARE -> VitalsAndCareScreen(
                            profilesState = profilesState,
                            selectedProfileId = selectedProfileId,
                            activeProfileName = activeProfileName,
                            vitalsState = vitalsState,
                            appointmentsState = appointmentsState,
                            remindersState = uiState,
                            emergencyContactName = preferences.emergencyContactName,
                            emergencyContactPhone = preferences.emergencyContactPhone,
                            onSelectProfile = viewModel::selectProfile,
                            onSaveProfile = viewModel::saveFamilyProfile,
                            onDeleteProfile = viewModel::deleteFamilyProfile,
                            onLogVital = viewModel::logVital,
                            onDeleteVital = viewModel::deleteVital,
                            onSaveAppointment = viewModel::saveAppointment,
                            onToggleAppointmentComplete = viewModel::toggleAppointmentComplete,
                            onDeleteAppointment = viewModel::deleteAppointment,
                            onSharePdfReport = { shareDoctorPdfReport() },
                            modifier = Modifier.padding(innerPadding)
                        )

                        RemindlyTab.STATS -> StatsScreen(
                            uiState = uiState,
                            selectedProfileId = selectedProfileId,
                            activeProfileName = activeProfileName,
                            nowMillis = nowMillis,
                            hasNotificationPermission = hasNotificationPermission,
                            canScheduleExactAlarms = canScheduleExactAlarms,
                            onRefillStock = { rem -> viewModel.refillStock(rem, 30) },
                            onSharePdfReport = { shareDoctorPdfReport() },
                            onSendInstantTest = { viewModel.fireInstantTestNotification(context) },
                            onSchedule5sTest = { viewModel.scheduleTestNotification(5) },
                            onScheduleUrgentTest = { viewModel.scheduleTestNotification(5, urgentAlarm = true) },
                            modifier = Modifier.padding(innerPadding)
                        )

                        RemindlyTab.SETTINGS -> SettingsScreen(
                            userEmail = userEmail,
                            userDisplayName = userDisplayName,
                            preferences = preferences,
                            completedCount = completedCount,
                            onLanguageChange = viewModel::setAppLanguage,
                            onVoiceAlertsChange = viewModel::setVoiceAlertsEnabled,
                            onTestVoiceAlert = {
                                doseSpeaker.speakDose(
                                    title = "Dolo 650mg",
                                    dosage = "1 Tablet",
                                    mealInstructionRaw = MealInstruction.AFTER_FOOD.name,
                                    languageCode = preferences.appLanguage
                                )
                            },
                            onSaveEmergencyContact = viewModel::setEmergencyContact,
                            onThemeModeChange = viewModel::setThemeMode,
                            onDefaultSnoozeChange = viewModel::setDefaultSnoozeMinutes,
                            onDefaultLeadTimeChange = viewModel::setDefaultLeadTimeMinutes,
                            onVibrationChange = viewModel::setVibrationEnabled,
                            onQuietHoursChange = viewModel::setQuietHours,
                            onDigestChange = viewModel::setDigestSettings,
                            onAlertToneChange = viewModel::setAlertTone,
                            onOpenNotificationSettings = openSystemNotificationSettings,
                            onClearCompleted = {
                                val list = (uiState as? UiState.Success)?.data ?: emptyList()
                                viewModel.clearCompleted(list)
                            },
                            onSignOut = {
                                signOut(credentialManager = credentialManager, scope = scope)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }

        if (showEditor) {
            ReminderEditorSheet(
                editingReminder = editingReminder,
                profiles = profiles,
                initialProfileId = selectedProfileId,
                defaultLeadTimeMinutes = preferences.defaultLeadTimeMinutes,
                isSaving = isMutating,
                onCaptureLocation = { viewModel.captureCurrentLocation() },
                onDismiss = { showEditor = false },
                onSave = { draft ->
                    viewModel.saveReminder(editingReminder, draft) {
                        showEditor = false
                    }
                }
            )
        }

        if (showScannerSheet) {
            PrescriptionScannerSheet(
                scanState = aiScanState,
                isSaving = isMutating,
                activeProfileName = activeProfileName,
                onScanBitmap = viewModel::scanPrescriptionBitmap,
                onToggleItem = viewModel::toggleScannedItem,
                onImportSelected = {
                    viewModel.importScannedMedicines {
                        showScannerSheet = false
                    }
                },
                onDismiss = {
                    viewModel.clearAiScan()
                    showScannerSheet = false
                }
            )
        }

        aiInfoTarget?.let { target ->
            MedicineInfoSheet(
                target = target,
                infoState = aiInfoState,
                onDismiss = viewModel::clearAiInfo
            )
        }
    }
}
