package com.magicbill.app

import androidx.test.core.app.ApplicationProvider
import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.cloud.CloudSession
import com.magicbill.app.cloud.Mirror
import com.magicbill.app.cloud.SessionStore
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.prefs.MemoryBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class MirrorTest {
    private lateinit var db: MbDatabase
    private val server = FakeServer()
    private val sessions = SessionStore(MemoryBox())
    private val now = 1_800_000_000_000L
    private lateinit var mirror: Mirror
    private val r = "11111111-1111-1111-1111-111111111111"

    @Before fun open() {
        db = MbDatabase.inMemory(ApplicationProvider.getApplicationContext())
        sessions.save(CloudSession(CloudSession.Kind.OWNER, "tok", "ref", now + 3_600_000, "o@x.in"))
        mirror = Mirror(CloudLink("https://cloud.test", "anon", server.client(), sessions, Clock { now }), db, Clock { now })
    }

    @After fun close() = db.close()

    private fun page(rows: String, more: Boolean, cursor: String?) =
        FakeServer.Reply(200, """{"rows":$rows,"more":$more,"next_cursor":${cursor ?: "null"}}""")

    private fun bodyOf(i: Int) = server.sent[i].body

    @Test fun pages_until_more_is_false_and_keeps_the_cursor() = runTest {
        server.once("POST", "rpc/mb_changes", page("""[{"business_day":"2026-08-27","bills":10,"voids":0,"gross_paise":100000,"discount_paise":0,"tax_paise":5000,"charges_paise":0,"net_paise":105000,"by_payment":{"cash":80000,"upi":25000},"expenses_paise":2000,"credit_given_paise":0,"credit_collected_paise":0,"is_day_closed":true,"updated_at":"2026-08-27T18:00:00+00:00","updated_ms":1}]""", true, """{"t":1000,"id":"2026-08-27"}"""))
        server.once("POST", "rpc/mb_changes", page("""[{"business_day":"2026-08-28","bills":3,"voids":1,"gross_paise":30000,"discount_paise":0,"tax_paise":1500,"charges_paise":0,"net_paise":31500,"by_payment":{},"expenses_paise":0,"credit_given_paise":0,"credit_collected_paise":0,"is_day_closed":false,"updated_ms":2}]""", false, """{"t":2000,"id":"2026-08-28"}"""))

        val report = mirror.pull(r, setOf("reports.view"), only = setOf("day_totals"), pageSize = 1)
        assertTrue(report.toString(), report.ok)
        assertEquals(2, report.rows)
        assertTrue("the first ask has no cursor", bodyOf(0).contains("\"cursor\":null"))
        assertTrue("the second ask carries the first page's cursor", bodyOf(1).contains("\"cursor\":{\"t\":1000,\"id\":\"2026-08-27\"}"))
        val days = db.totals().days(r, "2026-08-01", "2026-08-31").first()
        assertEquals(2, days.size)
        assertEquals(105000L, days[0].netPaise)
        assertEquals("""{"cash":80000,"upi":25000}""", days[0].byPayment)
        assertEquals("""{"t":2000,"id":"2026-08-28"}""", db.cursors().get(r, "day_totals")?.cursor)

        // The next pull starts where this one stopped.
        server.once("POST", "rpc/mb_changes", page("[]", false, null))
        mirror.pull(r, setOf("reports.view"), only = setOf("day_totals"))
        assertTrue(bodyOf(2).contains("\"cursor\":{\"t\":2000,\"id\":\"2026-08-28\"}"))
    }

    @Test fun a_table_the_role_does_not_open_is_skipped_and_the_rest_still_comes() = runTest {
        server.keep { s ->
            when {
                s.body.contains("\"tbl\":\"staff\"") -> FakeServer.Reply(403, """{"code":"42501","message":"your role does not allow staff"}""")
                s.body.contains("\"tbl\":\"menu_items\"") -> page("""[{"id":"i1","name":"Idli","unit_price_paise":4000,"tax_rate_bp":500,"is_available":true,"sort_order":1,"updated_ms":5}]""", false, """{"t":5,"id":"i1"}""")
                else -> page("[]", false, null)
            }
        }
        val report = mirror.pull(r, setOf("staff.manage"), only = setOf("staff", "menu_items", "bills", "customers"))
        assertTrue(report.ok)
        assertEquals(listOf("bills", "customers", "staff"), report.skipped.sorted())
        assertEquals(1, report.pulled["menu_items"])
        assertEquals("Idli", db.menu().items(r).first().single().name)
    }

    @Test fun a_tombstone_is_kept_and_hidden() = runTest {
        server.once("POST", "rpc/mb_changes", page("""[{"id":"c1","name":"Ravi","balance_paise":500,"is_active":true,"updated_ms":1},{"id":"c2","name":"Gone","balance_paise":0,"is_active":true,"deleted_at":"2026-08-27T00:00:00+00:00","updated_ms":2}]""", false, """{"t":2,"id":"c2"}"""))
        mirror.pull(r, setOf("credit.collect"), only = setOf("customers"))
        val shown = db.khata().customers(r).first()
        assertEquals(listOf("Ravi"), shown.map { it.name })
        assertEquals(true, db.khata().customer(r, "c2")?.deleted)
    }

    @Test fun unreachable_stops_the_pull_and_says_so_once() = runTest {
        server.fail()
        val report = mirror.pull(r, setOf("reports.view"), only = setOf("day_totals", "bills"))
        assertTrue(report.trouble is Answer.Unreachable)
        assertEquals(1, server.sent.size)
        assertNull(db.cursors().get(r, "day_totals"))
    }

    @Test fun bills_keep_their_json_and_their_day() = runTest {
        server.once("POST", "rpc/mb_changes", page("""[{"id":"b1","terminal_id":"t","bill_number":"A/0001","token_number":7,"business_day":"2026-08-28","created_at":"2026-08-28T05:30:00+00:00","settled_at":"2026-08-28T05:31:00+00:00","order_type":"dine_in","placement":"table","table_name":"7","status":"settled","subtotal_paise":24000,"discount_paise":0,"tax_paise":1200,"charges_paise":0,"round_off_paise":0,"grand_total_paise":25200,"payments":[{"mode":"cash","paise":25200}],"lines":[{"name":"Masala Dosa","qty":2}],"tax_rows":[],"source":"counter","updated_ms":9}]""", false, """{"t":9,"id":"b1"}"""))
        mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        val b = db.bills().byId(r, "b1")!!
        assertEquals("A/0001", b.billNumber)
        assertEquals(25200L, b.grandTotalPaise)
        assertEquals("""[{"mode":"cash","paise":25200}]""", b.payments)
        assertEquals(7, b.tokenNumber)
        assertEquals(1, db.bills().between(r, "2026-08-28", "2026-08-28").first().size)
    }
}
