package com.magicbill.app.core

/**
 * The ONE place a table's identity string is built on the phone — never
 * compose it inline anywhere else.
 *
 * `processing_orders.table_number` at the counter is free text with no section
 * column, so the section has to travel inside the name itself. Without it,
 * "1" in AC and "1" in NORMAL are the same table to the counter: the second
 * one opened silently becomes the sub-table "1B", and on the phone one order
 * lights up every tile labelled "1".
 *
 * What this returns is exactly what the KOT and the bill print, so the waiter
 * taps "AC 1" and the kitchen reads "Table: AC 1".
 */
fun composeTableName(section: String?, label: String?): String {
    val s = section.orEmpty().trim()
    val l = label.orEmpty().trim()
    return when {
        s.isEmpty() -> l
        l.isEmpty() -> s
        else -> "$s $l"
    }
}

/** A table's own slot plus its sub-tables: "AC 1" owns "AC 1B".."AC 1H". */
private val SUB_LETTERS = 'B'..'H'

/**
 * True when an order sitting on [tableNumber] belongs to the tile named
 * [name] — an exact match, or the name plus one sub-table letter. Mirrors
 * `belongsToTable` in the POS (MB-pos/src/features/billing/tableUtils.ts) so
 * both ends agree on what "this table is occupied" means.
 */
fun tableNumberBelongsTo(tableNumber: String?, name: String): Boolean {
    val value = tableNumber.orEmpty().trim().uppercase()
    val base = name.trim().uppercase()
    if (base.isEmpty() || value.isEmpty()) return false
    if (value == base) return true
    return value.length == base.length + 1 &&
        value.startsWith(base) &&
        value.last() in SUB_LETTERS
}
