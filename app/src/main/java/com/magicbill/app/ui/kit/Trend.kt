package com.magicbill.app.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.magicbill.app.core.formatShortINR
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Mb

data class TrendPoint(val label: String, val value: Double)

/**
 * One series of thin rounded bars growing from the baseline with a staggered entrance; tap a
 * bar to read it (haptic tick, readout above). One hue, no legend, one recessive baseline.
 */
@Composable
fun TrendChart(points: List<TrendPoint>, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val haptics = LocalHapticFeedback.current
    val c = Mb.colors
    val bar = c.accent
    val dimBar = bar.copy(alpha = 0.38f)

    val growth = remember(points) { Animatable(0f) }
    LaunchedEffect(points) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(MBMotion.DurLong, easing = MBMotion.EaseOut))
    }

    val maxValue = points.maxOf { it.value }.coerceAtLeast(1.0)
    val sel = points[selected]

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sel.label, style = Mb.type.label, color = c.inkMuted)
            Text(formatShortINR(sel.value), style = Mb.type.button, color = c.ink)
        }
        val density = LocalDensity.current
        Canvas(
            Modifier.fillMaxWidth().height(height).pointerInput(points) {
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
            drawLine(color = c.lineSoft, start = Offset(0f, chartHeight), end = Offset(size.width, chartHeight), strokeWidth = with(density) { 1.dp.toPx() })
            points.forEachIndexed { i, p ->
                val stagger = (i.toFloat() / points.size) * 0.35f
                val t = ((growth.value - stagger) / (1f - stagger)).coerceIn(0f, 1f)
                val fullH = (p.value / maxValue).toFloat() * (chartHeight * 0.92f)
                val h = (fullH * t).coerceAtLeast(with(density) { 3.dp.toPx() })
                val left = i * slot + (slot - barWidth) / 2f
                drawRoundRect(color = if (i == selected) bar else dimBar, topLeft = Offset(left, chartHeight - h), size = Size(barWidth, h), cornerRadius = CornerRadius(corner, corner))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = Mb.type.caption, color = c.inkMuted)
            Text(points[points.size / 2].label, style = Mb.type.caption, color = c.inkMuted)
            Text(points.last().label, style = Mb.type.caption, color = c.inkMuted)
        }
    }
}
