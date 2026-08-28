package com.daywalker91.parfumsammlung.data

import android.content.Context

/**
 * Lokale Ablage des individuellen Lizenzschlüssels für den Gateway-Zugang
 * (siehe ClaudeService) — bewusst unverschlüsselt wie SortPreferenceStore,
 * der Code selbst ist kein finanziell wertvolles Geheimnis, sondern ein
 * revoke-barer Türsteher-Code (siehe Plan). Kein Default, kein einkompilierter
 * Code — leer heißt schlicht "kein Gateway-Zugang vorhanden", genau wie ein
 * leerer ClaudeApiKeyStore heute schon "kein eigener Key" bedeutet.
 */
class GatewayAccessCodeStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun getCode(): String? = prefs.getString(KEY_ZUGANGSCODE, null)?.takeIf { it.isNotBlank() }

    fun setCode(code: String) {
        prefs.edit().putString(KEY_ZUGANGSCODE, code.trim()).apply()
    }

    fun clearCode() {
        prefs.edit().remove(KEY_ZUGANGSCODE).apply()
    }

    private companion object {
        const val KEY_ZUGANGSCODE = "gateway_zugangscode"
    }
}
