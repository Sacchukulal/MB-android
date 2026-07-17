package com.magicbill.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatting + IST date helpers (POS day boundary = Asia/Kolkata). */

val IST: ZoneId = ZoneId.of("Asia/Kolkata")

/** ₹ with Indian digit grouping: 123456.7 -> "₹1,23,456.70" */
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

/** Short form for chart labels: 1234 -> "₹1.2k", 245000 -> "₹2.4L" */
fun formatShortINR(value: Double): String {
    val abs = abs(value)
    return when {
        abs >= 1e7 -> "₹${String.format(Locale.US, "%.1f", value / 1e7)}Cr"
        abs >= 1e5 -> "₹${String.format(Locale.US, "%.1f", value / 1e5)}L"
        abs >= 1e3 -> "₹${String.format(Locale.US, "%.1f", value / 1e3)}k"
        else -> "₹${value.roundToLong()}"
    }
}

/** "3 min ago" / "2 hr ago" / "yesterday" for cache-age chips. */
fun timeAgo(epochMs: Long): String {
    val s = ((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)
    if (s < 60) return "just now"
    val m = s / 60
    if (m < 60) return "$m min ago"
    val h = m / 60
    if (h < 24) return "$h hr ago"
    val d = h / 24
    return if (d == 1L) "yesterday" else "$d days ago"
}

/** The IST calendar date ("YYYY-MM-DD") for a given instant. */
fun istDayString(instant: Instant = Instant.now()): String =
    instant.atZone(IST).toLocalDate().toString()

/** UTC instant at which the given IST day starts. */
fun istDayStartUtc(day: String): Instant =
    LocalDate.parse(day).atStartOfDay(IST).toInstant()

/** UTC instant at which the given IST day ends (exclusive). */
fun istDayEndUtc(day: String): Instant =
    LocalDate.parse(day).plusDays(1).atStartOfDay(IST).toInstant()

/** Shift an IST day string by n days. */
fun shiftDay(day: String, n: Long): String =
    LocalDate.parse(day).plusDays(n).toString()

private val shortDateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val longDateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
private val billTimeFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
private val timeOnlyFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "2026-07-16" -> "16 Jul" */
fun shortDate(day: String): String = LocalDate.parse(day).format(shortDateFmt)

/** "2026-07-16" -> "Wed 16 Jul 2026" */
fun longDate(day: String): String = LocalDate.parse(day).format(longDateFmt)

/** ISO timestamp -> "16 Jul, 2:35 pm" in IST. */
fun billTime(iso: String): String =
    Instant.parse(normalizeIso(iso)).atZone(IST).format(billTimeFmt).lowercase()
        .replaceFirstChar { it.uppercase() }

/** ISO timestamp -> "2:35 pm" in IST. */
fun billClock(iso: String): String =
    Instant.parse(normalizeIso(iso)).atZone(IST).format(timeOnlyFmt).lowercase()

/** Postgres timestamps may lack the trailing Z / use +00:00 with micros. */
private fun normalizeIso(iso: String): String = when {
    iso.endsWith("Z") || iso.contains('+') -> iso
    else -> iso + "Z"
}
