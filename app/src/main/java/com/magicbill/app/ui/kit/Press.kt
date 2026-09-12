package com.magicbill.app.ui.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Radius
import kotlinx.coroutines.awaitCancellation

/*
 * The kit's touch modifiers. They are composable functions, not `composed {}` blocks: a
 * `composed` modifier is a small composition of its own, and a floor of table cards wearing
 * two each is forty compositions to build every time the floor comes back.
 */

/**
 * Tactile press: the element squishes to [pressedScale] while held, on the bouncy spring.
 * Pair with `tappable(interactionSource = it)`.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.965f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, MBMotion.bouncy(), label = "pressScale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * The one clickable of the kit. On the tap itself — never on a finger merely landing, so a
 * scroll costs nothing — it tells the motion system where the tap was and how big the thing
 * tapped is: the page that opens next grows out of that rectangle and closes back into it.
 * [radius] is the corner the window starts with.
 */
@Composable
fun Modifier.tappable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    radius: Dp = Radius.lg,
): Modifier {
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // The page's layer stops recording while this is pressed (see MBMotion.pressing).
    val pressed by source.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) { MBMotion.pressBegan(); try { awaitCancellation() } finally { MBMotion.pressEnded() } }
    }
    // Placed once per layout, not once per frame of a move: the coordinates stay live, and
    // the bounds are read from them at the tap.
    return onPlaced { placed[0] = it }.clickable(interactionSource = source, indication = indication ?: ripple(), enabled = enabled) {
        placed[0]?.takeIf { it.isAttached }?.boundsInRoot()?.let { bounds ->
            if (bounds.width > 0f && bounds.height > 0f) MBMotion.opensFrom(MBMotion.Launch(bounds, radiusPx, System.currentTimeMillis()))
        }
        onClick()
    }
}

/** A slow breath, for something that is on its way — a tile whose order is still sending. */
@Composable
fun Modifier.pulse(on: Boolean): Modifier {
    if (!on) return this
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650, easing = MBMotion.EaseOut), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return graphicsLayer { this.alpha = alpha }
}
