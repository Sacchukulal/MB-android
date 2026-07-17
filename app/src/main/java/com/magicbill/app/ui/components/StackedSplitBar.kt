package com.magicbill.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.magicbill.app.core.formatINR
import com.magicbill.app.ui.theme.LocalMBDarkTheme
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.PaymentColors
import kotlin.math.roundToInt

data class SplitEntry(val label: String, val amount: Double?, val pct: Float, val color: Color)

/**
 * Payment-mode split: ONE stacked bar with 2dp surface gaps between segments
 * plus a legend of dot + label + value rows. Colors are entity-fixed
 * (cash/card/upi/credit — CVD-validated against both surfaces); a mode with
 * zero value keeps its color and simply contributes no segment.
 *
 * [amounts] may be null (staff masked mode) — then only percentages show.
 */
@Composable
fun StackedSplitBar(
    cash: Double?,
    card: Double?,
    upi: Double?,
    credit: Double?,
    pctFallback: List<Float>? = null,
    modifier: Modifier = Modifier,
) {
    val dark = LocalMBDarkTheme.current
    val colors = listOf(
        if (dark) PaymentColors.cashDark else PaymentColors.cashLight,
        if (dark) PaymentColors.cardDark else PaymentColors.cardLight,
        if (dark) PaymentColors.upiDark else PaymentColors.upiLight,
        if (dark) PaymentColors.creditDark else PaymentColors.creditLight,
    )
    val labels = listOf("Cash", "Card", "UPI", "Credit")
    val amounts = listOf(cash, card, upi, credit)
    val total = amounts.sumOf { it ?: 0.0 }

    val entries = labels.indices.map { i ->
        val pct = when {
            total > 0 -> ((amounts[i] ?: 0.0) / total).toFloat()
            pctFallback != null -> (pctFallback.getOrNull(i) ?: 0f) / 100f
            else -> 0f
        }
        SplitEntry(labels[i], amounts[i], pct, colors[i])
    }
    val animated = entries.map { e ->
        val v by animateFloatAsState(e.pct, tween(MBMotion.DurLong, easing = MBMotion.EaseOut), label = e.label)
        v
    }
    val anyValue = animated.any { it > 0.001f }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(percent = 50)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (anyValue) {
                entries.forEachIndexed { i, e ->
                    val w = animated[i]
                    if (w > 0.001f) {
                        Box(
                            Modifier
                                .weight(w)
                                .fillMaxHeight()
                                .background(e.color),
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Legend: identity is dot + text, never color alone.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(e.color, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        e.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(e.pct * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    if (e.amount != null) {
                        Text(
                            formatINR(e.amount, decimals = 0),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
