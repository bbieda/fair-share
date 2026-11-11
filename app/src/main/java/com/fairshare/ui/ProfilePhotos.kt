package com.fairshare.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/** Profile photo is copied into app storage so the gallery grant can expire. */
object ProfilePhotos {
    /** Longest side of the saved picture. An avatar does not need more. */
    private const val MAX_SIDE = 512

    /** Reads a picture from the gallery or the camera, shrinks it, fixes its rotation and stores it. */
    fun save(context: Context, source: Uri): String {
        val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
            ?: throw IOException("Nie udało się odczytać zdjęcia")
        val bitmap = decodeScaled(bytes) ?: throw IOException("Nie udało się odczytać zdjęcia")
        val upright = rotateByExif(bitmap, bytes)
        val dest = file(context)
        dest.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return stored(dest)
    }

    fun load(stored: String?): Bitmap? {
        val path = stored?.substringBefore('?')?.takeIf { it.isNotBlank() && it != "bitmap_cache" } ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun delete(context: Context) {
        file(context).delete()
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE && bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** Camera apps often store the picture sideways and only write the rotation into EXIF. */
    private fun rotateByExif(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun file(context: Context) = File(context.filesDir, "profile.jpg")

    // "?v=" changes with every save, so a new photo is never shown from an old cache
    private fun stored(dest: File) = "${dest.absolutePath}?v=${System.currentTimeMillis()}"
}

/** Loads the stored profile photo off the main thread. null = no photo. */
@Composable
fun rememberProfilePhoto(stored: String?): State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, stored) {
        value = withContext(Dispatchers.IO) { ProfilePhotos.load(stored) }
    }
