package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.WriterException

object QRCodeGenerator {
    /**
     * Generiert QR-Code aus Text
     */
    fun generateQRCode(text: String, size: Int): Bitmap? {
        return try {
            val hints = hashMapOf<com.google.zxing.EncodeHintType, Any>()
            hints[com.google.zxing.EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[com.google.zxing.EncodeHintType.MARGIN] = 1

            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Legacy-Funktion für DogProfile (alle Felder)
     */
    fun generateDogProfileQR(context: Context, profile: DogProfile, size: Int): Bitmap? {
        return generateQRCode(profile.toQRString(context), size)
    }
}
