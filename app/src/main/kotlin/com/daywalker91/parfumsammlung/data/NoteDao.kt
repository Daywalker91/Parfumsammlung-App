package com.daywalker91.parfumsammlung.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(note: Note): Long

    @Query("SELECT * FROM note WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Note?

    @Query("SELECT * FROM note ORDER BY name ASC")
    fun observeAll(): Flow<List<Note>>
}
