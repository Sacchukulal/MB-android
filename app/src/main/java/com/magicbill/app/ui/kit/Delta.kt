package com.magicbill.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magicbill.app.cloud.ReportMath
import com.magicbill.app.ui.theme.Mb

/** "+12% vs yesterday" pill. Green up, red down, quiet when there is nothing to compare against. The sum is [ReportMath.change]'s. */
@Composable
fun DeltaChip(nowPaise: Long, beforePaise: Long?, modifier: Modifier = Modifier, label: String = "vs yesterday") {
    val c = Mb.colors
    val pct: Int? = beforePaise?.let { ReportMath.change(nowPaise, it) }
    val up = (pct ?: 0) >= 0
    val (bg, fg) = when {
        pct == null -> c.raised to c.inkMuted
        up -> c.okSoft to c.ok
        else -> c.dangerSoft to c.danger
    }
    Row(
        modifier.background(bg, RoundedCornerShape(percent = 50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pct != null) {
            Icon(if (up) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(" ${if (up) "+" else ""}$pct% $label", style = Mb.type.label, color = fg)
        } else {
            Text("— $label", style = Mb.type.label, color = fg)
        }
    }
}
