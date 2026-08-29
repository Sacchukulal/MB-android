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
import androidx.compose.foundation.shape.CircleShape
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
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.IconSize
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Radius
import com.magicbill.app.ui.theme.Space
import com.magicbill.app.ui.theme.Target

/*
 * Controls, in the one language: the accent gradient primary with a press squish, quiet raised
 * slabs for everything else, pill chips that fill the accent when chosen, filled borderless
 * text fields that grow an accent ring on focus. No outlines, no boxes. Every colour and every
 * size comes from the theme — nothing here is spelled by number.
 */

/** The one button that does the thing on this screen — the gradient, 52dp, press squish. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val on = enabled && !busy
    val shape = RoundedCornerShape(Radius.lg)
    val c = Mb.colors
    Box(
        modifier.pressScale(interaction).alpha(if (on) 1f else 0.55f).clip(shape)
            .background(Brush.horizontalGradient(listOf(c.accent, c.accent2)))
            .clickable(interactionSource = interaction, indication = ripple(), enabled = on, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.onAccent, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(19.dp)); HGap(9.dp) }
                Text(text, style = Mb.type.button, color = c.onAccent)
            }
        }
    }
}

/** A quiet raised slab. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val c = Mb.colors
    Box(
        modifier.pressScale(interaction).alpha(if (enabled) 1f else 0.55f).clip(RoundedCornerShape(Radius.lg))
            .background(c.raisedHigh)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(19.dp)); HGap(9.dp) }
            Text(text, style = Mb.type.button, color = c.ink)
        }
    }
}

/** A ghost: just words in the accent. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tone: Tone = Tone.Quiet) {
    val color = if (tone == Tone.Quiet) Mb.colors.accent else tone.color()
    Box(
        modifier.clip(RoundedCornerShape(Radius.lg)).clickable(enabled = enabled, onClick = onClick).defaultMinSize(minHeight = Target.small).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Mb.type.button, color = color) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(interaction).alpha(if (enabled) 1f else 0.55f).clip(RoundedCornerShape(Radius.lg))
            .background(Mb.colors.dangerSoft)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Mb.type.button, color = Mb.colors.danger) }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color? = null, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(Target.min)) {
        Icon(icon, contentDescription = description, tint = tint ?: Mb.colors.ink)
    }
}

/** A round tonal button with one icon: the + and − on a dish, the back arrow. Squishes when pressed. */
@Composable
fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, filled: Boolean = false, size: androidx.compose.ui.unit.Dp = 44.dp) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val c = Mb.colors
    val bg by animateColorAsState(if (filled) c.accent else c.accent.copy(alpha = 0.14f), label = "roundBg")
    val fg by animateColorAsState(if (filled) c.onAccent else c.accent, label = "roundFg")
    Box(
        modifier.size(size).pressScale(interaction, 0.88f).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).background(bg)
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = fg, modifier = Modifier.size(IconSize.md)) }
}

/** One pill of a chip row: chosen fills the accent, the rest are quiet raised pills. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val c = Mb.colors
    val bg by animateColorAsState(if (selected) c.accent else c.raisedHigh, label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.inkMuted, label = "chipFg")
    Text(
        text,
        style = Mb.type.button,
        color = fg,
        modifier = modifier.clip(RoundedCornerShape(percent = 50)).background(bg)
            .clickable { if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
fun ChipRow(options: List<String>, selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        options.forEach { o -> Chip(o, o == selected, { onSelect(o) }) }
    }
}

/** A labelled switch on a row — an owner's one-time setting. */
@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListRow(title, subtitle, onClick = if (enabled) ({ onChange(!checked) }) else null, trailing = {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Mb.colors.accent, checkedThumbColor = Mb.colors.onAccent))
    })
}

/** Filled, borderless field: quiet label above, raised slab, accent ring on focus. */
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
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    var shown by remember { mutableStateOf(false) }
    val c = Mb.colors
    val labelColor by animateColorAsState(if (error != null) c.danger else c.inkMuted, label = "fieldLabel")
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Mb.type.label, color = labelColor, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            textStyle = textStyle ?: Mb.type.body,
            placeholder = placeholder?.let { { Text(it, style = textStyle ?: Mb.type.body, color = c.inkFaint) } },
            singleLine = singleLine,
            isError = error != null,
            visualTransformation = if (secret && !shown) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ime, capitalization = if (capitalise) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
            supportingText = error?.let { { Text(it, style = Mb.type.caption, color = c.danger) } },
            trailingIcon = when {
                secret -> ({
                    IconButton(onClick = { shown = !shown }) {
                        Icon(if (shown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (shown) "Hide" else "Show", tint = c.inkMuted)
                    }
                })
                trailing != null -> trailing
                else -> null
            },
            shape = RoundedCornerShape(Radius.lg),
            colors = mbFieldColors(),
        )
    }
}

@Composable
private fun mbFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Mb.colors.ink,
    unfocusedTextColor = Mb.colors.ink,
    errorTextColor = Mb.colors.ink,
    cursorColor = Mb.colors.accent,
    focusedBorderColor = Mb.colors.accent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = Mb.colors.danger,
    focusedContainerColor = Mb.colors.raised,
    unfocusedContainerColor = Mb.colors.raised,
    disabledContainerColor = Mb.colors.raised,
    errorContainerColor = Mb.colors.raised,
)

/** A find box: a quiet raised pill. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = Mb.type.body,
        placeholder = { Text(hint, style = Mb.type.body, color = Mb.colors.inkFaint) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Mb.colors.inkMuted) },
        trailingIcon = if (value.isNotEmpty()) ({ IconAction(Icons.Outlined.Close, "Clear", { onValueChange("") }, tint = Mb.colors.inkMuted) }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(percent = 50),
        colors = mbFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/** − 2 + : both targets full size, on a quiet slab; the figure ticks. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.clip(RoundedCornerShape(Radius.lg)).background(Mb.colors.raisedHigh).height(Target.min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMinus, enabled = enabled, modifier = Modifier.size(Target.min)) { Icon(Icons.Outlined.Remove, contentDescription = "Less", tint = Mb.colors.ink) }
        Box(Modifier.defaultMinSize(minWidth = 28.dp), contentAlignment = Alignment.Center) { Ticker(value) }
        IconButton(onClick = onPlus, enabled = enabled, modifier = Modifier.size(Target.min)) { Icon(Icons.Outlined.Add, contentDescription = "More", tint = Mb.colors.ink) }
    }
}

/** Four slabs for a PIN, typed on the number pad. The digits are never shown. */
@Composable
fun PinField(pin: String, onChange: (String) -> Unit, length: Int = 4, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val c = Mb.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.field)) {
            repeat(length) { i ->
                val active = i == pin.length
                Box(
                    Modifier.size(Target.min, Target.min + 8.dp).clip(RoundedCornerShape(14.dp))
                        .background(c.raisedHigh)
                        .then(if (active) Modifier.background(c.accent.copy(alpha = 0.12f)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (i < pin.length) Box(Modifier.size(12.dp).clip(CircleShape).background(c.ink))
                }
            }
        }
        BasicTextField(
            value = pin,
            onValueChange = { v -> if (v.length <= length && v.all { it.isDigit() }) { onChange(v); if (v.length == length) onDone?.invoke() } },
            // A number pad, not a password field: the digits are never drawn, and a password field
            // makes the phone offer to save a PIN to its password manager on every sign-in.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            textStyle = Mb.type.body.copy(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().height(Target.min + 8.dp).alpha(0.01f),
        )
    }
}

/** A figure on the canvas: quiet label over a big number. No tile, no box. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, tone: Tone? = null) {
    Column(modifier) {
        Text(label, style = Mb.type.label, color = Mb.colors.inkMuted)
        VGap(2.dp)
        Text(value, style = Mb.type.stat, color = tone?.color() ?: Mb.colors.ink)
        if (sub != null) Text(sub, style = Mb.type.caption, color = Mb.colors.inkMuted)
    }
}

/** ONE stacked bar with 2dp gaps, animated — the payment split. */
@Composable
fun SplitBar(parts: List<Triple<String, Long, Color>>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    val weights = parts.map { (_, v, _) ->
        val w by animateFloatAsState(v.toFloat() / total, tween(MBMotion.DurLong, easing = MBMotion.EaseOut), label = "split")
        w
    }
    Row(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(percent = 50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val any = weights.any { it > 0.001f }
        if (any) {
            parts.forEachIndexed { i, (_, _, colour) -> if (weights[i] > 0.001f) Box(Modifier.weight(weights[i]).fillMaxHeight().background(colour)) }
        } else {
            Box(Modifier.fillMaxWidth().fillMaxHeight().background(Mb.colors.raisedHigh))
        }
    }
}

/** Legend: identity is dot + text, never colour alone; the amount sits right. */
@Composable
fun Legend(name: String, color: Color, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        HGap(10.dp)
        Text(name, style = Mb.type.caption, color = Mb.colors.inkMuted, modifier = Modifier.weight(1f))
        Text(value, style = Mb.type.cell, color = Mb.colors.ink)
    }
}

@Composable
fun Busy(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(Space.s5), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(IconSize.lg), color = Mb.colors.accent, strokeWidth = 3.dp)
    }
}

@Composable
fun androidx.compose.foundation.layout.RowScope.Fill() = Box(Modifier.weight(1f))

@Composable
fun WidthSpacer() = Box(Modifier.size(Space.s2))

/**
 * A code somebody types off a screen — the pairing code, the shop code. The dash is put in for
 * them and the cursor stays at the END: a formatter that re-writes the text while the cursor
 * sits where it was turns "6SAJB8" into "6SA-B8J", one letter at a time.
 */
@Composable
fun CodeField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String? = null, onDone: (() -> Unit)? = null) {
    var field by remember(value) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Mb.type.label, color = Mb.colors.inkMuted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        OutlinedTextField(
            value = field,
            onValueChange = { typed ->
                onValueChange(typed.text)
                field = typed.copy(selection = androidx.compose.ui.text.TextRange(typed.text.length))
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = Mb.type.code,
            placeholder = placeholder?.let { { Text(it, style = Mb.type.code, color = Mb.colors.inkFaint) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go, capitalization = KeyboardCapitalization.Characters),
            keyboardActions = KeyboardActions(onGo = { onDone?.invoke() }, onDone = { onDone?.invoke() }),
            shape = RoundedCornerShape(Radius.lg),
            colors = mbFieldColors(),
        )
    }
}
