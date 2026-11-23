package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Calendar
import java.util.Locale

enum class ReminderPriority(val label: String, val sortWeight: Int) {
    URGENT("Critical", 4),
    HIGH("Essential", 3),
    MEDIUM("Regular", 2),
    LOW("Optional", 1);

    companion object {
        fun fromRaw(raw: String?): ReminderPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class ReminderCategory(val label: String) {
    DAILY_MEDS("Daily Meds"),
    VITAMINS("Vitamins"),
    HEART_BP("BP / Heart"),
    DIABETES("Diabetes"),
    ANTIBIOTIC("Antibiotic"),
    EYES_SKIN("Drops / Care");

    companion object {
        fun fromRaw(raw: String?): ReminderCategory = when (raw) {
            "HEALTH" -> DAILY_MEDS
            "PERSONAL", "WORK", "FINANCE", "ERRANDS", "STUDY" -> DAILY_MEDS
            else -> entries.firstOrNull { it.name == raw } ?: DAILY_MEDS
        }
    }
}

enum class MedicationForm(val label: String) {
    TABLET("Tablet"),
    CAPSULE("Capsule"),
    SYRUP("Syrup"),
    INJECTION("Injection"),
    DROPS("Drops"),
    INHALER("Inhaler"),
    OTHER("Other");

    companion object {
        fun fromRaw(raw: String?): MedicationForm =
            entries.firstOrNull { it.name == raw } ?: TABLET
    }
}

enum class MealInstruction(val label: String) {
    BEFORE_FOOD("Before Meal"),
    AFTER_FOOD("After Meal"),
    WITH_FOOD("With Meal"),
    EMPTY_STOMACH("Empty Stomach"),
    ANYTIME("Anytime");

    companion object {
        fun fromRaw(raw: String?): MealInstruction =
            entries.firstOrNull { it.name == raw } ?: AFTER_FOOD
    }
}

enum class ReminderRecurrence(val label: String) {
    NONE("Once"),
    EVERY_8_HOURS("Every 8h"),
    EVERY_12_HOURS("Every 12h"),
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    fun nextOccurrenceMillis(fromMillis: Long): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = maxOf(fromMillis, now)
        }
        when (this) {
            NONE -> return fromMillis
            EVERY_8_HOURS -> {
                val base = Calendar.getInstance().apply { timeInMillis = fromMillis }
                do {
                    base.add(Calendar.HOUR_OF_DAY, 8)
                } while (base.timeInMillis <= now)
                return base.timeInMillis
            }
            EVERY_12_HOURS -> {
                val base = Calendar.getInstance().apply { timeInMillis = fromMillis }
                do {
                    base.add(Calendar.HOUR_OF_DAY, 12)
                } while (base.timeInMillis <= now)
                return base.timeInMillis
            }
            DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            WEEKDAYS -> {
                do {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                } while (
                    cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                )
            }
            WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            MONTHLY -> cal.add(Calendar.MONTH, 1)
            YEARLY -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    companion object {
        fun fromRaw(raw: String?): ReminderRecurrence =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

@Keep
@IgnoreExtraProperties
data class SubtaskItem(
    val id: String = "",
    val title: String = "",
    val completed: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id.take(64).ifBlank { "sub_1" },
        "title" to title.trim().take(120),
        "completed" to completed
    )
}

@Keep
@IgnoreExtraProperties
data class Reminder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileId: String = "self",
    val profileName: String = "Self",
    val title: String = "",
    val notes: String = "",
    val dueAtMillis: Long = 0L,
    val snoozedUntilMillis: Long = 0L,
    val leadTimeMinutes: Long = 0L,
    val priority: String = ReminderPriority.MEDIUM.name,
    val category: String = ReminderCategory.DAILY_MEDS.name,
    val recurrence: String = ReminderRecurrence.DAILY.name,
    val completed: Boolean = false,
    val pinned: Boolean = false,
    val urgentAlarm: Boolean = false,
    val locationLabel: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Long = 200L,
    val locationTriggerEnabled: Boolean = false,
    val dosage: String = "1 Tablet",
    val medForm: String = MedicationForm.TABLET.name,
    val mealInstruction: String = MealInstruction.AFTER_FOOD.name,
    val prescribedBy: String = "",
    val stockCount: Int = 30,
    val dosePerIntake: Int = 1,
    val refillThreshold: Int = 5,
    val takenCount: Int = 0,
    val skippedCount: Int = 0,
    val lastDoseStatus: String = "PENDING",
    val courseDays: Int = 0,
    val courseStartMillis: Long = 0L,
    val subtasks: List<SubtaskItem> = emptyList(),
    val completedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val priorityEnum: ReminderPriority
        get() = ReminderPriority.fromRaw(priority)

    val categoryEnum: ReminderCategory
        get() = ReminderCategory.fromRaw(category)

    val recurrenceEnum: ReminderRecurrence
        get() = ReminderRecurrence.fromRaw(recurrence)

    val medFormEnum: MedicationForm
        get() = MedicationForm.fromRaw(medForm)

    val mealInstructionEnum: MealInstruction
        get() = MealInstruction.fromRaw(mealInstruction)

    val effectiveTriggerMillis: Long
        get() = if (snoozedUntilMillis > dueAtMillis) snoozedUntilMillis else dueAtMillis

    val isLowStock: Boolean
        get() = stockCount >= 0 && stockCount <= refillThreshold

    val doseDisplaySummary: String
        get() = buildList {
            if (dosage.isNotBlank()) add(dosage) else add(medFormEnum.label)
            if (mealInstructionEnum != MealInstruction.ANYTIME) add(mealInstructionEnum.label)
        }.joinToString(" • ")

    fun currentCourseDay(nowMillis: Long = System.currentTimeMillis()): Int {
        if (courseDays <= 0) return 0
        val start = if (courseStartMillis > 0L) courseStartMillis else dueAtMillis
        val elapsedDays = ((nowMillis - start).coerceAtLeast(0L) / 86_400_000L).toInt() + 1
        return elapsedDays.coerceIn(1, courseDays)
    }

    fun courseProgressFraction(nowMillis: Long = System.currentTimeMillis()): Float {
        if (courseDays <= 0) return 0f
        if (completed) return 1f
        return (currentCourseDay(nowMillis).toFloat() / courseDays.toFloat()).coerceIn(0f, 1f)
    }

    fun isOverdue(nowMillis: Long): Boolean = !completed && effectiveTriggerMillis < nowMillis
}

@Keep
@IgnoreExtraProperties
data class SharedReminder(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val ownerEmail: String = "",
    val participantEmails: List<String> = emptyList(),
    val title: String = "",
    val notes: String = "",
    val dueAtMillis: Long = 0L,
    val snoozedUntilMillis: Long = 0L,
    val leadTimeMinutes: Long = 0L,
    val priority: String = ReminderPriority.MEDIUM.name,
    val category: String = ReminderCategory.DAILY_MEDS.name,
    val recurrence: String = ReminderRecurrence.DAILY.name,
    val completed: Boolean = false,
    val pinned: Boolean = false,
    val dosage: String = "1 Tablet",
    val medForm: String = MedicationForm.TABLET.name,
    val mealInstruction: String = MealInstruction.AFTER_FOOD.name,
    val prescribedBy: String = "",
    val stockCount: Int = 30,
    val dosePerIntake: Int = 1,
    val refillThreshold: Int = 5,
    val takenCount: Int = 0,
    val skippedCount: Int = 0,
    val lastDoseStatus: String = "PENDING",
    val subtasks: List<SubtaskItem> = emptyList(),
    val completedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toReminder(): Reminder = Reminder(
        id = id,
        userId = ownerId,
        title = title,
        notes = notes,
        dueAtMillis = dueAtMillis,
        snoozedUntilMillis = snoozedUntilMillis,
        leadTimeMinutes = leadTimeMinutes,
        priority = priority,
        category = category,
        recurrence = recurrence,
        completed = completed,
        pinned = pinned,
        dosage = dosage,
        medForm = medForm,
        mealInstruction = mealInstruction,
        prescribedBy = prescribedBy,
        stockCount = stockCount,
        dosePerIntake = dosePerIntake,
        refillThreshold = refillThreshold,
        takenCount = takenCount,
        skippedCount = skippedCount,
        lastDoseStatus = lastDoseStatus,
        subtasks = subtasks,
        completedAtMillis = completedAtMillis,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

data class ReminderDraft(
    val profileId: String = "self",
    val profileName: String = "Self",
    val title: String = "",
    val notes: String = "",
    val dueAtMillis: Long = System.currentTimeMillis() + 30 * 60_000L,
    val leadTimeMinutes: Long = 5L,
    val priority: ReminderPriority = ReminderPriority.HIGH,
    val category: ReminderCategory = ReminderCategory.DAILY_MEDS,
    val recurrence: ReminderRecurrence = ReminderRecurrence.DAILY,
    val pinned: Boolean = false,
    val urgentAlarm: Boolean = false,
    val locationLabel: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Long = 200L,
    val locationTriggerEnabled: Boolean = false,
    val dosage: String = "1 Tablet",
    val medForm: MedicationForm = MedicationForm.TABLET,
    val mealInstruction: MealInstruction = MealInstruction.AFTER_FOOD,
    val prescribedBy: String = "",
    val stockCount: Int = 30,
    val dosePerIntake: Int = 1,
    val refillThreshold: Int = 5,
    val courseDays: Int = 0,
    val courseStartMillis: Long = 0L,
    val subtasks: List<SubtaskItem> = emptyList(),
    val sharedWithEmails: List<String> = emptyList()
)

@Keep
@IgnoreExtraProperties
data class FamilyProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val relation: String = "Family",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val emergencyPhone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class VitalType(val label: String, val defaultUnit: String) {
    BP("Blood Pressure", "mmHg"),
    SUGAR_FASTING("Sugar (Fasting)", "mg/dL"),
    SUGAR_PP("Sugar (After Meal)", "mg/dL"),
    SPO2("SpO2 Oxygen", "%"),
    TEMPERATURE("Temperature", "°F"),
    WEIGHT("Weight", "kg");

    companion object {
        fun fromRaw(raw: String?): VitalType =
            entries.firstOrNull { it.name == raw } ?: BP
    }
}

@Keep
@IgnoreExtraProperties
data class VitalRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileId: String = "self",
    val type: String = VitalType.BP.name,
    val valuePrimary: Double = 0.0,
    val valueSecondary: Double = 0.0,
    val unit: String = "mmHg",
    val notes: String = "",
    val recordedAtMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val typeEnum: VitalType
        get() = VitalType.fromRaw(type)

    fun formattedValue(): String {
        return when (typeEnum) {
            VitalType.BP -> "${valuePrimary.toInt()}/${valueSecondary.toInt()} $unit"
            VitalType.SPO2 -> "${valuePrimary.toInt()}%"
            VitalType.SUGAR_FASTING, VitalType.SUGAR_PP -> "${valuePrimary.toInt()} $unit"
            else -> String.format(Locale.getDefault(), "%.1f %s", valuePrimary, unit)
        }
    }
}

@Keep
@IgnoreExtraProperties
data class DoctorAppointment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val profileId: String = "self",
    val doctorName: String = "",
    val specialty: String = "",
    val clinicName: String = "",
    val clinicPhone: String = "",
    val appointmentAtMillis: Long = System.currentTimeMillis(),
    val notes: String = "",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
