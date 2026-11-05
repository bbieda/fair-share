package com.fairshare.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Profile photo is copied into app storage so the gallery grant can expire. */
object ProfilePhotos {
    fun save(context: Context, source: Uri): String {
        val dest = file(context)
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Nie udało się odczytać zdjęcia" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return stored(dest)
    }

    fun save(context: Context, bitmap: Bitmap): String {
        val dest = file(context)
        dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
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

    private fun file(context: Context) = File(context.filesDir, "profile.jpg")

    private fun stored(dest: File) = "${dest.absolutePath}?v=${System.currentTimeMillis()}"
}
