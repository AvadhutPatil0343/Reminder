package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.MealInstruction
import com.example.data.model.MedicationForm
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderDraft
import com.example.data.model.ReminderPriority
import com.example.data.model.ReminderRecurrence
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.max

data class ScannedMedicineItem(
    val id: String,
    val name: String,
    val dosage: String,
    val medForm: MedicationForm,
    val mealInstruction: MealInstruction,
    val category: ReminderCategory,
    val recurrence: ReminderRecurrence,
    val courseDays: Int,
    val suggestedHour: Int,
    val prescribedBy: String,
    val notes: String,
    val selected: Boolean = true
) {
    fun toReminderDraft(profileId: String, profileName: String): ReminderDraft {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, suggestedHour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return ReminderDraft(
            profileId = profileId,
            profileName = profileName,
            title = name.take(120),
            notes = notes.take(500),
            dueAtMillis = cal.timeInMillis,
            leadTimeMinutes = 5L,
            priority = if (category == ReminderCategory.HEART_BP || category == ReminderCategory.DIABETES) {
                ReminderPriority.URGENT
            } else {
                ReminderPriority.HIGH
            },
            category = category,
            recurrence = recurrence,
            urgentAlarm = category == ReminderCategory.HEART_BP || category == ReminderCategory.DIABETES,
            dosage = dosage.take(60),
            medForm = medForm,
            mealInstruction = mealInstruction,
            prescribedBy = prescribedBy.take(80),
            stockCount = if (courseDays > 0) (courseDays * 2).coerceIn(1, 365) else 30,
            dosePerIntake = 1,
            refillThreshold = 5,
            courseDays = courseDays.coerceIn(0, 365),
            courseStartMillis = if (courseDays > 0) System.currentTimeMillis() else 0L
        )
    }
}

class MedicineAiService {

    private val prescriptionSchema = Schema.obj(
        properties = mapOf(
            "doctorName" to Schema.string(description = "Doctor or clinic name if visible on prescription, else empty string"),
            "medicines" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Medicine brand or generic name"),
                        "dosage" to Schema.string(description = "Strength or dose amount e.g. 500 mg, 1 Tablet, 10 ml"),
                        "medForm" to Schema.enumeration(
                            values = listOf("TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROPS", "INHALER", "OTHER")
                        ),
                        "mealInstruction" to Schema.enumeration(
                            values = listOf("BEFORE_FOOD", "AFTER_FOOD", "WITH_FOOD", "EMPTY_STOMACH", "ANYTIME")
                        ),
                        "category" to Schema.enumeration(
                            values = listOf("DAILY_MEDS", "VITAMINS", "HEART_BP", "DIABETES", "ANTIBIOTIC", "EYES_SKIN")
                        ),
                        "recurrence" to Schema.enumeration(
                            values = listOf("NONE", "EVERY_8_HOURS", "EVERY_12_HOURS", "DAILY", "WEEKDAYS", "WEEKLY")
                        ),
                        "courseDays" to Schema.integer(description = "Number of days prescribed, or 0 if ongoing/daily"),
                        "suggestedHour" to Schema.integer(description = "Suggested 24-hour clock hour (0-23) for first dose"),
                        "notes" to Schema.string(description = "Brief usage notes or frequency e.g. 1-0-1 after meals")
                    ),
                    optionalProperties = listOf("notes")
                ),
                description = "All distinct medicines found in the prescription or medicine strip image"
            )
        ),
        optionalProperties = listOf("doctorName")
    )

    private val scannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = prescriptionSchema
            }
        )
    }

    private val infoModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun scanPrescriptionOrStrip(rawBitmap: Bitmap): List<ScannedMedicineItem> {
        val scaled = scaleDownBitmap(rawBitmap, maxDimension = 1280)
        return try {
            val response = withTimeout(60_000L) {
                scannerModel.generateContent(
                    content {
                        image(scaled)
                        text(
                            "Analyze this medical prescription, pill strip, medicine bottle, or syrup box. " +
                                "Extract every distinct medication with its exact name, dosage, form, meal timing " +
                                "(e.g. before/after food or empty stomach), likely category, repeat interval " +
                                "(1-0-1 or BD -> EVERY_12_HOURS, 1-1-1 or TDS -> EVERY_8_HOURS, OD -> DAILY), " +
                                "course duration in days (0 if ongoing), suggested hour (0-23), and doctor name if visible."
                        )
                    }
                )
            }
            val jsonText = response.text ?: throw IllegalStateException("AI returned an empty scan result.")
            val root = JSONObject(jsonText)
            val doctor = root.optString("doctorName", "").trim()
            val arr = root.optJSONArray("medicines") ?: throw IllegalStateException("No medicines found in image.")
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name", "").trim()
                    if (name.isNotBlank()) {
                        add(
                            ScannedMedicineItem(
                                id = "scan_${i}_${System.currentTimeMillis()}",
                                name = name,
                                dosage = obj.optString("dosage", "1 Tablet").ifBlank { "1 Tablet" },
                                medForm = MedicationForm.fromRaw(obj.optString("medForm")),
                                mealInstruction = MealInstruction.fromRaw(obj.optString("mealInstruction")),
                                category = ReminderCategory.fromRaw(obj.optString("category")),
                                recurrence = ReminderRecurrence.fromRaw(obj.optString("recurrence")),
                                courseDays = obj.optInt("courseDays", 0).coerceIn(0, 365),
                                suggestedHour = obj.optInt("suggestedHour", 9).coerceIn(0, 23),
                                prescribedBy = doctor,
                                notes = obj.optString("notes", "").trim()
                            )
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Prescription scan timed out", e)
            throw IllegalStateException("Scan timed out after 60 seconds. Please try a clearer photo.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Prescription scan failed", e)
            throw e
        }
    }

    fun explainMedicineInfo(
        medicineName: String,
        dosage: String,
        languageCode: String
    ): Flow<String> = flow {
        val isMarathi = languageCode == "mr"
        val prompt = if (isMarathi) {
            "औषधाचे नाव: $medicineName ($dosage). " +
                "रुग्णाला समजेल अशा सोप्या मराठीत ३ लहान मुद्द्यांमध्ये माहिती द्या: " +
                "१) हे औषध सामान्यतः कशासाठी वापरले जाते, " +
                "२) घेतानाची सामान्य काळजी (जेवण, पाणी, झोप येणे इ.), " +
                "३) डॉक्टरांना कधी विचारावे. " +
                "शेवटी स्पष्ट लिहा की हे फक्त माहितीसाठी आहे, डोससाठी नेहमी डॉक्टरांचा सल्ला घ्या."
        } else {
            "Medicine: $medicineName ($dosage). " +
                "Provide clear, patient-friendly information in 3 concise bullet points: " +
                "1) Common medical use, " +
                "2) Practical precautions (food, water, drowsiness, missed dose tip), " +
                "3) When to consult a doctor. " +
                "End with a short reminder to always follow their prescribing doctor's advice."
        }
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                infoModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI returned an empty medicine summary.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Medicine info stream timed out", e)
            throw IllegalStateException("Request timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Medicine info generation failed", e)
            throw e
        }
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private companion object {
        const val TAG = "MedicineAiService"
    }
}
