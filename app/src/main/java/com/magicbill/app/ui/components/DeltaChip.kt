package com.magicbill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.theme.LocalMBDarkTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "+12% vs yesterday" pill. Green when up, amber-red when down, neutral when
 * there is nothing to compare against.
 */
@Composable
fun DeltaChip(
    current: Double,
    previous: Double?,
    modifier: Modifier = Modifier,
    label: String = "vs yesterday",
) {
    val dark = LocalMBDarkTheme.current
    val pct: Int? = when {
        previous == null || previous <= 0.0 -> null
        else -> (((current - previous) / previous) * 100).roundToInt()
    }
    val up = (pct ?: 0) >= 0
    val (bg, fg) = when {
        pct == null -> MaterialTheme.colorScheme.surfaceContainerHigh to
            MaterialTheme.colorScheme.onSurfaceVariant

        up -> (if (dark) Color(0x3310B981) else Color(0x2210B981)) to
            (if (dark) Color(0xFF6EE7B7) else Color(0xFF047857))

        else -> (if (dark) Color(0x33F87171) else Color(0x22DC2626)) to
            (if (dark) Color(0xFFFCA5A5) else Color(0xFFB91C1C))
    }

    Row(
        modifier = modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pct != null) {
            Icon(
                if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp).padding(end = 0.dp),
            )
            Text(
                " ${if (up) "+" else ""}$pct% $label",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        } else {
            Text(
                "— $label",
                style = MaterialTheme.typography.labelMedium,
                color = fg,
            )
        }
    }
}

/** Convenience: absolute percent change, capped for sanity in the UI. */
fun deltaPct(current: Double, previous: Double): Int =
    (((current - previous) / previous) * 100).roundToInt().coerceIn(-999, 999).let { abs(it) }
