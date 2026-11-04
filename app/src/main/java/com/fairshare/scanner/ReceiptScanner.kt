package com.fairshare.scanner

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.fairshare.BuildConfig
import com.fairshare.scanner.rules.RuleBasedReceiptParser

/**
 * The recommended setup from the evaluation (backend/WNIOSKI.md): ML Kit + rules on the phone; only when the items do
 * not add up to the total, the photo goes to Gemini Flash-Lite. On the 44 test receipts this read 44/44 correctly,
 * with the AI needed for 6 of them.
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

    /** Everything the scan produced, also the intermediate steps, so the test screen can show them. */
    class Outcome(
        /** What to use: the rules' reading when its sum matches, otherwise the AI's (if it answered). */
        val receipt: ScannedReceipt,
        val byRules: ScannedReceipt,
        val ocrText: String,
        val rotation: Int,
        val ocrMs: Long,
        val ocrTotalMs: Long,
        val rulesMs: Long,
        val aiModel: String? = null,
        val aiMs: Long? = null,
        val aiError: String? = null,
        /** Gemini's reading, when it was asked (also when the rules' reading was kept). */
        val byAi: ScannedReceipt? = null,
        /** Median tilt of the text lines, degrees. */
        val textAngle: Float = 0f,
    ) {
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

        val rulesStarted = SystemClock.elapsedRealtime()
        val byRules = ScannedReceipt.fromRules(RuleBasedReceiptParser.parse(read.text))
        val rulesMs = SystemClock.elapsedRealtime() - rulesStarted
        Log.i(TAG, "Reguły $rulesMs ms: ${byRules.describe()}")
        val base = Outcome(byRules, byRules, read.text, read.rotation, read.ms, read.totalMs, rulesMs, textAngle = read.angle)
        if (byRules.sumMatches || gemini == null) return base

        onStage("Suma się nie zgadza, doczytuję zdjęcie przez Gemini…")
        return try {
            val ai = gemini.read(read.upright)
            Log.i(TAG, "Gemini ${ai.model} ${ai.ms} ms, zapytań ${ai.attempts}: ${ai.receipt.describe()}")
            // the AI's answer wins unless it is further from adding up than the rules
            val receipt = if (ai.receipt.items.isNotEmpty() && ai.receipt.gap <= byRules.gap) ai.receipt else byRules
            Outcome(
                receipt, byRules, read.text, read.rotation, read.ms, read.totalMs, rulesMs,
                aiModel = ai.model, aiMs = ai.ms, byAi = ai.receipt, textAngle = read.angle,
            )
        } catch (e: ScanException) {
            Log.w(TAG, "Gemini: ${e.message}")
            Outcome(
                byRules, byRules, read.text, read.rotation, read.ms, read.totalMs, rulesMs,
                aiError = e.message, textAngle = read.angle,
            )
        }
    }

    private fun ScannedReceipt.describe(): String =
        "sklep=$store, ${items.size} poz., suma pozycji=$itemsSumGrosze gr, do zapłaty=$totalGrosze gr, zgodna=$sumMatches" +
            items.joinToString("") { "\n  ${it.name} = ${it.priceGrosze}" }

    private companion object {
        /** Logcat tag; scripts\zapisz-log.bat saves these lines from the phone. */
        const val TAG = "FairShareScan"
    }
}
