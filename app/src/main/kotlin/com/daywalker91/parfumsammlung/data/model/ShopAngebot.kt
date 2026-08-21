package com.daywalker91.parfumsammlung.data.model

/**
 * Ein per Websuche gefundenes Shop-Angebot (Phase 8c) — bewusst eine reine
 * Momentaufnahme zum Abrufzeitpunkt, wird nirgends in der DB gespeichert.
 */
data class ShopAngebot(
    val shopName: String,
    val link: String?,
    val preis: Double?,
    val verfuegbarkeit: String?,
)
