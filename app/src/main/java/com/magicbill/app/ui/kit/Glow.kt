package com.magicbill.app.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.magicbill.app.ui.theme.Mb

/**
 * The signature backdrop: two soft radial glows breathing on the canvas. This — not boxes — is
 * what gives a screen depth, so every screen sits on it.
 */
@Composable
fun Glow(modifier: Modifier = Modifier, intensity: Float = 1f, content: @Composable () -> Unit) {
    val c = Mb.colors
    val a1 = (if (c.isDark) 0.16f else 0.10f) * intensity
    val a2 = (if (c.isDark) 0.10f else 0.07f) * intensity
    Box(modifier.background(c.bg)) {
        Canvas(Modifier.fillMaxSize()) {
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
        content()
    }
}
