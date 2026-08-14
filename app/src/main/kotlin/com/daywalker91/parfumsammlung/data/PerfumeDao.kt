package com.daywalker91.parfumsammlung.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfumeDao {
    @Insert
    suspend fun insert(perfume: Perfume): Long

    @Update
    suspend fun update(perfume: Perfume)

    @Delete
    suspend fun delete(perfume: Perfume)

    @Query("SELECT * FROM perfume WHERE id = :id")
    suspend fun getById(id: Long): Perfume?

    @Query("SELECT * FROM perfume WHERE status = :status ORDER BY name ASC")
    fun observeByStatus(status: PerfumeStatus): Flow<List<Perfume>>

    @Query("SELECT * FROM perfume WHERE id = :id")
    fun observeById(id: Long): Flow<Perfume?>

    /** Für den Duplikat-Check beim Hinzufügen (siehe Plan, Kapitel „User-Flow: Parfum hinzufügen"). */
    @Query("SELECT * FROM perfume WHERE name = :name AND marke = :marke LIMIT 1")
    suspend fun findByNameAndMarke(name: String, marke: String): Perfume?
}
