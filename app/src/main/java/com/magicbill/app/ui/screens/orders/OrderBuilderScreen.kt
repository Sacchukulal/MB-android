package com.magicbill.app.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.formatINR
import com.magicbill.app.data.orders.MenuItem
import com.magicbill.app.data.orders.OrderLine
import com.magicbill.app.data.orders.PosStatus
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.MBBottomSheet
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.MBEmptyState
import com.magicbill.app.ui.components.MBTextField
import com.magicbill.app.ui.components.ScreenHeader
import com.magicbill.app.ui.components.SegmentedChips
import kotlinx.coroutines.delay

/**
 * Build a new order (or add items to an open one) and send it to the
 * counter. The draft lives only on this phone until Send; a rejection keeps
 * every line so nothing typed is ever lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderBuilderScreen(
    orderClientUuid: String?,
    orderType: String,
    tableNumber: String,
    section: String,
    onBack: () -> Unit,
    viewModel: OrderBuilderViewModel = hiltViewModel(),
) {
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val sendState by viewModel.sendState.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LifecycleResumeEffect(Unit) {
        viewModel.connect()
        viewModel.ensureLoaded()
        onPauseOrDispose { viewModel.disconnect() }
    }

    // Existing order (add-items mode) — resolve its display facts.
    val existing = orderClientUuid?.let { uuid ->
        ordersState.data?.orders?.firstOrNull { it.clientUuid == uuid }
    }
    val effectiveType = existing?.orderType ?: orderType
    val effectiveTable = existing?.tableNumber ?: tableNumber
    val title = when {
        effectiveType == "Table" && effectiveTable.isNotEmpty() -> "Table $effectiveTable"
        else -> effectiveType
    }

    var confirmOpen by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<OrderLine?>(null) }

    // Success: brief confirmation, then pop back to the live grid.
    LaunchedEffect(sendState) {
        if (sendState is SendState.Done) {
            confirmOpen = false
            delay(1_200)
            onBack()
        }
    }

    val data = ordersState.data
    val categories = data?.categories.orEmpty()
    val menuItems = data?.items.orEmpty()

    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val chipLabels = listOf("All") + categories.map { it.name }
    val selectedCategory = categories.getOrNull(categoryIndex - 1)
    val visibleItems = menuItems
        .filter { selectedCategory == null || it.categoryLocalId == selectedCategory.localId }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

    // §4.3 — a waiter is stopped only by something we actually KNOW. When we
    // could not check, let them try: the server decides in about a third of a
    // second and refuses with clear wording if the counter really is down.
    // Blocking on a status we are unsure of refuses real orders for nothing.
    val posStatus = ordersState.posStatus
    val counterDown = posStatus == PosStatus.Offline
    val canSend = draft.isNotEmpty() && online && !counterDown && sendState !is SendState.Sending
    val sendBlockedReason = when {
        !online -> "No internet"
        counterDown -> "Counter is offline — ask at the billing desk"
        else -> null
    }

    GlowBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                ScreenHeader(
                    title = title,
                    subtitle = if (existing != null) "Add items" else "New order",
                    onBack = onBack,
                    trailing = {
                        StatusChip(online = online, posStatus = posStatus, gate = ordersState.gate)
                    },
                )
                MBTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "",
                    placeholder = "Search menu…",
                    leadingIcon = Icons.Outlined.Search,
                )
                if (chipLabels.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    SegmentedChips(
                        options = chipLabels,
                        selectedIndex = categoryIndex.coerceIn(0, chipLabels.lastIndex),
                        onSelect = { categoryIndex = it },
                    )
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp,
                ),
            ) {
                if (visibleItems.isEmpty()) {
                    item {
                        Spacer(Modifier.height(30.dp))
                        MBEmptyState(
                            title = if (menuItems.isEmpty()) "Menu not synced yet" else "No matches",
                            subtitle = if (menuItems.isEmpty()) {
                                "The counter pushes the menu automatically — pull to refresh in a moment."
                            } else {
                                "Try a different search or category."
                            },
                        )
                    }
                }
                items(visibleItems, key = { it.localId }) { item ->
                    MenuItemRow(
                        item = item,
                        quantity = draft.filter { it.localId == item.localId }.sumOf { it.quantity },
                        onAdd = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.add(item)
                        },
                        onRemove = {
                            draft.lastOrNull { it.localId == item.localId }
                                ?.let { viewModel.decrement(it) }
                        },
                    )
                }
            }

            // Bottom bar: count + total + the one primary action.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
            ) {
                if (sendBlockedReason != null && draft.isNotEmpty()) {
                    Text(
                        sendBlockedReason,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${draft.sumOf { it.quantity }} items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatINR(draft.sumOf { it.price * it.quantity }, decimals = 0),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    MBButton(
                        text = when {
                            existing != null -> "Send ${draft.sumOf { it.quantity }} new items"
                            else -> "Send to kitchen"
                        },
                        onClick = { confirmOpen = true },
                        enabled = canSend,
                        loading = sendState is SendState.Sending,
                    )
                }
            }
        }
    }

    // Confirm sheet: exactly what will print, with per-line notes.
    if (confirmOpen) {
        MBBottomSheet(
            onDismissRequest = { if (sendState !is SendState.Sending) confirmOpen = false },
            title = if (existing != null) "Send new items?" else "Send to kitchen?",
        ) {
            when (val s = sendState) {
                is SendState.Sending -> SendProgress("Sending to the counter…")
                is SendState.Done -> SendProgress(
                    if (s.printerWarning) "Saved — printer problem at the counter" else "Printed at the counter ✓",
                )
                else -> {
                    draft.forEach { line ->
                        DraftLineRow(
                            line = line,
                            onNote = { noteTarget = line },
                            onInc = { viewModel.increment(line) },
                            onDec = { viewModel.decrement(line) },
                        )
                    }
                    (sendState as? SendState.Failed)?.let { failed ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            failed.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    MBButton(
                        text = "This will print at the counter — Send",
                        onClick = {
                            viewModel.dismissSendError()
                            viewModel.send(orderClientUuid, effectiveType, effectiveTable, existing?.section ?: section)
                        },
                        enabled = canSend,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    MBButton(
                        text = "Keep editing",
                        onClick = { confirmOpen = false },
                        variant = MBButtonVariant.Ghost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // Per-line note ("no onion") — identity-aware, merges duplicates.
    noteTarget?.let { line ->
        var note by remember(line) { mutableStateOf(line.note ?: "") }
        MBBottomSheet(onDismissRequest = { noteTarget = null }, title = line.name) {
            MBTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = "Note for the kitchen",
                placeholder = "e.g. no onion, extra spicy",
            )
            Spacer(Modifier.height(14.dp))
            MBButton(
                text = "Save note",
                onClick = {
                    viewModel.setNote(line, note)
                    noteTarget = null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------------- pieces ----------------

@Composable
private fun MenuItemRow(
    item: MenuItem,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isAvailable, onClick = onAdd)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isAvailable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (item.isAvailable) formatINR(item.price, decimals = 0)
                else "${formatINR(item.price, decimals = 0)} · Unavailable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (quantity > 0) {
            Stepper(quantity = quantity, onInc = onAdd, onDec = onRemove)
        } else if (item.isAvailable) {
            RoundIcon(Icons.Outlined.Add, onClick = onAdd)
        }
    }
}

@Composable
private fun DraftLineRow(
    line: OrderLine,
    onNote: () -> Unit,
    onInc: () -> Unit,
    onDec: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                line.note?.let { "“$it”" } ?: formatINR(line.price, decimals = 0),
                style = MaterialTheme.typography.labelMedium,
                color = if (line.note != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RoundIcon(Icons.Outlined.EditNote, onClick = onNote)
        Spacer(Modifier.size(8.dp))
        Stepper(quantity = line.quantity, onInc = onInc, onDec = onDec)
    }
}

@Composable
private fun Stepper(quantity: Int, onInc: () -> Unit, onDec: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundIcon(Icons.Outlined.Remove, onClick = onDec)
        Text(
            "$quantity",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        RoundIcon(Icons.Outlined.Add, onClick = onInc)
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SendProgress(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
