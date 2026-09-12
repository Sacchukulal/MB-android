package com.magicbill.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.MbColors
import kotlin.math.roundToInt

/**
 * The signature backdrop: two soft radial glows breathing on the canvas. This — not boxes — is
 * what gives a screen depth, so every screen sits on it.
 *
 * The glows are painted ONCE per screen size and theme into a small picture and put up from
 * there: two full-screen gradients shaded again on every frame of a page moving over another
 * page is what made a slow phone stutter through every motion.
 */
@Composable
fun Glow(modifier: Modifier = Modifier, intensity: Float = 1f, content: @Composable () -> Unit) {
    val c = Mb.colors
    Box(modifier.background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            if (w > 0 && h > 0) {
                drawImage(Backdrops.of(w, h, c, intensity, this), dstSize = IntSize(w, h), filterQuality = FilterQuality.Low)
            }
        }
        content()
    }
}

/** The painted glows, one picture per screen size and theme, at a quarter of the size: a glow has no edges to lose. */
private object Backdrops {
    private data class Key(val width: Int, val height: Int, val dark: Boolean, val intensity: Float)
    private val kept = LinkedHashMap<Key, ImageBitmap>()
    private const val SHRINK = 4
    private const val KEEP = 4

    fun of(width: Int, height: Int, c: MbColors, intensity: Float, density: DrawScope): ImageBitmap {
        val key = Key(width, height, c.isDark, intensity)
        kept[key]?.let { return it }
        val w = (width / SHRINK).coerceAtLeast(1)
        val h = (height / SHRINK).coerceAtLeast(1)
        val picture = ImageBitmap(w, h)
        val a1 = (if (c.isDark) 0.16f else 0.10f) * intensity
        val a2 = (if (c.isDark) 0.10f else 0.07f) * intensity
        CanvasDrawScope().draw(density, density.layoutDirection, Canvas(picture), Size(w.toFloat(), h.toFloat())) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(c.glow1.copy(alpha = a1), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.05f),
                    radius = size.width * 0.9f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(c.glow2.copy(alpha = a2), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.28f),
                    radius = size.width * 0.7f,
                ),
            )
        }
        if (kept.size >= KEEP) kept.remove(kept.keys.first())
        kept[key] = picture
        return picture
    }
}
