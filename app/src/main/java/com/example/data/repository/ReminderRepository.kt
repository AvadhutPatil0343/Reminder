package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Reminder
import com.example.data.model.ReminderDraft
import com.example.data.model.ReminderRecurrence
import com.example.data.model.SharedReminder
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale

data class DoseActionOutcome(
    val rescheduled: Boolean,
    val updatedStock: Int,
    val lowStockTriggered: Boolean,
    val courseCompleted: Boolean = false
)

class ReminderRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun requireUserEmail(): String {
        return auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Verified Google email is required for caregiver sharing.")
    }

    private fun remindersCollection(userId: String) =
        db.collection("users").document(userId).collection("reminders")

    private fun sharedCollection() = db.collection("shared_reminders")

    fun observeReminders(userId: String): Flow<List<Reminder>> = flow {
        val path = "users/$userId/reminders"
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            remindersCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Reminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSharedReminders(userEmail: String?): Flow<List<SharedReminder>> = flow {
        val cleanEmail = userEmail?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (cleanEmail.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "shared_reminders"
        emitAll(
            sharedCollection()
                .whereArrayContains("participantEmails", cleanEmail)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SharedReminder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getReminder(userId: String, reminderId: String): Reminder? {
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user read." }
            remindersCollection(uid).document(reminderId)
                .get()
                .await()
                .toObject(Reminder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$userId/reminders/$reminderId")
            null
        }
    }

    suspend fun createReminder(draft: ReminderDraft): Result<Reminder> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders"
        return try {
            val uid = requireUserId()
            val cleanTitle = draft.title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Medicine name cannot be empty" }
            val cleanNotes = draft.notes.trim().take(500)
            val safeDueAt = maxOf(draft.dueAtMillis, 1L)
            val safeLead = if (draft.leadTimeMinutes in ALLOWED_LEAD_TIMES) draft.leadTimeMinutes else 0L
            val cleanLocationLabel = draft.locationLabel.trim().take(120)
            val safeRadius = draft.radiusMeters.coerceIn(50L, 5000L)
            val cleanLat = draft.latitude.coerceIn(-90.0, 90.0)
            val cleanLng = draft.longitude.coerceIn(-180.0, 180.0)
            val cleanDosage = draft.dosage.trim().take(60)
            val cleanPrescribedBy = draft.prescribedBy.trim().take(80)
            val safeStock = draft.stockCount.coerceIn(-1, 10000)
            val safeDosePerIntake = draft.dosePerIntake.coerceIn(1, 100)
            val safeRefillThreshold = draft.refillThreshold.coerceIn(0, 500)
            val cleanSubtasks = draft.subtasks
                .filter { it.title.trim().isNotEmpty() }
                .take(15)
                .mapIndexed { index, item ->
                    item.copy(
                        id = item.id.take(64).ifBlank { "sub_${index + 1}" },
                        title = item.title.trim().take(120)
                    )
                }

            val cleanProfileId = draft.profileId.trim().take(128).ifBlank { "self" }
            val cleanProfileName = draft.profileName.trim().take(60).ifBlank { "Self" }
            val safeCourseDays = draft.courseDays.coerceIn(0, 3650)
            val safeCourseStart = if (safeCourseDays > 0) {
                draft.courseStartMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            } else {
                0L
            }

            val docRef = remindersCollection(uid).document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "profileId" to cleanProfileId,
                "profileName" to cleanProfileName,
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "dueAtMillis" to safeDueAt,
                "snoozedUntilMillis" to 0L,
                "leadTimeMinutes" to safeLead,
                "priority" to draft.priority.name,
                "category" to draft.category.name,
                "recurrence" to draft.recurrence.name,
                "completed" to false,
                "pinned" to draft.pinned,
                "urgentAlarm" to draft.urgentAlarm,
                "locationLabel" to cleanLocationLabel,
                "latitude" to cleanLat,
                "longitude" to cleanLng,
                "radiusMeters" to safeRadius,
                "locationTriggerEnabled" to draft.locationTriggerEnabled,
                "dosage" to cleanDosage,
                "medForm" to draft.medForm.name,
                "mealInstruction" to draft.mealInstruction.name,
                "prescribedBy" to cleanPrescribedBy,
                "stockCount" to safeStock,
                "dosePerIntake" to safeDosePerIntake,
                "refillThreshold" to safeRefillThreshold,
                "takenCount" to 0,
                "skippedCount" to 0,
                "lastDoseStatus" to "PENDING",
                "courseDays" to safeCourseDays,
                "courseStartMillis" to safeCourseStart,
                "subtasks" to cleanSubtasks.map { it.toFirestoreMap() },
                "completedAtMillis" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            Result.success(
                Reminder(
                    id = docRef.id,
                    userId = uid,
                    profileId = cleanProfileId,
                    profileName = cleanProfileName,
                    title = cleanTitle,
                    notes = cleanNotes,
                    dueAtMillis = safeDueAt,
                    leadTimeMinutes = safeLead,
                    priority = draft.priority.name,
                    category = draft.category.name,
                    recurrence = draft.recurrence.name,
                    pinned = draft.pinned,
                    urgentAlarm = draft.urgentAlarm,
                    locationLabel = cleanLocationLabel,
                    latitude = cleanLat,
                    longitude = cleanLng,
                    radiusMeters = safeRadius,
                    locationTriggerEnabled = draft.locationTriggerEnabled,
                    dosage = cleanDosage,
                    medForm = draft.medForm.name,
                    mealInstruction = draft.mealInstruction.name,
                    prescribedBy = cleanPrescribedBy,
                    stockCount = safeStock,
                    dosePerIntake = safeDosePerIntake,
                    refillThreshold = safeRefillThreshold,
                    courseDays = safeCourseDays,
                    courseStartMillis = safeCourseStart,
                    subtasks = cleanSubtasks
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createSharedReminder(draft: ReminderDraft): Result<SharedReminder> {
        var docPath = "shared_reminders"
        return try {
            val uid = requireUserId()
            val ownerEmail = requireUserEmail()
            val cleanTitle = draft.title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Medicine name cannot be empty" }
            val cleanNotes = draft.notes.trim().take(500)
            val safeDueAt = maxOf(draft.dueAtMillis, 1L)
            val safeLead = if (draft.leadTimeMinutes in ALLOWED_LEAD_TIMES) draft.leadTimeMinutes else 0L
            val cleanDosage = draft.dosage.trim().take(60)
            val cleanPrescribedBy = draft.prescribedBy.trim().take(80)
            val safeStock = draft.stockCount.coerceIn(-1, 10000)
            val safeDosePerIntake = draft.dosePerIntake.coerceIn(1, 100)
            val safeRefillThreshold = draft.refillThreshold.coerceIn(0, 500)
            val participants = (listOf(ownerEmail) + draft.sharedWithEmails.map {
                it.trim().lowercase(Locale.ROOT)
            })
                .filter { it.contains("@") && it.length in 3..160 }
                .distinct()
                .take(10)

            val cleanSubtasks = draft.subtasks
                .filter { it.title.trim().isNotEmpty() }
                .take(15)
                .mapIndexed { index, item ->
                    item.copy(
                        id = item.id.take(64).ifBlank { "sub_${index + 1}" },
                        title = item.title.trim().take(120)
                    )
                }

            val docRef = sharedCollection().document()
            docPath = docRef.path

            val payload = mapOf(
                "ownerId" to uid,
                "ownerEmail" to ownerEmail,
                "participantEmails" to participants,
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "dueAtMillis" to safeDueAt,
                "snoozedUntilMillis" to 0L,
                "leadTimeMinutes" to safeLead,
                "priority" to draft.priority.name,
                "category" to draft.category.name,
                "recurrence" to draft.recurrence.name,
                "completed" to false,
                "pinned" to draft.pinned,
                "dosage" to cleanDosage,
                "medForm" to draft.medForm.name,
                "mealInstruction" to draft.mealInstruction.name,
                "prescribedBy" to cleanPrescribedBy,
                "stockCount" to safeStock,
                "dosePerIntake" to safeDosePerIntake,
                "refillThreshold" to safeRefillThreshold,
                "takenCount" to 0,
                "skippedCount" to 0,
                "lastDoseStatus" to "PENDING",
                "subtasks" to cleanSubtasks.map { it.toFirestoreMap() },
                "completedAtMillis" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            Result.success(
                SharedReminder(
                    id = docRef.id,
                    ownerId = uid,
                    ownerEmail = ownerEmail,
                    participantEmails = participants,
                    title = cleanTitle,
                    notes = cleanNotes,
                    dueAtMillis = safeDueAt,
                    leadTimeMinutes = safeLead,
                    priority = draft.priority.name,
                    category = draft.category.name,
                    recurrence = draft.recurrence.name,
                    pinned = draft.pinned,
                    dosage = cleanDosage,
                    medForm = draft.medForm.name,
                    mealInstruction = draft.mealInstruction.name,
                    prescribedBy = cleanPrescribedBy,
                    stockCount = safeStock,
                    dosePerIntake = safeDosePerIntake,
                    refillThreshold = safeRefillThreshold,
                    subtasks = cleanSubtasks
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateReminder(existing: Reminder, draft: ReminderDraft): Result<Reminder> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${existing.id}"
        return try {
            val uid = requireUserId()
            require(existing.id.isNotBlank()) { "Reminder ID cannot be blank" }
            val cleanTitle = draft.title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Medicine name cannot be empty" }
            val cleanNotes = draft.notes.trim().take(500)
            val safeDueAt = maxOf(draft.dueAtMillis, 1L)
            val safeLead = if (draft.leadTimeMinutes in ALLOWED_LEAD_TIMES) draft.leadTimeMinutes else 0L
            val cleanLocationLabel = draft.locationLabel.trim().take(120)
            val safeRadius = draft.radiusMeters.coerceIn(50L, 5000L)
            val cleanLat = draft.latitude.coerceIn(-90.0, 90.0)
            val cleanLng = draft.longitude.coerceIn(-180.0, 180.0)
            val cleanDosage = draft.dosage.trim().take(60)
            val cleanPrescribedBy = draft.prescribedBy.trim().take(80)
            val safeStock = draft.stockCount.coerceIn(-1, 10000)
            val safeDosePerIntake = draft.dosePerIntake.coerceIn(1, 100)
            val safeRefillThreshold = draft.refillThreshold.coerceIn(0, 500)
            val cleanSubtasks = draft.subtasks
                .filter { it.title.trim().isNotEmpty() }
                .take(15)
                .mapIndexed { index, item ->
                    item.copy(
                        id = item.id.take(64).ifBlank { "sub_${index + 1}" },
                        title = item.title.trim().take(120)
                    )
                }

            val cleanProfileId = draft.profileId.trim().take(128).ifBlank { "self" }
            val cleanProfileName = draft.profileName.trim().take(60).ifBlank { "Self" }
            val safeCourseDays = draft.courseDays.coerceIn(0, 3650)
            val safeCourseStart = if (safeCourseDays > 0) {
                existing.courseStartMillis.takeIf { it > 0L }
                    ?: draft.courseStartMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
            } else {
                0L
            }

            val docRef = remindersCollection(uid).document(existing.id)
            docPath = docRef.path

            val updates = mapOf(
                "profileId" to cleanProfileId,
                "profileName" to cleanProfileName,
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "dueAtMillis" to safeDueAt,
                "snoozedUntilMillis" to 0L,
                "leadTimeMinutes" to safeLead,
                "priority" to draft.priority.name,
                "category" to draft.category.name,
                "recurrence" to draft.recurrence.name,
                "pinned" to draft.pinned,
                "urgentAlarm" to draft.urgentAlarm,
                "locationLabel" to cleanLocationLabel,
                "latitude" to cleanLat,
                "longitude" to cleanLng,
                "radiusMeters" to safeRadius,
                "locationTriggerEnabled" to draft.locationTriggerEnabled,
                "dosage" to cleanDosage,
                "medForm" to draft.medForm.name,
                "mealInstruction" to draft.mealInstruction.name,
                "prescribedBy" to cleanPrescribedBy,
                "stockCount" to safeStock,
                "dosePerIntake" to safeDosePerIntake,
                "refillThreshold" to safeRefillThreshold,
                "courseDays" to safeCourseDays,
                "courseStartMillis" to safeCourseStart,
                "subtasks" to cleanSubtasks.map { it.toFirestoreMap() },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()

            Result.success(
                existing.copy(
                    profileId = cleanProfileId,
                    profileName = cleanProfileName,
                    title = cleanTitle,
                    notes = cleanNotes,
                    dueAtMillis = safeDueAt,
                    snoozedUntilMillis = 0L,
                    leadTimeMinutes = safeLead,
                    priority = draft.priority.name,
                    category = draft.category.name,
                    recurrence = draft.recurrence.name,
                    pinned = draft.pinned,
                    urgentAlarm = draft.urgentAlarm,
                    locationLabel = cleanLocationLabel,
                    latitude = cleanLat,
                    longitude = cleanLng,
                    radiusMeters = safeRadius,
                    locationTriggerEnabled = draft.locationTriggerEnabled,
                    dosage = cleanDosage,
                    medForm = draft.medForm.name,
                    mealInstruction = draft.mealInstruction.name,
                    prescribedBy = cleanPrescribedBy,
                    stockCount = safeStock,
                    dosePerIntake = safeDosePerIntake,
                    refillThreshold = safeRefillThreshold,
                    courseDays = safeCourseDays,
                    courseStartMillis = safeCourseStart,
                    subtasks = cleanSubtasks
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleComplete(reminder: Reminder): Result<DoseActionOutcome> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${reminder.id}"
        return try {
            val uid = requireUserId()
            require(reminder.id.isNotBlank()) { "Reminder ID cannot be blank" }
            val docRef = remindersCollection(uid).document(reminder.id)
            docPath = docRef.path

            val recurrence = reminder.recurrenceEnum
            val markingTaken = !reminder.completed
            val newStock = if (markingTaken && reminder.stockCount >= 0) {
                (reminder.stockCount - reminder.dosePerIntake.coerceAtLeast(1)).coerceAtLeast(0)
            } else {
                reminder.stockCount
            }
            val newTaken = if (markingTaken) (reminder.takenCount + 1).coerceAtMost(100000) else reminder.takenCount
            val courseReachedEnd = markingTaken &&
                reminder.courseDays > 0 &&
                reminder.currentCourseDay() >= reminder.courseDays &&
                newTaken >= reminder.courseDays
            val rescheduled = markingTaken && recurrence != ReminderRecurrence.NONE && !courseReachedEnd

            val updates = if (rescheduled) {
                val nextDue = recurrence.nextOccurrenceMillis(reminder.dueAtMillis)
                mapOf(
                    "completed" to false,
                    "completedAtMillis" to System.currentTimeMillis(),
                    "dueAtMillis" to nextDue,
                    "snoozedUntilMillis" to 0L,
                    "stockCount" to newStock,
                    "takenCount" to newTaken,
                    "lastDoseStatus" to "TAKEN",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "completed" to markingTaken,
                    "completedAtMillis" to if (markingTaken) System.currentTimeMillis() else 0L,
                    "snoozedUntilMillis" to 0L,
                    "stockCount" to newStock,
                    "takenCount" to newTaken,
                    "lastDoseStatus" to if (markingTaken) "TAKEN" else "PENDING",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            docRef.update(updates).await()
            val lowStock = newStock >= 0 && newStock <= reminder.refillThreshold && markingTaken
            Result.success(
                DoseActionOutcome(
                    rescheduled = rescheduled,
                    updatedStock = newStock,
                    lowStockTriggered = lowStock,
                    courseCompleted = courseReachedEnd
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun skipDose(reminder: Reminder): Result<Boolean> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${reminder.id}"
        return try {
            val uid = requireUserId()
            require(reminder.id.isNotBlank()) { "Reminder ID cannot be blank" }
            val docRef = remindersCollection(uid).document(reminder.id)
            docPath = docRef.path

            val recurrence = reminder.recurrenceEnum
            val rescheduled = recurrence != ReminderRecurrence.NONE
            val newSkipped = (reminder.skippedCount + 1).coerceAtMost(100000)
            val updates = if (rescheduled) {
                val nextDue = recurrence.nextOccurrenceMillis(reminder.dueAtMillis)
                mapOf(
                    "completed" to false,
                    "completedAtMillis" to System.currentTimeMillis(),
                    "dueAtMillis" to nextDue,
                    "snoozedUntilMillis" to 0L,
                    "skippedCount" to newSkipped,
                    "lastDoseStatus" to "SKIPPED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "completed" to true,
                    "completedAtMillis" to System.currentTimeMillis(),
                    "snoozedUntilMillis" to 0L,
                    "skippedCount" to newSkipped,
                    "lastDoseStatus" to "SKIPPED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            docRef.update(updates).await()
            Result.success(rescheduled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun refillStock(reminder: Reminder, addUnits: Int): Result<Int> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${reminder.id}"
        return try {
            val uid = requireUserId()
            require(reminder.id.isNotBlank()) { "Reminder ID cannot be blank" }
            val base = if (reminder.stockCount < 0) 0 else reminder.stockCount
            val updatedStock = (base + addUnits.coerceAtLeast(1)).coerceAtMost(10000)
            val docRef = remindersCollection(uid).document(reminder.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "stockCount" to updatedStock,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(updatedStock)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleSharedComplete(shared: SharedReminder): Result<Boolean> {
        val docPath = "shared_reminders/${shared.id}"
        return try {
            requireUserId()
            require(shared.id.isNotBlank()) { "Shared reminder ID cannot be blank" }
            val recurrence = ReminderRecurrence.fromRaw(shared.recurrence)
            val markingTaken = !shared.completed
            val rescheduled = markingTaken && recurrence != ReminderRecurrence.NONE
            val newStock = if (markingTaken && shared.stockCount >= 0) {
                (shared.stockCount - shared.dosePerIntake.coerceAtLeast(1)).coerceAtLeast(0)
            } else {
                shared.stockCount
            }
            val newTaken = if (markingTaken) (shared.takenCount + 1).coerceAtMost(100000) else shared.takenCount
            val updates = if (rescheduled) {
                val nextDue = recurrence.nextOccurrenceMillis(shared.dueAtMillis)
                mapOf(
                    "completed" to false,
                    "completedAtMillis" to System.currentTimeMillis(),
                    "dueAtMillis" to nextDue,
                    "snoozedUntilMillis" to 0L,
                    "stockCount" to newStock,
                    "takenCount" to newTaken,
                    "lastDoseStatus" to "TAKEN",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "completed" to markingTaken,
                    "completedAtMillis" to if (markingTaken) System.currentTimeMillis() else 0L,
                    "snoozedUntilMillis" to 0L,
                    "stockCount" to newStock,
                    "takenCount" to newTaken,
                    "lastDoseStatus" to if (markingTaken) "TAKEN" else "PENDING",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            sharedCollection().document(shared.id).update(updates).await()
            Result.success(rescheduled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleSharedSubtask(shared: SharedReminder, subtaskId: String): Result<Unit> {
        val docPath = "shared_reminders/${shared.id}"
        return try {
            requireUserId()
            val updatedSubtasks = shared.subtasks.map { item ->
                if (item.id == subtaskId) item.copy(completed = !item.completed) else item
            }
            sharedCollection().document(shared.id).update(
                mapOf(
                    "subtasks" to updatedSubtasks.map { it.toFirestoreMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSharedReminder(sharedId: String): Result<Unit> {
        val docPath = "shared_reminders/$sharedId"
        return try {
            requireUserId()
            sharedCollection().document(sharedId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun completeFromNotification(reminderId: String): Result<Reminder?> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            val reminder = getReminder(uid, reminderId) ?: return Result.success(null)
            val docRef = remindersCollection(uid).document(reminderId)
            docPath = docRef.path
            val recurrence = reminder.recurrenceEnum
            val newStock = if (reminder.stockCount >= 0) {
                (reminder.stockCount - reminder.dosePerIntake.coerceAtLeast(1)).coerceAtLeast(0)
            } else {
                reminder.stockCount
            }
            val newTaken = (reminder.takenCount + 1).coerceAtMost(100000)
            val courseReachedEnd = reminder.courseDays > 0 &&
                reminder.currentCourseDay() >= reminder.courseDays &&
                newTaken >= reminder.courseDays

            if (recurrence != ReminderRecurrence.NONE && !courseReachedEnd) {
                val nextDue = recurrence.nextOccurrenceMillis(reminder.dueAtMillis)
                docRef.update(
                    mapOf(
                        "completed" to false,
                        "completedAtMillis" to System.currentTimeMillis(),
                        "dueAtMillis" to nextDue,
                        "snoozedUntilMillis" to 0L,
                        "stockCount" to newStock,
                        "takenCount" to newTaken,
                        "lastDoseStatus" to "TAKEN",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(
                    reminder.copy(
                        dueAtMillis = nextDue,
                        snoozedUntilMillis = 0L,
                        completed = false,
                        stockCount = newStock,
                        takenCount = newTaken,
                        lastDoseStatus = "TAKEN"
                    )
                )
            } else {
                docRef.update(
                    mapOf(
                        "completed" to true,
                        "completedAtMillis" to System.currentTimeMillis(),
                        "snoozedUntilMillis" to 0L,
                        "stockCount" to newStock,
                        "takenCount" to newTaken,
                        "lastDoseStatus" to "TAKEN",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(
                    reminder.copy(
                        completed = true,
                        snoozedUntilMillis = 0L,
                        stockCount = newStock,
                        takenCount = newTaken,
                        lastDoseStatus = "TAKEN"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun togglePin(reminder: Reminder): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${reminder.id}"
        return try {
            val uid = requireUserId()
            require(reminder.id.isNotBlank()) { "Reminder ID cannot be blank" }
            val docRef = remindersCollection(uid).document(reminder.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "pinned" to !reminder.pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun snoozeReminder(reminderId: String, snoozeMinutes: Int): Result<Long> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            require(reminderId.isNotBlank()) { "Reminder ID cannot be blank" }
            val targetMillis = System.currentTimeMillis() + snoozeMinutes.coerceAtLeast(1) * 60_000L
            val docRef = remindersCollection(uid).document(reminderId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "dueAtMillis" to targetMillis,
                    "snoozedUntilMillis" to targetMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(targetMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleSubtask(reminder: Reminder, subtaskId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/${reminder.id}"
        return try {
            val uid = requireUserId()
            require(reminder.id.isNotBlank()) { "Reminder ID cannot be blank" }
            val updatedSubtasks = reminder.subtasks.map { item ->
                if (item.id == subtaskId) item.copy(completed = !item.completed) else item
            }
            val docRef = remindersCollection(uid).document(reminder.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "subtasks" to updatedSubtasks.map { it.toFirestoreMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteReminder(reminderId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders/$reminderId"
        return try {
            val uid = requireUserId()
            require(reminderId.isNotBlank()) { "Reminder ID cannot be blank" }
            val docRef = remindersCollection(uid).document(reminderId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearCompleted(completedIds: List<String>): Result<Int> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/reminders"
        return try {
            val uid = requireUserId()
            if (completedIds.isEmpty()) return Result.success(0)
            val batch = db.batch()
            completedIds.take(450).forEach { id ->
                if (id.isNotBlank()) {
                    batch.delete(remindersCollection(uid).document(id))
                }
            }
            batch.commit().await()
            Result.success(completedIds.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        val ALLOWED_LEAD_TIMES = setOf(0L, 5L, 15L, 30L, 60L)
    }
}
