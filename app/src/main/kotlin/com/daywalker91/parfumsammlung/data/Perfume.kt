package com.daywalker91.parfumsammlung.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ob ein Eintrag zur eigenen Sammlung gehört oder nur auf der Wunschliste steht. */
enum class PerfumeStatus { BESITZT, WUNSCHLISTE }

/** Steuert, welches der beiden gecachten Bilder angezeigt wird (siehe Plan, Kapitel „Bildspeicherung"). */
enum class AktivesBild { EIGEN, STOCK }

@Entity(tableName = "perfume")
data class Perfume(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val marke: String,
    val beschreibung: String? = null,
    val uvp: Double? = null,
    val status: PerfumeStatus,
    val flakongroesse: String? = null,
    val ean: String? = null,
    @ColumnInfo(name = "verfuegbare_groessen")
    val verfuegbareGroessen: String? = null,
    @ColumnInfo(name = "bild_pfad_stock")
    val bildPfadStock: String? = null,
    @ColumnInfo(name = "bild_pfad_eigen")
    val bildPfadEigen: String? = null,
    @ColumnInfo(name = "aktives_bild")
    val aktivesBild: AktivesBild? = null,
    val notiz: String? = null,
    val bewertung: Int? = null,
    @ColumnInfo(name = "erstellt_am")
    val erstelltAm: Long = System.currentTimeMillis(),
)
