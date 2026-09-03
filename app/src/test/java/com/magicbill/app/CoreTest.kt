package com.magicbill.app

import com.magicbill.app.core.Argon
import com.magicbill.app.core.Ist
import com.magicbill.app.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test fun indian_grouping() {
        assertEquals("₹12,34,567.89", Money.rupees(123456789))
        assertEquals("₹1,000.00", Money.rupees(100000))
        assertEquals("₹100.00", Money.rupees(10000))
        assertEquals("₹0.50", Money.rupees(50))
        assertEquals("−₹5.00", Money.rupees(-500))
        assertEquals("1,00,00,000.00", Money.plain(1000000000))
    }

    @Test fun whole_rounds_half_up() {
        assertEquals("₹2,000", Money.whole(199950))
        assertEquals("₹1,999", Money.whole(199949))
        assertEquals("₹0", Money.whole(49))
    }

    @Test fun the_counters_text_becomes_paise() {
        assertEquals(24000L, Money.parsePlain("240.00"))
        assertEquals(24050L, Money.parsePlain("240.5"))
        assertEquals(123456789L, Money.parsePlain("₹12,34,567.89"))
        assertNull(Money.parsePlain("two hundred"))
        assertNull(Money.parsePlain(""))
    }

    @Test fun quantities() {
        assertEquals("2", Money.qty(2000))
        assertEquals("0.5", Money.qty(500))
        assertEquals("1.25", Money.qty(1250))
        assertEquals(500L, Money.parseQty("0.5"))
        assertEquals(2000L, Money.parseQty("2"))
        assertNull(Money.parseQty("0"))
        assertNull(Money.parseQty("x"))
    }
}

class IstTest {
    @Test fun a_business_day_is_ist_not_utc() {
        // 2026-08-27 18:35 UTC is 00:05 on the 28th in India.
        val ms = java.time.Instant.parse("2026-08-27T18:35:00Z").toEpochMilli()
        assertEquals("2026-08-28", Ist.key(Ist.day(ms)))
        assertEquals("12:05 am", Ist.clock(ms))
    }

    @Test fun ranges() {
        val today = LocalDate.of(2026, 8, 28) // a Friday
        assertEquals(LocalDate.of(2026, 8, 24), Ist.thisWeek(today).from)
        assertEquals(LocalDate.of(2026, 8, 1), Ist.thisMonth(today).from)
        val last = Ist.lastMonth(today)
        assertEquals(LocalDate.of(2026, 7, 1), last.from)
        assertEquals(LocalDate.of(2026, 7, 31), last.to)
        val week = Ist.last7(today)
        assertEquals(7, week.days)
        val before = week.previous()
        assertEquals(7, before.days)
        assertEquals(week.from.minusDays(1), before.to)
    }

    @Test fun ago_is_one_wording() {
        val now = java.time.Instant.parse("2026-08-28T10:00:00Z").toEpochMilli()
        assertEquals("just now", Ist.ago(now - 10_000, now))
        assertEquals("4 min ago", Ist.ago(now - 4 * 60_000, now))
        assertEquals("2 h ago", Ist.ago(now - 2 * 3_600_000, now))
        assertEquals("yesterday", Ist.ago(now - 20 * 3_600_000, now))
    }

    @Test fun timestamps_from_the_cloud() {
        assertEquals(java.time.Instant.parse("2026-08-27T15:39:59.090Z").toEpochMilli(), Ist.parseTs("2026-08-27T15:39:59.09+00:00"))
        assertNull(Ist.parseTs(null))
        assertNull(Ist.parseTs("soon"))
    }
}

class ArgonTest {
    @Test fun the_counters_parameters_in_a_phc_string() {
        val salt = ByteArray(16) { it.toByte() }
        val phc = Argon.hashPin("1234", salt)
        assertTrue(phc, phc.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"))
        assertEquals(6, phc.split('$').size)
        assertEquals(phc, Argon.hashPin("1234", salt)) // deterministic for one salt
        assertTrue(Argon.verify("1234", phc))
        assertFalse(Argon.verify("1235", phc))
    }

    @Test fun a_random_salt_each_time() {
        val a = Argon.hashPin("4321")
        val b = Argon.hashPin("4321")
        assertTrue(a != b)
        assertTrue(Argon.verify("4321", a) && Argon.verify("4321", b))
    }
}

class OverscrollBandTest {
    @org.junit.Test fun the_band_resists_and_never_reaches_half_the_viewport() {
        val dim = 2000f
        val small = com.magicbill.app.ui.theme.RubberBandOverscroll.band(300f, dim)
        val big = com.magicbill.app.ui.theme.RubberBandOverscroll.band(30_000f, dim)
        org.junit.Assert.assertTrue("a short pull moves a little: $small", small in 50f..120f)
        org.junit.Assert.assertTrue("a huge pull stays under half the screen: $big", big < dim * 0.5f && big > dim * 0.4f)
        org.junit.Assert.assertEquals(-small, com.magicbill.app.ui.theme.RubberBandOverscroll.band(-300f, dim), 0.001f)
        org.junit.Assert.assertEquals(0f, com.magicbill.app.ui.theme.RubberBandOverscroll.band(0f, dim), 0f)
    }

    @org.junit.Test fun unband_is_the_inverse_of_band() {
        val dim = 1800f
        for (x in listOf(-2500f, -40f, 12f, 600f, 4000f)) {
            val back = com.magicbill.app.ui.theme.RubberBandOverscroll.unband(com.magicbill.app.ui.theme.RubberBandOverscroll.band(x, dim), dim)
            org.junit.Assert.assertEquals("$x", x, back, kotlin.math.abs(x) * 0.01f + 0.5f)
        }
    }
}
