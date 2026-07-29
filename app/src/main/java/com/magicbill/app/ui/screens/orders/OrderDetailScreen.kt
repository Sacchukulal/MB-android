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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.LocalPermissions
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.formatINR
import com.magicbill.app.data.orders.CreditCustomer
import com.magicbill.app.data.orders.LiveOrder
import com.magicbill.app.data.orders.OrderLine
import com.magicbill.app.data.orders.PosStatus
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.ListRow
import com.magicbill.app.ui.components.MBBottomSheet
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.MBEmptyState
import com.magicbill.app.ui.components.MBSnackbarHost
import com.magicbill.app.ui.components.MBSnackbarKind
import com.magicbill.app.ui.components.MBTextField
import com.magicbill.app.ui.components.ScreenHeader
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.showMBSnackbar
import com.magicbill.app.ui.theme.LocalMBDarkTheme
import com.magicbill.app.ui.theme.PaymentColors
import kotlinx.coroutines.launch

/**
 * One live order: items (printed vs still-sending), totals, and every action
 * the caller's permissions allow. Counter-side changes stream in live; a
 * settle/cancel from either side swaps this to a closed summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    clientUuid: String,
    onAddItems: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val perms = LocalPermissions.current

    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.connect()
        onPauseOrDispose { viewModel.disconnect() }
    }
    LaunchedEffect(clientUuid) { viewModel.bind(clientUuid) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OrderActionEvent.Done ->
                    scope.launch { snackbar.showMBSnackbar(event.message, MBSnackbarKind.Success) }
                is OrderActionEvent.Error ->
                    scope.launch { snackbar.showMBSnackbar(event.message, MBSnackbarKind.Error) }
            }
        }
    }

    var removeSheet by remember { mutableStateOf(false) }
    var settleSheet by remember { mutableStateOf(false) }
    var cancelDialog by remember { mutableStateOf(false) }

    val order = ui.order
    // Same rule as the builder (§4.3): only a counter we KNOW is down stops
    // an action. "We could not check" is not a reason to refuse a waiter.
    val canAct = online && ordersState.posStatus != PosStatus.Offline && !ui.busy

    Box(Modifier.fillMaxSize()) {
        GlowBackground(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                ScreenHeader(
                    title = order?.let { orderTitle(it) } ?: "Order",
                    subtitle = order?.let { detailSubtitle(it) },
                    onBack = onBack,
                    trailing = {
                        StatusChip(online = online, posStatus = ordersState.posStatus, gate = ordersState.gate)
                    },
                )

                when {
                    ui.closedStatus != null && order != null ->
                        ClosedSummary(order, ui.closedStatus!!, onBack)

                    order == null && ordersState.data == null -> SkeletonScreen()

                    order == null -> MBEmptyState(
                        title = "Order not found",
                        subtitle = "It was probably settled or cancelled at the counter.",
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        action = { MBButton("Back to orders", onClick = onBack, variant = MBButtonVariant.Tonal) },
                    )

                    else -> {
                        OpenOrderContent(
                            order = order,
                            canTakeOrders = PermissionKey.TakeOrders.key in perms,
                            canVoid = PermissionKey.VoidOrderItems.key in perms,
                            canFinalize = PermissionKey.FinalizeBill.key in perms,
                            actionsEnabled = canAct,
                            onAddItems = { onAddItems(order.clientUuid) },
                            onReprint = { viewModel.reprintKot() },
                            onRemove = { removeSheet = true },
                            onSettle = { settleSheet = true },
                            onCancel = { cancelDialog = true },
                        )
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
        MBSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }

    if (removeSheet && order != null) {
        RemoveItemsSheet(
            order = order,
            onDismiss = { removeSheet = false },
            onConfirm = { lines, reason ->
                removeSheet = false
                viewModel.voidItems(lines, reason)
            },
        )
    }

    if (settleSheet && order != null) {
        SettleSheet(
            order = order,
            customers = ordersState.data?.customers.orEmpty(),
            onDismiss = { settleSheet = false },
            onConfirm = { mode, customerId ->
                settleSheet = false
                viewModel.finalizeBill(mode, customerId)
            },
        )
    }

    if (cancelDialog && order != null) {
        ReasonDialog(
            title = "Cancel this order?",
            confirmLabel = "Cancel order",
            destructive = true,
            onDismiss = { cancelDialog = false },
            onConfirm = { reason ->
                cancelDialog = false
                viewModel.cancelOrder(reason)
            },
        )
    }
}

// ---------------- open order ----------------

@Composable
private fun OpenOrderContent(
    order: LiveOrder,
    canTakeOrders: Boolean,
    canVoid: Boolean,
    canFinalize: Boolean,
    actionsEnabled: Boolean,
    onAddItems: () -> Unit,
    onReprint: () -> Unit,
    onRemove: () -> Unit,
    onSettle: () -> Unit,
    onCancel: () -> Unit,
) {
    if (order.printError.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Saved, but there's a printer problem at the counter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    SectionHeader("Items")
    val printedByLine = order.printedItems.associate { (it.localId to it.note) to it.quantity }
    order.items.forEach { line ->
        val printed = printedByLine[(line.localId to line.note)] ?: 0
        ItemLineRow(line, unprinted = (line.quantity - printed).coerceAtLeast(0))
    }

    SectionHeader("Total")
    TotalRow("Subtotal", order.subtotal)
    if (order.gst > 0) TotalRow("GST", order.gst)
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(
            "Total",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Text(
            formatINR(order.total, decimals = 0),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }

    if (order.customerName.isNotEmpty()) {
        Text(
            "Customer: ${order.customerName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader("Actions")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canTakeOrders) {
            MBButton(
                text = "Add items",
                onClick = onAddItems,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (canFinalize) {
            MBButton(
                text = "Settle bill · ${formatINR(order.total, decimals = 0)}",
                onClick = onSettle,
                variant = MBButtonVariant.Tonal,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (canTakeOrders) {
            MBButton(
                text = "Reprint KOT",
                onClick = onReprint,
                variant = MBButtonVariant.Outline,
                enabled = actionsEnabled,
                leadingIcon = Icons.Outlined.Print,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (canVoid) {
            MBButton(
                text = "Remove items",
                onClick = onRemove,
                variant = MBButtonVariant.Outline,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            MBButton(
                text = "Cancel order",
                onClick = onCancel,
                variant = MBButtonVariant.Danger,
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!canTakeOrders && !canVoid && !canFinalize) {
            Text(
                "You can watch this order live. Ask the owner for ordering permissions to act on it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemLineRow(line: OrderLine, unprinted: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${line.quantity} × ${line.name}", style = MaterialTheme.typography.bodyLarge)
                if (unprinted > 0) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "sending",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            line.note?.let {
                Text(
                    "“$it”",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            formatINR(line.price * line.quantity, decimals = 0),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TotalRow(label: String, value: Double) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatINR(value, decimals = 0),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- closed summary ----------------

@Composable
private fun ClosedSummary(order: LiveOrder, status: String, onBack: () -> Unit) {
    val billed = status == "billed"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (billed) Icons.Outlined.Check else Icons.Outlined.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (billed) "Bill settled" else "Order cancelled",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append(orderTitle(order))
                order.billNumber?.let { append(" · Bill $it") }
                if (billed) append(" · ${formatINR(order.total, decimals = 0)}")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        MBButton("Back to orders", onClick = onBack, variant = MBButtonVariant.Tonal)
    }
}

// ---------------- sheets & dialogs ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveItemsSheet(
    order: LiveOrder,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<Long, Int>>, String) -> Unit,
) {
    // quantity-to-remove per cart line (keyed by localId — the contract
    // voids by item id).
    val toRemove = remember { androidx.compose.runtime.mutableStateMapOf<Long, Int>() }
    var reason by remember { mutableStateOf("") }

    // Per-item caps (notes collapsed — void_items works on item id).
    val byItem = order.items.groupBy { it.localId }.mapValues { (_, lines) ->
        lines.first().name to lines.sumOf { it.quantity }
    }
    val selectedCount = toRemove.values.sum()

    MBBottomSheet(onDismissRequest = onDismiss, title = "Remove items") {
        Text(
            "A cancellation slip prints in the kitchen, with your name and the reason.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        byItem.forEach { (localId, nameQty) ->
            val (name, maxQty) = nameQty
            val selected = toRemove[localId] ?: 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "$maxQty in order",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SmallAction("−") {
                        if (selected > 0) toRemove[localId] = selected - 1
                    }
                    Text(
                        "$selected",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (selected > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    SmallAction("+") {
                        if (selected < maxQty) toRemove[localId] = selected + 1
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        MBTextField(
            value = reason,
            onValueChange = { reason = it.take(200) },
            label = "Reason (required)",
            placeholder = "e.g. Customer cancelled",
        )
        Spacer(Modifier.height(14.dp))
        MBButton(
            text = if (selectedCount == 0) "Select items to remove"
            else "Remove $selectedCount item${if (selectedCount > 1) "s" else ""}",
            onClick = {
                onConfirm(toRemove.filterValues { it > 0 }.map { it.key to it.value }, reason.trim())
            },
            variant = MBButtonVariant.Danger,
            enabled = selectedCount > 0 && reason.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleSheet(
    order: LiveOrder,
    customers: List<CreditCustomer>,
    onDismiss: () -> Unit,
    onConfirm: (paymentMode: String, customerLocalId: Long?) -> Unit,
) {
    val dark = LocalMBDarkTheme.current
    var mode by remember { mutableStateOf<String?>(null) }
    var customer by remember { mutableStateOf<CreditCustomer?>(null) }
    var customerQuery by remember { mutableStateOf("") }

    MBBottomSheet(onDismissRequest = onDismiss, title = "Settle bill") {
        Text(
            "${orderTitle(order)} · ${formatINR(order.total, decimals = 0)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        val modes = listOf(
            "Cash" to if (dark) PaymentColors.cashDark else PaymentColors.cashLight,
            "Card" to if (dark) PaymentColors.cardDark else PaymentColors.cardLight,
            "UPI" to if (dark) PaymentColors.upiDark else PaymentColors.upiLight,
            "Credit" to if (dark) PaymentColors.creditDark else PaymentColors.creditLight,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (label, color) ->
                val selected = mode == label
                Box(
                    Modifier
                        .weight(1f)
                        .background(
                            if (selected) color.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable {
                            mode = label
                            if (label != "Credit") customer = null
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) color else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (mode == "Credit") {
            Spacer(Modifier.height(14.dp))
            if (customer == null) {
                MBTextField(
                    value = customerQuery,
                    onValueChange = { customerQuery = it },
                    label = "Credit customer",
                    placeholder = "Search by name or phone…",
                    leadingIcon = Icons.Outlined.Search,
                )
                val matches = customers.filter {
                    customerQuery.isBlank() ||
                        it.name.contains(customerQuery.trim(), ignoreCase = true) ||
                        it.phone.contains(customerQuery.trim())
                }.take(6)
                matches.forEach { c ->
                    ListRow(
                        title = c.name,
                        subtitle = listOfNotNull(
                            c.phone.takeIf { it.isNotEmpty() },
                            "Balance ${formatINR(c.creditBalance, decimals = 0)}",
                        ).joinToString(" · "),
                        onClick = { customer = c },
                    )
                }
                if (customers.isEmpty()) {
                    Text(
                        "No credit customers synced from the counter yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ListRow(
                    title = customer!!.name,
                    subtitle = "Tap to change",
                    icon = Icons.Outlined.Check,
                    onClick = { customer = null },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        MBButton(
            text = "Print bill at the counter",
            onClick = { onConfirm(mode!!, customer?.localId) },
            enabled = mode != null && (mode != "Credit" || customer != null),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "A cancellation slip prints at the counter with your name and this reason.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                MBTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = "Reason (required)",
                    placeholder = "e.g. Customer left",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank(),
            ) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep order") } },
    )
}

private fun detailSubtitle(order: LiveOrder): String {
    val parts = mutableListOf<String>()
    order.billNumber?.let { parts += "Bill $it" }
    if (order.createdByName.isNotEmpty()) parts += order.createdByName
    elapsedText(order.createdAt).takeIf { it.isNotEmpty() }?.let { parts += it }
    return parts.joinToString(" · ")
}
