package com.magicbill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.theme.LocalMBDarkTheme

/** Semantic license/staff statuses used across the app. */
enum class MBBadgeStatus { Active, Trial, Grace, Expired, Neutral }

private data class BadgeColors(val container: Color, val content: Color, val dot: Color)

@Composable
private fun badgeColors(status: MBBadgeStatus): BadgeColors {
    val dark = LocalMBDarkTheme.current
    return when (status) {
        MBBadgeStatus.Active -> if (dark) {
            BadgeColors(Color(0xFF065F46), Color(0xFFA7F3D0), Color(0xFF34D399))
        } else {
            BadgeColors(Color(0xFFD1FAE5), Color(0xFF065F46), Color(0xFF059669))
        }

        MBBadgeStatus.Trial -> if (dark) {
            BadgeColors(Color(0xFF1E3A8A), Color(0xFFBFDBFE), Color(0xFF60A5FA))
        } else {
            BadgeColors(Color(0xFFDBEAFE), Color(0xFF1E40AF), Color(0xFF2563EB))
        }

        MBBadgeStatus.Grace -> if (dark) {
            BadgeColors(Color(0xFF78350F), Color(0xFFFDE68A), Color(0xFFFBBF24))
        } else {
            BadgeColors(Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFFD97706))
        }

        MBBadgeStatus.Expired -> if (dark) {
            BadgeColors(Color(0xFF7F1D1D), Color(0xFFFECACA), Color(0xFFF87171))
        } else {
            BadgeColors(Color(0xFFFEE2E2), Color(0xFF991B1B), Color(0xFFDC2626))
        }

        MBBadgeStatus.Neutral -> BadgeColors(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Status pill with a leading dot: `● Active`, `● Expired`, … */
@Composable
fun MBBadge(
    text: String,
    status: MBBadgeStatus,
    modifier: Modifier = Modifier,
) {
    val colors = badgeColors(status)
    Row(
        modifier = modifier
            .background(colors.container, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(colors.dot, CircleShape),
        )
        Box(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.content)
    }
}
