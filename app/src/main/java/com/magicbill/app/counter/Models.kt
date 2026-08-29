package com.magicbill.app.counter

import com.magicbill.app.core.MbJson
import com.magicbill.app.core.bool
import com.magicbill.app.core.int
import com.magicbill.app.core.long
import com.magicbill.app.core.objects
import com.magicbill.app.core.str
import com.magicbill.app.core.strOrNull
import com.magicbill.app.core.strings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Where a counter is, and the certificate it must present. Kept in the secure box. */
@Serializable
data class Credential(
    val host: String,
    val port: Int,
    /** Lowercase hex SHA-256 of the counter's certificate. */
    val fingerprint: String,
    val serverId: String,
    val shopName: String,
    val deviceId: String,
    val secret: String,
) {
    val bearer: String get() = "$deviceId.$secret"
    fun url(path: String) = "https://$host:$port$path"
    fun ws(path: String) = "wss://$host:$port$path"
}

/** What the QR says: `magicbill://pair?h=<host>&p=<port>&f=<fingerprint>&t=<token>`. */
data class PairCode(val host: String, val port: Int, val fingerprint: String, val token: String) {
    companion object {
        fun parse(text: String): PairCode? {
            val t = text.trim()
            if (!t.startsWith("magicbill://pair?")) return null
            val q = t.substringAfter('?').split('&').mapNotNull { kv ->
                val k = kv.substringBefore('=')
                val v = kv.substringAfter('=', "")
                if (k.isEmpty()) null else k to java.net.URLDecoder.decode(v, "UTF-8")
            }.toMap()
            val host = q["h"] ?: return null
            val port = q["p"]?.toIntOrNull() ?: return null
            val fp = Fingerprints.normalise(q["f"]) ?: return null
            val token = q["t"] ?: return null
            return PairCode(host, port, fp, token)
        }
    }
}

@Serializable
data class Hello(
    @SerialName("server_id") val serverId: String,
    @SerialName("protocol_version") val protocolVersion: Int = 1,
    @SerialName("shop_name") val shopName: String = "",
    val fingerprint: String = "",
)

@Serializable
data class PairedDevice(
    @SerialName("device_id") val deviceId: String,
    val secret: String,
    @SerialName("server_id") val serverId: String,
)

/** Somebody on the staff list a phone can belong to (LAN_PROTOCOL.md §3). */
data class Person(val id: String, val name: String)

/** The counter's answer to a presented code: the request to claim, and who it could be for. */
data class Asked(val requestId: String, val people: List<Person>)

/** `GET /v1/me`: who this phone is at the counter, and what its person may do there. */
data class Me(val deviceId: String, val name: String, val staffId: String?, val may: Set<String>) {
    companion object {
        fun parse(o: JsonObject) = Me(o.str("device_id"), o.str("name"), o.strOrNull("staff_id"), o.strings("may").toSet())
    }
}

data class Catalogue(val version: String, val items: List<CatalogueItem>, val tables: List<CatalogueTable>) {
    companion object {
        fun parse(o: JsonObject) = Catalogue(
            version = o.str("version"),
            items = (o["items"] ?: JsonNull).objects().map { CatalogueItem(it.str("id"), it.str("name"), it.str("category"), it.str("price"), it.bool("is_available")) },
            tables = (o["tables"] ?: JsonNull).objects().map { CatalogueTable(it.str("id"), it.str("label"), it.str("section"), it.int("seats"), it.str("state")) },
        )
    }
}

data class CatalogueItem(val id: String, val name: String, val category: String, val price: String, val isAvailable: Boolean)
data class CatalogueTable(val id: String, val label: String, val section: String, val seats: Int, val state: String)

/**
 * An intent: the phone asks; the counter decides. There is nowhere in here to put money.
 * [at] is when the person pressed; [sentAt] is stamped by the link the moment it goes, on
 * the same clock — the counter reads the age as the gap between them (§5).
 */
data class Intent(val id: String, val orderId: String?, val at: Long, val what: JsonObject, val sentAt: Long? = null) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("order_id", orderId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("at", at)
        put("sent_at", sentAt?.let { JsonPrimitive(it) } ?: JsonNull)
        put("what", what)
    }

    val doName: String get() = what.str("do")
}

/** The operations, spelled exactly as LAN_PROTOCOL.md §5 spells them. */
object Ops {
    fun openOrder(orderType: String, tableId: String?, covers: Int?) = buildJsonObject {
        put("do", "open_order"); put("order_type", orderType)
        put("table_id", tableId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("covers", covers?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    fun addItem(itemId: String, qty: String, note: String?) = buildJsonObject {
        put("do", "add_item"); put("item_id", itemId); put("qty", qty)
        put("note", note?.let { JsonPrimitive(it) } ?: JsonNull); put("modifiers", JsonArray(emptyList()))
    }
    fun setQty(line: Int, qty: String) = buildJsonObject { put("do", "set_qty"); put("line", line); put("qty", qty) }
    fun voidItem(line: Int, reason: String) = buildJsonObject { put("do", "void_item"); put("line", line); put("reason", reason) }
    fun setOrderNote(note: String?) = buildJsonObject { put("do", "set_order_note"); put("note", note?.let { JsonPrimitive(it) } ?: JsonNull) }
    fun setCovers(covers: Int?) = buildJsonObject { put("do", "set_covers"); put("covers", covers?.let { JsonPrimitive(it) } ?: JsonNull) }
    fun sendToKitchen() = buildJsonObject { put("do", "send_to_kitchen") }
    fun moveTable(tableId: String) = buildJsonObject { put("do", "move_table"); put("table_id", tableId) }
    fun cancelOrder(reason: String) = buildJsonObject { put("do", "cancel_order"); put("reason", reason) }
    /** The counter prints the bill for the table; the waiter carries it over. */
    fun printBill() = buildJsonObject { put("do", "request_bill") }
}

/** A line of the order as the counter sees it. The money is the counter's. */
data class LineView(val line: Int, val name: String, val qty: String, val amount: String, val note: String?, val sentToKitchen: Boolean) {
    companion object {
        fun parse(o: JsonObject) = LineView(o.int("line"), o.str("name"), o.str("qty"), o.str("amount"), o.strOrNull("note"), o.bool("sent_to_kitchen"))
    }
}

/** Every outcome is final and carries a sentence a waiter can read. None is retried. */
sealed interface Outcome {
    data class Ok(val orderId: String, val total: String, val lines: List<LineView>, val token: String?, val note: String?) : Outcome
    data class Refused(val message: String) : Outcome
    data class Held(val message: String, val batchId: String) : Outcome

    val sentence: String
        get() = when (this) {
            is Ok -> note ?: ""
            is Refused -> message
            is Held -> message
        }

    companion object {
        fun parse(o: JsonObject): Outcome? = when (o.str("outcome")) {
            "ok" -> Ok(o.str("order_id"), o.str("total"), (o["lines"] ?: JsonNull).objects().map(LineView::parse), o.strOrNull("token"), o.strOrNull("note"))
            "refused" -> Refused(o.str("message"))
            "held" -> Held(o.str("message"), o.str("batch_id"))
            else -> null
        }

        fun toJson(outcome: Outcome): String = when (outcome) {
            is Ok -> buildJsonObject {
                put("outcome", "ok"); put("order_id", outcome.orderId); put("total", outcome.total)
                put("lines", JsonArray(outcome.lines.map { l -> buildJsonObject { put("line", l.line); put("name", l.name); put("qty", l.qty); put("amount", l.amount); put("note", l.note?.let { JsonPrimitive(it) } ?: JsonNull); put("sent_to_kitchen", l.sentToKitchen) } }))
                put("token", outcome.token?.let { JsonPrimitive(it) } ?: JsonNull); put("note", outcome.note?.let { JsonPrimitive(it) } ?: JsonNull)
            }.toString()
            is Refused -> buildJsonObject { put("outcome", "refused"); put("message", outcome.message) }.toString()
            is Held -> buildJsonObject { put("outcome", "held"); put("message", outcome.message); put("batch_id", outcome.batchId) }.toString()
        }

        fun fromJson(text: String): Outcome? = try { parse(MbJson.parseToJsonElement(text) as JsonObject) } catch (e: Exception) { null }
    }
}

data class BatchResult(val outcomes: List<Pair<String, Outcome>>, val says: String) {
    companion object {
        fun parse(o: JsonObject): BatchResult {
            val list = (o["outcomes"] as? JsonArray)?.mapNotNull { pair ->
                val arr = pair as? JsonArray ?: return@mapNotNull null
                val id = (arr.getOrNull(0) as? JsonPrimitive)?.content ?: return@mapNotNull null
                val out = (arr.getOrNull(1) as? JsonObject)?.let(Outcome::parse) ?: return@mapNotNull null
                id to out
            } ?: emptyList()
            return BatchResult(list, o.str("says"))
        }
    }
}

/** What arrives on the stream. */
data class Push(val seq: Long, val kind: String, val body: JsonElement) {
    companion object {
        fun parse(o: JsonObject) = Push(o.long("seq"), o.str("kind"), o["body"] ?: JsonNull)
    }
}

sealed interface Missed {
    data class Since(val pushes: List<Push>) : Missed
    data class TooFarBehind(val newest: Long) : Missed

    companion object {
        fun parse(o: JsonObject): Missed? = when (o.str("what")) {
            "since" -> Since((o["pushes"] ?: JsonNull).objects().map(Push::parse))
            "too_far_behind" -> TooFarBehind(o.long("newest"))
            else -> null
        }
    }
}
