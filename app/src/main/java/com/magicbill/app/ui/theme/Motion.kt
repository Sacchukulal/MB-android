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

    // ---- Navigation transitions (forward = slide from right, back = reverse) ----

    val enterForward: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(DurMedium, easing = EaseEmphasized)) { it / 5 } +
            fadeIn(tween(DurMedium))
    }
    val exitForward: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(DurMedium, easing = EaseEmphasized)) { -it / 8 } +
            fadeOut(tween(DurShort))
    }
    val enterBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(tween(DurMedium, easing = EaseEmphasized)) { -it / 5 } +
            fadeIn(tween(DurMedium))
    }
    val exitBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(tween(DurMedium, easing = EaseEmphasized)) { it / 8 } +
            fadeOut(tween(DurShort))
    }

    /** Tab-switch transition inside shells: subtle vertical drift + fade. */
    val tabEnter: EnterTransition =
        slideInVertically(tween(DurMedium, easing = EaseOut)) { it / 30 } + fadeIn(tween(DurMedium))
    val tabExit: ExitTransition = fadeOut(tween(90))
}
