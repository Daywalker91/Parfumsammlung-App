package com.daywalker91.parfumsammlung.data.update

import android.content.Context

/** Siehe Plan, Kapitel „Entwickler-Optionen & Update-Kanal (Stable/Experimental)". */
enum class UpdateChannel { STABLE, EXPERIMENTAL }

/** Persistiert unabhängig von der (nur session-lokalen) Sichtbarkeit des Entwickler-Menüs. */
class UpdateChannelStore(context: Context) {
    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    fun getChannel(): UpdateChannel =
        UpdateChannel.valueOf(prefs.getString(KEY_CHANNEL, UpdateChannel.STABLE.name)!!)

    fun setChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.name).apply()
    }

    private companion object {
        const val KEY_CHANNEL = "update_kanal"
    }
}
