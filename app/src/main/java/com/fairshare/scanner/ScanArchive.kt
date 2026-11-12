package com.fairshare.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fairshare.data.model.Money
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * For development only (BuildConfig.SAVE_SCANS, on in debug builds, off in release): keeps every scanned photo with
 * what was read from it, so a wrong reading can be checked on the PC next to the log and turned into a test case.
 *
 * One folder per scan in the app's own external storage, Android/data/com.fairshare/files/skany/<date_time>/:
 * - `zdjecie.jpg`: the photo exactly as taken or picked from the gallery
 * - `ocr.txt`: the text ML Kit gave the rules (RuleBasedReceiptParser.parse(it) reproduces the rules' reading)
 * - `wynik.txt`: timings, what the rules and Gemini read, errors
 * - `linie.json`: the OCR lines with their boxes in the upright photo (to check how receipts were told apart)
 *
 * Not shown in the gallery. scripts\zapisz-log.bat copies new scans to the PC (skany\). The newest [KEEP] are kept.
 */
class ScanArchive(context: Context) {
    private val root: File? = context.getExternalFilesDir(FOLDER)
    private val resolver = context.contentResolver

    /** Saves one scan (also a failed one); returns its folder name, e.g. "2026-11-04_18-05-12", or null if it failed. */
    suspend fun save(photo: Uri, outcome: ReceiptScanner.Outcome?, error: String?): String? = withContext(Dispatchers.IO) {
        val base = root ?: return@withContext null
        val id = LocalDateTime.now().format(ID_FORMAT)
        try {
            val dir = File(base, id).apply { mkdirs() }
            resolver.openInputStream(photo)?.use { input ->
                File(dir, "zdjecie.jpg").outputStream().use { input.copyTo(it) }
            }
            if (outcome != null) {
                File(dir, "ocr.txt").writeText(outcome.ocrText)
                File(dir, "linie.json").writeText(linesJson(outcome.lines))
            }
            File(dir, "wynik.txt").writeText(describe(outcome, error))
            prune(base)
            Log.i(TAG, "Zapisano skan: skany/$id")
            id
        } catch (e: IOException) {
            Log.w(TAG, "Nie udało się zapisać skanu: $e")
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Nie udało się zapisać skanu: $e")
            null
        }
    }

    private fun describe(outcome: ReceiptScanner.Outcome?, error: String?): String = buildString {
        if (error != null) appendLine("Błąd: $error")
        if (outcome == null) return@buildString
        appendLine("ML Kit: ${outcome.ocrMs} ms (z próbami obrotu ${outcome.ocrTotalMs} ms), obrót ${outcome.rotation}°, " +
            "pochylenie linii ${"%.1f".format(outcome.textAngle)}°")
        if (outcome.parts.size > 1) appendLine("Paragonów na zdjęciu: ${outcome.parts.size}")
        if (outcome.multipleSuspected) appendLine("Uwaga: dwa nagłówki PARAGON FISKALNY, ale nie dało się rozdzielić paragonów")
        appendLine("Łącznie: ${outcome.totalMs} ms")
        outcome.parts.forEachIndexed { index, part ->
            val label = if (outcome.parts.size > 1) " (paragon ${index + 1})" else ""
            appendLine()
            appendLine("#### Paragon ${index + 1}")
            appendLine("Reguły: ${part.rulesMs} ms")
            part.aiModel?.let { appendLine("Gemini: $it, ${part.aiMs} ms") }
            part.aiError?.let { appendLine("Gemini, błąd po ${part.aiMs} ms: $it") }
            appendLine("Wynik pokazany na ekranie: ${part.receipt.source.label}")
            appendReceipt("Reguły$label", part.byRules)
            part.byAi?.let { appendReceipt("Gemini$label", it) }
            if (outcome.parts.size > 1) {
                appendLine()
                appendLine("Tekst tego paragonu:")
                appendLine(part.ocrText)
            }
        }
    }

    private fun linesJson(lines: List<OcrLine>): String = JSONArray().apply {
        lines.forEach { line ->
            put(
                JSONObject().put("text", line.text).put("left", line.left.toDouble()).put("top", line.top.toDouble())
                    .put("right", line.right.toDouble()).put("bottom", line.bottom.toDouble()),
            )
        }
    }.toString(1)

    private fun StringBuilder.appendReceipt(title: String, receipt: ScannedReceipt) {
        appendLine()
        appendLine("== $title ==")
        appendLine("Sklep: ${receipt.store ?: "?"}")
        appendLine("Do zapłaty: ${receipt.totalGrosze?.let { Money.format(it) } ?: "nie znaleziono"}")
        appendLine("Suma pozycji: ${Money.format(receipt.itemsSumGrosze)} (zgodna: ${if (receipt.sumMatches) "tak" else "nie"})")
        receipt.items.forEach { appendLine("  ${it.name} = ${Money.format(it.priceGrosze)}") }
        receipt.notes.forEach { appendLine("  uwaga: $it") }
    }

    private fun prune(base: File) {
        base.listFiles { file -> file.isDirectory }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.deleteRecursively() }
    }

    private companion object {
        const val FOLDER = "skany"
        const val KEEP = 50
        const val TAG = "FairShareScan"
        val ID_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
