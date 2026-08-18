package com.daywalker91.parfumsammlung.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Perfume::class, Note::class, PerfumeNote::class],
    version = 2,
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

        // Erste echte Migration dieser App (Phase 8a) — bewusst KEIN
        // fallbackToDestructiveMigration: Nutzer mit bereits installierter
        // App dürfen beim Update nicht ihre Sammlung verlieren, nur weil ein
        // neues optionales Feld dazukommt.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE perfume ADD COLUMN saison TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aromathek.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
