package com.magicbill.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.magicbill.app.ui.theme.MBMotion

/**
 * Tactile press: the element squishes to [pressedScale] while held.
 * Pair with clickable(interactionSource = it) — or use standalone, which
 * installs its own interaction source into the layer only (visual, no click).
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        MBMotion.bouncy(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Suppress("unused")
fun Modifier.rememberPressScale(): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    pressScale(source)
}
