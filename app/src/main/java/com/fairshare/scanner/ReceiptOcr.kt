package com.fairshare.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Google ML Kit text recognition (Latin model bundled in the APK: works offline, no Google Play download).
 *
 * A receipt is often photographed sideways or upside down, so the photo is read in the orientation the text lines
 * suggest and, if that does not look like a receipt, in the others; the best reading wins. The text is put back into
 * receipt rows ([ReceiptRows]) the same way as in the benchmark.
 */
class ReceiptOcr(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    class Result(
        /** Rows for the rules, e.g. "Chleb C   1szt x8,20 8,20C". */
        val text: String,
        /** Clockwise rotation that made the photo upright (0, 90, 180, 270). */
        val rotation: Int,
        /** Time of the reading that won. */
        val ms: Long,
        /** Time of all readings (other orientations included). */
        val totalMs: Long,
        /** The photo as ML Kit saw it, upright, for the AI fallback. */
        val upright: Bitmap,
    )

    private class Reading(val rotation: Int, val lines: List<OcrLine>, val text: String, val ms: Long, val medianAngle: Float) {
        /** How much this looks like a receipt read the right way up: lines with prices and receipt words. */
        val score: Int = run {
            val rows = text.lines()
            rows.count { AMOUNT.containsMatchIn(it) } * 10 +
                rows.count { KEYWORD.containsMatchIn(it.lowercase()) } * 25 +
                text.count { it.isLetter() } / 20
        }
        val looksGood: Boolean get() = score >= 60 && abs(medianAngle) < 30
    }

    suspend fun read(uri: Uri): Result = withContext(Dispatchers.Default) {
        val photo = loadUpright(uri)
        val started = SystemClock.elapsedRealtime()
        val readings = mutableListOf(recognize(photo, 0))
        // Lines read at ~90° or ~180° mean the receipt lies sideways / upside down in the photo.
        val angle = readings[0].medianAngle
        val guess = (((angle / 90f).roundToInt() * 90) % 360 + 360) % 360
        if (!readings[0].looksGood) {
            val order = listOf(guess, 360 - guess, 180, 90, 270).map { it % 360 }.filter { it != 0 }.distinct()
            for (rotation in order) {
                readings += recognize(photo, rotation)
                if (readings.last().looksGood) break
            }
        }
        val best = readings.maxBy { it.score }
        Result(
            text = best.text,
            rotation = best.rotation,
            ms = best.ms,
            totalMs = SystemClock.elapsedRealtime() - started,
            upright = if (best.rotation == 0) photo else photo.rotated(best.rotation),
        )
    }

    private suspend fun recognize(photo: Bitmap, rotation: Int): Reading {
        val started = SystemClock.elapsedRealtime()
        // ML Kit turns the image by `rotation` itself and returns boxes in the upright frame
        val text = recognizer.process(InputImage.fromBitmap(photo, rotation)).await()
        val ms = SystemClock.elapsedRealtime() - started
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { it.toOcrLine() }
        val angles = text.textBlocks.flatMap { it.lines }.filter { it.text.length >= 3 }.map { it.angle }.sorted()
        val median = if (angles.isEmpty()) 0f else angles[angles.size / 2]
        return Reading(rotation, lines, ReceiptRows.rebuild(lines), ms, median)
    }

    fun close() = recognizer.close()

    private fun Text.Line.toOcrLine(): OcrLine? {
        val box = boundingBox ?: return null
        return OcrLine(text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
    }

    /** Decodes the photo with its EXIF orientation applied, at most [MAX_SIDE] px on the long side. */
    private fun loadUpright(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        // with inJustDecodeBounds decodeStream always returns null and only fills outWidth/outHeight
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        if (!opened) throw ScanException("Nie można otworzyć zdjęcia.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ScanException("To nie wygląda na zdjęcie (JPG/PNG).")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw ScanException("Nie udało się wczytać zdjęcia.")

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        val upright = if (degrees == 0) bitmap else bitmap.rotated(degrees)
        val longSide = maxOf(upright.width, upright.height)
        return if (longSide <= MAX_SIDE) upright else {
            val scale = MAX_SIDE.toFloat() / longSide
            Bitmap.createScaledBitmap(upright, (upright.width * scale).roundToInt(), (upright.height * scale).roundToInt(), true)
        }
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(ScanException("ML Kit: ${it.message}", it)) }
        addOnCanceledListener { cont.cancel() }
    }

    companion object {
        /** Phone photos are ~4000 px; ML Kit reads small receipt print better at full size. */
        const val MAX_SIDE = 4096
        private val AMOUNT = Regex("""\d[.,]\d{2}""")
        private val KEYWORD = Regex("""paragon|fiskaln|suma|razem|ptu|do zap|sprzeda""")
    }
}

class ScanException(message: String, cause: Throwable? = null) : Exception(message, cause)
