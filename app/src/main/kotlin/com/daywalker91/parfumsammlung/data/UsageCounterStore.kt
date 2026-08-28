package com.daywalker91.parfumsammlung.data

import android.content.Context
import java.time.YearMonth

/**
 * Zwei unabhängige, lokal geschätzte Verbrauchszähler für die Claude-API —
 * bewusst nur eine grobe Schätzung anhand der veröffentlichten Preise, kein
 * Live-Kontostand (dafür bräuchte es Admin-API-Zugriff, den ein persönlicher
 * API-Key nicht hat).
 *
 * "Seit letzter Zahlung" läuft, bis er manuell per [verbrauchBeglichen]
 * zurückgesetzt wird (siehe Settings-Button, im Zusammenspiel mit dem
 * PayPal-Spendenlink). "Diesen Monat" setzt sich beim Kalendermonatswechsel
 * automatisch zurück (fauler On-Access-Check statt Scheduler/WorkManager —
 * es reicht, beim nächsten Lesen/Schreiben zu bemerken, dass sich der
 * gespeicherte Monats-Schlüssel vom aktuellen unterscheidet).
 *
 * Geldbeträge werden intern als Mikro-Cent (Long, 1 Cent = 1_000_000
 * Mikro-Cent) akkumuliert statt als Float/Double — bei Claude Haiku 4.5s
 * Preisen ($1,00/$5,00 pro 1 Mio. Token) ergibt das exakte Ganzzahl-Arithmetik
 * ohne jede Rundungs-Drift über viele Anfragen hinweg.
 */
class UsageCounterStore(context: Context) {
    private val prefs = context.getSharedPreferences("usage_counter_prefs", Context.MODE_PRIVATE)

    // --- Set 1: seit letzter Zahlung (nur manueller Reset) ---

    fun tokenSeitZahlung(): Long = prefs.getLong(KEY_TOKEN_ZAHLUNG, 0)
    fun anfragenSeitZahlung(): Int = prefs.getInt(KEY_ANFRAGEN_ZAHLUNG, 0)
    fun kostenSeitZahlungEuro(): Double = microCentZuEuro(prefs.getLong(KEY_KOSTEN_ZAHLUNG, 0))
    fun letzteZahlungMillis(): Long = prefs.getLong(KEY_LETZTE_ZAHLUNG, 0)

    /** Setzt ausschließlich Set 1 zurück — der Monats-Zähler (Set 2) bleibt unberührt. */
    fun verbrauchBeglichen() {
        prefs.edit()
            .putLong(KEY_TOKEN_ZAHLUNG, 0)
            .putInt(KEY_ANFRAGEN_ZAHLUNG, 0)
            .putLong(KEY_KOSTEN_ZAHLUNG, 0)
            .putLong(KEY_LETZTE_ZAHLUNG, System.currentTimeMillis())
            .apply()
    }

    // --- Set 2: aktueller Kalendermonat (automatischer Reset) ---

    fun tokenDiesenMonat(): Long = monatBereinigt().getLong(KEY_TOKEN_MONAT, 0)
    fun anfragenDiesenMonat(): Int = monatBereinigt().getInt(KEY_ANFRAGEN_MONAT, 0)
    fun kostenDiesenMonatEuro(): Double = microCentZuEuro(monatBereinigt().getLong(KEY_KOSTEN_MONAT, 0))

    /** Prüft den gespeicherten Monats-Schlüssel und setzt Set 2 bei Bedarf auf 0, bevor gelesen wird. */
    private fun monatBereinigt(): android.content.SharedPreferences {
        val aktuellerMonat = YearMonth.now().toString()
        if (prefs.getString(KEY_MONAT_SCHLUESSEL, null) != aktuellerMonat) {
            prefs.edit()
                .putString(KEY_MONAT_SCHLUESSEL, aktuellerMonat)
                .putLong(KEY_TOKEN_MONAT, 0)
                .putInt(KEY_ANFRAGEN_MONAT, 0)
                .putLong(KEY_KOSTEN_MONAT, 0)
                .apply()
        }
        return prefs
    }

    // --- Gemeinsames Hinzufügen (aktualisiert beide Sets gleichzeitig) ---

    /** Wird nach jeder erfolgreichen Claude-Antwort mit deren usage-Feld aufgerufen. */
    fun hinzufuegen(inputTokens: Int, outputTokens: Int) {
        val kostenMicroCent = inputTokens.toLong() * PREIS_INPUT_MICROCENT_PRO_TOKEN +
            outputTokens.toLong() * PREIS_OUTPUT_MICROCENT_PRO_TOKEN
        val tokenGesamt = (inputTokens + outputTokens).toLong()

        monatBereinigt() // stellt sicher, dass Set 2 vor dem Schreiben aktuell ist
        prefs.edit()
            .putLong(KEY_TOKEN_ZAHLUNG, tokenSeitZahlung() + tokenGesamt)
            .putInt(KEY_ANFRAGEN_ZAHLUNG, anfragenSeitZahlung() + 1)
            .putLong(KEY_KOSTEN_ZAHLUNG, microCentRoh(KEY_KOSTEN_ZAHLUNG) + kostenMicroCent)
            .putLong(KEY_TOKEN_MONAT, tokenDiesenMonat() + tokenGesamt)
            .putInt(KEY_ANFRAGEN_MONAT, anfragenDiesenMonat() + 1)
            .putLong(KEY_KOSTEN_MONAT, microCentRoh(KEY_KOSTEN_MONAT) + kostenMicroCent)
            .apply()
    }

    private fun microCentRoh(key: String): Long = prefs.getLong(key, 0)
    private fun microCentZuEuro(microCent: Long): Double = microCent / 1_000_000.0 / 100.0

    private companion object {
        const val KEY_TOKEN_ZAHLUNG = "token_seit_zahlung"
        const val KEY_ANFRAGEN_ZAHLUNG = "anfragen_seit_zahlung"
        const val KEY_KOSTEN_ZAHLUNG = "kosten_seit_zahlung_microcent"
        const val KEY_LETZTE_ZAHLUNG = "letzte_zahlung_millis"

        const val KEY_TOKEN_MONAT = "token_monat"
        const val KEY_ANFRAGEN_MONAT = "anfragen_monat"
        const val KEY_KOSTEN_MONAT = "kosten_monat_microcent"
        const val KEY_MONAT_SCHLUESSEL = "monat_schluessel"

        // Claude Haiku 4.5: $1,00 / $5,00 pro 1 Mio. Token (Anthropic-Preisliste).
        // Bewusst ohne USD→EUR-Umrechnung — in der Praxis stimmten die reinen
        // Dollar-Zahlen bereits 1:1 mit dem tatsächlichen Verbrauch auf dem
        // Account überein (Cent-Beträge werden direkt als €-Cent behandelt).
        // 1 Cent = 1_000_000 Mikro-Cent, $1,00/1_000_000 Token = 100 Cent/1_000_000
        // Token = 100 Mikro-Cent/Token — exakte Ganzzahl, keine Rundung nötig.
        const val PREIS_INPUT_MICROCENT_PRO_TOKEN = 100L
        const val PREIS_OUTPUT_MICROCENT_PRO_TOKEN = 500L
    }
}
