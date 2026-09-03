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
 *
 * The type scale is v2.4.6's, the one the owner signed off: a 45sp extra-bold hero, a 24sp
 * page title, 16sp body, 14sp rows, 12sp small print, 11sp nav labels. There is ONE size; no
 * text-size setting multiplies it.
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
    val page = 20.dp
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

/** A table card on the floor: as many to a row as the width allows, none narrower than [min]. */
object Tile {
    val min = 96.dp
    /** Width to height. */
    val ratio = 0.9f
    /** The one coloured edge. */
    val stripe = 5.dp
}

/** Motion: short, and nothing on the floor path waits for it. */
object Motion {
    const val fast = 120
    const val normal = 200
}

object IconSize {
    val xs = 12.dp
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
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

/** Type by job. Numbers that align are tabular; prose is never mono. */
@Immutable
class MbType {
    private val tabular = "tnum"

    /** The page title (v1 headlineSmall). */
    val page = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp)
    /** A section heading, a row's title when it leads (v1 titleMedium). */
    val section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp)
    /** A field's label, a column heading, an overline (v1 labelMedium). */
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
    /** The label under a nav icon (v1 labelSmall). */
    val navLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
    /** Running text and the name on a row (v1 bodyLarge). */
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp)
    /** A number in a row that must line up with the number above it (v1 bodyMedium, tabular). */
    val cell = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontFeatureSettings = tabular)
    /** The small line under a row, a timestamp, a hint (v1 bodySmall). */
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
    /** The one big rupee figure on a screen (v1 displayMedium). */
    val hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 45.sp, lineHeight = 52.sp, fontFeatureSettings = tabular)
    /** A figure in a tile (v1 titleLarge). */
    val stat = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, fontFeatureSettings = tabular)
    /** A small figure beside a row, an insight value (v1 titleSmall). */
    val statSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontFeatureSettings = tabular)
    /** A button (v1 labelLarge). */
    val button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)
    /** The brand name on the welcome screen (v1 displaySmall). */
    val brand = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp)
    /** The section over a table card's number. */
    val tileLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp)
    /** The table's number. */
    val tileNumber = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, fontFeatureSettings = tabular)
    /** The money on a table card. */
    val tileMoney = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp, fontFeatureSettings = tabular)
    /** Whose the table is, the items, the timer, the seats. */
    val tileNote = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp, fontFeatureSettings = tabular)
    /** A code somebody types from a screen: the pairing code, the shop code. */
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 2.sp)
}
