package com.magicbill.app.counter

import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.MbJson
import com.magicbill.app.core.Sentences
import com.magicbill.app.core.arr
import com.magicbill.app.core.bool
import com.magicbill.app.core.newId
import com.magicbill.app.core.objects
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.core.str
import com.magicbill.app.core.strOrNull
import com.magicbill.app.db.FloorItemRow
import com.magicbill.app.db.FloorOrderRow
import com.magicbill.app.db.FloorTableRow
import com.magicbill.app.db.IntentRow
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.di.AppScope
import com.magicbill.app.prefs.KeyBox
import com.magicbill.app.prefs.Secure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The floor, from the phone: the counter's catalogue (cached), EVERY open order as the counter
 * last described it, and the ONE way anything reaches the counter — an intent, written to the
 * database before its first send, sent, and answered with an outcome that is final.
 *
 * Sending is never waited for on a screen. An order is staged in one write, the screen goes
 * back to the floor at once, and the tile shows the order as "sending" until the counter's
 * answer replaces it — a fraction of a second on the shop's WiFi, and the waiter has already
 * moved on. What the counter said arrives on [sentences], once, wherever the phone is.
 */
@Singleton
class Floor @Inject constructor(
    private val link: CounterLink,
    private val counter: Counter,
    private val db: MbDatabase,
    private val secure: KeyBox,
    private val clock: Clock,
    @AppScope private val scope: CoroutineScope,
) {
    val items: Flow<List<FloorItemRow>> get() = db.floor().items()
    val tables: Flow<List<FloorTableRow>> get() = db.floor().tables()
    val openOrders: Flow<List<FloorOrderRow>> get() = db.floor().openOrders()
    val queuedCount: Flow<Int> get() = db.intents().queuedCount()
    val held: Flow<List<IntentRow>> get() = db.intents().held()
    fun recent(limit: Int = 50): Flow<List<IntentRow>> = db.intents().recent(limit)
    fun order(orderId: String): Flow<FloorOrderRow?> = db.floor().orderFlow(orderId)

    /** The counter's sentences, said once each, in the order they came. The shell shows them. */
    private val said = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val sentences: SharedFlow<String> get() = said

    private val sending = Mutex()

    /** `GET /v1/catalogue?version=…`; 304 keeps what we have. */
    suspend fun refreshCatalogue(force: Boolean = false): Answer<Boolean> {
        val c = counter.credential.value ?: return Answer.SignedOut(Sentences.NOT_PAIRED)
        val held = if (force) null else secure.get(Secure.CATALOGUE_VERSION)
        return when (val a = link.catalogue(c, held)) {
            is Answer.Ok -> {
                val cat = a.value
                if (cat != null) {
                    db.floor().replaceCatalogue(
                        cat.items.mapIndexed { i, it -> FloorItemRow(it.id, it.name, it.category, it.price, it.isAvailable, i) },
                        cat.tables.mapIndexed { i, it -> FloorTableRow(it.id, it.label, it.section, it.seats, it.state, i) },
                    )
                    secure.put(Secure.CATALOGUE_VERSION, cat.version)
                }
                Answer.Ok(cat != null)
            }
            is Answer.Refused -> a
            is Answer.Unreachable -> a
            is Answer.SignedOut -> { counter.refreshMe(); a }
        }
    }

    /** Where an order sits, for the label on the queue. */
    data class Place(val tableId: String?, val tableLabel: String?, val orderType: String)

    /**
     * One request about an existing order, answered. The row is durable before the first send;
     * a lost reply is retried by [flush] with the SAME id; the outcome, whatever it is, is final.
     */
    suspend fun submit(what: JsonObject, orderId: String?, label: String, place: Place?): Answer<Outcome> {
        val now = clock.now()
        val row = IntentRow(newId(), orderId, now, what.toString(), label, place?.tableLabel, "queued", null, now, null, 0)
        db.intents().put(row)
        return send(row, place)
    }

    private suspend fun send(row: IntentRow, place: Place?): Answer<Outcome> = sending.withLock {
        val c = counter.credential.value ?: return@withLock Answer.SignedOut(Sentences.NOT_PAIRED)
        val what = parseJsonOrNull(row.what) as? JsonObject ?: return@withLock Answer.Refused("This request is not readable any more.")
        when (val a = link.intent(c, Intent(row.id, row.orderId, row.atMs, what))) {
            is Answer.Ok -> { record(row, a.value, place); a }
            is Answer.Unreachable -> { db.intents().put(row.copy(attempts = row.attempts + 1)); a }
            is Answer.Refused -> {
                if (a.code == "too_many") {
                    // The counter said wait — the batch takes it again in a moment.
                    db.intents().put(row.copy(attempts = row.attempts + 1))
                } else {
                    // A permission or version refusal is FINAL (LAN_PROTOCOL §13): a retry gets
                    // the same sentence, so the queue must not hold it and ask again for ever.
                    record(row, Outcome.Refused(a.sentence), place)
                }
                a
            }
            is Answer.SignedOut -> { counter.refreshMe(); a }
        }
    }

    /** Everything still queued, in order, as one batch. Idempotent across the whole batch. */
    suspend fun flush(): Answer<BatchResult?> = sending.withLock {
        val c = counter.credential.value ?: return@withLock Answer.SignedOut(Sentences.NOT_PAIRED)
        val queued = db.intents().queued()
        if (queued.isEmpty()) return@withLock Answer.Ok(null)
        val intents = queued.mapNotNull { row -> (parseJsonOrNull(row.what) as? JsonObject)?.let { Intent(row.id, row.orderId, row.atMs, it) } }
        when (val a = link.batch(c, intents)) {
            is Answer.Ok -> {
                val byId = queued.associateBy { it.id }
                for ((id, outcome) in a.value.outcomes) byId[id]?.let { record(it, outcome, null) }
                // A staged order whose batch has now been answered is no longer "sending".
                db.floor().dropPendingAnswered()
                if (a.value.says.isNotBlank()) said.tryEmit(a.value.says)
                a
            }
            is Answer.Unreachable -> a
            is Answer.Refused -> a
            is Answer.SignedOut -> { counter.refreshMe(); a }
        }
    }

    /** One dish of a staged order. */
    data class StagedLine(val itemId: String, val name: String, val qty: String, val note: String?)

    /**
     * A WHOLE order in one go — the way a waiter works: build it on the phone, press once.
     * Open (when there is no order yet), every dish, then send to the kitchen, staged as
     * durable intents in ONE write, shown on the floor at once as a sending order, and sent as
     * ONE batch in the background; the counter carries the new order's id through the batch.
     * The screen does not wait: this returns the moment the rows are on disk.
     */
    suspend fun stageOrder(orderId: String?, place: Place, lines: List<StagedLine>, note: String?, estimate: String): Answer<Unit> {
        if (lines.isEmpty()) return Answer.Refused("Nothing on this order yet. Add a dish first.")
        val now = clock.now()
        val whereLabel = place.tableLabel?.let { "table $it" } ?: place.orderType.replace('_', ' ')
        val batchId = newId()
        val rows = buildList {
            if (orderId == null) {
                add(IntentRow(batchId, null, now, Ops.openOrder(place.orderType, place.tableId, null).toString(), "Open $whereLabel", place.tableLabel, "queued", null, now, null, 0))
            }
            lines.forEach { l ->
                add(IntentRow(newId(), orderId, now, Ops.addItem(l.itemId, l.qty, l.note).toString(), "${l.qty} × ${l.name}", place.tableLabel, "queued", null, now, null, 0))
            }
            if (!note.isNullOrBlank()) add(IntentRow(newId(), orderId, now, Ops.setOrderNote(note).toString(), "Order note", place.tableLabel, "queued", null, now, null, 0))
            add(IntentRow(newId(), orderId, now, Ops.sendToKitchen().toString(), "Send $whereLabel to the kitchen", place.tableLabel, "queued", null, now, null, 0))
        }
        // The floor shows it NOW: a new order as a sending tile, an addition as sending lines
        // on the order it belongs to. The counter's answer replaces both.
        val pendingLines = lines.mapIndexed { i, l -> LineView(i, l.name, l.qty, "", l.note, false) }
        val pending = if (orderId == null) {
            FloorOrderRow(
                orderId = PENDING_PREFIX + batchId, tableId = place.tableId, tableLabel = place.tableLabel, orderType = place.orderType,
                total = estimate, token = null, lines = linesJson(pendingLines), note = note, by = counter.me.value?.name, byId = counter.me.value?.staffId,
                mine = true, billAsked = false, sending = true, closedSays = null, updatedMs = now,
            )
        } else {
            db.floor().order(orderId)?.let { existing ->
                val have = parseLines(existing.lines)
                existing.copy(lines = linesJson(have + pendingLines.map { it.copy(line = have.size + it.line) }), sending = true, updatedMs = now)
            }
        }
        db.floor().stage(rows, pending)
        scope.launch { flushOrQueue() }
        return Answer.Ok(Unit)
    }

    /** The batch, in the background; a counter that cannot be reached keeps the rows queued. */
    private suspend fun flushOrQueue() {
        when (flush()) {
            is Answer.Unreachable -> said.tryEmit("Could not reach the counter — the order is queued and goes the moment it is back.")
            else -> {}
        }
    }

    /** A held intent is released as a NEW decision: a new id, a fresh `at`, the same request. */
    suspend fun release(heldId: String): Answer<Outcome> {
        val old = db.intents().byId(heldId) ?: return Answer.Refused("That request is not here any more.")
        val what = parseJsonOrNull(old.what) as? JsonObject ?: return Answer.Refused("This request is not readable any more.")
        db.intents().put(old.copy(state = "released"))
        return submit(what, old.orderId, old.label, Place(null, old.tableLabel, ""))
    }

    private suspend fun record(row: IntentRow, outcome: Outcome, place: Place?) {
        val state = when (outcome) { is Outcome.Ok -> "ok"; is Outcome.Refused -> "refused"; is Outcome.Held -> "held" }
        db.intents().put(row.copy(state = state, outcome = Outcome.toJson(outcome), answeredMs = clock.now()))
        if (outcome is Outcome.Ok) {
            val existing = db.floor().order(outcome.orderId)
            val what = parseJsonOrNull(row.what) as? JsonObject
            val closes = what?.str("do") == "cancel_order"
            val asksBill = what?.str("do") == "request_bill"
            db.floor().putOrder(
                FloorOrderRow(
                    orderId = outcome.orderId,
                    tableId = place?.tableId ?: existing?.tableId ?: what?.takeIf { it.str("do") == "move_table" }?.strOrNull("table_id"),
                    tableLabel = place?.tableLabel ?: existing?.tableLabel ?: row.tableLabel,
                    orderType = place?.orderType?.takeIf { it.isNotEmpty() } ?: existing?.orderType ?: what?.strOrNull("order_type") ?: "",
                    total = outcome.total,
                    token = outcome.token ?: existing?.token,
                    lines = linesJson(outcome.lines),
                    // outcome.note is the counter's TRANSIENT sentence ("1 item sent to the
                    // kitchen.") — said once, never stored. The order's own note arrives on
                    // the floor push, which carries the core note.
                    note = existing?.note,
                    by = existing?.by ?: counter.me.value?.name,
                    byId = existing?.byId ?: counter.me.value?.staffId,
                    mine = existing?.mine ?: true,
                    billAsked = asksBill || (existing?.billAsked ?: false),
                    sending = false,
                    closedSays = if (closes) "This order was cancelled." else null,
                    updatedMs = clock.now(),
                ),
            )
        } else if (outcome is Outcome.Refused && row.orderId != null) {
            // "That bill has already been paid…" — the order is finished with, and the phone is told.
            val finished = listOf("already been paid", "already settled", "was voided", "was cancelled", "Start a new order")
            if (finished.any { outcome.message.contains(it, ignoreCase = true) }) {
                db.floor().order(row.orderId)?.let { db.floor().putOrder(it.copy(closedSays = outcome.message, sending = false, updatedMs = clock.now())) }
            }
        }
    }

    /** What the stream says (LAN_PROTOCOL.md §4). Unknown kinds are ignored — a counter one version ahead is an ordinary Tuesday. */
    suspend fun apply(push: Push) {
        val body = push.body as? JsonObject ?: return
        when (push.kind) {
            "catalogue" -> if (body.strOrNull("version") != secure.get(Secure.CATALOGUE_VERSION)) refreshCatalogue(force = false)
            "floor" -> takeFloor(body)
        }
    }

    /**
     * The floor as the counter describes it: every table's state, every open order — any
     * waiter's. What is here is the floor; an order this phone holds that is not here is one
     * the counter has finished with. A staged order still on its way is left alone.
     */
    suspend fun takeFloor(body: JsonObject) {
        val now = clock.now()
        val myStaff = counter.me.value?.staffId
        for (t in body.arr("tables").objects()) {
            val id = t.str("id")
            if (id.isNotEmpty()) db.floor().setTableState(id, t.str("state"))
        }
        val open = body.arr("orders").objects().associateBy { it.str("order_id") }
        val known = db.floor().openOrders().first().associateBy { it.orderId }
        // Gone from the floor: finished with.
        for (row in known.values) {
            if (row.orderId.startsWith(PENDING_PREFIX)) continue
            if (open[row.orderId] == null) {
                db.floor().putOrder(row.copy(closedSays = "The counter has finished with this order.", sending = false, updatedMs = now))
            }
        }
        // On the floor: brought up to date, or adopted.
        for ((id, o) in open) {
            val row = known[id]
            val byId = o.strOrNull("by_id")
            db.floor().putOrder(
                FloorOrderRow(
                    orderId = id,
                    tableId = o.strOrNull("table_id") ?: row?.tableId,
                    tableLabel = o.strOrNull("table_label") ?: row?.tableLabel,
                    orderType = o.strOrNull("order_type")?.takeIf { it.isNotBlank() } ?: row?.orderType ?: "",
                    total = o.strOrNull("total") ?: row?.total ?: "0.00",
                    token = o.strOrNull("token") ?: row?.token,
                    lines = (o["lines"] as? JsonArray)?.toString() ?: row?.lines ?: "[]",
                    // The push carries the order's real note (or none) — it REPLACES, so a
                    // sentence that was wrongly stored as the note heals on the next push.
                    note = o.strOrNull("note"),
                    by = o.strOrNull("by") ?: row?.by,
                    byId = byId ?: row?.byId,
                    mine = (byId != null && byId == myStaff) || (row?.mine ?: false),
                    billAsked = o.bool("bill_asked"),
                    // A push while an addition is on its way keeps the tile quiet until the
                    // batch answers; the answer clears it.
                    sending = row?.sending ?: false,
                    closedSays = null,
                    updatedMs = now,
                ),
            )
        }
    }

    /** After `too_far_behind`: one decision, one snapshot. */
    suspend fun catchUp() {
        refreshCatalogue(force = false)
        val c = counter.credential.value ?: return
        when (val a = link.floor(c)) {
            is Answer.Ok -> takeFloor(a.value)
            else -> {}
        }
    }

    /** Pull to refresh: the snapshot, now. */
    suspend fun refreshFloor(): Answer<Unit> {
        val c = counter.credential.value ?: return Answer.SignedOut(Sentences.NOT_PAIRED)
        return when (val a = link.floor(c)) {
            is Answer.Ok -> { takeFloor(a.value); Answer.Ok(Unit) }
            is Answer.Refused -> a
            is Answer.Unreachable -> a
            is Answer.SignedOut -> { counter.refreshMe(); a }
        }
    }

    suspend fun housekeeping() {
        val weekAgo = clock.now() - 7 * 24 * 3_600_000L
        db.intents().prune(weekAgo)
        db.floor().pruneClosed(clock.now() - 24 * 3_600_000L)
    }

    /** Everything about this counter, gone — with the credential. */
    suspend fun forgetAll() {
        db.floor().replaceCatalogue(emptyList(), emptyList())
        db.floor().clearOrders()
        counter.forget()
    }

    companion object {
        /** A staged order's id until the counter names it. */
        const val PENDING_PREFIX = "pending_"

        fun linesJson(lines: List<LineView>): String = JsonArray(lines.map { l ->
            buildJsonObject {
                put("line", l.line); put("name", l.name); put("qty", l.qty); put("amount", l.amount)
                put("note", l.note?.let { JsonPrimitive(it) } ?: JsonNull); put("sent_to_kitchen", l.sentToKitchen)
            }
        }).toString()

        fun parseLines(text: String): List<LineView> = try {
            (MbJson.parseToJsonElement(text)).objects().map(LineView::parse)
        } catch (e: Exception) { emptyList() }
    }
}
