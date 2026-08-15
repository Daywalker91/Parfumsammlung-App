package com.daywalker91.parfumsammlung.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        val grad = ByteArrayInputStream(bytes).use { exifRotationGrad(it) }
        return speichereBitmap(skaliereAufMax(rotiere(grob, grad), maxDimension), qualitaet)
    }

    /**
     * Dreht ein bereits lokal gespeichertes Bild manuell um 90° im Uhrzeigersinn
     * (Editor-Funktion, für Fälle wo weder EXIF noch Auto-Erkennung die richtige
     * Ausrichtung treffen). Legt bewusst eine neue Datei an statt die alte zu
     * überschreiben — sonst würde Coils Bild-Cache (Cache-Key = Dateipfad) die
     * alte, ungedrehte Version weiter anzeigen.
     */
    fun drehe90Grad(pfad: String): String? {
        val original = BitmapFactory.decodeFile(pfad) ?: return null
        val gedreht = rotiere(original, 90)
        val neuerPfad = speichereBitmap(gedreht, qualitaet = 90)
        loesche(pfad)
        return neuerPfad
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
        // Wichtig: BitmapFactory.decodeStream() liefert bei inJustDecodeBounds=true IMMER
        // null zurück (so ist die Bounds-only-API gebaut) — das darf nicht mit einem
        // fehlgeschlagenen openInputStream() verwechselt werden, sonst schlägt jeder
        // Aufruf fehl, egal ob das Bild gültig ist.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
            boundsOptions.outHeight / sampleSize > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = resolver.openInputStream(uri) ?: return null
        val grob = decodeStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: return null

        // Kamerafotos speichern ihre tatsächliche Ausrichtung oft nur als
        // EXIF-Tag, die rohen Pixel bleiben in der Sensor-Ausrichtung (meist
        // quer) — ohne diese Korrektur landet z. B. ein Hochformat-Foto quer
        // in der App, obwohl die Kamera-App selbst es korrekt anzeigt.
        val exifStream = resolver.openInputStream(uri) ?: return skaliereAufMax(grob, maxDimension)
        val grad = exifStream.use { exifRotationGrad(it) }

        return skaliereAufMax(rotiere(grob, grad), maxDimension)
    }

    /** Rotationswinkel (0/90/180/270) aus den EXIF-Metadaten eines Bild-Streams. Spiegelungen
     * (FLIP_HORIZONTAL/VERTICAL) werden bewusst nicht extra behandelt — kommen bei Fotos aus
     * Kamera/Galerie/Websuche praktisch nicht vor, nur die vier Rotationsstufen sind relevant. */
    private fun exifRotationGrad(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }

    private fun rotiere(bitmap: Bitmap, grad: Int): Bitmap {
        if (grad == 0) return bitmap
        val matrix = Matrix().apply { postRotate(grad.toFloat()) }
        val gedreht = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (gedreht !== bitmap) bitmap.recycle()
        return gedreht
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
