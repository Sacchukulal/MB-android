package com.magicbill.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer placeholder — shown ONLY on true first load (no cache yet).
 * Cached screens never shimmer; they render data instantly and refresh silently.
 */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    background(
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x * 600f, 0f),
            end = Offset((x + 1f) * 600f, 200f),
        ),
    )
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    radius: Dp = 8.dp,
) {
    Box(
        modifier
            .height(height)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
            .shimmer(),
    )
}

/** Standard dashboard-shaped skeleton for first-ever load. */
@Composable
fun SkeletonScreen(modifier: Modifier = Modifier) {
    Column(modifier) {
        SkeletonBlock(Modifier.width(120.dp), height = 14.dp)
        Spacer(Modifier.height(12.dp))
        SkeletonBlock(Modifier.width(220.dp), height = 40.dp, radius = 12.dp)
        Spacer(Modifier.height(28.dp))
        SkeletonBlock(Modifier.fillMaxWidth(), height = 14.dp)
        Spacer(Modifier.height(20.dp))
        repeat(4) {
            SkeletonBlock(Modifier.fillMaxWidth(), height = 44.dp, radius = 12.dp)
            Spacer(Modifier.height(12.dp))
        }
    }
}
