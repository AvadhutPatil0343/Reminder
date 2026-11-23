package com.example.data.repository

import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.DoctorAppointment
import com.example.data.model.FamilyProfile
import com.example.data.model.VitalRecord
import com.example.data.model.VitalType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CareDataRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    private fun profilesCollection(userId: String) =
        db.collection("users").document(userId).collection("family_profiles")

    private fun vitalsCollection(userId: String) =
        db.collection("users").document(userId).collection("health_vitals")

    private fun appointmentsCollection(userId: String) =
        db.collection("users").document(userId).collection("appointments")

    fun observeFamilyProfiles(userId: String): Flow<List<FamilyProfile>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/family_profiles"
        emitAll(
            profilesCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FamilyProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeVitals(userId: String): Flow<List<VitalRecord>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/health_vitals"
        emitAll(
            vitalsCollection(userId)
                .orderBy("recordedAtMillis", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VitalRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAppointments(userId: String): Flow<List<DoctorAppointment>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/appointments"
        emitAll(
            appointmentsCollection(userId)
                .orderBy("appointmentAtMillis", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DoctorAppointment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val ex = firestoreError ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveFamilyProfile(
        existingId: String?,
        name: String,
        relation: String,
        doctorName: String,
        doctorPhone: String,
        emergencyPhone: String
    ): Result<FamilyProfile> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/family_profiles"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Member name cannot be empty" }
            val cleanRelation = relation.trim().take(40).ifBlank { "Family" }
            val cleanDoctor = doctorName.trim().take(80)
            val cleanDocPhone = doctorPhone.trim().take(30)
            val cleanEmergencyPhone = emergencyPhone.trim().take(30)

            if (existingId.isNullOrBlank()) {
                val docRef = profilesCollection(uid).document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "name" to cleanName,
                    "relation" to cleanRelation,
                    "doctorName" to cleanDoctor,
                    "doctorPhone" to cleanDocPhone,
                    "emergencyPhone" to cleanEmergencyPhone,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(
                    FamilyProfile(
                        id = docRef.id,
                        userId = uid,
                        name = cleanName,
                        relation = cleanRelation,
                        doctorName = cleanDoctor,
                        doctorPhone = cleanDocPhone,
                        emergencyPhone = cleanEmergencyPhone
                    )
                )
            } else {
                val docRef = profilesCollection(uid).document(existingId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "relation" to cleanRelation,
                        "doctorName" to cleanDoctor,
                        "doctorPhone" to cleanDocPhone,
                        "emergencyPhone" to cleanEmergencyPhone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(
                    FamilyProfile(
                        id = existingId,
                        userId = uid,
                        name = cleanName,
                        relation = cleanRelation,
                        doctorName = cleanDoctor,
                        doctorPhone = cleanDocPhone,
                        emergencyPhone = cleanEmergencyPhone
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFamilyProfile(profileId: String): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/family_profiles/$profileId"
        return try {
            val uid = requireUserId()
            require(profileId.isNotBlank() && profileId != "self")
            profilesCollection(uid).document(profileId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun logVital(
        profileId: String,
        type: VitalType,
        valuePrimary: Double,
        valueSecondary: Double,
        notes: String,
        recordedAtMillis: Long = System.currentTimeMillis()
    ): Result<VitalRecord> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/health_vitals"
        return try {
            val uid = requireUserId()
            val cleanProfileId = profileId.trim().take(128).ifBlank { "self" }
            val safePrimary = valuePrimary.coerceIn(0.0, 1000.0)
            val safeSecondary = valueSecondary.coerceIn(0.0, 1000.0)
            val cleanNotes = notes.trim().take(300)
            val docRef = vitalsCollection(uid).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "profileId" to cleanProfileId,
                "type" to type.name,
                "valuePrimary" to safePrimary,
                "valueSecondary" to safeSecondary,
                "unit" to type.defaultUnit,
                "notes" to cleanNotes,
                "recordedAtMillis" to maxOf(recordedAtMillis, 1L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                VitalRecord(
                    id = docRef.id,
                    userId = uid,
                    profileId = cleanProfileId,
                    type = type.name,
                    valuePrimary = safePrimary,
                    valueSecondary = safeSecondary,
                    unit = type.defaultUnit,
                    notes = cleanNotes,
                    recordedAtMillis = recordedAtMillis
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteVital(vitalId: String): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/health_vitals/$vitalId"
        return try {
            val uid = requireUserId()
            vitalsCollection(uid).document(vitalId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveAppointment(
        profileId: String,
        doctorName: String,
        specialty: String,
        clinicName: String,
        clinicPhone: String,
        appointmentAtMillis: Long,
        notes: String
    ): Result<DoctorAppointment> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments"
        return try {
            val uid = requireUserId()
            val cleanProfileId = profileId.trim().take(128).ifBlank { "self" }
            val cleanDoctor = doctorName.trim().take(100)
            require(cleanDoctor.isNotEmpty()) { "Doctor name cannot be empty" }
            val cleanSpecialty = specialty.trim().take(80)
            val cleanClinic = clinicName.trim().take(100)
            val cleanPhone = clinicPhone.trim().take(30)
            val cleanNotes = notes.trim().take(400)
            val safeMillis = maxOf(appointmentAtMillis, 1L)

            val docRef = appointmentsCollection(uid).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "profileId" to cleanProfileId,
                "doctorName" to cleanDoctor,
                "specialty" to cleanSpecialty,
                "clinicName" to cleanClinic,
                "clinicPhone" to cleanPhone,
                "appointmentAtMillis" to safeMillis,
                "notes" to cleanNotes,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(
                DoctorAppointment(
                    id = docRef.id,
                    userId = uid,
                    profileId = cleanProfileId,
                    doctorName = cleanDoctor,
                    specialty = cleanSpecialty,
                    clinicName = cleanClinic,
                    clinicPhone = cleanPhone,
                    appointmentAtMillis = safeMillis,
                    notes = cleanNotes,
                    completed = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleAppointmentComplete(appt: DoctorAppointment): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments/${appt.id}"
        return try {
            val uid = requireUserId()
            appointmentsCollection(uid).document(appt.id).update(
                mapOf(
                    "completed" to !appt.completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAppointment(appointmentId: String): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments/$appointmentId"
        return try {
            val uid = requireUserId()
            appointmentsCollection(uid).document(appointmentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
