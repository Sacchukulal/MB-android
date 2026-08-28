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

    @Test fun every_colour_lives_in_the_design_system() {
        // ui/theme is the palette; ui/components is the 2.x design system restored verbatim,
        // whose semantic colours (delta greens, badge sets) are part of that system. Screens
        // and the kit never spell a colour.
        val bad = sources.filter { f ->
            val p = f.path.replace('\\', '/')
            !p.contains("/ui/theme/") && !p.contains("/ui/components/") && Regex("Color\\(0x[0-9A-Fa-f]{6,8}\\)").containsMatchIn(f.readText())
        }
        assertTrue("raw colours outside the design system in: ${bad.map { it.name }}", bad.isEmpty())
    }

    @Test fun no_realtime_no_polling_no_edge_function_but_the_one() {
        val bad = sources.filter { f ->
            val t = f.readText()
            t.contains("/realtime/") || (t.contains("/functions/v1/") && !t.contains("staff-login"))
        }
        assertTrue("a metered or realtime call in: ${bad.map { it.name }}", bad.isEmpty())
    }

    @Test fun nothing_runs_blocking_on_a_dispatcher_it_does_not_own() {
        val bad = sources.filter { f -> f.readText().contains("runBlocking(") && !f.path.contains("Test") }
        assertTrue("runBlocking in app code: ${bad.map { it.name }}", bad.isEmpty())
    }
}
