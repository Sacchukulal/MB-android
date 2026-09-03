package com.magicbill.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * THE ONE FILE THAT DECIDES WHAT THE PHONE'S COLOURS ARE.
 *
 * A theme is one [MbColors]. Two ship — light and dark — and everything on every screen reads
 * its colour from here BY JOB (`Mb.colors.accent`, `Mb.colors.ink`), never by number. The
 * Material colour scheme the M3 components use is DERIVED from this in Theme.kt, so a
 * component and a screen can never disagree. To change the look of the whole app, change the
 * values below and nothing else. A test enforces that no other file spells a colour.
 */
@Immutable
data class MbColors(
    val isDark: Boolean,
    /** The canvas. Screens draw on it directly; there are no cards in cards. */
    val bg: Color,
    /** A sheet, a dialog, a bar. */
    val surface: Color,
    /** The one raised level: a tile, an input, a tonal button. Dark shows it by being LIGHTER. */
    val raised: Color,
    /** Raised, once more: a pressed tile, the nav bar. */
    val raisedHigh: Color,
    val line: Color,
    val lineSoft: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    /** The brand: emerald, and the teal it fades into. */
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    /** The two glows behind every screen. */
    val glow1: Color,
    val glow2: Color,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    /** "The counter said": the brand's teal, never a foreign blue. */
    val info: Color,
    val infoSoft: Color,
    /** Fixed to the payment mode in both themes, so a chart reads the same on any phone. */
    val cash: Color,
    val card: Color,
    val upi: Color,
    val credit: Color,
    val otherPay: Color,
    /**
     * One colour per PERSON on the floor: a waiter's tables all wear their colour, on the phone
     * and on the counter alike (the counter's tokens.css lists the same eight, in the same
     * order, and picks by the same sum). Eight is what a room can tell apart at a glance.
     */
    val people: List<Color>,
)

val LightColors = MbColors(
    isDark = false,
    bg = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFF1F5F9),
    raisedHigh = Color(0xFFE8EEF5),
    line = Color(0xFFCBD5E1),
    lineSoft = Color(0xFFE2E8F0),
    ink = Color(0xFF0F172A),
    inkMuted = Color(0xFF475569),
    inkFaint = Color(0xFF94A3B8),
    accent = Color(0xFF047857),
    accent2 = Color(0xFF0D9488),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD1FAE5),
    onAccentSoft = Color(0xFF064E3B),
    glow1 = Color(0xFF10B981),
    glow2 = Color(0xFF2DD4BF),
    ok = Color(0xFF047857),
    okSoft = Color(0xFFD1FAE5),
    warn = Color(0xFFB45309),
    warnSoft = Color(0xFFFEF3C7),
    danger = Color(0xFFB91C1C),
    dangerSoft = Color(0xFFFEE2E2),
    info = Color(0xFF0F766E),
    infoSoft = Color(0xFFCCFBF1),
    cash = Color(0xFF059669),
    card = Color(0xFF2563EB),
    upi = Color(0xFF0D9488),
    credit = Color(0xFFB45309),
    otherPay = Color(0xFF64748B),
    people = listOf(
        Color(0xFF0D9488), Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFDB2777),
        Color(0xFFEA580C), Color(0xFFCA8A04), Color(0xFF65A30D), Color(0xFF0284C7),
    ),
)

val DarkColors = MbColors(
    isDark = true,
    bg = Color(0xFF0B1120),
    surface = Color(0xFF131B2E),
    raised = Color(0xFF182238),
    raisedHigh = Color(0xFF1E2A45),
    line = Color(0xFF334155),
    lineSoft = Color(0xFF1E293B),
    ink = Color(0xFFE2E8F0),
    inkMuted = Color(0xFF94A3B8),
    inkFaint = Color(0xFF64748B),
    accent = Color(0xFF10B981),
    accent2 = Color(0xFF2DD4BF),
    onAccent = Color(0xFF04281B),
    accentSoft = Color(0xFF065F46),
    onAccentSoft = Color(0xFFA7F3D0),
    glow1 = Color(0xFF10B981),
    glow2 = Color(0xFF2DD4BF),
    ok = Color(0xFF34D399),
    okSoft = Color(0xFF064E3B),
    warn = Color(0xFFFBBF24),
    warnSoft = Color(0xFF78350F),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0xFF7F1D1D),
    info = Color(0xFF5EEAD4),
    infoSoft = Color(0xFF134E4A),
    cash = Color(0xFF10B981),
    card = Color(0xFF3B82F6),
    upi = Color(0xFF2DD4BF),
    credit = Color(0xFFF59E0B),
    otherPay = Color(0xFF94A3B8),
    people = listOf(
        Color(0xFF2DD4BF), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF472B6),
        Color(0xFFFB923C), Color(0xFFFACC15), Color(0xFFA3E635), Color(0xFF38BDF8),
    ),
)

/**
 * The one place a person becomes a colour. The sum of the id's characters picks the slot —
 * the counter does the same sum over the same id, so one waiter is one colour everywhere.
 * Nobody (an order the counter opened with no person) is the muted ink.
 */
fun MbColors.person(id: String?): Color = if (id.isNullOrBlank()) inkMuted else people[personSlot(id, people.size)]

fun personSlot(id: String, slots: Int): Int = id.sumOf { it.code } % slots

/** The one place a payment mode becomes a colour. Unknown modes share one grey. */
fun MbColors.payment(mode: String): Color = when (mode.lowercase()) {
    "cash" -> cash
    "card" -> card
    "upi" -> upi
    "credit", "khata" -> credit
    else -> otherPay
}
