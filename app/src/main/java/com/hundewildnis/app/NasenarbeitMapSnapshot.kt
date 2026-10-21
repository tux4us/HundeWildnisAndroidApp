package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Kartenausschnitt für den PDF-Export.
 *
 * Der Ausschnitt wird aus der sichtbaren Karte (osmdroid-MapView) gerendert, solange die
 * Karte auf dem Bildschirm ist. Dafür werden keine zusätzlichen Kacheln geladen: es
 * erscheint nur, was die Nutzerin oder der Nutzer ohnehin gerade sieht (inklusive der
 * eingezeichneten Strecke oder Fläche). Die Karte trägt ihre eigene Quellenangabe
 * („© OpenStreetMap contributors“); das PDF wiederholt sie unter dem Bild.
 *
 * Ablauf: Karte → [capture] legt eine vorläufige Datei an → Ergebnis-Bildschirm →
 * [adopt] benennt sie nach der Session-ID um → [load] liest sie beim PDF-Export.
 */
object NasenarbeitMapSnapshot {

    private const val DIR = "nasenarbeit_maps"
    private const val MAX_SIDE_PX = 1200

    private fun dir(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    /** Rendert [view] in eine PNG-Datei. Gibt den Dateipfad zurück oder null bei Fehler. */
    fun capture(context: Context, view: View): String? {
        return try {
            if (view.width <= 0 || view.height <= 0) return null
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            view.draw(Canvas(bitmap))

            val scale = minOf(1f, MAX_SIDE_PX.toFloat() / maxOf(bitmap.width, bitmap.height))
            val out = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== bitmap) bitmap.recycle() }
            } else bitmap

            val file = File(dir(context), "pending_${UUID.randomUUID()}.png")
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 90, it) }
            out.recycle()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Weist die vorläufige Datei einer gespeicherten Session zu. */
    fun adopt(context: Context, pendingPath: String?, sessionId: String) {
        if (pendingPath.isNullOrEmpty()) return
        try {
            val source = File(pendingPath)
            // Nur Dateien aus dem eigenen Ordner anfassen
            if (!source.exists() || source.parentFile?.canonicalPath != dir(context).canonicalPath) return
            val target = fileFor(context, sessionId)
            if (target.exists()) target.delete()
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Verwirft eine vorläufige Datei (z. B. wenn die Karte erneut aufgerufen wird). */
    fun discardPending(context: Context, pendingPath: String?) {
        if (pendingPath.isNullOrEmpty()) return
        try {
            val file = File(pendingPath)
            if (file.parentFile?.canonicalPath == dir(context).canonicalPath) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun fileFor(context: Context, sessionId: String): File = File(dir(context), "$sessionId.png")

    fun load(context: Context, sessionId: String): Bitmap? {
        val file = fileFor(context, sessionId)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context, sessionId: String) {
        try {
            fileFor(context, sessionId).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
