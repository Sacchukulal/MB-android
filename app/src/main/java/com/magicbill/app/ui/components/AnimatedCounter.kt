package com.magicbill.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.magicbill.app.core.formatINR
import com.magicbill.app.ui.theme.MBMotion

/**
 * Money that counts up to its value — the hero number animation.
 * Re-animates whenever [value] changes (e.g. background refresh lands).
 */
@Composable
fun AnimatedRupees(
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = LocalContentColor.current,
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(MBMotion.DurLong, easing = MBMotion.EaseOut),
        label = "rupees",
    )
    // Skip decimals while in motion so digits don't jitter.
    val text = remember(animated) { formatINR(animated.toDouble(), decimals = 0) }
    Text(text, modifier = modifier, style = style, color = color)
}

/** Plain integer counter (bill counts etc.). */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = LocalContentColor.current,
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(MBMotion.DurLong, easing = MBMotion.EaseOut),
        label = "count",
    )
    Text("${animated.toInt()}", modifier = modifier, style = style, color = color)
}
