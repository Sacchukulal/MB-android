package com.magicbill.app.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

/** Shared motion vocabulary — every animated thing in the app draws from here
 *  so the whole app moves with one personality: quick, springy, never floaty. */
object MBMotion {
    val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EaseEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard interactive spring (chips, toggles, selection pills). */
    fun <T> snappy() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Bouncier spring for playful bits (press scale, badges appearing). */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.6f, stiffness = 500f)

    const val DurShort = 200
    const val DurMedium = 350
    const val DurLong = 600

    // ---- Navigation transitions --------------------------------------------------------
    // Every screen is drawn on its own opaque canvas (Shell), so two screens never blend:
    // drilling in, the new one slides in over the old, which drifts a quarter of the width
    // beneath it; coming back reverses the two. Nothing fades, so nothing is ever seen
    // through anything else.

    private fun slide() = tween<androidx.compose.ui.unit.IntOffset>(DurMedium, easing = EaseEmphasized)

    val enterForward: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(slide()) { it }
    }
    val exitForward: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(slide()) { -it / 4 }
    }
    val enterBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(slide()) { -it / 4 }
    }
    val exitBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(slide()) { it }
    }

    /** A tab hop: the old tab is gone at once, the new one fades up with a small lift. */
    val tabEnter: EnterTransition =
        slideInVertically(tween(DurShort, easing = EaseOut)) { it / 30 } + fadeIn(tween(DurShort))
    val tabExit: ExitTransition = fadeOut(tween(90))

    /** The floating tab bar slips down and away when a screen without one opens, and back up on return. */
    val barEnter: EnterTransition = slideInVertically(tween(DurShort, easing = EaseOut)) { it } + fadeIn(tween(DurShort))
    val barExit: ExitTransition = slideOutVertically(tween(DurShort, easing = EaseOut)) { it } + fadeOut(tween(DurShort))
}
