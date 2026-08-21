package com.daywalker91.parfumsammlung.data

import android.content.Context

/**
 * Persistiert einen frei gewählten Spenden-Link (z. B. PayPal.me) — für den
 * Fall, dass die App mal von mehreren Personen genutzt wird und diese sich an
 * den Claude-API-Kosten beteiligen wollen (siehe UsageCounterStore). Rein
 * manueller/Ehrlichkeits-Ablauf: kein Backend, keine automatische
 * Zahlungsbestätigung möglich — der Nutzer bestätigt selbst über den
 * "Verbrauch beglichen"-Button, sobald tatsächlich Geld eingegangen ist.
 */
class SpendenLinkStore(context: Context) {
    private val prefs = context.getSharedPreferences("spenden_prefs", Context.MODE_PRIVATE)

    fun getLink(): String? = prefs.getString(KEY_LINK, null)?.takeIf { it.isNotBlank() }

    fun setLink(link: String) {
        prefs.edit().putString(KEY_LINK, link.trim()).apply()
    }

    private companion object {
        const val KEY_LINK = "spenden_link"
    }
}
