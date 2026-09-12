package com.magicbill.app.ui.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Radius
import kotlinx.coroutines.launch

/**
 * Tactile press: the element squishes to [pressedScale] while held, on the bouncy spring.
 * Pair with `clickable(interactionSource = it)`.
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.965f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, MBMotion.bouncy(), label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Where a press lands and how big the thing pressed is, told to the motion system: the page
 * that opens next grows out of this element and closes back into it, at its size and place.
 * Every kit clickable wears it, so a screen never has to say where a page came from. The
 * press itself is left alone for the clickable. The geometry is worked out at the press, not
 * in the position callback — that fires on every frame of a move, for every clickable.
 */
fun Modifier.launchPoint(): Modifier = composed {
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val radius = with(LocalDensity.current) { Radius.lg.toPx() }
    val scope = rememberCoroutineScope()
    onGloballyPositioned { placed[0] = it }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            placed[0]?.takeIf { it.isAttached }?.let { coordinates ->
                val bounds = coordinates.boundsInRoot()
                if (bounds.width > 0f && bounds.height > 0f) scope.launch { MBMotion.tapped(MBMotion.Launch(bounds, radius)) }
            }
        }
    }
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
