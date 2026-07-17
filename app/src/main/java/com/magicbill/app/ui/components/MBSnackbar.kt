package com.magicbill.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.magicbill.app.ui.theme.LocalMBDarkTheme

enum class MBSnackbarKind { Info, Success, Error }

private class MBSnackbarVisuals(
    override val message: String,
    val kind: MBSnackbarKind,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val withDismissAction: Boolean = false,
) : SnackbarVisuals

suspend fun SnackbarHostState.showMBSnackbar(
    message: String,
    kind: MBSnackbarKind = MBSnackbarKind.Info,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
) = showSnackbar(MBSnackbarVisuals(message, kind, actionLabel, duration))

/** Snackbar host whose container color reflects success/error semantics. */
@Composable
fun MBSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val dark = LocalMBDarkTheme.current
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        val kind = (data.visuals as? MBSnackbarVisuals)?.kind ?: MBSnackbarKind.Info
        val (container, content) = when (kind) {
            MBSnackbarKind.Success ->
                if (dark) Color(0xFF065F46) to Color(0xFFD1FAE5)
                else Color(0xFF047857) to Color(0xFFFFFFFF)

            MBSnackbarKind.Error ->
                if (dark) Color(0xFF7F1D1D) to Color(0xFFFEE2E2)
                else Color(0xFFB91C1C) to Color(0xFFFFFFFF)

            MBSnackbarKind.Info ->
                MaterialTheme.colorScheme.inverseSurface to MaterialTheme.colorScheme.inverseOnSurface
        }
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = container,
            contentColor = content,
            actionColor = content,
        )
    }
}
