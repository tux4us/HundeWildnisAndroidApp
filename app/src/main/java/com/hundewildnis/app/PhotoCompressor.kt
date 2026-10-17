package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Zentrale Fotokomprimierung der App.
 *
 * Alle Fotos, die in die App übernommen werden (Galerie, Dateimanager, Kamera), laufen über
 * [importPhoto] bzw. [compressToFile]:
 *  - längste Kante höchstens [MAX_EDGE] px (Seitenverhältnis bleibt erhalten, kein Hochskalieren)
 *  - Ausgabe als JPEG mit Qualität [QUALITY]
 *  - EXIF-Ausrichtung wird in die Pixel übernommen; EXIF-Daten (z. B. GPS) entfallen
 *  - Bilder, die bereits klein genug und richtig ausgerichtet sind, werden unverändert kopiert
 *    (keine erneute Qualitätsminderung)
 *  - Schlägt die Komprimierung fehl (unbekanntes Format, zu wenig Speicher), wird das Original
 *    unverändert kopiert; ein Foto geht dadurch nie verloren.
 */
object PhotoCompressor {

    private const val TAG = "PhotoCompressor"

    const val MAX_EDGE = 1280
    const val QUALITY = 85

    /** Ordner im internen Speicher für Fotos von Hundeprofil, Tagesnotizen und Notfallmeldungen. */
    const val DIR_USER_PHOTOS = "user_photos"

    /**
     * Kopiert [source] komprimiert in den internen Ordner [dirName] und liefert die
     * FileProvider-URI, oder null, wenn das Foto nicht gelesen werden kann.
     */
    fun importPhoto(context: Context, source: Uri, dirName: String = DIR_USER_PHOTOS): Uri? {
        val dest = File(context.filesDir, "$dirName/${UUID.randomUUID()}.jpg")
        dest.parentFile?.mkdirs()
        if (!compressToFile(context, source, dest)) {
            dest.delete()
            return null
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
    }

    /**
     * Löscht ein mit [importPhoto] im Ordner [DIR_USER_PHOTOS] abgelegtes Foto.
     * @return true, wenn die URI auf diesen Ordner zeigte (unabhängig davon, ob die Datei noch existierte).
     */
    fun deleteOwnPhoto(context: Context, uri: Uri): Boolean {
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != DIR_USER_PHOTOS) return false
        try {
            File(context.filesDir, "$DIR_USER_PHOTOS/${segments.last()}").delete()
        } catch (e: Exception) {
            Log.w(TAG, "Löschen fehlgeschlagen: ${e.message}")
        }
        return true
    }

    /** String-Variante von [deleteOwnPhoto]. */
    fun deleteOwnPhoto(context: Context, uriString: String): Boolean =
        uriString.isNotBlank() && deleteOwnPhoto(context, Uri.parse(uriString))

    /**
     * Schreibt eine verkleinerte Fassung von [source] nach [dest].
     * @return true, wenn [dest] danach ein gültiges Foto enthält (komprimiert oder unverändert kopiert).
     */
    fun compressToFile(
        context: Context,
        source: Uri,
        dest: File,
        maxEdge: Int = MAX_EDGE,
        quality: Int = QUALITY
    ): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        return try {
            if (compressInternal(context, source, tmp, maxEdge, quality)) {
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest) || run { tmp.copyTo(dest, overwrite = true); tmp.delete(); true }
            } else {
                tmp.delete()
                copyRaw(context, source, dest)
            }
        } catch (e: Throwable) {
            // Auch OutOfMemoryError: Original unverändert übernehmen
            Log.w(TAG, "Komprimierung fehlgeschlagen, kopiere Original: ${e.message}")
            tmp.delete()
            copyRaw(context, source, dest)
        }
    }

    /** @return true, wenn [tmp] geschrieben wurde; false, wenn das Original unverändert bleiben soll. */
    private fun compressInternal(context: Context, source: Uri, tmp: File, maxEdge: Int, quality: Int): Boolean {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream liefert mit inJustDecodeBounds immer null; deshalb nur den Stream prüfen
        val boundsStream = resolver.openInputStream(source) ?: return false
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return false

        val orientation = resolver.openInputStream(source)?.use {
            try {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } ?: ExifInterface.ORIENTATION_NORMAL
        val needsTransform = orientation != ExifInterface.ORIENTATION_NORMAL &&
                orientation != ExifInterface.ORIENTATION_UNDEFINED

        // Bereits klein genug und richtig ausgerichtet: unverändert übernehmen
        if (maxOf(width, height) <= maxEdge && !needsTransform) return false

        // Grobes Verkleinern beim Dekodieren (Zweierpotenz), Feinskalierung danach
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return false

        val scale = minOf(1f, maxEdge.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        applyOrientation(matrix, orientation)

        var result = if (matrix.isIdentity) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (result !== decoded) decoded.recycle()

        // JPEG kennt keine Transparenz: auf Weiß setzen statt Schwarz
        if (result.hasAlpha()) {
            val flat = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(result, 0f, 0f, null) }
            result.recycle()
            result = flat
        }

        FileOutputStream(tmp).use { out ->
            if (!result.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                result.recycle()
                return false
            }
        }
        result.recycle()
        return tmp.length() > 0
    }

    private fun applyOrientation(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
    }

    private fun copyRaw(context: Context, source: Uri, dest: File): Boolean = try {
        val input = context.contentResolver.openInputStream(source)
        if (input == null) {
            false
        } else {
            input.use { inp -> FileOutputStream(dest).use { inp.copyTo(it) } }
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Kopieren fehlgeschlagen: ${e.message}")
        false
    }
}