package com.magicbill.app

import com.magicbill.app.cloud.ReportMath
import com.magicbill.app.core.BillJson
import com.magicbill.app.core.Ist
import com.magicbill.app.db.DayItemTotalRow
import com.magicbill.app.db.DayTotalRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BillJsonTest {
    private val lines = """[{"snapshot":{"item_id":"itm_1","name":"Masala Dosa","unit_price":12000,"tax":{"kind":"gst","rate":500,"basis":"inclusive"},"hsn":null,"category_id":"cat_1","course":"Main"},"qty":2000,"note":"extra spicy","modifiers":[{"modifier_id":"m1","name":"Cheese","price_delta":2000}],"line_discount":{"discount":{"percent":1000},"reason":"regular","authorised_by":null}},{"snapshot":{"item_id":"itm_2","name":"Tea","unit_price":1500,"tax":{"kind":"gst","rate":500,"basis":"inclusive"}},"qty":500,"note":null,"modifiers":[],"line_discount":null}]"""

    @Test fun lines_as_the_counter_froze_them() {
        val l = BillJson.lines(lines)
        assertEquals(2, l.size)
        assertEquals("Masala Dosa", l[0].name)
        assertEquals("2", l[0].qty)
        assertEquals(28000L, l[0].grossPaise) // (120 + 20) × 2
        assertEquals("10% off", l[0].discount)
        assertEquals("extra spicy", l[0].note)
        assertEquals("0.5", l[1].qty)
        assertEquals(750L, l[1].grossPaise)
        assertNull(l[1].discount)
    }

    @Test fun payments_in_every_spelling() {
        val (p, tip) = BillJson.payments("""{"payments":[{"mode":"cash","amount":20000,"reference":null,"settles_credit":false},{"mode":{"credit":"cust_1"},"amount":5200,"settles_credit":false},{"mode":{"other":"Sodexo"},"amount":100}],"tip":500}""")
        assertEquals(3, p.size)
        assertEquals("cash" to "Cash", p[0].mode to p[0].label)
        assertEquals("credit" to "Credit", p[1].mode to p[1].label)
        assertEquals("other" to "Sodexo", p[2].mode to p[2].label)
        assertEquals(500L, tip)
        assertEquals("₹20 off", BillJson.discountText(com.magicbill.app.core.parseJsonOrNull("""{"amount":2000}""") as kotlinx.serialization.json.JsonObject))
    }

    @Test fun tax_rows() {
        val t = BillJson.taxes("""{"rows":[{"rate":500,"taxable":24000,"gst":{"central":600,"state":600,"integrated":0}},{"rate":1800,"taxable":1000,"gst":{"central":90,"state":90,"integrated":0}}],"vat":[],"non_gst_value":0,"exempt_value":300,"untaxed_value":0}""")
        assertEquals(2, t.rows.size)
        assertEquals("5%", t.rows[0].rateText)
        assertEquals(1200L, t.rows[0].taxPaise)
        assertEquals(300L, t.exemptPaise)
    }
}

class ReportMathTest {
    private fun day(d: String, bills: Int, net: Long, split: String = """{"cash":$net}""", expenses: Long = 0) =
        DayTotalRow("r", d, bills, 0, net, 0, 0, 0, net, split, expenses, 0, 0, false, 1)

    @Test fun totals_are_sums_of_what_the_counter_computed() {
        val t = ReportMath.totals(listOf(
            day("2026-08-26", 10, 100000, """{"cash":60000,"upi":40000}""", 5000),
            day("2026-08-27", 0, 0, "{}"),
            day("2026-08-28", 5, 50000, """{"upi":50000}""", 1000),
        ))
        assertEquals(15, t.bills)
        assertEquals(150000L, t.netPaise)
        assertEquals(6000L, t.expensesPaise)
        assertEquals(10000L, t.averageBillPaise)
        assertEquals(2, t.daysWithSales)
        assertEquals(listOf("upi" to 90000L, "cash" to 60000L), t.byPayment)
        assertEquals(144000L, t.afterExpensesPaise)
    }

    @Test fun top_items_merge_days_and_keep_the_latest_name() {
        val rows = listOf(
            DayItemTotalRow("r", "2026-08-26", "i1", "Dosa", null, 2000, 24000, 1),
            DayItemTotalRow("r", "2026-08-27", "i1", "Masala Dosa", null, 3000, 36000, 2),
            DayItemTotalRow("r", "2026-08-27", "i2", "Tea", null, 10000, 15000, 2),
        )
        val top = ReportMath.topItems(rows)
        assertEquals("Masala Dosa", top[0].name)
        assertEquals(60000L, top[0].salesPaise)
        assertEquals(5000L, top[0].qtyThousandths)
        assertEquals("Tea", top[1].name)
    }

    @Test fun change_and_per_day() {
        assertEquals(20, ReportMath.change(120, 100))
        assertEquals(-25, ReportMath.change(75, 100))
        assertNull(ReportMath.change(75, 0))
        val range = Ist.Range(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 27), "3 days")
        val per = ReportMath.perDay(listOf(day("2026-08-26", 1, 500)), range)
        assertEquals(listOf(0L, 500L, 0L), per.map { it.second })
    }
}
