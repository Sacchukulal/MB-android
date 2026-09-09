package com.magicbill.app

import com.magicbill.app.cloud.Licence
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The licence as the cloud sends it (PHONE_API.md §1): its word is read, never re-derived. */
class LicenceTest {
    private fun licence(vararg fields: Pair<String, Any?>) = Licence.parse(
        buildJsonObject {
            put("status", "active"); put("plan", "one"); put("plan_name", "ONE")
            put("features", buildJsonArray { })
            put("renews_on", "2027-09-09"); put("trial_ends_on", JsonNull)
            put("bound", false)
            for ((k, v) in fields) when (v) {
                null -> put(k, JsonNull)
                is Boolean -> put(k, v)
                else -> put(k, v.toString())
            }
        },
    )

    @Test fun the_clouds_answer_is_read_as_it_is() {
        val ending = licence("status" to "cancelled", "standing" to "ending", "runs_until" to "2027-09-09", "operating" to true)
        assertEquals("ending", ending.standing)
        assertTrue(ending.operating)
        assertTrue(ending.sentence.contains("runs until 2027-09-09"))

        val ended = licence("status" to "cancelled", "standing" to "ended", "runs_until" to "2026-09-01", "operating" to false)
        assertFalse(ended.operating)
        assertTrue(ended.sentence.contains("magicbill.in"))
    }

    @Test fun an_older_cloud_that_does_not_say_is_read_by_status_alone() {
        assertTrue(licence("status" to "active").operating)
        assertTrue(licence("status" to "trial").operating)
        assertFalse(licence("status" to "cancelled").operating)
        assertEquals("cancelled", licence("status" to "cancelled").standing)
    }
}
