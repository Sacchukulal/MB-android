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
    private const val RUPEE = "₹"
    private const val MINUS = "−"

    /** "₹1,23,456.50". */
    fun rupees(paise: Long): String = sign(paise) + RUPEE + plainAbs(paise)

    /** "1,23,456.50" — no symbol, two decimals, for a column of numbers. */
    fun plain(paise: Long): String = sign(paise) + plainAbs(paise)

    /** Whole rupees, rounded half up, for a tile: "₹1,23,457". */
    fun whole(paise: Long): String {
        val rounded = (abs(paise) + 50) / 100
        return sign(paise) + RUPEE + groupIndian(rounded)
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
