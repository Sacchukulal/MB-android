package com.magicbill.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Money is paise, a Long, everywhere in the app. This is the ONE place it becomes text.
 * Indian grouping: 12,34,567.00. The phone never computes money — it shows what the counter
 * or the cloud computed — so there is no add, no tax, no discount here on purpose.
 */
object Money {
    /** The one rupee sign, for a label beside a field. */
    const val SYMBOL = "₹"
    private const val RUPEE = SYMBOL
    private const val MINUS = "−"

    /** "₹1,23,456.50". */
    fun rupees(paise: Long): String = sign(paise) + RUPEE + plainAbs(paise)

    /** The counter's "1187.00", shown the one way: "₹1,187.00". Text that is not money is shown as it came. */
    fun fromPlain(text: String): String = parsePlain(text)?.let { rupees(it) } ?: text

    /** "1,23,456.50" — no symbol, two decimals, for a column of numbers. */
    fun plain(paise: Long): String = sign(paise) + plainAbs(paise)

    /** Whole rupees, rounded half up, for a tile: "₹1,23,457". */
    fun whole(paise: Long): String {
        val rounded = (abs(paise) + 50) / 100
        return sign(paise) + RUPEE + groupIndian(rounded)
    }

    /** A chart's readout: "₹1.2k", "₹2.4L", "₹1.5Cr"; under a thousand, whole rupees. */
    fun short(paise: Long): String {
        val rupees = abs(paise) / 100.0
        val figure = when {
            rupees >= 1e7 -> String.format(java.util.Locale.US, "%.1fCr", rupees / 1e7)
            rupees >= 1e5 -> String.format(java.util.Locale.US, "%.1fL", rupees / 1e5)
            rupees >= 1e3 -> String.format(java.util.Locale.US, "%.1fk", rupees / 1e3)
            else -> ((abs(paise) + 50) / 100).toString()
        }
        return sign(paise) + RUPEE + figure
    }

    private fun sign(paise: Long) = if (paise < 0) MINUS else ""

    private fun plainAbs(paise: Long): String {
        val a = abs(paise)
        return groupIndian(a / 100) + "." + (a % 100).toString().padStart(2, '0')
    }

    fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /** The counter sends "240.00"; paise is what we keep. Null when the text is not money. */
    fun parsePlain(text: String): Long? = try {
        BigDecimal(text.trim().replace(",", "").replace(RUPEE, "").replace(MINUS, "-"))
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    } catch (e: Exception) {
        null
    }

    /** Quantities travel as thousandths; on screen "2", "0.5", "1.25". */
    fun qty(thousandths: Long): String {
        val whole = thousandths / 1000
        val frac = abs(thousandths % 1000)
        if (frac == 0L) return whole.toString()
        return whole.toString() + "." + frac.toString().padStart(3, '0').trimEnd('0')
    }

    /** "2" or "0.5" → thousandths. Null when it is not a quantity. */
    fun parseQty(text: String): Long? = try {
        BigDecimal(text.trim()).setScale(3, RoundingMode.HALF_UP).movePointRight(3).longValueExact()
            .takeIf { it > 0 }
    } catch (e: Exception) {
        null
    }
}
