package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MealInstruction
import com.example.data.model.MedicationForm
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import com.example.data.parser.SmartReminderParser
import com.example.data.preferences.AlertTone
import com.example.notifications.LocalAlarmStore
import com.example.notifications.NotificationHelper
import com.example.notifications.ScheduledAlarmRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Remindly", appName)
    }

    @Test
    fun `notification channels and local alarm store work on Android`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.ensureNotificationChannels(context)

        val store = LocalAlarmStore(context)
        val record = ScheduledAlarmRecord(
            reminderId = "med_test_1",
            title = "Telma 40",
            notes = "Check BP before dose",
            dosage = "1 Tablet",
            mealInstruction = MealInstruction.BEFORE_FOOD.name,
            triggerAtMillis = System.currentTimeMillis() + 60_000L,
            leadTimeMinutes = 15L,
            priority = "URGENT",
            category = "HEART_BP",
            recurrence = "DAILY",
            urgentAlarm = true
        )
        store.updateSnooze("med_test_1", record.triggerAtMillis)
        store.saveAlertSettings(
            quietEnabled = true,
            quietStartHour = 22,
            quietEndHour = 7,
            alertTone = AlertTone.CHIME.name,
            vibrationEnabled = true
        )
        assertTrue(store.isQuietHoursActive(23))
        assertFalse(store.isQuietHoursActive(12))
        assertEquals(AlertTone.CHIME.name, store.getAlertTone())

        val next8h = ReminderRecurrence.EVERY_8_HOURS.nextOccurrenceMillis(System.currentTimeMillis())
        assertTrue(next8h > System.currentTimeMillis())

        val lowStockMed = Reminder(title = "Dolo 650", stockCount = 4, refillThreshold = 5)
        assertTrue(lowStockMed.isLowStock)
    }

    @Test
    fun `smart parser parses English and Marathi medicine reminders`() {
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
}
