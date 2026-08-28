package com.magicbill.app.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A bill as the counter froze it (`bills.lines`, `bills.payments`, `bills.tax_rows`), read
 * back for the receipt view. The shapes are mb-core's own serde output: money is paise, a
 * quantity is thousandths, a payment mode is "cash" or {"credit": "<customer>"}.
 *
 * The phone shows what was frozen. The only arithmetic here is a line's gross — the frozen
 * unit price times the frozen quantity — so a receipt has a figure beside each line; the
 * subtotal, tax, discount and total are the counter's own columns and never recomputed.
 */
object BillJson {
    data class Line(
        val name: String,
        val qtyThousandths: Long,
        val unitPricePaise: Long,
        val modifiers: List<Pair<String, Long>>,
        val note: String?,
        /** "10% off" / "₹20 off", as given, or null. */
        val discount: String?,
        val course: String?,
    ) {
        val qty: String get() = Money.qty(qtyThousandths)
        /** Gross: (unit price + modifiers) × qty, before any discount. */
        val grossPaise: Long get() = ((unitPricePaise + modifiers.sumOf { it.second }) * qtyThousandths + 500) / 1000
    }

    data class Payment(val mode: String, val label: String, val paise: Long, val reference: String?, val settlesCredit: Boolean)

    data class TaxRow(val rateBp: Int, val taxablePaise: Long, val centralPaise: Long, val statePaise: Long, val integratedPaise: Long) {
        val taxPaise: Long get() = centralPaise + statePaise + integratedPaise
        val rateText: String get() = if (rateBp % 100 == 0) "${rateBp / 100}%" else "${rateBp / 100.0}%"
    }

    data class Taxes(val rows: List<TaxRow>, val nonGstPaise: Long, val exemptPaise: Long, val untaxedPaise: Long)

    fun lines(text: String): List<Line> = parseJsonOrNull(text)?.objects()?.map { o ->
        val snap = o.obj("snapshot") ?: JsonObject(emptyMap())
        Line(
            name = snap.str("name").ifBlank { "Item" },
            qtyThousandths = o.long("qty"),
            unitPricePaise = snap.long("unit_price"),
            modifiers = o.arr("modifiers").mapNotNull { m -> (m as? JsonObject)?.let { it.str("name") to it.long("price_delta") } },
            note = o.strOrNull("note"),
            discount = o.obj("line_discount")?.let { discountText(it.obj("discount")) },
            course = snap.strOrNull("course"),
        )
    } ?: emptyList()

    /** `{"percent": 1000}` = 10 %, `{"amount": 2000}` = ₹20. */
    fun discountText(d: JsonObject?): String? {
        d ?: return null
        d.longOrNull("percent")?.let { bp -> return (if (bp % 100 == 0L) "${bp / 100}%" else "${bp / 100.0}%") + " off" }
        d.longOrNull("amount")?.let { return (if (it % 100 == 0L) Money.whole(it) else Money.rupees(it)) + " off" }
        return null
    }

    fun payments(text: String): Pair<List<Payment>, Long> {
        val o = parseJsonOrNull(text)?.asObjectOrNull()
        val list = when {
            o != null -> o.arr("payments").objects()
            else -> parseJsonOrNull(text)?.objects() ?: emptyList()
        }
        val tip = o?.long("tip") ?: 0L
        return list.map { p ->
            val (mode, label) = modeOf(p["mode"])
            Payment(mode, label, p.longOrNull("amount") ?: p.long("paise"), p.strOrNull("reference"), p.bool("settles_credit"))
        } to tip
    }

    /** "cash" → cash · {"credit": id} → credit · {"other": "Sodexo"} → other, labelled Sodexo. */
    fun modeOf(e: kotlinx.serialization.json.JsonElement?): Pair<String, String> = when (e) {
        is JsonPrimitive -> (e.contentOrNull ?: "other").let { it to modeLabel(it) }
        is JsonObject -> when {
            e.containsKey("credit") -> "credit" to "Credit"
            e.containsKey("other") -> "other" to (e.strOrNull("other")?.ifBlank { null } ?: "Other")
            else -> "other" to "Other"
        }
        else -> "other" to "Other"
    }

    fun modeLabel(mode: String): String = when (mode.lowercase()) {
        "cash" -> "Cash"
        "card" -> "Card"
        "upi" -> "UPI"
        "credit", "khata" -> "Credit"
        else -> mode.replaceFirstChar { it.uppercase() }
    }

    fun taxes(text: String): Taxes {
        val o = parseJsonOrNull(text)?.asObjectOrNull() ?: return Taxes(emptyList(), 0, 0, 0)
        val rows = o.arr("rows").objects().map { r ->
            val g = r.obj("gst")
            TaxRow(r.int("rate"), r.long("taxable"), g?.long("central") ?: 0L, g?.long("state") ?: 0L, g?.long("integrated") ?: 0L)
        } + o.arr("vat").objects().map { r ->
            val v = r.obj("vat")
            TaxRow(r.int("rate"), r.long("taxable"), 0L, v?.long("amount") ?: v?.long("state") ?: 0L, 0L)
        }
        return Taxes(rows, o.long("non_gst_value"), o.long("exempt_value"), o.long("untaxed_value"))
    }

    /** The `by_payment` map of a day: {"cash": paise, …} → ordered list. */
    fun split(text: String): List<Pair<String, Long>> {
        val o = parseJsonOrNull(text)?.asObjectOrNull() ?: return emptyList()
        return o.entries.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let { k to it } }
            .sortedByDescending { it.second }
    }
}
