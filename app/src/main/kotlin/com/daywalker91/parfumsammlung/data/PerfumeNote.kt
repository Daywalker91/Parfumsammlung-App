package com.daywalker91.parfumsammlung.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Duftpyramiden-Position einer Note innerhalb eines Parfums. */
enum class Position { KOPF, HERZ, BASIS }

/** m:n-Verknüpfung Parfum↔Note inkl. Position in der Duftpyramide. */
@Entity(
    tableName = "perfume_note",
    primaryKeys = ["perfumeId", "noteId"],
    foreignKeys = [
        ForeignKey(
            entity = Perfume::class,
            parentColumns = ["id"],
            childColumns = ["perfumeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class PerfumeNote(
    val perfumeId: Long,
    val noteId: Long,
    val position: Position,
)
