package com.daywalker91.parfumsammlung.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Note samt ihrer Duftpyramiden-Position für ein bestimmtes Parfum (Join-Projektion, keine eigene Entity). */
data class NoteWithPosition(
    val id: Long,
    val name: String,
    val kategorie: String?,
    val position: Position,
)

@Dao
interface PerfumeNoteDao {
    @Insert
    suspend fun insert(crossRef: PerfumeNote)

    @Delete
    suspend fun delete(crossRef: PerfumeNote)

    /** Beim Bearbeiten werden bestehende Zuordnungen komplett ersetzt statt einzeln geändert. */
    @Query("DELETE FROM perfume_note WHERE perfumeId = :perfumeId")
    suspend fun deleteAllForPerfume(perfumeId: Long)

    @Query(
        """
        SELECT note.id AS id, note.name AS name, note.kategorie AS kategorie, perfume_note.position AS position
        FROM perfume_note
        INNER JOIN note ON note.id = perfume_note.noteId
        WHERE perfume_note.perfumeId = :perfumeId
        """
    )
    fun observeNotesForPerfume(perfumeId: Long): Flow<List<NoteWithPosition>>
}
