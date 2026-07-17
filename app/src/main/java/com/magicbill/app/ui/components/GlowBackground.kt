package com.magicbill.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.LocalMBDarkTheme
import com.magicbill.app.ui.theme.Teal

/**
 * The signature Magic Bill backdrop: soft emerald/teal radial glows breathing
 * on the navy canvas. Used behind the welcome flow and screen heroes — this
 * (not boxes) is what gives screens depth.
 */
@Composable
fun GlowBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = LocalMBDarkTheme.current
    val bg = MaterialTheme.colorScheme.background
    val emeraldAlpha = (if (dark) 0.16f else 0.10f) * intensity
    val tealAlpha = (if (dark) 0.10f else 0.07f) * intensity

    Box(modifier.background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Emerald.copy(alpha = emeraldAlpha), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.05f),
                    radius = size.width * 0.9f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Teal.copy(alpha = tealAlpha), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.28f),
                    radius = size.width * 0.7f,
                ),
            )
        }
        content()
    }
}
