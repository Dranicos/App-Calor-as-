package com.nutriscan.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Guarda y prepara las fotos en el almacenamiento interno de la app. */
class PhotoStore(private val context: Context) {
    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** Archivo nuevo para la cámara y su Uri compartible con la app de cámara. */
    fun newCaptureTarget(): Pair<File, Uri> {
        val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /** Copia una imagen de la galería a un archivo propio. */
    fun importFromUri(uri: Uri): File {
        val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    /** JPEG reducido (lado mayor ≤ [maxSide]) y bien orientado. */
    fun compressedJpeg(file: File, maxSide: Int, quality: Int = 85): ByteArray {
        val bitmap = decodeScaled(file, maxSide)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    /** Guarda una miniatura permanente para el diario y devuelve su ruta. */
    fun saveThumbnail(source: File): String {
        val target = File(dir, "meal_${UUID.randomUUID()}.jpg")
        target.writeBytes(compressedJpeg(source, maxSide = 720, quality = 80))
        return target.absolutePath
    }

    fun loadBitmap(path: String?, maxSide: Int = 1080): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        return if (f.exists()) runCatching { decodeScaled(f, maxSide) }.getOrNull() else null
    }

    fun delete(path: String?) {
        if (!path.isNullOrBlank()) File(path).delete()
    }

    /** Borra capturas temporales que ya no están asociadas a ninguna comida. */
    fun cleanUp(keep: Set<String>) {
        dir.listFiles()?.forEach { f ->
            if (f.absolutePath !in keep) f.delete()
        }
    }

    private fun decodeScaled(file: File, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("No se pudo leer la imagen")

        val rotation = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        val scale = minOf(1f, maxSide.toFloat() / max(raw.width, raw.height))
        if (rotation == 0f && scale >= 1f) return raw
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val result = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (result !== raw) raw.recycle()
        return result
    }
}
