package com.magicbill.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.magicbill.app.core.formatShortINR
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.MBMotion

data class TrendPoint(val label: String, val value: Double)

/**
 * Single-series revenue trend: thin rounded bars growing from the baseline
 * with a staggered entrance, tap to select a bar (haptic tick + value readout
 * above the chart). One hue; identity comes from the section title, so there
 * is no legend. Grid is a single recessive baseline.
 */
@Composable
fun TrendChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    barColor: Color = Emerald,
) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val haptics = LocalHapticFeedback.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val dimBar = barColor.copy(alpha = 0.38f)

    // Entrance: bars grow with a slight stagger.
    val growth = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(MBMotion.DurLong, easing = MBMotion.EaseOut))
    }

    val maxValue = points.maxOf { it.value }.coerceAtLeast(1.0)
    val sel = points[selected]

    Column(modifier) {
        // Readout for the selected bar — the "tooltip" lives here so it never
        // clips or covers marks.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(sel.label, style = MaterialTheme.typography.labelMedium, color = muted)
            Text(
                formatShortINR(sel.value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        val density = LocalDensity.current
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val slot = size.width / points.size.toFloat()
                        val idx = (offset.x / slot).toInt().coerceIn(0, points.lastIndex)
                        if (idx != selected) {
                            selected = idx
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    }
                },
        ) {
            val slot = size.width / points.size
            val barWidth = (slot * 0.55f).coerceAtMost(with(density) { 22.dp.toPx() })
            val corner = with(density) { 5.dp.toPx() }
            val chartHeight = size.height - with(density) { 2.dp.toPx() }

            // Recessive baseline only — no grid cage.
            drawLine(
                color = baseline,
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = with(density) { 1.dp.toPx() },
            )

            points.forEachIndexed { i, p ->
                val stagger = (i.toFloat() / points.size) * 0.35f
                val t = ((growth.value - stagger) / (1f - stagger)).coerceIn(0f, 1f)
                val fullH = (p.value / maxValue).toFloat() * (chartHeight * 0.92f)
                val h = (fullH * t).coerceAtLeast(with(density) { 3.dp.toPx() })
                val left = i * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = if (i == selected) barColor else dimBar,
                    topLeft = Offset(left, chartHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
        }

        // Sparse x labels: first, middle, last — everything else stays quiet.
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            val first = points.first().label
            val mid = points[points.size / 2].label
            val last = points.last().label
            Text(first, style = MaterialTheme.typography.labelSmall, color = muted)
            Text(mid, style = MaterialTheme.typography.labelSmall, color = muted)
            Text(last, style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
