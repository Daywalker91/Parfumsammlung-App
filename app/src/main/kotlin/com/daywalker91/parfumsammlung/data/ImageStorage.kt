package com.daywalker91.parfumsammlung.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Speichert Fotos lokal komprimiert im internen App-Speicher (kein Netzwerk,
 * kein externer Zugriff nötig für die Anzeige — siehe Plan, Kapitel
 * „Bildspeicherung"). Wird sowohl für eigene Fotos (`bild_pfad_eigen`) als
 * auch für per Gemini gefundene Stock-Bilder (`bild_pfad_stock`) genutzt.
 */
class ImageStorage(private val context: Context) {

    private val imagesDir: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    /** Erzeugt ein content://-Ziel im Cache, in das eine Kamera-App ein Foto schreiben kann. */
    fun neueKameraZielUri(): Uri {
        val cacheImagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val datei = File(cacheImagesDir, "capture_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", datei)
    }

    /**
     * Liest ein Bild von einer beliebigen Quelle (Kamera-Aufnahme oder
     * Galerie-Auswahl), komprimiert es und legt es dauerhaft im internen
     * Speicher ab. Gibt den absoluten Pfad zurück oder `null`, falls das
     * Bild nicht dekodiert werden konnte.
     */
    fun speichereVonUri(uri: Uri, maxDimension: Int = 1600, qualitaet: Int = 85): String? {
        val bitmap = dekodiereSkaliertVonUri(uri, maxDimension) ?: return null
        return speichereBitmap(bitmap, qualitaet)
    }

    /** Für per Gemini/Websuche gefundene Stock-Bilder (bereits heruntergeladene Bytes). */
    fun speichereVonBytes(bytes: ByteArray, maxDimension: Int = 1600, qualitaet: Int = 85): String? {
        val grob = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return speichereBitmap(skaliereAufMax(grob, maxDimension), qualitaet)
    }

    /** Löscht ein zuvor gespeichertes Bild (z. B. beim Ersetzen des eigenen Fotos). */
    fun loesche(pfad: String?) {
        if (pfad == null) return
        val datei = File(pfad)
        if (datei.exists() && datei.parentFile == imagesDir) {
            datei.delete()
        }
    }

    private fun speichereBitmap(bitmap: Bitmap, qualitaet: Int): String {
        val datei = File(imagesDir, "img_${UUID.randomUUID()}.jpg")
        FileOutputStream(datei).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, qualitaet, out) }
        bitmap.recycle()
        return datei.absolutePath
    }

    private fun dekodiereSkaliertVonUri(uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver

        // 1. Durchlauf: nur Abmessungen ermitteln, ohne das volle Bild in den Speicher zu laden.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            ?: return null

        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
            boundsOptions.outHeight / sampleSize > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val grob = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        return skaliereAufMax(grob, maxDimension)
    }

    private fun skaliereAufMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val groessterWert = maxOf(bitmap.width, bitmap.height)
        if (groessterWert <= maxDimension) return bitmap
        val faktor = maxDimension.toFloat() / groessterWert
        val neu = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * faktor).toInt(),
            (bitmap.height * faktor).toInt(),
            true,
        )
        if (neu !== bitmap) bitmap.recycle()
        return neu
    }
}
