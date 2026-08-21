package com.daywalker91.parfumsammlung.di

import com.daywalker91.parfumsammlung.data.model.PerfumeSuggestion

/**
 * Trägt einen Claude-Erkennungsvorschlag (+ die dafür bereits lokal
 * gespeicherten Bilder) einmalig vom Foto-Hinzufügen-Flow zum Editor-Screen.
 * Compose Navigation kann nur einfache Typen als Routen-Argumente
 * transportieren — für dieses kurzlebige, komplexere Objekt reicht ein
 * simpler In-Memory-Zwischenspeicher (nur eine Add-Flow-Instanz gleichzeitig
 * möglich, passt zur Single-Activity-App).
 */
object PerfumeSuggestionBridge {
    data class Payload(
        val vorschlag: PerfumeSuggestion?,
        val bildPfadEigen: String?,
        val bildPfadStock: String?,
        val ean: String? = null,
    )

    private var payload: Payload? = null

    fun setzen(payload: Payload) {
        this.payload = payload
    }

    /** Liest den Vorschlag EINMALIG aus und leert den Speicher danach. */
    fun konsumieren(): Payload? {
        val aktuell = payload
        payload = null
        return aktuell
    }
}
