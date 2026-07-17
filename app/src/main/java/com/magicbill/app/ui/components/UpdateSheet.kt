package com.magicbill.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magicbill.app.data.UpdateUiState
import kotlin.math.roundToInt

/**
 * The update flow UI: version + notes, Update Now with a live progress bar
 * (DownloadManager), then Android's one required install confirmation.
 * Dismissible — it stays quiet for 24h and lights the Account-tab dot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    state: UpdateUiState,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    val info = state.available ?: return

    if (state.needsInstallPermission) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("One-time permission needed") },
            text = {
                Text(
                    "To install updates, please allow \"Install unknown apps\" for " +
                        "Magic Bill in the next screen, then come back and tap Update again.",
                )
            },
            confirmButton = {
                TextButton(onClick = onOpenInstallSettings) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        )
        return
    }

    MBBottomSheet(
        onDismissRequest = { if (!state.downloading) onDismiss() },
        title = "Update available",
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(bottom = 20.dp)) {
            Row {
                MBBadge("v${info.version.removePrefix("v")}", MBBadgeStatus.Trial)
                Spacer(Modifier.width(8.dp))
                MBBadge("Free update", MBBadgeStatus.Active)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                info.release_notes?.takeIf { it.isNotBlank() }
                    ?: "Bug fixes and improvements.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Downloading… ${(state.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MBButton(
                    "Update now",
                    onClick = onUpdateNow,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                MBButton(
                    "Not now",
                    variant = MBButtonVariant.Ghost,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
