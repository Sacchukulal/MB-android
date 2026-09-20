package com.magicbill.app

import androidx.test.core.app.ApplicationProvider
import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.cloud.CloudSession
import com.magicbill.app.cloud.Mirror
import com.magicbill.app.cloud.SessionStore
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.dayOf
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.prefs.MemoryBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
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
        server.keep { s -> if (s.path.contains("/object/list/")) FakeServer.Reply(200, "[]") else null } // no day files yet
        val report = mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        assertTrue(report.toString(), report.ok)
        assertEquals(0, report.archived)
        val b = db.bills().byId(r, "b1")!!
        assertEquals("A/0001", b.billNumber)
        assertEquals(25200L, b.grandTotalPaise)
        assertEquals("""[{"mode":"cash","paise":25200}]""", b.payments)
        assertEquals(7, b.tokenNumber)
        assertEquals(1, db.bills().between(r, "2026-08-28", "2026-08-28").first().size)
    }

    // ---- The day files ----------------------------------------------------------------------

    /** 2026-08-27 as the counter writes a day: days since 1970-01-01. */
    private val day = 20692
    /** 2026-08-27T05:40:00Z as the counter writes an instant: ms. */
    private val createdMs = 20692L * 86_400_000 + 5 * 3_600_000 + 40 * 60_000
    private val sealedMs = createdMs + 3 * 86_400_000

    private fun billLine(id: String, n: Int, createdAt: Long = createdMs) =
        """{"table":"orders","id":"$id","updated_at":${createdAt + 60_000},"deleted":false,"data":{"terminal_id":"t1","bill_number":"A/000$n","token_number":$n,"business_day":$day,"created_at":$createdAt,"settled_at":${createdAt + 60_000},"order_type":"dine_in","placement":"table","table_name":"$n","customer_id":null,"customer_name":null,"staff_id":"s1","staff_name":"Ravi","status":"settled","subtotal_paise":24000,"discount_paise":0,"tax_paise":1200,"charges_paise":0,"round_off_paise":0,"grand_total_paise":25200,"payments":[{"mode":"cash","paise":25200}],"lines":[{"item_id":"i1","name":"Masala Dosa","qty_thousandths":2000}],"tax_rows":[],"void_reason":null,"source":"counter","restore":{"placement":"table"}}}"""

    private val header =
        """{"${'$'}kind":"day","business_day":$day,"schema":40,"app_version":"3.2.0","sealed_at":$sealedMs,"bills":3,"day_totals":{"business_day":$day,"bills":3,"voids":0,"gross_paise":75600,"discount_paise":0,"tax_paise":3600,"charges_paise":0,"net_paise":75600,"by_payment":{"cash":50400,"upi":25200},"expenses_paise":0,"credit_given_paise":0,"credit_collected_paise":0,"is_day_closed":true},"day_item_totals":[{"business_day":$day,"item_id":"i1","item_name":"Masala Dosa","category_id":"c1","qty_thousandths":6000,"sales_paise":72000}],"day_category_totals":[{"business_day":$day,"category_id":"c1","category_name":"Tiffin","qty_thousandths":6000,"sales_paise":72000}]}"""

    private val refundLine = """{"table":"refunds","id":"rf1","updated_at":$sealedMs,"deleted":false,"data":{"order_id":"b1","amount_paise":100}}"""
    private val corruptLine = """{"table":"orders","id":"b9","updated_at":1,"data":{"bill_number":"""

    private fun gz(lines: List<String>): ByteArray = java.io.ByteArrayOutputStream().also { out ->
        java.util.zip.GZIPOutputStream(out).bufferedWriter().use { w -> lines.forEach { w.write(it); w.write("\n") } }
    }.toByteArray()

    private val dayFile = gz(listOf(header, billLine("b1", 1), billLine("b2", 2), refundLine, corruptLine, billLine("b3", 3)))

    private fun listing(vararg files: Pair<String, String>) =
        files.joinToString(",", "[", "]") { (name, at) -> """{"name":"$name","id":"x","updated_at":"$at","created_at":"$at","metadata":{"size":123,"mimetype":"application/gzip"}}""" }

    /** The shop's Storage: year folders, then the files of 2026 with their `updated_at`, and the bytes of each. */
    private fun storage(files: Map<String, String>, bytes: Map<String, ByteArray>) {
        server.keep { s ->
            when {
                s.path.contains("rpc/mb_changes") -> page("[]", false, null)
                s.path == "/storage/v1/object/list/bill-archives" && s.body.contains("\"prefix\":\"$r/\"") ->
                    FakeServer.Reply(200, """[{"name":"2026","id":null,"updated_at":null,"created_at":null,"last_accessed_at":null,"metadata":null}]""")
                s.path == "/storage/v1/object/list/bill-archives" && s.body.contains("\"prefix\":\"$r/2026/\"") ->
                    FakeServer.Reply(200, listing(*files.entries.map { it.key to it.value }.toTypedArray()))
                s.path.startsWith("/storage/v1/object/authenticated/bill-archives/$r/2026/") ->
                    bytes[s.path.substringAfterLast('/')]?.let { FakeServer.Reply(200, bytes = it) } ?: FakeServer.Reply(404, """{"statusCode":"404","error":"not_found","message":"Object not found"}""")
                else -> null
            }
        }
    }

    private fun downloads() = server.sent.filter { it.path.contains("/object/authenticated/") }.map { it.path.substringAfterLast('/') }

    @Test fun a_day_file_lands_through_the_pull_and_is_not_fetched_twice() = runTest {
        storage(mapOf("2026-08-27.jsonl.gz" to "2026-08-30T00:00:00.000Z"), mapOf("2026-08-27.jsonl.gz" to dayFile))
        val report = mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        assertTrue(report.toString(), report.ok)
        assertEquals(1, report.archived)

        val b = db.bills().byId(r, "b1")!!
        assertEquals("2026-08-27", b.businessDay)
        assertEquals(createdMs, b.createdAtMs)
        assertEquals(createdMs + 60_000, b.settledAtMs)
        assertEquals(createdMs + 60_000, b.updatedMs)
        assertEquals("A/0001", b.billNumber)
        assertEquals(25200L, b.grandTotalPaise)
        assertEquals("""[{"mode":"cash","paise":25200}]""", b.payments)
        assertEquals(3, db.bills().between(r, "2026-08-27", "2026-08-27").first().size)

        val total = db.totals().days(r, "2026-08-27", "2026-08-27").first().single()
        assertEquals(3, total.bills)
        assertEquals(75600L, total.netPaise)
        assertEquals("""{"cash":50400,"upi":25200}""", total.byPayment)
        assertEquals(true, total.isDayClosed)
        assertEquals(sealedMs, total.updatedMs)
        assertEquals("Masala Dosa", db.totals().items(r, "2026-08-27", "2026-08-27").first().single().itemName)
        assertEquals("Tiffin", db.totals().categories(r, "2026-08-27", "2026-08-27").first().single().categoryName)

        val row = db.archive().get(r, "2026-08-27")!!
        assertEquals("2026-08-30T00:00:00.000Z", row.objectUpdatedAt)
        assertEquals(3, row.bills)
        assertEquals(now, row.importedAtMs)

        val storageCall = server.sent.first { it.path.startsWith("/storage/") }
        assertEquals("anon", storageCall.headers["apikey"])
        assertEquals("Bearer tok", storageCall.headers["Authorization"])
        assertEquals(listOf("2026-08-27.jsonl.gz"), downloads())

        // The same listing again: nothing to fetch.
        val again = mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        assertTrue(again.ok)
        assertEquals(0, again.archived)
        assertEquals(1, downloads().size)
    }

    @Test fun the_reader_counts_the_lines_it_does_not_keep() = runTest {
        storage(mapOf("2026-08-27.jsonl.gz" to "u1"), mapOf("2026-08-27.jsonl.gz" to dayFile))
        val imported = mirror.archive.importDay(r, com.magicbill.app.cloud.Archive.DayFile("2026-08-27", "$r/2026/2026-08-27.jsonl.gz", "u1"))
        assertEquals(Answer.Ok(com.magicbill.app.cloud.Archive.Imported(bills = 3, skipped = 2)), imported)
        assertEquals(3, db.bills().count(r))
    }

    @Test fun a_moved_updated_at_imports_the_day_again() = runTest {
        val files = mutableMapOf("2026-08-27.jsonl.gz" to "u1")
        val bytes = mutableMapOf("2026-08-27.jsonl.gz" to dayFile)
        storage(files, bytes)
        assertEquals(1, mirror.pull(r, setOf("reports.view"), only = setOf("bills")).archived)

        // The counter voided b1 after the day sealed: the file was re-uploaded, its updated_at moved.
        val voided = billLine("b1", 1).replace("\"status\":\"settled\"", "\"status\":\"voided\"")
        files["2026-08-27.jsonl.gz"] = "u2"
        bytes["2026-08-27.jsonl.gz"] = gz(listOf(header, voided, billLine("b2", 2), billLine("b3", 3)))

        assertEquals(1, mirror.pull(r, setOf("reports.view"), only = setOf("bills")).archived)
        assertEquals(2, downloads().size)
        assertEquals("voided", db.bills().byId(r, "b1")?.status)
        assertEquals("u2", db.archive().get(r, "2026-08-27")?.objectUpdatedAt)
    }

    @Test fun newest_first_and_a_bounded_batch_per_pull() = runTest {
        storage(
            mapOf("2026-08-25.jsonl.gz" to "u", "2026-08-27.jsonl.gz" to "u", "2026-08-26.jsonl.gz" to "u"),
            mapOf("2026-08-25.jsonl.gz" to dayFile, "2026-08-26.jsonl.gz" to dayFile, "2026-08-27.jsonl.gz" to dayFile),
        )
        assertEquals(Answer.Ok(2), mirror.archive.pull(r, limit = 2))
        assertEquals(listOf("2026-08-27.jsonl.gz", "2026-08-26.jsonl.gz"), downloads())
        assertEquals(Answer.Ok(1), mirror.archive.pull(r, limit = 2))
        assertEquals(listOf("2026-08-27", "2026-08-26", "2026-08-25"), db.archive().list(r).map { it.businessDay })
    }

    @Test fun a_file_that_cannot_be_read_is_skipped_and_leaves_nothing_behind() = runTest {
        storage(
            mapOf("2026-08-27.jsonl.gz" to "u", "2026-08-26.jsonl.gz" to "u"),
            mapOf("2026-08-27.jsonl.gz" to "not gzip at all".toByteArray(), "2026-08-26.jsonl.gz" to gz(listOf(header, billLine("b1", 1)))),
        )
        val report = mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        assertTrue(report.ok)
        assertEquals(1, report.archived)
        assertNull(db.archive().get(r, "2026-08-27"))
        assertEquals(1, db.archive().get(r, "2026-08-26")?.bills)
        // The broken day is asked for again next time.
        mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        assertEquals(listOf("2026-08-27.jsonl.gz", "2026-08-26.jsonl.gz", "2026-08-27.jsonl.gz"), downloads())
    }

    @Test fun the_cloud_going_away_mid_archive_is_trouble() = runTest {
        server.fail("/object/authenticated/")
        storage(mapOf("2026-08-27.jsonl.gz" to "u"), emptyMap())
        val report = mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        assertTrue(report.trouble is Answer.Unreachable)
    }

    /** The file the counter's own test writes (MB-pos, WP2) — one source of truth for the format. Skipped until it is checked in. */
    @Test fun the_counters_own_day_file_reads() = runTest {
        val bytes = javaClass.getResourceAsStream("/day-file.jsonl.gz")?.use { it.readBytes() }
        org.junit.Assume.assumeTrue("app/src/test/resources/day-file.jsonl.gz is not there yet", bytes != null)
        // The file names its own day in the header (an integer, the counter's way); the key is derived from it.
        val head = java.util.zip.GZIPInputStream(bytes!!.inputStream()).bufferedReader().readLine()
        val theDay = com.magicbill.app.core.parseJsonOrNull(head)!!.jsonObject.dayOf("business_day")
        assertTrue(theDay, Regex("\\d{4}-\\d{2}-\\d{2}").matches(theDay))
        val name = "$theDay.jsonl.gz"
        storage(mapOf(name to "u"), mapOf(name to bytes))

        val report = mirror.pull(r, setOf("reports.view"), only = setOf("bills"))
        assertTrue(report.toString(), report.ok)
        assertEquals(1, report.archived)
        val row = db.archive().get(r, theDay)!!
        assertTrue("a day file carries at least one bill", row.bills > 0)
        assertEquals(row.bills, db.bills().count(r))
        val bills = db.bills().between(r, theDay, theDay).first()
        assertEquals(row.bills, bills.size)
        assertTrue("every bill has its moment from the counter's integer ms", bills.all { it.createdAtMs > 0 && it.updatedMs > 0 && it.billNumber.isNotBlank() })
        assertEquals(1, db.totals().days(r, theDay, theDay).first().size)
        assertTrue(db.totals().items(r, theDay, theDay).first().isNotEmpty())
        assertTrue(db.totals().categories(r, theDay, theDay).first().isNotEmpty())
    }
}
