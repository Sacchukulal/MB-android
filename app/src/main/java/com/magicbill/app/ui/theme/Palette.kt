package com.magicbill.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A theme is one of these. Semantic colours (ok / warn / danger / info) are separate from the
 * accent; payment-mode colours are fixed to the mode in both themes so a chart reads the same
 * on any phone. Every raw hex in the app is in this file — a test enforces it.
 */
@Immutable
data class MbColors(
    val isDark: Boolean,
    /** The canvas. Screens draw on it directly; there are no cards in cards. */
    val bg: Color,
    /** A sheet, a dialog, a bar. */
    val surface: Color,
    /** The one raised level: a panel, a tile, an input. */
    val raised: Color,
    val line: Color,
    val lineSoft: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val cash: Color,
    val card: Color,
    val upi: Color,
    val credit: Color,
    val otherPay: Color,
)

val LightColors = MbColors(
    isDark = false,
    bg = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE8EEF5),
    line = Color(0xFFCBD5E1),
    lineSoft = Color(0xFFE2E8F0),
    ink = Color(0xFF0F172A),
    inkMuted = Color(0xFF475569),
    inkFaint = Color(0xFF94A3B8),
    accent = Color(0xFF047857),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD1FAE5),
    onAccentSoft = Color(0xFF064E3B),
    ok = Color(0xFF047857),
    okSoft = Color(0xFFD1FAE5),
    warn = Color(0xFFB45309),
    warnSoft = Color(0xFFFEF3C7),
    danger = Color(0xFFB91C1C),
    dangerSoft = Color(0xFFFEE2E2),
    info = Color(0xFF1D4ED8),
    infoSoft = Color(0xFFDBEAFE),
    cash = Color(0xFF059669),
    card = Color(0xFF2563EB),
    upi = Color(0xFF0D9488),
    credit = Color(0xFFB45309),
    otherPay = Color(0xFF64748B),
)

val DarkColors = MbColors(
    isDark = true,
    bg = Color(0xFF0B1120),
    surface = Color(0xFF131B2E),
    raised = Color(0xFF182238),
    line = Color(0xFF334155),
    lineSoft = Color(0xFF1E293B),
    ink = Color(0xFFE2E8F0),
    inkMuted = Color(0xFF94A3B8),
    inkFaint = Color(0xFF64748B),
    accent = Color(0xFF10B981),
    onAccent = Color(0xFF04281B),
    accentSoft = Color(0xFF065F46),
    onAccentSoft = Color(0xFFA7F3D0),
    ok = Color(0xFF34D399),
    okSoft = Color(0xFF064E3B),
    warn = Color(0xFFFBBF24),
    warnSoft = Color(0xFF78350F),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0xFF7F1D1D),
    info = Color(0xFF60A5FA),
    infoSoft = Color(0xFF1E3A8A),
    cash = Color(0xFF10B981),
    card = Color(0xFF3B82F6),
    upi = Color(0xFF2DD4BF),
    credit = Color(0xFFF59E0B),
    otherPay = Color(0xFF94A3B8),
)

/** The one place a payment mode becomes a colour. Unknown modes share one grey. */
fun MbColors.payment(mode: String): Color = when (mode.lowercase()) {
    "cash" -> cash
    "card" -> card
    "upi" -> upi
    "credit", "khata" -> credit
    else -> otherPay
}
