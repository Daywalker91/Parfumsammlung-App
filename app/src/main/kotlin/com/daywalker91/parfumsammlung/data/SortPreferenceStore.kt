package com.daywalker91.parfumsammlung.data

import android.content.Context

/** Sortierung der Übersichtsliste (Phase 8a) — globale Einstellung, in den App-Einstellungen wählbar. */
enum class SortMode { NAME, MARKE, UVP }

/** Persistiert unabhängig von der jeweils aktuell angezeigten Liste (Sammlung/Wunschliste). */
class SortPreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("sort_prefs", Context.MODE_PRIVATE)

    fun getSortMode(): SortMode =
        SortMode.valueOf(prefs.getString(KEY_SORT_MODE, SortMode.NAME.name)!!)

    fun setSortMode(mode: SortMode) {
        prefs.edit().putString(KEY_SORT_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_SORT_MODE = "sortier_modus"
    }
}
