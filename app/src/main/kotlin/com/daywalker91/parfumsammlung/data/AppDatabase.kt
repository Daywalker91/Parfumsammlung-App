package com.daywalker91.parfumsammlung.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Perfume::class, Note::class, PerfumeNote::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun perfumeDao(): PerfumeDao
    abstract fun noteDao(): NoteDao
    abstract fun perfumeNoteDao(): PerfumeNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aromathek.db",
                ).build().also { INSTANCE = it }
            }
    }
}
