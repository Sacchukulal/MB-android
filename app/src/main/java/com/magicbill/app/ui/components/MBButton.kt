package com.magicbill.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.Teal

enum class MBButtonVariant { Primary, Tonal, Outline, Ghost, Danger }

/**
 * Magic Bill button. Primary wears the emerald→teal gradient with a press
 * squish; the rest are quiet. [loading] swaps the label for a spinner without
 * resizing; the button stays a fixed comfortable 52dp tall.
 */
@Composable
fun MBButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MBButtonVariant = MBButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val interactive = enabled && !loading
    val shape = RoundedCornerShape(16.dp)

    val (background, contentColor, border) = when (variant) {
        MBButtonVariant.Primary -> Triple(
            Brush.horizontalGradient(listOf(Emerald, Teal)),
            Color(0xFF04281B),
            null,
        )

        MBButtonVariant.Tonal -> Triple(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ),
            MaterialTheme.colorScheme.onSurface,
            null,
        )

        MBButtonVariant.Outline -> Triple(
            null,
            MaterialTheme.colorScheme.onSurface,
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        )

        MBButtonVariant.Ghost -> Triple(
            null,
            MaterialTheme.colorScheme.primary,
            null,
        )

        MBButtonVariant.Danger -> Triple(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.errorContainer,
                ),
            ),
            MaterialTheme.colorScheme.onErrorContainer,
            null,
        )
    }

    Box(
        modifier = modifier
            .pressScale(interaction)
            .alpha(if (interactive) 1f else 0.55f)
            .clip(shape)
            .let { m -> background?.let { m.background(it) } ?: m }
            .let { m -> border?.let { m.border(it, shape) } ?: m }
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = interactive,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}
