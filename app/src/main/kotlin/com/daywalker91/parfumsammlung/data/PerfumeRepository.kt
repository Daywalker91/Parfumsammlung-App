package com.daywalker91.parfumsammlung.data

import kotlinx.coroutines.flow.Flow

/** Eingabe-Repräsentation einer Note aus dem Formular — noch ohne Note-ID, da die Note ggf. neu ist. */
data class NotenEingabe(val name: String, val position: Position)

/**
 * Bündelt die drei DAOs zu den Operationen, die die UI tatsächlich braucht —
 * vor allem das Zusammenspiel Perfume↔Notes, das über mehrere Tabellen geht.
 */
class PerfumeRepository(
    private val perfumeDao: PerfumeDao,
    private val noteDao: NoteDao,
    private val perfumeNoteDao: PerfumeNoteDao,
) {
    fun observeByStatus(status: PerfumeStatus): Flow<List<Perfume>> =
        perfumeDao.observeByStatus(status)

    fun observeById(id: Long): Flow<Perfume?> = perfumeDao.observeById(id)

    suspend fun getById(id: Long): Perfume? = perfumeDao.getById(id)

    fun observeNotesForPerfume(perfumeId: Long): Flow<List<NoteWithPosition>> =
        perfumeNoteDao.observeNotesForPerfume(perfumeId)

    /** Für den Duplikat-Check beim Hinzufügen (siehe Plan, Kapitel „User-Flow: Parfum hinzufügen"). */
    suspend fun findByNameAndMarke(name: String, marke: String): Perfume? =
        perfumeDao.findByNameAndMarke(name, marke)

    /** Legt ein Parfum inkl. seiner Noten-Zuordnungen neu an. */
    suspend fun insert(perfume: Perfume, notenZuordnung: List<NotenEingabe>): Long {
        val perfumeId = perfumeDao.insert(perfume)
        speichereNotenZuordnung(perfumeId, notenZuordnung)
        return perfumeId
    }

    /** Aktualisiert ein bestehendes Parfum; Noten-Zuordnungen werden komplett ersetzt. */
    suspend fun update(perfume: Perfume, notenZuordnung: List<NotenEingabe>) {
        perfumeDao.update(perfume)
        perfumeNoteDao.deleteAllForPerfume(perfume.id)
        speichereNotenZuordnung(perfume.id, notenZuordnung)
    }

    suspend fun delete(perfume: Perfume) = perfumeDao.delete(perfume)

    /** Direktes Umschalten zwischen eigenem Foto und Stock-Bild, ohne den vollen Bearbeiten-Flow. */
    suspend fun setzeAktivesBild(perfumeId: Long, bild: AktivesBild) {
        val bestehend = perfumeDao.getById(perfumeId) ?: return
        perfumeDao.update(bestehend.copy(aktivesBild = bild))
    }

    /** Bestehende Notes (nach Name) werden wiederverwendet, neue automatisch angelegt. */
    private suspend fun speichereNotenZuordnung(perfumeId: Long, notenZuordnung: List<NotenEingabe>) {
        notenZuordnung.forEach { eingabe ->
            val note = noteDao.findByName(eingabe.name) ?: Note(
                id = noteDao.insert(Note(name = eingabe.name)),
                name = eingabe.name,
            )
            perfumeNoteDao.insert(
                PerfumeNote(perfumeId = perfumeId, noteId = note.id, position = eingabe.position),
            )
        }
    }
}
