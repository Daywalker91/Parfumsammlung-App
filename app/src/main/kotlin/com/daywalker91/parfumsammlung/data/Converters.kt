package com.daywalker91.parfumsammlung.data

import androidx.room.TypeConverter

/** Enum↔String-Konvertierung für Room (explizit statt impliziter Enum-Unterstützung, versionsunabhängig). */
class Converters {
    @TypeConverter
    fun fromPerfumeStatus(value: PerfumeStatus): String = value.name

    @TypeConverter
    fun toPerfumeStatus(value: String): PerfumeStatus = PerfumeStatus.valueOf(value)

    @TypeConverter
    fun fromAktivesBild(value: AktivesBild?): String? = value?.name

    @TypeConverter
    fun toAktivesBild(value: String?): AktivesBild? = value?.let { AktivesBild.valueOf(it) }

    @TypeConverter
    fun fromPosition(value: Position): String = value.name

    @TypeConverter
    fun toPosition(value: String): Position = Position.valueOf(value)
}
