package com.magicbill.app.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * The old app's formatting helpers, kept verbatim so the ported components (AnimatedRupees,
 * TrendChart, CacheChip…) render exactly as they always did. New code prefers [Money] (paise);
 * these bridge at the UI edge only.
 */

/** ₹ with Indian digit grouping: 123456.7 → "₹1,23,456.70". */
fun formatINR(value: Double, decimals: Int? = null): String {
    val d = decimals ?: if (value == Math.floor(value)) 0 else 2
    val neg = value < 0
    val fixed = String.format(Locale.US, "%.${d}f", abs(value))
    val intPart = fixed.substringBefore('.')
    val fracPart = fixed.substringAfter('.', "")
    val last3 = intPart.takeLast(3)
    val rest = intPart.dropLast(3)
    val grouped = if (rest.isEmpty()) last3 else {
        rest.reversed().chunked(2).joinToString(",") { it }.reversed() + "," + last3
    }
    return "${if (neg) "-" else ""}₹$grouped${if (fracPart.isNotEmpty()) ".$fracPart" else ""}"
}

/** Short form for chart labels: 1234 → "₹1.2k", 245000 → "₹2.4L". */
fun formatShortINR(value: Double): String {
    val a = abs(value)
    return when {
        a >= 1e7 -> "₹${String.format(Locale.US, "%.1f", value / 1e7)}Cr"
        a >= 1e5 -> "₹${String.format(Locale.US, "%.1f", value / 1e5)}L"
        a >= 1e3 -> "₹${String.format(Locale.US, "%.1f", value / 1e3)}k"
        else -> "₹${value.roundToLong()}"
    }
}

/** "just now", "4 min ago" — the wording the CacheChip always used. */
fun timeAgo(ms: Long, nowMs: Long = System.currentTimeMillis()): String = Ist.ago(ms, nowMs)

/** Paise → the Double the old components take. UI edge only. */
fun Long.paiseToRupees(): Double = this / 100.0
