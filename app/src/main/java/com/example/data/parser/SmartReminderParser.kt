package com.example.data.parser

import com.example.data.model.MealInstruction
import com.example.data.model.MedicationForm
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderDraft
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ParsedQuickAdd(
    val draft: ReminderDraft,
    val detectedTimeLabel: String,
    val hasExplicitTime: Boolean
)

object SmartReminderParser {
    private val relativeRegex = Regex(
        """(?i)(?:in\s+(\d+)\s*(min(?:ute)?s?|hrs?|hours?)|(\d+)\s*(min(?:ute)?s?|hrs?|hours?)\s*(?:madhe|nantar))"""
    )
    private val clockRegex = Regex(
        """(?i)(?:at\s+|vajta\s*)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|vajta)?"""
    )
    private val dosageRegex = Regex(
        """(?i)(\d+(?:\.\d+)?)\s*(mg|mcg|ml|tab(?:let)?s?|cap(?:sule)?s?|goli|golya|drop(?:s)?|puff(?:s)?|units?)"""
    )

    fun parse(input: String, nowMillis: Long = System.currentTimeMillis()): ParsedQuickAdd {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.getDefault())
        var cleaned = trimmed

        // 1. Priority & full-screen alarm cues
        var priority = ReminderPriority.HIGH
        var urgentAlarm = false
        when {
            listOf("urgent", "critical", "tatkal", "asap", "alarm", "insulin").any { lower.contains(it) } -> {
                priority = ReminderPriority.URGENT
                urgentAlarm = true
            }
            listOf("low", "optional", "vitamin", "supplement").any { lower.contains(it) } -> {
                priority = ReminderPriority.LOW
            }
            listOf("regular", "medium").any { lower.contains(it) } -> {
                priority = ReminderPriority.MEDIUM
            }
        }

        // 2. Medication Form
        val medForm = when {
            listOf("syrup", "tonic", "liquid", "ml").any { lower.contains(it) } -> MedicationForm.SYRUP
            listOf("capsule", "cap").any { lower.contains(it) } -> MedicationForm.CAPSULE
            listOf("injection", "insulin", "shot").any { lower.contains(it) } -> MedicationForm.INJECTION
            listOf("drop", "drops", "eye", "ear").any { lower.contains(it) } -> MedicationForm.DROPS
            listOf("inhaler", "puff", "asthma").any { lower.contains(it) } -> MedicationForm.INHALER
            else -> MedicationForm.TABLET
        }

        // 3. Dosage extraction
        val doseMatch = dosageRegex.find(trimmed)
        val dosage = if (doseMatch != null) {
            val amount = doseMatch.groupValues[1]
            val unitRaw = doseMatch.groupValues[2].lowercase(Locale.getDefault())
            val unitLabel = when {
                unitRaw.startsWith("goli") -> if (amount == "1") "Tablet" else "Tablets"
                unitRaw.startsWith("tab") -> if (amount == "1") "Tablet" else "Tablets"
                unitRaw.startsWith("cap") -> if (amount == "1") "Capsule" else "Capsules"
                else -> unitRaw
            }
            "$amount $unitLabel"
        } else {
            when (medForm) {
                MedicationForm.SYRUP -> "10 ml"
                MedicationForm.DROPS -> "2 Drops"
                MedicationForm.INHALER -> "2 Puffs"
                MedicationForm.CAPSULE -> "1 Capsule"
                else -> "1 Tablet"
            }
        }

        // 4. Meal Instruction
        val mealInstruction = when {
            listOf("empty stomach", "upashi", "upashi poti", "before breakfast").any { lower.contains(it) } ->
                MealInstruction.EMPTY_STOMACH
            listOf("before food", "before meal", "before lunch", "before dinner", "jevanaadhi", "jevan aadhi").any { lower.contains(it) } ->
                MealInstruction.BEFORE_FOOD
            listOf("with food", "with meal", "jevnsobat").any { lower.contains(it) } ->
                MealInstruction.WITH_FOOD
            listOf("anytime", "any time").any { lower.contains(it) } ->
                MealInstruction.ANYTIME
            else -> MealInstruction.AFTER_FOOD
        }

        // 5. Medicine Category
        val category = when {
            listOf("vitamin", "d3", "b12", "calcium", "zinc", "iron", "omega", "multivitamin").any { lower.contains(it) } ->
                ReminderCategory.VITAMINS
            listOf("bp", "heart", "cardio", "telma", "amlodipine", "atorva", "ecosprin", "cholesterol").any { lower.contains(it) } ->
                ReminderCategory.HEART_BP
            listOf("sugar", "diabetes", "metformin", "insulin", "glimepiride", "glycomet").any { lower.contains(it) } ->
                ReminderCategory.DIABETES
            listOf("antibiotic", "azithro", "amox", "augmentin", "cefixime", "course").any { lower.contains(it) } ->
                ReminderCategory.ANTIBIOTIC
            listOf("drop", "drops", "eye", "ear", "cream", "ointment", "skin", "lotion").any { lower.contains(it) } ->
                ReminderCategory.EYES_SKIN
            else -> ReminderCategory.DAILY_MEDS
        }

        // 6. Recurrence
        val recurrence = when {
            lower.contains("every 8") || lower.contains("8 hourly") || lower.contains("8 hrs") || lower.contains("thrice") ->
                ReminderRecurrence.EVERY_8_HOURS
            lower.contains("every 12") || lower.contains("12 hourly") || lower.contains("twice") ->
                ReminderRecurrence.EVERY_12_HOURS
            lower.contains("weekday") -> ReminderRecurrence.WEEKDAYS
            lower.contains("weekly") || lower.contains("every week") || lower.contains("dar aathavadyala") ->
                ReminderRecurrence.WEEKLY
            lower.contains("monthly") || lower.contains("every month") || lower.contains("dar mahinyala") ->
                ReminderRecurrence.MONTHLY
            lower.contains("once") || lower.contains("ektach") -> ReminderRecurrence.NONE
            lower.contains("daily") || lower.contains("every day") || lower.contains("everyday") || lower.contains("roj") ->
                ReminderRecurrence.DAILY
            else -> ReminderRecurrence.DAILY
        }

        // 7. Time parsing
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        var hasExplicitTime = false

        val relMatch = relativeRegex.find(lower)
        if (relMatch != null) {
            val amountStr = relMatch.groupValues[1].ifEmpty { relMatch.groupValues[3] }
            val unitStr = relMatch.groupValues[2].ifEmpty { relMatch.groupValues[4] }.lowercase()
            val amount = amountStr.toIntOrNull() ?: 15
            if (unitStr.startsWith("h")) {
                cal.add(Calendar.HOUR_OF_DAY, amount)
            } else {
                cal.add(Calendar.MINUTE, amount)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            hasExplicitTime = true
            cleaned = cleaned.replaceRange(relMatch.range, " ")
        } else {
            val isTomorrow = lower.contains("tomorrow") || lower.contains("udya") || lower.contains("tmrw")
            val isMorning = lower.contains("morning") || lower.contains("sakali")
            val isAfternoon = lower.contains("afternoon") || lower.contains("dupari")
            val isEvening = lower.contains("evening") || lower.contains("sandhyakali")
            val isNight = lower.contains("tonight") || lower.contains("night") || lower.contains("ratri")

            if (isTomorrow) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                hasExplicitTime = true
            }

            val timeMatch = clockRegex.findAll(lower).firstOrNull { match ->
                val hasUnit = match.groupValues[3].isNotBlank() ||
                    match.groupValues[2].isNotBlank() ||
                    match.value.trim().startsWith("at", ignoreCase = true)
                hasUnit && (match.groupValues[1].toIntOrNull() ?: 99) in 0..23
            }

            if (timeMatch != null) {
                var hour = timeMatch.groupValues[1].toInt()
                val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                val suffix = timeMatch.groupValues[3].lowercase()
                when {
                    suffix == "pm" && hour in 1..11 -> hour += 12
                    suffix == "am" && hour == 12 -> hour = 0
                    suffix != "am" && (isAfternoon || isEvening || isNight) && hour in 1..11 -> hour += 12
                }
                cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (!isTomorrow && cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                hasExplicitTime = true
                cleaned = cleaned.replaceRange(timeMatch.range, " ")
            } else if (isMorning || isAfternoon || isEvening || isNight || isTomorrow) {
                val defaultHour = when {
                    isMorning -> 8
                    isAfternoon -> 13
                    isEvening -> 18
                    isNight -> 21
                    else -> 9
                }
                cal.set(Calendar.HOUR_OF_DAY, defaultHour)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (!isTomorrow && cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                hasExplicitTime = true
            } else {
                cal.add(Calendar.MINUTE, 15)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
        }

        // Clean scheduling & meal filler tokens to leave crisp medicine name
        val noiseTokens = listOf(
            "udya", "sakali", "dupari", "sandhyakali", "ratricha", "ratri", "vajta", "aaj",
            "tomorrow", "tmrw", "tonight", "today", "morning", "afternoon", "evening", "night",
            "every day", "everyday", "daily", "roj", "weekly", "monthly", "weekdays", "once",
            "every 8 hours", "every 12 hours", "every 8h", "every 12h",
            "before food", "after food", "with food", "before meal", "after meal", "with meal",
            "empty stomach", "upashi poti", "upashi", "jevananantar", "jevanaadhi", "jevan aadhi",
            "urgent", "tatkal", "critical", "goli", "golya", "tablet", "tablets", "capsule", "capsules"
        )
        var finalTitle = cleaned
        noiseTokens.forEach { token ->
            finalTitle = finalTitle.replace(Regex("""(?i)\b${Regex.escape(token)}\b"""), " ")
        }
        finalTitle = finalTitle.replace(Regex("""\s+"""), " ").trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .ifBlank { trimmed.take(120) }
            .take(120)

        val timeFmt = SimpleDateFormat("EEE, h:mm a", Locale.getDefault())
        return ParsedQuickAdd(
            draft = ReminderDraft(
                title = finalTitle,
                dueAtMillis = cal.timeInMillis,
                priority = priority,
                category = category,
                recurrence = recurrence,
                urgentAlarm = urgentAlarm,
                dosage = dosage,
                medForm = medForm,
                mealInstruction = mealInstruction
            ),
            detectedTimeLabel = timeFmt.format(Date(cal.timeInMillis)),
            hasExplicitTime = hasExplicitTime
        )
    }
}
