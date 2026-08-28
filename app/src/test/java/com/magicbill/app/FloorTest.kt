package com.magicbill.app

import androidx.test.core.app.ApplicationProvider
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.CounterLink
import com.magicbill.app.counter.Credential
import com.magicbill.app.counter.Discovery
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.Ops
import com.magicbill.app.counter.Outcome
import com.magicbill.app.db.FloorOrderRow
import com.magicbill.app.db.FloorTableRow
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.prefs.MemoryBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class FloorTest {
    private lateinit var db: MbDatabase
    private val server = FakeServer()
    private val box = MemoryBox()
    private val now = 1_800_000_000_000L
    private lateinit var floor: Floor
    private val cred = Credential("192.168.1.7", 7431, "0".repeat(64), "srv_1", "Anna's", "dev_1", "s")

    @Before fun open() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = MbDatabase.inMemory(context)
        box.put(com.magicbill.app.prefs.Secure.COUNTER_CREDENTIAL, com.magicbill.app.core.MbJson.encodeToString(Credential.serializer(), cred))
        val link = CounterLink(clientFactory = { server.client() })
        val counter = Counter(link, box, Discovery(context), Clock { now })
        counter.load()
        floor = Floor(link, counter, db, box, Clock { now })
        db.floor().replaceCatalogue(emptyList(), listOf(FloorTableRow("t1", "1", "Hall", 4, "free", 0), FloorTableRow("t2", "2", "Hall", 4, "free", 1)))
    }

    @Test fun an_intent_is_durable_before_it_is_sent_and_its_outcome_is_final() = runTest {
        server.fail("/v1/intent")
        val first = floor.submit(Ops.openOrder("dine_in", "t1", null), null, "Open table 1", Floor.Place("t1", "1", "dine_in"))
        assertTrue(first is Answer.Unreachable)
        val queued = db.intents().queued()
        assertEquals(1, queued.size)
        assertEquals(1, queued[0].attempts)

        // The counter comes back: the SAME id goes in the batch, and the outcome lands on the order.
        server.once("POST", "/v1/batch", FakeServer.Reply(200, """{"outcomes":[["${queued[0].id}",{"outcome":"ok","order_id":"ord_1","total":"0.00","lines":[],"token":"4","note":null}]],"says":"1 of 1 went through."}"""))
        val flushed = floor.flush() as Answer.Ok
        assertEquals("1 of 1 went through.", flushed.value?.says)
        assertTrue(server.sent.last().body.contains(queued[0].id))
        assertEquals("ok", db.intents().byId(queued[0].id)?.state)
        val order = db.floor().order("ord_1")
        assertNotNull(order)
        assertEquals("1", order!!.tableLabel)
        assertEquals("4", order.token)
        assertTrue(db.intents().queued().isEmpty())
    }

    @Test fun a_refusal_that_says_the_bill_is_paid_closes_the_order_on_the_phone() = runTest {
        db.floor().putOrder(FloorOrderRow("ord_9", "t2", "2", "dine_in", "120.00", "9", "[]", null, null, now))
        server.once("POST", "/v1/intent", FakeServer.Reply(409, """{"outcome":"refused","message":"That bill has already been paid at the counter. Start a new order for anything else."}"""))
        val a = floor.submit(Ops.addItem("i1", "1", null), "ord_9", "1 × Idli", null) as Answer.Ok
        assertTrue(a.value is Outcome.Refused)
        assertNotNull(db.floor().order("ord_9")?.closedSays)
    }

    @Test fun the_floor_push_updates_tables_and_my_orders_and_closes_what_is_gone() = runTest {
        db.floor().putOrder(FloorOrderRow("ord_1", "t1", "1", "dine_in", "0.00", "4", "[]", null, null, now))
        db.floor().putOrder(FloorOrderRow("ord_2", "t2", "2", "dine_in", "50.00", "5", "[]", null, null, now))
        val body = com.magicbill.app.core.parseJsonOrNull("""{"tables":[{"id":"t1","state":"taken","order_id":"ord_1"},{"id":"t2","state":"free","order_id":null}],"orders":[{"order_id":"ord_1","table_id":"t1","table_label":"1","order_type":"dine_in","total":"240.00","token":"4","note":null,"lines":[{"line":0,"name":"Masala Dosa","qty":"2","amount":"240.00","note":null,"sent_to_kitchen":true}]},{"order_id":"ord_77","table_id":null,"table_label":null,"order_type":"parcel","total":"10.00","token":"7","note":null,"lines":[]}]}""") as JsonObject
        floor.takeFloor(body)
        val tables = db.floor().tables().first()
        assertEquals("taken", tables.first { it.id == "t1" }.state)
        assertEquals("free", tables.first { it.id == "t2" }.state)
        val mine = db.floor().order("ord_1")!!
        assertEquals("240.00", mine.total)
        assertEquals("Masala Dosa", Floor.parseLines(mine.lines).single().name)
        assertNull(mine.closedSays)
        assertNotNull("settled at the counter → finished with here", db.floor().order("ord_2")!!.closedSays)
        assertNull("an order this phone never touched is not adopted", db.floor().order("ord_77"))
    }

    @Test fun a_permission_refusal_is_final_but_too_many_keeps_the_queue() = runTest {
        // "You do not have permission…" is a pure function of who asked — retrying gets the
        // same sentence, so it must not sit in the queue asking for ever.
        server.once("POST", "/v1/intent", FakeServer.Reply(403, """{"message":"You do not have permission to cancel the order. Ask somebody who can."}"""))
        val refused = floor.submit(Ops.cancelOrder("wrong"), "ord_1", "Cancel the order", null)
        assertTrue(refused is Answer.Refused)
        assertTrue("a final refusal must not stay queued", db.intents().queued().isEmpty())
        assertEquals("refused", db.intents().recent(10).first().first().state)

        // 429 is the counter asking for a moment — that one IS retried by the batch.
        server.once("POST", "/v1/intent", FakeServer.Reply(429, """{"message":"The counter is busy. Try again in 3 seconds."}""", mapOf("Retry-After" to "3")))
        floor.submit(Ops.requestBill(), "ord_1", "Ask for the bill", null)
        assertEquals(1, db.intents().queued().size)
    }

    @Test fun a_held_intent_is_released_as_a_new_decision() = runTest {
        server.once("POST", "/v1/intent", FakeServer.Reply(202, """{"outcome":"held","message":"This was typed more than 12 hours ago.","batch_id":"b1"}"""))
        floor.submit(Ops.requestBill(), "ord_1", "Ask for the bill", null)
        val held = db.intents().held().first().single()
        server.once("POST", "/v1/intent", FakeServer.Reply(200, """{"outcome":"ok","order_id":"ord_1","total":"1.00","lines":[],"token":null,"note":"Sent."}"""))
        val a = floor.release(held.id) as Answer.Ok
        assertTrue(a.value is Outcome.Ok)
        val sent = server.sent.last().body
        assertTrue("a new id, not the held one", !sent.contains(held.id))
        assertEquals("released", db.intents().byId(held.id)?.state)
    }
}
