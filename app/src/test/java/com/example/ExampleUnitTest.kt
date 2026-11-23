package com.example

import com.example.ai.ScannedMedicineItem
import com.example.data.model.MealInstruction
import com.example.data.model.MedicationForm
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import com.example.data.model.VitalRecord
import com.example.data.model.VitalType
import com.example.data.parser.SmartReminderParser
import com.example.ui.i18n.stringsForLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {

    @Test
    fun `smart parser extracts medicine dosage meal instruction and Marathi times`() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val marathi = SmartReminderParser.parse(
            "Udya sakali 7 vajta Metformin 1 goli upashi poti urgent",
            baseCal.timeInMillis
        )
        assertEquals(ReminderCategory.DIABETES, marathi.draft.category)
        assertEquals(ReminderPriority.URGENT, marathi.draft.priority)
        assertEquals(MedicationForm.TABLET, marathi.draft.medForm)
        assertEquals(MealInstruction.EMPTY_STOMACH, marathi.draft.mealInstruction)
        assertEquals("1 Tablet", marathi.draft.dosage)
        assertTrue(marathi.draft.urgentAlarm)
        assertTrue(marathi.hasExplicitTime)
        val parsedCal = Calendar.getInstance().apply { timeInMillis = marathi.draft.dueAtMillis }
        assertEquals(7, parsedCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(11, parsedCal.get(Calendar.DAY_OF_MONTH))

        val intervalMed = SmartReminderParser.parse(
            "Azithromycin 500 mg tablet every 12 hours after food in 30 mins",
            baseCal.timeInMillis
        )
        assertEquals(ReminderCategory.ANTIBIOTIC, intervalMed.draft.category)
        assertEquals(ReminderRecurrence.EVERY_12_HOURS, intervalMed.draft.recurrence)
        assertEquals(MealInstruction.AFTER_FOOD, intervalMed.draft.mealInstruction)
        assertEquals("500 mg", intervalMed.draft.dosage)
        assertEquals(baseCal.timeInMillis + 30 * 60_000L, intervalMed.draft.dueAtMillis)
    }

    @Test
    fun `medicine stock threshold and 8h interval calculation work accurately`() {
        val next8h = ReminderRecurrence.EVERY_8_HOURS.nextOccurrenceMillis(System.currentTimeMillis())
        assertTrue(next8h > System.currentTimeMillis())

        val lowStockMed = Reminder(
            title = "Dolo 650",
            dosage = "1 Tablet",
            mealInstruction = MealInstruction.AFTER_FOOD.name,
            stockCount = 4,
            refillThreshold = 5
        )
        assertTrue(lowStockMed.isLowStock)
        assertEquals("1 Tablet • After Meal", lowStockMed.doseDisplaySummary)
    }

    @Test
    fun `course progress vital formatting scanned item draft and localization work`() {
        val now = 1_700_000_000_000L
        val courseStart = now - (2 * 86_400_000L)
        val antibiotic = Reminder(
            title = "Augmentin 625",
            courseDays = 5,
            courseStartMillis = courseStart,
            completed = false
        )
        assertEquals(3, antibiotic.currentCourseDay(now))
        assertEquals(0.6f, antibiotic.courseProgressFraction(now), 0.01f)

        val bp = VitalRecord(
            type = VitalType.BP.name,
            valuePrimary = 122.0,
            valueSecondary = 80.0,
            unit = "mmHg"
        )
        assertEquals("122/80 mmHg", bp.formattedValue())

        val scanned = ScannedMedicineItem(
            id = "scan_1",
            name = "Telma 40",
            dosage = "40 mg",
            medForm = MedicationForm.TABLET,
            mealInstruction = MealInstruction.BEFORE_FOOD,
            category = ReminderCategory.HEART_BP,
            recurrence = ReminderRecurrence.DAILY,
            courseDays = 30,
            suggestedHour = 8,
            prescribedBy = "Dr. Joshi",
            notes = "1-0-0 morning"
        )
        val draft = scanned.toReminderDraft("fam_aai", "Aai")
        assertEquals("fam_aai", draft.profileId)
        assertEquals("Aai", draft.profileName)
        assertEquals(30, draft.courseDays)
        assertTrue(draft.urgentAlarm)

        assertEquals("काळजी", stringsForLanguage("mr").tabCare)
        assertEquals("Care", stringsForLanguage("en").tabCare)
    }
}
