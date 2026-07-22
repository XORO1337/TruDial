package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.IncidentReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the incident list to a PDF and writes it into the device's public
 * Downloads folder. Returns the human-readable location on success, or null on failure.
 *
 * On API 29+ this uses MediaStore (no runtime permission required). On older
 * versions it writes directly to the public Downloads directory, which needs
 * WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion="28" in the manifest).
 */
object ReportExporter {

    // A4 at 72dpi.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 18f

    fun exportToDownloads(context: Context, incidents: List<IncidentReport>): String? {
        val document = buildPdf(incidents)
        val fileName = "TruDial_Incident_Report_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".pdf"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, document, fileName)
            } else {
                writeLegacy(document, fileName)
            }
        } catch (e: Exception) {
            null
        } finally {
            document.close()
        }
    }

    private fun writeViaMediaStore(context: Context, document: PdfDocument, fileName: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Unable to create Downloads entry")

        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out) { "Null output stream" }
            document.writeTo(out)
        }

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "Downloads/$fileName"
    }

    private fun writeLegacy(document: PdfDocument, fileName: String): String {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()
        val file = File(downloads, fileName)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        return "Downloads/$fileName"
    }

    private fun buildPdf(incidents: List<IncidentReport>): PdfDocument {
        val document = PdfDocument()

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val metaPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
            isAntiAlias = true
        }
        val rulePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        val contentWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

        var pageNumber = 1
        var page = document.startPage(pageInfo(pageNumber))
        var canvas = page.canvas
        var y = MARGIN

        // Report header.
        canvas.drawText("TruDial — Incident Report", MARGIN, y + 16f, titlePaint)
        y += 28f
        canvas.drawText(
            "Generated ${dateFormat.format(Date())}   •   ${incidents.size} incident(s)",
            MARGIN, y, metaPaint
        )
        y += 12f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
        y += 20f

        if (incidents.isEmpty()) {
            canvas.drawText("No incidents recorded.", MARGIN, y, bodyPaint)
        }

        for (incident in incidents) {
            // Estimate the space this entry needs; start a new page if it won't fit.
            val summaryLines = wrapText(incident.transcriptSummary, bodyPaint, contentWidth)
            val neededHeight = LINE_HEIGHT * (3 + summaryLines.size) + 16f
            if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(pageInfo(pageNumber))
                canvas = page.canvas
                y = MARGIN
            }

            canvas.drawText(incident.callerId, MARGIN, y, headerPaint)
            val riskLabel = "Risk: ${incident.riskLevel}"
            val riskWidth = bodyPaint.measureText(riskLabel)
            canvas.drawText(riskLabel, PAGE_WIDTH - MARGIN - riskWidth, y, bodyPaint)
            y += LINE_HEIGHT

            canvas.drawText(dateFormat.format(Date(incident.timestamp)), MARGIN, y, metaPaint)
            val status = if (incident.isReported) "Reported to Cybercell" else "Not reported"
            val statusWidth = metaPaint.measureText(status)
            canvas.drawText(status, PAGE_WIDTH - MARGIN - statusWidth, y, metaPaint)
            y += LINE_HEIGHT

            for (line in summaryLines) {
                canvas.drawText(line, MARGIN, y, bodyPaint)
                y += LINE_HEIGHT
            }

            y += 6f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rulePaint)
            y += 16f
        }

        document.finishPage(page)
        return document
    }

    private fun pageInfo(pageNumber: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()

    /** Greedy word-wrap so long transcript summaries stay inside the page margins. */
    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        if (text.isBlank()) return listOf("(no summary)")
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
