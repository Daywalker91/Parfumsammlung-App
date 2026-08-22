package com.daywalker91.parfumsammlung.data

import android.content.Context

/** Unverschlüsselte, unkritische App-Präferenzen (kein API-Key o. Ä.). */
class FirstLaunchPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun datenschutzGesehen(): Boolean = prefs.getBoolean(KEY_DATENSCHUTZ_GESEHEN, false)

    fun datenschutzAlsGesehenMarkieren() {
        prefs.edit().putBoolean(KEY_DATENSCHUTZ_GESEHEN, true).apply()
    }

    /** Onboarding-Dialog "Eigener API-Key oder Lizenzschlüssel" (Lizenz-Gateway-Plan) — wird
     * einmalig direkt nach dem Datenschutz-Hinweis gezeigt, per "Später" überspringbar. */
    fun zugangsWahlGesehen(): Boolean = prefs.getBoolean(KEY_ZUGANGSWAHL_GESEHEN, false)

    fun zugangsWahlAlsGesehenMarkieren() {
        prefs.edit().putBoolean(KEY_ZUGANGSWAHL_GESEHEN, true).apply()
    }

    private companion object {
        const val KEY_DATENSCHUTZ_GESEHEN = "datenschutz_gesehen"
        const val KEY_ZUGANGSWAHL_GESEHEN = "zugangswahl_gesehen"
    }
}
