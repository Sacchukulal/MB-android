package com.magicbill.app.ui.kit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.magicbill.app.core.formatINR
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Mb

/** Money that counts up to its value — the hero number. Re-animates whenever [value] changes. */
@Composable
fun AnimatedRupees(value: Double, modifier: Modifier = Modifier, style: TextStyle = Mb.type.hero, color: Color = Mb.colors.ink) {
    val animated by animateFloatAsState(value.toFloat(), tween(MBMotion.DurLong, easing = MBMotion.EaseOut), label = "rupees")
    // No decimals while in motion, so digits do not jitter.
    val text = remember(animated) { formatINR(animated.toDouble(), decimals = 0) }
    Text(text, modifier = modifier, style = style, color = color)
}

/** A whole number that counts to its value. */
@Composable
fun AnimatedCount(value: Int, modifier: Modifier = Modifier, style: TextStyle = Mb.type.stat, color: Color = Mb.colors.ink) {
    val animated by animateFloatAsState(value.toFloat(), tween(MBMotion.DurLong, easing = MBMotion.EaseOut), label = "count")
    Text("${animated.toInt()}", modifier = modifier, style = style, color = color)
}

/**
 * A short figure that changes in steps — a quantity, an item count: the new one slides in
 * from below (up) or above (down) while the old one leaves. The one motion a waiter sees
 * forty times a night, so it is quick.
 */
@Composable
fun Ticker(value: String, modifier: Modifier = Modifier, style: TextStyle = Mb.type.section, color: Color = Mb.colors.ink) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = (targetState.toDoubleOrNull() ?: 0.0) >= (initialState.toDoubleOrNull() ?: 0.0)
            val sign = if (up) 1 else -1
            ContentTransform(
                slideInVertically(tween(MBMotion.DurShort)) { sign * it / 2 } + fadeIn(tween(MBMotion.DurShort)),
                slideOutVertically(tween(MBMotion.DurShort)) { -sign * it / 2 } + fadeOut(tween(90)),
            )
        },
        label = "ticker",
        modifier = modifier,
    ) { shown -> Text(shown, style = style, color = color) }
}

@Suppress("unused")
private val keep = ContentTransform::class to (fadeIn() togetherWith fadeOut())
