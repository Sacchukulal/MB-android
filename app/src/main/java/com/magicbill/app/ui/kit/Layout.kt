package com.magicbill.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Radius
import com.magicbill.app.ui.theme.Space

/*
 * The kit, in the one language: OPEN CANVAS. Sections separate with typography and whitespace,
 * never boxes; lists are borderless rows that breathe via padding; the glow behind the screen
 * gives the depth. No cards-in-cards, no dividers, no borders.
 */

/** A screen. Large-title header (typography, not an app bar), content on the canvas. */
@Composable
fun Page(
    title: String?,
    subtitle: String? = null,
    back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    bottomPadding: androidx.compose.ui.unit.Dp = Space.s7,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (title != null) {
            PageHeader(title, subtitle, back, actions)
        } else {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
        }
        val body = Modifier.fillMaxWidth().let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
        Column(body.padding(start = Gap.page, end = Gap.page, bottom = bottomPadding)) { content() }
    }
}

/** Large title + optional circular raised back button. */
@Composable
fun PageHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Gap.page, end = Gap.page, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (back != null) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Mb.colors.raisedHigh).clickable(onClick = back),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Mb.colors.ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = Mb.type.page, color = Mb.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = Mb.type.caption, color = Mb.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
    }
}

/** Overline section label — sections separate by typography and space, never by boxes. */
@Composable
fun Section(title: String, trailing: (@Composable () -> Unit)? = null, first: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 8.dp else 28.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = Mb.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), color = Mb.colors.inkMuted)
        trailing?.invoke()
    }
}

/** Open canvas: a Panel is just a breathing group — NO box, no border, no shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(vertical = Space.s2), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth()
        .clip(RoundedCornerShape(Radius.lg))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(padding)
    Column(m) { content() }
}

/** A row of "label … value", the value tabular so a column of them lines up. */
@Composable
fun KeyValue(label: String, value: String, valueColor: Color? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Mb.type.body, color = Mb.colors.inkMuted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (bold) Mb.type.cell.copy(fontWeight = FontWeight.SemiBold) else Mb.type.cell,
            color = valueColor ?: Mb.colors.ink,
            textAlign = TextAlign.End,
        )
    }
}

/** Borderless list row — ripple on tap, breathes via padding. No card, no divider. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Mb.type.body, color = titleColor ?: Mb.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = Mb.type.caption, color = Mb.colors.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A tinted icon disc for a ListRow's leading slot. */
@Composable
fun IconDisc(icon: ImageVector, tint: Color = Mb.colors.accent) {
    Box(Modifier.size(42.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Rows breathe via padding — there are no dividers in this design. */
@Composable
fun RowLine() {
}

@Composable
fun VGap(space: androidx.compose.ui.unit.Dp = Gap.field) = Spacer(Modifier.height(space))

@Composable
fun HGap(space: androidx.compose.ui.unit.Dp = Gap.inline) = Spacer(Modifier.width(space))

/** An empty shop must still look right: quiet icon, one sentence, centred. */
@Composable
fun Empty(sentence: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = Mb.colors.inkFaint)
        Spacer(Modifier.height(16.dp))
        Text(sentence, style = Mb.type.body, color = Mb.colors.inkMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

enum class Tone { Ok, Warn, Danger, Info, Quiet }

@Composable
fun Tone.color(): Color = when (this) {
    Tone.Ok -> Mb.colors.ok
    Tone.Warn -> Mb.colors.warn
    Tone.Danger -> Mb.colors.danger
    Tone.Info -> Mb.colors.info
    Tone.Quiet -> Mb.colors.inkMuted
}

@Composable
fun Tone.soft(): Color = when (this) {
    Tone.Ok -> Mb.colors.okSoft
    Tone.Warn -> Mb.colors.warnSoft
    Tone.Danger -> Mb.colors.dangerSoft
    Tone.Info -> Mb.colors.infoSoft
    Tone.Quiet -> Mb.colors.raisedHigh
}

fun Tone.icon(): ImageVector = when (this) {
    Tone.Ok -> Icons.Outlined.CheckCircle
    Tone.Warn -> Icons.Outlined.WarningAmber
    Tone.Danger -> Icons.Outlined.ErrorOutline
    Tone.Info -> Icons.Outlined.Info
    Tone.Quiet -> Icons.Outlined.Info
}

/**
 * A state a screen is in — an order the counter has finished with, a phone that was removed —
 * as one quiet line: icon + words in the tone, a faint tint behind. Never for a passing
 * sentence; those go through the reporter and leave by themselves.
 */
@Composable
fun Notice(tone: Tone, sentence: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(tone.soft().copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gap.inline),
    ) {
        Icon(tone.icon(), contentDescription = null, tint = tone.color(), modifier = Modifier.size(18.dp))
        Text(sentence, style = Mb.type.caption, color = Mb.colors.ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Status pill with a leading dot — `● Live`. */
@Composable
fun Badge(text: String, tone: Tone = Tone.Quiet) {
    Row(
        Modifier.background(tone.soft(), RoundedCornerShape(percent = 50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(tone.color(), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Mb.type.label, color = if (tone == Tone.Quiet) Mb.colors.inkMuted else tone.color())
    }
}
