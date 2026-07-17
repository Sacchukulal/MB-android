package com.magicbill.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.theme.MBMotion

/**
 * 4-digit PIN entry: four rounded cells that fill with dots as digits arrive.
 * A hidden text field drives the numeric keyboard; each cell pops on fill.
 */
@Composable
fun PinInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    length: Int = 4,
) {
    BasicTextField(
        value = value,
        onValueChange = { new ->
            onValueChange(new.filter { it.isDigit() }.take(length))
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier,
        decorationBox = { _ ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                repeat(length) { i ->
                    val filled = i < value.length
                    val active = i == value.length
                    val borderColor by animateColorAsState(
                        when {
                            isError -> MaterialTheme.colorScheme.error
                            active -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        },
                        label = "pinBorder",
                    )
                    val dotScale by animateFloatAsState(
                        if (filled) 1f else 0f, MBMotion.bouncy(), label = "pinDot",
                    )
                    Box(
                        Modifier
                            .size(58.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(16.dp),
                            )
                            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .scale(dotScale)
                                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                        )
                    }
                }
            }
        },
    )
}
