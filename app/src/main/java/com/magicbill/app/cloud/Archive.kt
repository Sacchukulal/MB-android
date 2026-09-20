package com.magicbill.app.cloud

import androidx.room.withTransaction
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.core.arr
import com.magicbill.app.core.asObjectOrNull
import com.magicbill.app.core.map
import com.magicbill.app.core.obj
import com.magicbill.app.core.objects
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.core.str
import com.magicbill.app.core.strOrNull
import com.magicbill.app.core.tsOf
import com.magicbill.app.db.ArchiveDayRow
import com.magicbill.app.db.MbDatabase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.zip.GZIPInputStream

/**
 * The day files (docs/PLAN_BILL_ARCHIVE §4): the shop's permanent bill history, one gzip of
 * JSON lines per business day in the `bill-archives` bucket, keyed
 * `{restaurant}/{yyyy}/{yyyy-mm-dd}.jsonl.gz`. Line 1 is the day header carrying the day's
 * totals exactly as the counter pushes them; every other line is a wire row exactly as the
 * counter pushes it. ONE reader; every row lands through the mirror's own table writers, so
 * the file and the cloud's window converge on the same rows.
 *
 * Runs at the end of a pull, under the pull's single in-flight rule; never on a timer.
 */
class Archive(private val cloud: CloudLink, private val db: MbDatabase, private val clock: Clock, private val tables: List<Mirror.Table>) {

    /** A day file as the listing names it; [updatedAt] is the object's `updated_at` as given. */
    data class DayFile(val businessDay: String, val key: String, val updatedAt: String)

    /** What one file's import did: bill lines written, and lines not stored (other tables, unreadable). */
    data class Imported(val bills: Int, val skipped: Int)

    /** Every day file the shop has, newest first: one list for the year folders, one per year. */
    suspend fun list(restaurantId: String): Answer<List<DayFile>> {
        val years = when (val a = cloud.listObjects(BUCKET, "$restaurantId/")) {
            is Answer.Ok -> a.value.filter { it.isFolder }.map { it.name }
            is Answer.Refused -> return a
            is Answer.Unreachable -> return a
            is Answer.SignedOut -> return a
        }
        val files = ArrayList<DayFile>()
        for (year in years) {
            val entries = when (val a = cloud.listObjects(BUCKET, "$restaurantId/$year/")) {
                is Answer.Ok -> a.value
                is Answer.Refused -> return a
                is Answer.Unreachable -> return a
                is Answer.SignedOut -> return a
            }
            for (e in entries) {
                if (e.isFolder || !e.name.endsWith(SUFFIX)) continue
                val day = Ist.parseDay(e.name.removeSuffix(SUFFIX)) ?: continue
                files.add(DayFile(Ist.key(day), "$restaurantId/$year/${e.name}", e.updatedAt ?: ""))
            }
        }
        return Answer.Ok(files.sortedByDescending { it.businessDay })
    }

    /** The files not on this phone, or re-uploaded since (their `updated_at` moved), newest first. */
    suspend fun pending(restaurantId: String): Answer<List<DayFile>> = list(restaurantId).map { files ->
        val have = db.archive().list(restaurantId).associate { it.businessDay to it.objectUpdatedAt }
        files.filter { have[it.businessDay] != it.updatedAt }
    }

    /**
     * The pull's archive step: at most [limit] pending files, newest first; the next pull
     * continues. A file that cannot be read is skipped and tried again next time; the cloud
     * not answering stops the step. Answers how many files landed.
     */
    suspend fun pull(restaurantId: String, limit: Int = FILES_PER_PULL): Answer<Int> {
        val todo = when (val p = pending(restaurantId)) {
            is Answer.Ok -> p.value.take(limit)
            is Answer.Refused -> return p
            is Answer.Unreachable -> return p
            is Answer.SignedOut -> return p
        }
        var done = 0
        for (file in todo) {
            when (val r = importDay(restaurantId, file)) {
                is Answer.Ok -> done++
                is Answer.Refused -> {} // this one file; the next pull looks again
                is Answer.Unreachable -> return r
                is Answer.SignedOut -> return r
            }
        }
        return Answer.Ok(done)
    }

    /**
     * One file: downloaded as a stream, read line by line, written in batches of [BATCH] and
     * recorded in `archive_days` — all inside one transaction, so a file that breaks halfway
     * leaves nothing behind.
     */
    suspend fun importDay(restaurantId: String, file: DayFile): Answer<Imported> {
        val stream = when (val a = cloud.getObjectStream(BUCKET, file.key)) {
            is Answer.Ok -> a.value
            is Answer.Refused -> return a
            is Answer.Unreachable -> return a
            is Answer.SignedOut -> return a
        }
        return try {
            stream.use { s ->
                db.withTransaction { Answer.Ok(read(restaurantId, file, GZIPInputStream(s).bufferedReader().lineSequence())) }
            }
        } catch (e: Exception) {
            android.util.Log.w(CloudLink.TAG, "day file ${file.key} not read: ${e.javaClass.simpleName}: ${e.message}")
            Answer.Refused("The bills of ${file.businessDay} could not be read.")
        }
    }

    /**
     * The ONE reader. The header's totals go to the `day_*` writers; every wire row goes to
     * its table's writer with `id` and `updated_at` merged into its data. A line whose table
     * the phone does not keep, or that does not parse, is skipped and counted, never fatal.
     */
    private suspend fun read(restaurantId: String, file: DayFile, lines: Sequence<String>): Imported {
        val rest = lines.iterator()
        val header = if (rest.hasNext()) parseJsonOrNull(rest.next())?.asObjectOrNull() else null
        check(header != null && header.str("\$kind") == "day") { "not a day file" }
        val sealedAt = header.tsOf("sealed_at")
        write(restaurantId, "day_totals", listOfNotNull(header.obj("day_totals")).map { stamp(it, null, sealedAt) })
        write(restaurantId, "day_item_totals", header.arr("day_item_totals").objects().map { stamp(it, null, sealedAt) })
        write(restaurantId, "day_category_totals", header.arr("day_category_totals").objects().map { stamp(it, null, sealedAt) })

        val batches = HashMap<String, ArrayList<JsonObject>>()
        var bills = 0
        var skipped = 0
        for (line in rest) {
            if (line.isBlank()) continue
            val row = parseJsonOrNull(line)?.asObjectOrNull()
            val table = row?.strOrNull("table")?.let { ALIASES[it] ?: it }
            val data = row?.obj("data")
            if (row == null || table == null || data == null || tables.none { it.name == table }) { skipped++; continue }
            val batch = batches.getOrPut(table) { ArrayList() }
            batch.add(stamp(data, row.strOrNull("id"), row.tsOf("updated_at")))
            if (table == "bills") bills++
            if (batch.size >= BATCH) { write(restaurantId, table, batch); batch.clear() }
        }
        for ((table, batch) in batches) write(restaurantId, table, batch)
        db.archive().upsert(ArchiveDayRow(restaurantId, file.businessDay, file.updatedAt, bills, clock.now()))
        return Imported(bills, skipped)
    }

    /** The wire row's envelope folded into its data, the way the mirror's mappers read a cloud row. */
    private fun stamp(data: JsonObject, id: String?, updatedMs: Long?): JsonObject = JsonObject(
        data + listOfNotNull(id?.let { "id" to JsonPrimitive(it) }, updatedMs?.let { "updated_ms" to JsonPrimitive(it) }),
    )

    private suspend fun write(restaurantId: String, table: String, rows: List<JsonObject>) {
        if (rows.isNotEmpty()) tables.first { it.name == table }.write(restaurantId, rows)
    }

    companion object {
        const val BUCKET = "bill-archives"
        const val SUFFIX = ".jsonl.gz"
        /** Files per pull, newest first; a fresh phone fills its history over a few pulls. */
        const val FILES_PER_PULL = 60
        /** Rows per upsert inside the file's transaction. */
        const val BATCH = 500
        /** The counter's names for the phone's tables (SYNC_PROTOCOL.md §3). */
        private val ALIASES = mapOf("orders" to "bills", "items" to "menu_items", "categories" to "menu_categories")
    }
}
