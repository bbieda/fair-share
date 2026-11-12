package com.fairshare.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.fairshare.BuildConfig
import com.fairshare.scanner.rules.RuleBasedReceiptParser
import kotlin.math.roundToInt

/**
 * The recommended setup from the evaluation (backend/WNIOSKI.md): ML Kit + rules on the phone; only when the items do
 * not add up to the total, the photo goes to Gemini Flash-Lite. On the 44 test receipts this read 44/44 correctly,
 * with the AI needed for 6 of them.
 *
 * A photo with several receipts ([ReceiptSplitter]) is read as several receipts, each with its own checksum; the AI
 * then gets only the part of the photo with the receipt that did not add up.
 */
class ReceiptScanner(context: Context) {
    private val ocr = ReceiptOcr(context.applicationContext)
    private val gemini: GeminiReceiptReader? = BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }?.let { key ->
        GeminiReceiptReader(key, BuildConfig.GEMINI_MODELS.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }

    val aiAvailable: Boolean get() = gemini != null

    /** First model tried by the AI step, for the screen ("gemini-3.5-flash-lite"). */
    val aiModel: String? get() = gemini?.firstModel

    fun close() = ocr.close()

    /** One receipt found in the photo. */
    class Part(
        /** What to use: the rules' reading when its sum matches, otherwise the AI's (if it answered). */
        val receipt: ScannedReceipt,
        val byRules: ScannedReceipt,
        /** The rows the rules got for this receipt. */
        val ocrText: String,
        val rulesMs: Long,
        val aiModel: String? = null,
        /** Time of the AI step, also when it failed. */
        val aiMs: Long? = null,
        val aiError: String? = null,
        /** Gemini's reading, when it was asked (also when the rules' reading was kept). */
        val byAi: ScannedReceipt? = null,
    )

    /** Everything the scan produced, also the intermediate steps, so the test screen can show them. */
    class Outcome(
        /** One per receipt in the photo, left to right / top to bottom; usually one. */
        val parts: List<Part>,
        /** All the text ML Kit read in the photo, as rows. */
        val ocrText: String,
        val rotation: Int,
        val ocrMs: Long,
        val ocrTotalMs: Long,
        /** Median tilt of the text lines, degrees. */
        val textAngle: Float = 0f,
        /** OCR lines with their boxes (saved by debug builds to check how receipts were told apart). */
        val lines: List<OcrLine> = emptyList(),
        /** Two "PARAGON FISKALNY" headers but no gap between the receipts: read as one, probably wrong. */
        val multipleSuspected: Boolean = false,
    ) {
        private val first: Part get() = parts.first()
        val receipt: ScannedReceipt get() = first.receipt
        val byRules: ScannedReceipt get() = first.byRules
        val rulesMs: Long get() = parts.sumOf { it.rulesMs }
        val aiModel: String? get() = parts.firstNotNullOfOrNull { it.aiModel }
        val aiMs: Long? get() = parts.mapNotNull { it.aiMs }.takeIf { it.isNotEmpty() }?.sum()
        val aiError: String? get() = parts.firstNotNullOfOrNull { it.aiError }
        val byAi: ScannedReceipt? get() = first.byAi
        val totalMs: Long get() = ocrTotalMs + rulesMs + (aiMs ?: 0)
    }

    suspend fun scan(photo: Uri, onStage: (String) -> Unit = {}): Outcome {
        onStage("Czytam paragon (ML Kit)…")
        val read = ocr.read(photo)
        Log.i(TAG, "ML Kit ${read.ms} ms (z próbami obrotu ${read.totalMs} ms), obrót ${read.rotation}°, " +
            "pochylenie linii ${"%.1f".format(read.angle)}°, ${read.text.lines().size} wierszy")
        // the text the rules got, to reproduce a wrong reading in the backend tests (scripts\zapisz-log.bat)
        read.text.chunked(3000).forEach { Log.d(TAG, "OCR:\n$it") }
        if (read.text.isBlank()) throw ScanException("Nie znaleziono tekstu na zdjęciu. Zrób zdjęcie bliżej i przy lepszym świetle.")

        val groups = ReceiptSplitter.split(read.lines)
        val headers = read.lines.count { ReceiptSplitter.isFiscalHeader(it.text) }
        val parts = if (groups.size == 1) {
            listOf(readPart(read.text, read.upright, "", onStage))
        } else {
            Log.i(TAG, "Na zdjęciu ${groups.size} paragony")
            groups.mapIndexed { index, group ->
                val label = " (paragon ${index + 1} z ${groups.size})"
                readPart(ReceiptRows.rebuild(group, read.tilt), crop(read.upright, group), label, onStage)
            }
        }
        return Outcome(
            parts = parts,
            ocrText = read.text,
            rotation = read.rotation,
            ocrMs = read.ms,
            ocrTotalMs = read.totalMs,
            textAngle = read.angle,
            lines = read.lines,
            multipleSuspected = groups.size == 1 && headers >= 2,
        )
    }

    /** Rules on one receipt's rows, then the AI on its picture when the items do not add up. */
    private suspend fun readPart(text: String, picture: Bitmap, label: String, onStage: (String) -> Unit): Part {
        val rulesStarted = SystemClock.elapsedRealtime()
        val byRules = ScannedReceipt.fromRules(RuleBasedReceiptParser.parse(text))
        val rulesMs = SystemClock.elapsedRealtime() - rulesStarted
        Log.i(TAG, "Reguły$label $rulesMs ms: ${byRules.describe()}")
        if (byRules.sumMatches || gemini == null) return Part(byRules, byRules, text, rulesMs)

        onStage("Suma się nie zgadza$label, doczytuję zdjęcie przez Gemini…")
        val aiStarted = SystemClock.elapsedRealtime()
        return try {
            val ai = gemini.read(picture)
            Log.i(TAG, "Gemini$label ${ai.model} ${ai.ms} ms, zapytań ${ai.attempts}: ${ai.receipt.describe()}")
            // the AI's answer wins unless it is further from adding up than the rules
            val receipt = if (ai.receipt.items.isNotEmpty() && ai.receipt.gap <= byRules.gap) ai.receipt else byRules
            Part(receipt, byRules, text, rulesMs, aiModel = ai.model, aiMs = ai.ms, byAi = ai.receipt)
        } catch (e: ScanException) {
            val ms = SystemClock.elapsedRealtime() - aiStarted
            Log.w(TAG, "Gemini$label po $ms ms: ${e.message}")
            Part(byRules, byRules, text, rulesMs, aiMs = ms, aiError = e.message)
        }
    }

    /** The part of the photo with one receipt (its lines' box and a margin), for the AI. */
    private fun crop(photo: Bitmap, lines: List<OcrLine>): Bitmap {
        val margin = 0.03f * maxOf(photo.width, photo.height)
        val left = (lines.minOf { it.left } - margin).roundToInt().coerceIn(0, photo.width - 1)
        val top = (lines.minOf { it.top } - margin).roundToInt().coerceIn(0, photo.height - 1)
        val right = (lines.maxOf { it.right } + margin).roundToInt().coerceIn(left + 1, photo.width)
        val bottom = (lines.maxOf { it.bottom } + margin).roundToInt().coerceIn(top + 1, photo.height)
        return Bitmap.createBitmap(photo, left, top, right - left, bottom - top)
    }

    private fun ScannedReceipt.describe(): String =
        "sklep=$store, ${items.size} poz., suma pozycji=$itemsSumGrosze gr, do zapłaty=$totalGrosze gr, zgodna=$sumMatches" +
            items.joinToString("") { "\n  ${it.name} = ${it.priceGrosze}" }

    private companion object {
        /** Logcat tag; scripts\zapisz-log.bat saves these lines from the phone. */
        const val TAG = "FairShareScan"
    }
}
