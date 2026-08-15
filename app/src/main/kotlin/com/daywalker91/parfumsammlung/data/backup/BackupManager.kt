package com.daywalker91.parfumsammlung.data.backup

import android.content.Context
import android.net.Uri
import com.daywalker91.parfumsammlung.data.AktivesBild
import com.daywalker91.parfumsammlung.data.ImageStorage
import com.daywalker91.parfumsammlung.data.NotenEingabe
import com.daywalker91.parfumsammlung.data.Perfume
import com.daywalker91.parfumsammlung.data.PerfumeRepository
import com.daywalker91.parfumsammlung.data.PerfumeStatus
import com.daywalker91.parfumsammlung.data.Position
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup/Restore als ZIP (Phase 6, „Komfort"): ein `daten.json`-Manifest mit
 * allen Parfums+Noten (Bildfelder als reine Dateinamen, nicht als absoluter
 * Pfad — der wäre nach einer Neuinstallation/auf einem anderen Gerät ungültig)
 * plus die referenzierten Bilddateien unter `images/`. Bewusst kein Kopieren
 * der rohen Room-Datenbankdatei — das JSON-Format ist robust gegenüber
 * Pfadänderungen und braucht keine Rücksicht auf offene DB-Connections/WAL.
 */
class BackupManager(
    private val repository: PerfumeRepository,
    private val imageStorage: ImageStorage,
) {
    data class ImportErgebnis(val importiert: Int, val uebersprungen: Int)

    suspend fun exportiere(context: Context, zielUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val perfums = repository.getAll()
            val eintraege = JSONArray()
            perfums.forEach { perfume ->
                val noten = repository.observeNotesForPerfume(perfume.id).first()
                eintraege.put(
                    JSONObject().apply {
                        put("name", perfume.name)
                        put("marke", perfume.marke)
                        put("beschreibung", perfume.beschreibung)
                        put("uvp", perfume.uvp ?: JSONObject.NULL)
                        put("status", perfume.status.name)
                        put("flakongroesse", perfume.flakongroesse)
                        put("ean", perfume.ean)
                        put("verfuegbareGroessen", perfume.verfuegbareGroessen)
                        put("notiz", perfume.notiz)
                        put("bewertung", perfume.bewertung ?: JSONObject.NULL)
                        put("erstelltAm", perfume.erstelltAm)
                        put("bildDateiEigen", perfume.bildPfadEigen?.let { File(it).name })
                        put("bildDateiStock", perfume.bildPfadStock?.let { File(it).name })
                        put("aktivesBild", perfume.aktivesBild?.name)
                        put(
                            "noten",
                            JSONArray().apply {
                                noten.forEach { note ->
                                    put(JSONObject().put("name", note.name).put("position", note.position.name))
                                }
                            },
                        )
                    },
                )
            }
            val manifest = JSONObject().apply {
                put("version", 1)
                put("erstelltAm", System.currentTimeMillis())
                put("parfums", eintraege)
            }

            val bildDateien = perfums.flatMap { listOfNotNull(it.bildPfadEigen, it.bildPfadStock) }.distinct()

            val stream = context.contentResolver.openOutputStream(zielUri) ?: return@withContext false
            stream.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("daten.json"))
                    zip.write(manifest.toString().toByteArray())
                    zip.closeEntry()

                    bildDateien.forEach { pfad ->
                        val datei = File(pfad)
                        if (datei.exists()) {
                            zip.putNextEntry(ZipEntry("images/${datei.name}"))
                            datei.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Bereits vorhandene Parfums (gleicher Name+Marke, derselbe Duplikat-Check
     * wie beim manuellen Anlegen) werden übersprungen statt dupliziert — ein
     * wiederholtes Einspielen desselben Backups ist damit gefahrlos.
     */
    suspend fun importiere(context: Context, quelleUri: Uri): ImportErgebnis? = withContext(Dispatchers.IO) {
        try {
            var manifest: JSONObject? = null
            val bilder = mutableMapOf<String, ByteArray>()

            val stream = context.contentResolver.openInputStream(quelleUri) ?: return@withContext null
            stream.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == "daten.json" -> manifest = JSONObject(zip.readBytes().decodeToString())
                            // Schutz vor Zip-Path-Traversal (".."), auch wenn wir die ZIP selbst erzeugt haben.
                            name.startsWith("images/") && !name.contains("..") ->
                                bilder[name.removePrefix("images/")] = zip.readBytes()
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            val daten = manifest ?: return@withContext null
            val parfums = daten.getJSONArray("parfums")

            var importiert = 0
            var uebersprungen = 0

            for (i in 0 until parfums.length()) {
                val eintrag = parfums.getJSONObject(i)
                val name = eintrag.getString("name")
                val marke = eintrag.getString("marke")

                if (repository.findByNameAndMarke(name, marke) != null) {
                    uebersprungen++
                    continue
                }

                val bildPfadEigen = eintrag.optStringOrNull("bildDateiEigen")
                    ?.let { bilder[it] }
                    ?.let { imageStorage.speichereVonBytes(it) }
                val bildPfadStock = eintrag.optStringOrNull("bildDateiStock")
                    ?.let { bilder[it] }
                    ?.let { imageStorage.speichereVonBytes(it) }

                val perfume = Perfume(
                    name = name,
                    marke = marke,
                    beschreibung = eintrag.optStringOrNull("beschreibung"),
                    uvp = eintrag.optDoubleOrNullSafe("uvp"),
                    status = PerfumeStatus.valueOf(eintrag.getString("status")),
                    flakongroesse = eintrag.optStringOrNull("flakongroesse"),
                    ean = eintrag.optStringOrNull("ean"),
                    verfuegbareGroessen = eintrag.optStringOrNull("verfuegbareGroessen"),
                    notiz = eintrag.optStringOrNull("notiz"),
                    bewertung = eintrag.optIntOrNullSafe("bewertung"),
                    erstelltAm = eintrag.optLong("erstelltAm", System.currentTimeMillis()),
                    bildPfadEigen = bildPfadEigen,
                    bildPfadStock = bildPfadStock,
                    aktivesBild = eintrag.optStringOrNull("aktivesBild")?.let { AktivesBild.valueOf(it) },
                )

                val notenArray = eintrag.getJSONArray("noten")
                val notenZuordnung = (0 until notenArray.length()).map { j ->
                    val note = notenArray.getJSONObject(j)
                    NotenEingabe(note.getString("name"), Position.valueOf(note.getString("position")))
                }

                repository.insert(perfume, notenZuordnung)
                importiert++
            }

            ImportErgebnis(importiert, uebersprungen)
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optDoubleOrNullSafe(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.optIntOrNullSafe(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
}
