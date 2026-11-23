package com.example.notifications

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.data.model.MealInstruction
import java.util.Locale

class DoseSpeaker(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    private var pendingLang: String = "en"

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                pendingText?.let { text ->
                    speakInternal(text, pendingLang)
                    pendingText = null
                }
            }
        }
    }

    fun speakDose(
        title: String,
        dosage: String,
        mealInstructionRaw: String,
        languageCode: String
    ) {
        val meal = MealInstruction.fromRaw(mealInstructionRaw)
        val phrase = if (languageCode == "mr") {
            val mealMr = when (meal) {
                MealInstruction.BEFORE_FOOD -> "जेवणाआधी घ्या"
                MealInstruction.AFTER_FOOD -> "जेवणानंतर घ्या"
                MealInstruction.WITH_FOOD -> "जेवणासोबत घ्या"
                MealInstruction.EMPTY_STOMACH -> "उपाशी पोटी घ्या"
                MealInstruction.ANYTIME -> "कधीही घ्या"
            }
            "औषध घेण्याची वेळ झाली आहे. $title, $dosage, $mealMr."
        } else {
            "Time to take your medicine: $title, $dosage, ${meal.label}."
        }
        if (isReady) {
            speakInternal(phrase, languageCode)
        } else {
            pendingText = phrase
            pendingLang = languageCode
        }
    }

    private fun speakInternal(text: String, languageCode: String) {
        val engine = tts ?: return
        val locale = if (languageCode == "mr") Locale("mr", "IN") else Locale("en", "IN")
        val availability = engine.isLanguageAvailable(locale)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = locale
        } else {
            engine.language = Locale.getDefault()
        }
        engine.setSpeechRate(0.95f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "remindly_dose_tts")
    }

    fun shutdown() {
        pendingText = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isReady = false
    }
}
