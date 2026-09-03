package com.magicbill.app

import com.magicbill.app.update.Release
import com.magicbill.app.update.Updater
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shelf's file, and the one rule that decides "newer": the code, never the name. */
class UpdateTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun the_code_decides_whatever_the_name_says() {
        // A release named lower than the installed build is still newer when its code is higher.
        val named200 = Release(version = "2.0.0", version_code = 21, apk_url = "https://x/apk")
        assertTrue(Updater.isNewer(named200, installedCode = 20, installedName = "3.1.1"))
        assertFalse(Updater.isNewer(named200, installedCode = 21, installedName = "2.0.0"))
        assertFalse(Updater.isNewer(named200, installedCode = 22, installedName = "2.0.1"))
    }

    @Test fun a_shelf_without_a_code_is_compared_by_name() {
        val old = Release(version = "v2.4.6", apk_url = "https://x/apk")
        assertTrue(Updater.isNewer(old, installedCode = 15, installedName = "2.4.5"))
        assertFalse(Updater.isNewer(old, installedCode = 16, installedName = "2.4.6"))
        assertFalse(Updater.isNewer(old, installedCode = 20, installedName = "3.1.1"))
    }

    @Test fun names_compare_part_by_part() {
        assertTrue(Updater.compareNames("3.2", "3.1.9") > 0)
        assertTrue(Updater.compareNames("3.1.10", "3.1.9") > 0)
        assertEquals(0, Updater.compareNames("2.0", "2.0.0"))
        assertTrue(Updater.compareNames("2.0.0-rc1", "1.9") > 0)
    }

    @Test fun the_july_file_still_reads_and_the_new_fields_are_optional() {
        val july = json.decodeFromString(Release.serializer(), """{"version":"2.4.6","apk_url":"https://x/magic-bill.apk","release_notes":"n"}""")
        assertEquals("2.4.6", july.name)
        assertEquals(null, july.version_code)
        val now = json.decodeFromString(
            Release.serializer(),
            """{"version":"2.0.0","version_code":21,"apk_url":"https://x/a.apk","apk_size":12345,"published":"2026-09-03","release_notes":"notes","extra":1}""",
        )
        assertEquals(21, now.version_code)
        assertEquals(12345L, now.apk_size)
        assertEquals("2026-09-03", now.published)
    }
}
