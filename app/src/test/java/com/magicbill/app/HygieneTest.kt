package com.magicbill.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The rules that erode, enforced by a test rather than by agreement. */
class HygieneTest {
    private val root = File("src/main/java/com/magicbill/app")
    private val sources: List<File> get() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test fun the_tree_is_there() {
        assertTrue(root.absolutePath, root.isDirectory && sources.isNotEmpty())
    }

    @Test fun no_key_and_no_old_library_in_the_source() {
        val bad = sources.filter { f ->
            val t = f.readText()
            t.contains("eyJ") || t.contains("io.github.jan-tennert") || t.contains("supabase-kt") || t.contains("rlvygwituwywofwcwjsf")
        }
        assertTrue("keys or the old library in: ${bad.map { it.name }}", bad.isEmpty())
    }

    /** ONE palette. Every colour in the app is a value in ui/theme/Palette.kt, read by job. */
    @Test fun every_colour_lives_in_the_palette() {
        val bad = sources.filter { f ->
            val p = f.path.replace('\\', '/')
            !p.endsWith("/ui/theme/Palette.kt") && Regex("Color\\(0x[0-9A-Fa-f]{6,8}\\)").containsMatchIn(f.readText())
        }
        assertTrue("raw colours outside the palette in: ${bad.map { it.name }}", bad.isEmpty())
    }

    /**
     * ONE kit. Screens read the theme through `Mb.colors` / `Mb.type`, never Material's scheme
     * or typography directly — those are derived from the palette for Material's own
     * components, and a screen that reads them is a screen that drifts when the palette moves.
     */
    @Test fun screens_read_the_theme_by_job_not_from_material() {
        val bad = sources.filter { f ->
            val p = f.path.replace('\\', '/')
            p.contains("/ui/screens/") && Regex("MaterialTheme\\.(colorScheme|typography)").containsMatchIn(f.readText())
        }
        assertTrue("Material's scheme or typography read in a screen: ${bad.map { it.name }}", bad.isEmpty())
    }

    /** The old component set is gone; there is `ui/kit` and nothing beside it. */
    @Test fun there_is_one_kit() {
        val twins = sources.filter { it.path.replace('\\', '/').contains("/ui/components/") }
        assertTrue("a second kit: ${twins.map { it.name }}", twins.isEmpty())
    }

    /** The phone calls no Edge Function at all: its staff login is fetched by the counter. */
    @Test fun no_realtime_no_polling_no_edge_function() {
        val bad = sources.filter { f ->
            val t = f.readText()
            t.contains("/realtime/") || t.contains("/functions/v1/")
        }
        assertTrue("a metered or realtime call in: ${bad.map { it.name }}", bad.isEmpty())
    }

    @Test fun nothing_runs_blocking_on_a_dispatcher_it_does_not_own() {
        val bad = sources.filter { f -> f.readText().contains("runBlocking(") && !f.path.contains("Test") }
        assertTrue("runBlocking in app code: ${bad.map { it.name }}", bad.isEmpty())
    }
}
