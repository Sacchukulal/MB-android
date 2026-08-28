package com.magicbill.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Every date the shop sees is an IST business day. Nothing here reads the phone's zone. */
object Ist {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun today(nowMs: Long = System.currentTimeMillis()): LocalDate = day(nowMs)

    fun key(d: LocalDate): String = d.toString()

    fun parseDay(s: String?): LocalDate? = try {
        if (s.isNullOrBlank()) null else LocalDate.parse(s.take(10))
    } catch (e: Exception) {
        null
    }

    /** "2026-08-27T15:39:59.09+00:00" → ms. Null when it is not a timestamp. */
    fun parseTs(s: String?): Long? = try {
        if (s.isNullOrBlank()) null else java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            java.time.LocalDateTime.parse(s!!.replace(' ', 'T')).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }

    private val clock12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    private val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    /** "9:41 pm". */
    fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(clock12).lowercase()

    /** "Today", "Yesterday", "12 Aug", "12 Aug 2025". */
    fun dateWords(d: LocalDate, today: LocalDate): String = when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(dayMonth)
        else -> d.format(dayMonthYear)
    }

    /** "12 Aug, 9:41 pm" — a bill's moment. */
    fun moment(ms: Long, today: LocalDate): String {
        val d = day(ms)
        return dateWords(d, today) + ", " + clock(ms)
    }

    /** "just now", "4 min ago", "2 h ago", "yesterday", "12 Aug". One clock, one wording. */
    fun ago(ms: Long, nowMs: Long): String {
        val diff = nowMs - ms
        if (diff < 60_000) return "just now"
        val min = diff / 60_000
        if (min < 60) return "$min min ago"
        val h = min / 60
        if (h < 24 && day(ms) == day(nowMs)) return "$h h ago"
        val d = day(ms)
        val today = day(nowMs)
        return if (d == today.minusDays(1)) "yesterday" else dateWords(d, today).lowercase()
    }

    data class Range(val from: LocalDate, val to: LocalDate, val label: String) {
        val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1
        fun contains(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)

        /** The same length of days ending the day before this range starts — "vs last time". */
        fun previous(): Range {
            val n = days
            return Range(from.minusDays(n), from.minusDays(1), "before")
        }
    }

    fun today(today: LocalDate) = Range(today, today, "Today")
    fun yesterday(today: LocalDate) = Range(today.minusDays(1), today.minusDays(1), "Yesterday")
    fun thisWeek(today: LocalDate): Range {
        val monday = today.minusDays(((today.dayOfWeek.value - DayOfWeek.MONDAY.value) % 7).toLong())
        return Range(monday, today, "This week")
    }
    fun thisMonth(today: LocalDate) = Range(today.withDayOfMonth(1), today, "This month")
    fun lastMonth(today: LocalDate): Range {
        val first = today.withDayOfMonth(1).minusMonths(1)
        return Range(first, first.withDayOfMonth(first.lengthOfMonth()), "Last month")
    }
    fun last7(today: LocalDate) = Range(today.minusDays(6), today, "7 days")
    fun last30(today: LocalDate) = Range(today.minusDays(29), today, "30 days")

    fun startOfDayMs(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOfDayMs(d: LocalDate): Long = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    fun isMorning(nowMs: Long): Boolean = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime().isBefore(LocalTime.NOON)
}
