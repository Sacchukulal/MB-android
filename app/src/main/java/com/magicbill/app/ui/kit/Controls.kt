package com.magicbill.app.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.components.pressScale
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.IconSize
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import com.magicbill.app.ui.theme.Target
import com.magicbill.app.ui.theme.Teal

/*
 * Controls in the 2.x language: the emerald→teal gradient primary with a press squish, quiet
 * tonal slabs for everything else, pill chips that fill primary when chosen, filled borderless
 * text fields that grow a primary ring on focus. No outlines, no boxes.
 */

/** The one button that does the thing on this screen — the gradient, 52dp, press squish. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val on = enabled && !busy
    val shape = RoundedCornerShape(16.dp)
    val content = com.magicbill.app.ui.theme.DarkOnPrimary // ink on the gradient, both themes — the old MBButton's exact colour
    Box(
        modifier.pressScale(interaction).alpha(if (on) 1f else 0.55f).clip(shape)
            .background(Brush.horizontalGradient(listOf(Emerald, Teal)))
            .clickable(interactionSource = interaction, indication = ripple(), enabled = on, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp)); HGap(9.dp) }
                Text(text, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

/** A quiet tonal slab. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.pressScale(interaction).alpha(if (enabled) 1f else 0.55f).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp)); HGap(9.dp) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A ghost: just words in the accent. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tone: Tone = Tone.Quiet) {
    val color = if (tone == Tone.Quiet) MaterialTheme.colorScheme.primary else tone.color()
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).defaultMinSize(minHeight = Target.small).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = color) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(interaction).alpha(if (enabled) 1f else 0.55f).clip(RoundedCornerShape(16.dp))
            .background(Mb.colors.dangerSoft)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Mb.colors.danger) }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Target.min)) {
        Icon(icon, contentDescription = description, tint = tint ?: MaterialTheme.colorScheme.onSurface)
    }
}

/** One pill of a chip row: chosen fills primary, the rest are quiet tonal pills. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "chipBg")
    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "chipFg")
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = modifier.clip(RoundedCornerShape(percent = 50)).background(bg)
            .clickable { if (!selected) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }; onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
fun ChipRow(options: List<String>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> Chip(o, o == selected, { onSelect(o) }) }
    }
}

/** A labelled switch on a row — an owner's one-time setting. */
@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListRow(title, subtitle, onClick = if (enabled) ({ onChange(!checked) }) else null, trailing = {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary))
    })
}

/** Filled, borderless field: quiet label above, tonal slab, primary ring on focus. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    error: String? = null,
    secret: Boolean = false,
    enabled: Boolean = true,
    capitalise: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    val labelColor by animateColorAsState(if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, label = "fieldLabel")
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)) } },
            singleLine = singleLine,
            isError = error != null,
            visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime, capitalization = if (capitalise) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            trailingIcon = when {
                secret -> ({
                    IconButton(onClick = { shown = !shown }) {
                        Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (shown) "Hide" else "Show", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
                trailing != null -> trailing
                else -> null
            },
            shape = MaterialTheme.shapes.large,
            colors = mbFieldColors(),
        )
    }
}

@Composable
private fun mbFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    errorTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

/** A find box: a quiet tonal pill. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = if (value.isNotEmpty()) ({ IconAction(Icons.Outlined.Close, "Clear", { onValueChange("") }, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(percent = 50),
        colors = mbFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/** − 2 + : both targets full size, on a quiet slab. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).height(Target.min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinus, enabled = enabled, modifier = Modifier.size(Target.min)) { Icon(Icons.Outlined.Remove, contentDescription = "Less", tint = MaterialTheme.colorScheme.onSurface) }
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.defaultMinSize(minWidth = 28.dp), textAlign = TextAlign.Center)
        IconButton(onClick = onPlus, enabled = enabled, modifier = Modifier.size(Target.min)) { Icon(Icons.Outlined.Add, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface) }
    }
}

/** Four slabs for a PIN, typed on the number pad. The digits are never shown. */
@Composable
fun PinField(pin: String, onChange: (String) -> Unit, length: Int = 4, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.field)) {
            repeat(length) { i ->
                val active = i == pin.length
                Box(
                    Modifier.size(Target.min, Target.min + 8.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(if (active) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (i < pin.length) Box(Modifier.size(12.dp).clip(RoundedCornerShape(percent = 50)).background(MaterialTheme.colorScheme.onSurface))
                }
            }
        }
        BasicTextField(
            value = pin,
            onValueChange = { v -> if (v.length <= length && v.all { it.isDigit() }) { onChange(v); if (v.length == length) onDone?.invoke() } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().height(Target.min + 8.dp).alpha(0.01f),
        )
    }
}

/** A figure on the canvas: quiet label over a big number. No tile, no box. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, tone: Tone? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VGap(2.dp)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = tone?.color() ?: MaterialTheme.colorScheme.onSurface)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** ONE stacked bar with 2dp gaps, animated — the old payment split. */
@Composable
fun SplitBar(parts: List<Triple<String, Long, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    val weights = parts.map { (_, v, _) ->
        val w by animateFloatAsState(v.toFloat() / total, tween(MBMotion.DurLong, easing = MBMotion.EaseOut), label = "split")
        w
    }
    Row(
        modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(percent = 50)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val any = weights.any { it > 0.001f }
        if (any) {
            parts.forEachIndexed { i, (_, _, c) ->
                if (weights[i] > 0.001f) Box(Modifier.weight(weights[i]).fillMaxHeight().background(c))
            }
        } else {
            Box(Modifier.fillMaxWidth().fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}

/** Legend: identity is dot + text, never colour alone; the amount sits right. */
@Composable
fun Legend(name: String, color: Color, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(percent = 50)).background(color))
        HGap(10.dp)
        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun Busy(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Space.s5), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(IconSize.lg), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
    }
}

@Composable
fun androidx.compose.foundation.layout.RowScope.Fill() = Box(Modifier.weight(1f))

@Composable
fun WidthSpacer() = Box(Modifier.size(Space.s2))
