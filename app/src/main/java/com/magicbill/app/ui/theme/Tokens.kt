package com.magicbill.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magicbill.app.R

/*
 * THE ONE FILE THAT DECIDES WHAT THE PHONE LOOKS LIKE — the scales. They do not change between
 * themes: a theme is a palette, not a different product. Spacing and type are by JOB, never by
 * number, so a screen says "the gap between two sections" and not "24".
 */

object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp
}

/** The spacing contract. */
object Gap {
    /** An icon to its label, a number to its unit — inside one thing. */
    val inline = Space.s2
    /** Between two controls that belong to each other. */
    val field = Space.s3
    /** Between two groups of controls under one heading. */
    val group = Space.s5
    /** Between two sections of a page. */
    val section = Space.s6
    /** The page's own margin, all four sides. */
    val page = Space.s4
}

/** "Middle": modest, not soft, not sharp. */
object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 22.dp
    val pill = 999.dp
}

/** A thumb on a phone held in one hand, forty times a night. */
object Target {
    val min = 48.dp
    val small = 36.dp
    val row = 56.dp
}

/** Motion: short, and nothing on the floor path waits for it. */
object Motion {
    const val fast = 120
    const val normal = 200
}

object IconSize {
    val sm = 16.dp
    val md = 20.dp
    val lg = 28.dp
}

/** Layout breakpoints. Above `wide` the floor is two panes. */
object Breakpoint {
    val wide = 600.dp
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Type by job. `scale` is the one text-size setting and multiplies everything, so it reaches
 * every screen at once. Numbers that align are tabular; prose is never mono.
 */
@Immutable
class MbType(val scale: Float = 1f) {
    private fun s(n: Float) = (n * scale).sp
    private val tabular = "tnum"

    /** The page title. */
    val page = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = s(23f), lineHeight = s(28f), letterSpacing = (-0.2).sp)
    /** A section heading. */
    val section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = s(16f), lineHeight = s(22f))
    /** A field's label, a column heading. */
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = s(13f), lineHeight = s(18f), letterSpacing = 0.2.sp)
    /** Running text and the name on a row. */
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = s(16f), lineHeight = s(22f))
    /** A number in a row that must line up with the number above it. */
    val cell = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = s(15f), lineHeight = s(20f), fontFeatureSettings = tabular)
    /** The small line under a row, a timestamp, a hint. */
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = s(13f), lineHeight = s(18f))
    /** The one big rupee figure on a screen. */
    val hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = s(34f), lineHeight = s(40f), letterSpacing = (-0.5).sp, fontFeatureSettings = tabular)
    /** A figure in a tile. */
    val stat = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = s(22f), lineHeight = s(28f), fontFeatureSettings = tabular)
    /** A button. */
    val button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = s(15f), lineHeight = s(20f))
    /** A code somebody types from a screen: the shop code, a pairing code. */
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = s(20f), lineHeight = s(28f), letterSpacing = 2.sp)
}
