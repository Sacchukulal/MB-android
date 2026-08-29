package com.magicbill.app.ui.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.magicbill.app.ui.theme.MBMotion

/**
 * Tactile press: the element squishes to [pressedScale] while held, on the bouncy spring.
 * Pair with `clickable(interactionSource = it)`.
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.965f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, MBMotion.bouncy(), label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A slow breath, for something that is on its way — a tile whose order is still sending. */
fun Modifier.pulse(on: Boolean): Modifier = composed {
    if (!on) return@composed this
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650, easing = MBMotion.EaseOut), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    graphicsLayer { this.alpha = alpha }
}
