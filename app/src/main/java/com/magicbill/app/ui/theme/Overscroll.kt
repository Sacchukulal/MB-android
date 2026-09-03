package com.magicbill.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

/**
 * The edge of a list, when a finger pulls past it.
 *
 * Android's own overscroll STRETCHES the content: every glyph is re-rasterised taller each
 * frame, and on a page that fits its screen (so every swipe is an overscroll) the letters
 * look like they shuffle. This one moves the content instead — whole pixels, on a graphics
 * layer, so the type is never redrawn — with a rubber band's resistance, and springs it back
 * when the finger lets go. A fling that reaches the edge gives one soft bump.
 */
class RubberBandOverscroll : OverscrollEffect {
    /** Raw finger travel past the edge, per axis. What the band is pulled BY. */
    private var raw = Offset.Zero
    /** Where the content sits, per axis. What the band is pulled TO. */
    private var pull by mutableStateOf(Offset.Zero)
    private var viewportW = 0f
    private var viewportH = 0f

    override val isInProgress: Boolean get() = pull != Offset.Zero

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        val user = source == NestedScrollSource.UserInput
        var byBand = Offset.Zero
        var toScroll = delta
        if (user && raw != Offset.Zero) {
            // A finger coming back lets the band go first, before the list scrolls.
            byBand = Offset(release(raw.x, delta.x), release(raw.y, delta.y))
            raw += byBand
            toScroll = delta - byBand
            settle()
        }
        val byScroll = performScroll(toScroll)
        val left = toScroll - byScroll
        if (user && (abs(left.x) > 0.5f || abs(left.y) > 0.5f)) {
            raw += left
            settle()
            return delta
        }
        return byBand + byScroll
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        val remaining = performFling(velocity)
        val kick = Offset(remaining.x.coerceIn(-4000f, 4000f) * 0.2f, remaining.y.coerceIn(-4000f, 4000f) * 0.2f)
        if (pull == Offset.Zero && abs(kick.x) < 20f && abs(kick.y) < 20f) return
        val anim = Animatable(pull, Offset.VectorConverter)
        try {
            anim.animateTo(
                Offset.Zero,
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset(0.5f, 0.5f)),
                initialVelocity = kick,
            ) { pull = value }
            pull = Offset.Zero
            raw = Offset.Zero
        } finally {
            // A finger landing mid-spring cancels this; the band then continues from where it is.
            if (pull != Offset.Zero) raw = Offset(unband(pull.x, viewportW), unband(pull.y, viewportH))
        }
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val p = measurable.measure(constraints)
            viewportW = p.width.toFloat()
            viewportH = p.height.toFloat()
            return layout(p.width, p.height) {
                p.placeWithLayer(0, 0) { translationX = pull.x; translationY = pull.y }
            }
        }
    }

    private fun settle() {
        pull = Offset(band(raw.x, viewportW), band(raw.y, viewportH))
    }

    /** How much of a finger's move [d] goes to letting a band pulled by [r] go: none, some, or all of it. */
    private fun release(r: Float, d: Float): Float =
        if (r == 0f || d == 0f || sign(r) == sign(d)) 0f else if (abs(d) >= abs(r)) -r else d

    companion object {
        private const val RESIST = 0.55f
        private const val REACH = 0.5f

        /** iOS's rubber band: the pull approaches, and never reaches, half the viewport. */
        fun band(x: Float, dim: Float): Float {
            if (x == 0f) return 0f
            val d = if (dim > 1f) dim else 1f
            return sign(x) * (1f - 1f / (abs(x) * RESIST / d + 1f)) * d * REACH
        }

        /** The inverse of [band]: the raw travel that would put the content at [p]. */
        fun unband(p: Float, dim: Float): Float {
            if (p == 0f) return 0f
            val d = if (dim > 1f) dim else 1f
            val q = (abs(p) / (d * REACH)).coerceIn(0f, 0.999f)
            return sign(p) * (d / RESIST) * (q / (1f - q))
        }
    }
}

/** The one overscroll for the whole app; the theme provides it, every scrollable picks it up. */
object RubberBandOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = RubberBandOverscroll()
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x5b1c
}
