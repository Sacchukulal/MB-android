package com.magicbill.app.ui.screens.more

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.magicbill.app.BuildConfig
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Busy
import com.magicbill.app.ui.kit.HGap
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Radius
import com.magicbill.app.update.Updater
import kotlin.math.roundToInt

/**
 * The update, from the bottom: what is new, one button, a bar while it comes down, then
 * Android's own confirm. Every state says what is happening in one sentence and offers the
 * one thing to do next; nothing is forced, "Not now" is always there.
 */
@Composable
fun UpdateSheet(state: Updater.State, updater: Updater) {
    val c = Mb.colors
    val title = when (state) {
        is Updater.State.Available, is Updater.State.Downloading, is Updater.State.Ready -> "Update available"
        is Updater.State.UpToDate -> "Up to date"
        is Updater.State.Failed -> "Could not check"
        else -> "Checking for updates"
    }
    val busy = state is Updater.State.Downloading
    Sheet(title, onDismiss = { if (!busy) updater.dismiss() }) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            when (state) {
                Updater.State.Idle, Updater.State.Checking -> {
                    Busy()
                    Text("Asking GitHub for the newest build…", style = Mb.type.caption, color = c.inkMuted)
                }
                is Updater.State.UpToDate -> {
                    Notice(Tone.Ok, "You have the newest build, ${state.version}.")
                    VGap(Gap.group)
                    SecondaryButton("Done", updater::close, Modifier.fillMaxWidth())
                }
                is Updater.State.Failed -> {
                    Notice(Tone.Danger, state.says)
                    VGap(Gap.group)
                    PrimaryButton("Try again", { if (state.release != null) updater.download() else updater.checkNow() }, Modifier.fillMaxWidth())
                    VGap(Gap.inline)
                    QuietButton("Close", updater::close, Modifier.fillMaxWidth())
                }
                is Updater.State.Available -> {
                    WhatIsNew(state.release)
                    VGap(Gap.group)
                    PrimaryButton("Update now", updater::download, Modifier.fillMaxWidth())
                    VGap(Gap.inline)
                    QuietButton("Not now", updater::dismiss, Modifier.fillMaxWidth())
                }
                is Updater.State.Downloading -> {
                    WhatIsNew(state.release)
                    VGap(Gap.group)
                    val shown by animateFloatAsState(state.progress, label = "updateProgress")
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.pill)).background(c.raised)) {
                        Box(Modifier.fillMaxWidth(shown.coerceIn(0.02f, 1f)).height(6.dp).background(c.accent, RoundedCornerShape(Radius.pill)))
                    }
                    VGap(Gap.inline)
                    Text("Downloading… ${(state.progress * 100).roundToInt()}%", style = Mb.type.caption, color = c.inkMuted)
                    VGap(Gap.inline)
                    Text("Keep the app open. Android will ask once to confirm the install.", style = Mb.type.caption, color = c.inkFaint)
                }
                is Updater.State.Ready -> {
                    WhatIsNew(state.release)
                    VGap(Gap.group)
                    if (state.needsPermission) {
                        Notice(Tone.Warn, "Android needs a one-time switch: allow Magic Bill to install updates, then come back here.")
                        VGap(Gap.field)
                        PrimaryButton("Open settings", updater::openInstallSettings, Modifier.fillMaxWidth())
                    } else {
                        Notice(Tone.Ok, "Downloaded. Android will ask once to confirm the install.")
                        VGap(Gap.field)
                        PrimaryButton("Install", updater::install, Modifier.fillMaxWidth())
                    }
                    VGap(Gap.inline)
                    QuietButton("Not now", updater::dismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The version badges and the release notes, as the release wrote them. */
@Composable
private fun WhatIsNew(release: com.magicbill.app.update.Release) {
    val c = Mb.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge("v${release.name}", Tone.Info)
        HGap()
        Text("you have ${BuildConfig.VERSION_NAME}", style = Mb.type.caption, color = c.inkMuted)
        release.published?.takeIf { it.isNotBlank() }?.let {
            Text(" · $it", style = Mb.type.caption, color = c.inkFaint)
        }
    }
    VGap(Gap.field)
    Text("What's new", style = Mb.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.inkMuted)
    VGap(Gap.inline)
    Text(
        release.release_notes?.takeIf { it.isNotBlank() } ?: "Fixes and improvements.",
        style = Mb.type.body, color = c.ink,
        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
    )
}
