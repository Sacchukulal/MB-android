package com.magicbill.app.ui.components

import androidx.compose.runtime.Composable
import com.magicbill.app.core.LocalPermissions
import com.magicbill.app.core.PermissionKey

/**
 * Renders [content] only when the current session has [permission].
 * Optional [fallback] renders otherwise (defaults to nothing).
 *
 * Client-side gate only — every privileged call is re-checked server-side.
 */
@Composable
fun MBPermissionGate(
    permission: PermissionKey,
    fallback: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (LocalPermissions.current.contains(permission.key)) {
        content()
    } else {
        fallback?.invoke()
    }
}
