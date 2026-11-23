package com.example.report

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.DoctorAppointment
import com.example.data.model.Reminder
import com.example.data.model.VitalRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    fun generateAndShareReport(
        context: Context,
        patientName: String,
        reminders: List<Reminder>,
        vitals: List<VitalRecord>,
        appointments: List<DoctorAppointment>
    ): File {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 118, 110)
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 11f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }

        val dateFmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
        var y = 48f

        fun ensureSpace(needed: Float = 26f) {
            if (y + needed > pageHeight - 48f) {
                doc.finishPage(page)
                pageNumber += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }
        }

        fun drawDivider() {
            canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
            y += 14f
        }

        // Header
        canvas.drawText("Remindly — Medication & Vitals Report", 40f, y, titlePaint)
        y += 20f
        canvas.drawText(
            "Patient / Profile: $patientName   •   Generated: ${dateFmt.format(Date())}",
            40f,
            y,
            mutedPaint
        )
        y += 14f
        drawDivider()

        // Adherence Summary
        val totalTaken = reminders.sumOf { it.takenCount }
        val totalSkipped = reminders.sumOf { it.skippedCount }
        val totalLogged = totalTaken + totalSkipped
        val adherencePct = if (totalLogged > 0) (totalTaken * 100) / totalLogged else 100
        canvas.drawText("1. Adherence Summary", 40f, y, headerPaint)
        y += 18f
        canvas.drawText(
            "Active Medicines: ${reminders.count { !it.completed }}   |   Doses Taken: $totalTaken   |   Skipped: $totalSkipped   |   Adherence: $adherencePct%",
            40f,
            y,
            bodyPaint
        )
        y += 18f
        drawDivider()

        // Active Medicines
        ensureSpace(40f)
        canvas.drawText("2. Medication Schedule (${reminders.size})", 40f, y, headerPaint)
        y += 18f
        if (reminders.isEmpty()) {
            canvas.drawText("No medications recorded for this profile.", 40f, y, mutedPaint)
            y += 18f
        } else {
            reminders.forEach { med ->
                ensureSpace(34f)
                val courseTag = if (med.courseDays > 0) " • Course: ${med.takenCount}/${med.courseDays}d" else ""
                val stockTag = if (med.stockCount >= 0) " • Stock: ${med.stockCount}" else ""
                canvas.drawText(
                    "• ${med.title} (${med.doseDisplaySummary}) — ${med.recurrenceEnum.label}$courseTag$stockTag",
                    40f,
                    y,
                    bodyPaint
                )
                y += 14f
                val subLine = buildList {
                    if (med.prescribedBy.isNotBlank()) add("Dr: ${med.prescribedBy}")
                    add("Taken: ${med.takenCount}, Skipped: ${med.skippedCount}")
                    if (med.notes.isNotBlank()) add(med.notes.take(65))
                }.joinToString(" | ")
                canvas.drawText("   $subLine", 40f, y, mutedPaint)
                y += 16f
            }
        }
        drawDivider()

        // Recent Health Vitals
        ensureSpace(40f)
        canvas.drawText("3. Recent Health Vitals (${vitals.size})", 40f, y, headerPaint)
        y += 18f
        if (vitals.isEmpty()) {
            canvas.drawText("No vitals readings logged yet.", 40f, y, mutedPaint)
            y += 18f
        } else {
            vitals.take(15).forEach { v ->
                ensureSpace(20f)
                canvas.drawText(
                    "• ${dateFmt.format(Date(v.recordedAtMillis))} — ${v.typeEnum.label}: ${v.formattedValue()} ${v.notes.take(40)}",
                    40f,
                    y,
                    bodyPaint
                )
                y += 16f
            }
        }
        drawDivider()

        // Appointments
        ensureSpace(40f)
        canvas.drawText("4. Doctor Appointments (${appointments.size})", 40f, y, headerPaint)
        y += 18f
        if (appointments.isEmpty()) {
            canvas.drawText("No appointments scheduled.", 40f, y, mutedPaint)
            y += 18f
        } else {
            appointments.take(10).forEach { appt ->
                ensureSpace(20f)
                val status = if (appt.completed) "Done" else "Upcoming"
                canvas.drawText(
                    "• ${appt.doctorName} (${appt.specialty.ifBlank { "Consultation" }}) — ${dateFmt.format(Date(appt.appointmentAtMillis))} [$status]",
                    40f,
                    y,
                    bodyPaint
                )
                y += 16f
            }
        }

        ensureSpace(24f)
        y += 8f
        canvas.drawText(
            "Generated by Remindly. For clinical decisions, always rely on your prescribing physician.",
            40f,
            y,
            mutedPaint
        )

        doc.finishPage(page)

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = patientName.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "Patient" }
        val outFile = File(reportsDir, "Remindly_Report_${safeName}.pdf")
        FileOutputStream(outFile).use { stream ->
            doc.writeTo(stream)
        }
        doc.close()
        return outFile
    }

    fun sharePdfFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Remindly Medication & Health Vitals Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Doctor Report (PDF)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
