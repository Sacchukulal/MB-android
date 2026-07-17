package com.magicbill.app.ui.screens.owner

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.PermissionMap
import com.magicbill.app.core.StaffRow
import com.magicbill.app.core.billTime
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.components.MBBadge
import com.magicbill.app.ui.components.MBBadgeStatus
import com.magicbill.app.ui.components.MBBottomSheet
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.MBEmptyState
import com.magicbill.app.ui.components.MBSnackbarHost
import com.magicbill.app.ui.components.MBSnackbarKind
import com.magicbill.app.ui.components.MBTextField
import com.magicbill.app.ui.components.PermissionEditor
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.showMBSnackbar
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.Teal

/**
 * Staff management: the restaurant code (staff's login key) up top, then the
 * team. Everything mutates through the staff-manage Edge Function and takes
 * effect immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    owner: MBSession.Owner,
    viewModel: StaffViewModel = hiltViewModel(),
) {
    val licenseKey = owner.active?.licenseKey ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var sheetMode by remember { mutableStateOf<SheetMode?>(null) }
    var revealPin by remember { mutableStateOf<Pair<String, String>?>(null) } // name → pin
    var confirmRemove by remember { mutableStateOf<StaffRow?>(null) }
    var confirmResetPin by remember { mutableStateOf<StaffRow?>(null) }

    LaunchedEffect(licenseKey) { viewModel.load(licenseKey) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StaffEvent.PinCreated -> {
                    sheetMode = null
                    revealPin = event.staffName to event.pin
                }
                is StaffEvent.PinReset -> revealPin = event.staffName to event.pin
                is StaffEvent.Error -> snackbar.showMBSnackbar(event.message, MBSnackbarKind.Error)
                is StaffEvent.Saved -> {
                    sheetMode = null
                    snackbar.showMBSnackbar(event.message, MBSnackbarKind.Success)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.data != null,
            onRefresh = { viewModel.load(licenseKey, force = true) },
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                item {
                    Spacer(Modifier.statusBarsPadding().height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Staff", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.weight(1f))
                        MBButton(
                            "Add staff",
                            onClick = { sheetMode = SheetMode.Create },
                            variant = MBButtonVariant.Tonal,
                            leadingIcon = Icons.Filled.Add,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }

                val data = state.data
                when {
                    data == null && state.refreshing -> item { SkeletonScreen() }

                    data == null && state.error != null -> item {
                        MBErrorState(state.error!!, onRetry = { viewModel.load(licenseKey, force = true) })
                    }

                    data != null -> {
                        item {
                            RestaurantCodeHero(
                                code = data.restaurantCode,
                                onCopy = { code ->
                                    clipboard.setText(AnnotatedString(code))
                                },
                                onShare = { code ->
                                    val text = "Sign in to Magic Bill staff app with restaurant code $code. " +
                                        "Your manager will give you your PIN."
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            },
                                            "Share restaurant code",
                                        ),
                                    )
                                },
                            )
                            SectionHeader("Team · ${data.staff.size}")
                        }

                        if (data.staff.isEmpty()) {
                            item {
                                MBEmptyState(
                                    title = "No staff yet",
                                    subtitle = "Add your first staff member — waiters, managers, family. " +
                                        "You choose exactly what each person can see.",
                                )
                            }
                        }

                        items(data.staff.size, key = { data.staff[it].id }) { i ->
                            val staff = data.staff[i]
                            StaffRowItem(staff) { sheetMode = SheetMode.Edit(staff) }
                        }
                    }
                }

                item { Spacer(Modifier.height(130.dp)) }
            }
        }

        MBSnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )
    }

    // ---- Add / Edit sheet ----
    sheetMode?.let { mode ->
        StaffSheet(
            mode = mode,
            busy = busy,
            suggestPin = viewModel::generatePin,
            onDismiss = { sheetMode = null },
            onCreate = { name, role, pin, perms ->
                viewModel.create(licenseKey, name, role, pin, perms)
            },
            onUpdate = { staff, name, role, perms ->
                viewModel.update(licenseKey, staff, name, role, perms)
            },
            onResetPin = { staff -> confirmResetPin = staff },
            onToggleActive = { staff ->
                viewModel.setActive(licenseKey, staff, !staff.is_active)
            },
            onRemove = { staff -> confirmRemove = staff },
        )
    }

    // ---- PIN reveal (shown ONCE) ----
    revealPin?.let { (name, pin) ->
        AlertDialog(
            onDismissRequest = { revealPin = null },
            title = { Text("PIN for $name") },
            text = {
                Column {
                    Text(
                        pin,
                        style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 8.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Write this down and give it to $name. You won't be able to see it " +
                            "again, but you can reset it anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { revealPin = null }) { Text("Done") }
            },
        )
    }

    // ---- Confirm: remove ----
    confirmRemove?.let { staff ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${staff.name}?") },
            text = { Text("This permanently deletes their access. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        sheetMode = null
                        viewModel.remove(licenseKey, staff)
                    },
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text("Cancel") }
            },
        )
    }

    // ---- Confirm: reset PIN ----
    confirmResetPin?.let { staff ->
        AlertDialog(
            onDismissRequest = { confirmResetPin = null },
            title = { Text("Reset PIN for ${staff.name}?") },
            text = { Text("A new 4-digit PIN will be generated. Their old PIN stops working immediately.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResetPin = null
                        viewModel.resetPin(licenseKey, staff, viewModel.generatePin())
                    },
                ) { Text("Reset PIN") }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetPin = null }) { Text("Cancel") }
            },
        )
    }
}

private sealed interface SheetMode {
    data object Create : SheetMode
    data class Edit(val staff: StaffRow) : SheetMode
}

@Composable
private fun RestaurantCodeHero(
    code: String?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Emerald.copy(alpha = 0.16f), Teal.copy(alpha = 0.10f)),
                ),
                RoundedCornerShape(20.dp),
            )
            .padding(20.dp),
    ) {
        Text(
            "RESTAURANT CODE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                code ?: "—",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            if (code != null) {
                IconButton(onClick = { onCopy(code) }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { onShare(code) }) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = "Share code",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Staff sign in with this code and their personal PIN.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StaffRowItem(staff: StaffRow, onClick: () -> Unit) {
    com.magicbill.app.ui.components.ListRow(
        title = staff.name,
        subtitle = buildString {
            append(staff.role_label.ifBlank { "Staff" })
            staff.last_login?.let { append(" · last login ${billTime(it)}") }
        },
        onClick = onClick,
        icon = null,
        trailing = {
            MBBadge(
                if (staff.is_active) "Active" else "Off",
                if (staff.is_active) MBBadgeStatus.Active else MBBadgeStatus.Neutral,
            )
        },
        modifier = Modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffSheet(
    mode: SheetMode,
    busy: Boolean,
    suggestPin: () -> String,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, PermissionMap) -> Unit,
    onUpdate: (StaffRow, String, String, PermissionMap) -> Unit,
    onResetPin: (StaffRow) -> Unit,
    onToggleActive: (StaffRow) -> Unit,
    onRemove: (StaffRow) -> Unit,
) {
    val editing = (mode as? SheetMode.Edit)?.staff

    var name by remember(mode) { mutableStateOf(editing?.name ?: "") }
    var role by remember(mode) { mutableStateOf(editing?.role_label ?: "") }
    var pin by remember(mode) { mutableStateOf(if (editing == null) suggestPin() else "") }
    var permissions by remember(mode) {
        mutableStateOf<PermissionMap>(editing?.permissions ?: mapOf("view_dashboard" to true))
    }
    var nameError by remember(mode) { mutableStateOf<String?>(null) }
    var pinError by remember(mode) { mutableStateOf<String?>(null) }

    MBBottomSheet(
        onDismissRequest = onDismiss,
        title = if (editing == null) "Add staff" else "Edit ${editing.name}",
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MBTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = "Name",
                placeholder = "e.g. Ramesh",
                errorText = nameError,
                modifier = Modifier.fillMaxWidth(),
            )
            MBTextField(
                value = role,
                onValueChange = { role = it },
                label = "Role label",
                placeholder = "Waiter, Manager, Wife, Brother — anything",
                modifier = Modifier.fillMaxWidth(),
            )

            if (editing == null) {
                MBTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(4); pinError = null },
                    label = "4-digit PIN",
                    supportingText = "Auto-suggested — change it if you like. Shown once after saving.",
                    errorText = pinError,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PermissionEditor(
                permissions = permissions,
                onChange = { permissions = it },
            )

            MBButton(
                if (editing == null) "Create staff" else "Save changes",
                loading = busy,
                onClick = {
                    when {
                        name.isBlank() -> nameError = "Enter a name"
                        editing == null && pin.length != 4 -> pinError = "PIN must be 4 digits"
                        editing == null -> onCreate(name, role, pin, permissions)
                        else -> onUpdate(editing, name, role, permissions)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (editing != null) {
                Spacer(Modifier.height(2.dp))
                MBButton(
                    "Reset PIN",
                    variant = MBButtonVariant.Tonal,
                    onClick = { onResetPin(editing) },
                    modifier = Modifier.fillMaxWidth(),
                )
                MBButton(
                    if (editing.is_active) "Deactivate" else "Activate",
                    variant = MBButtonVariant.Outline,
                    onClick = { onToggleActive(editing) },
                    modifier = Modifier.fillMaxWidth(),
                )
                MBButton(
                    "Remove permanently",
                    variant = MBButtonVariant.Danger,
                    onClick = { onRemove(editing) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
