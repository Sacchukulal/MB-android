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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.formatINR
import com.magicbill.app.data.orders.LiveOrder
import com.magicbill.app.data.orders.OrdersData
import com.magicbill.app.data.orders.OrdersGate
import com.magicbill.app.data.orders.TableInfo
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.ListRow
import com.magicbill.app.ui.components.MBBadge
import com.magicbill.app.ui.components.MBBadgeStatus
import com.magicbill.app.ui.components.MBBottomSheet
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.MBEmptyState
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.MBTextField
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SegmentedChips
import com.magicbill.app.ui.components.SkeletonScreen
import java.time.Instant

/**
 * The Orders tab: live table grid + open orders, shared by owner and staff.
 * Cache renders instantly; realtime keeps it honest while the tab is visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    restaurantName: String,
    onOpenOrder: (String) -> Unit,
    onNewOrder: (orderType: String, tableNumber: String, section: String) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()

    // Socket + refresh live only while an orders surface is on screen.
    LifecycleResumeEffect(Unit) {
        viewModel.connect()
        viewModel.load()
        onPauseOrDispose { viewModel.disconnect() }
    }

    var tablePickSheet by remember { mutableStateOf<List<LiveOrder>?>(null) }
    var newTableSheet by remember { mutableStateOf(false) }

    GlowBackground(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.data != null,
            onRefresh = { viewModel.load(force = true) },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.statusBarsPadding().height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.data?.restaurantName?.takeIf { it.isNotEmpty() } ?: restaurantName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Orders", style = MaterialTheme.typography.headlineSmall)
                    }
                    StatusChip(online = online, posOnline = state.posOnline, gate = state.gate)
                }

                when {
                    state.gate != null -> GateContent(state.gate!!) { viewModel.load(force = true) }

                    state.data == null && state.refreshing -> {
                        Spacer(Modifier.height(40.dp))
                        SkeletonScreen()
                    }

                    state.data == null && state.error != null -> {
                        Spacer(Modifier.height(60.dp))
                        MBErrorState(state.error!!, onRetry = { viewModel.load(force = true) })
                    }

                    state.data != null -> OrdersContent(
                        data = state.data!!,
                        onOpenOrder = onOpenOrder,
                        onNewOrder = onNewOrder,
                        onPickAmong = { tablePickSheet = it },
                        onNewTableManual = { newTableSheet = true },
                    )
                }

                Spacer(Modifier.height(120.dp))
            }
        }
    }

    // One table, several open orders (sub-tables) — choose which to open.
    tablePickSheet?.let { orders ->
        MBBottomSheet(onDismissRequest = { tablePickSheet = null }, title = "Open orders") {
            orders.forEach { order ->
                ListRow(
                    title = orderTitle(order),
                    subtitle = orderSubtitle(order),
                    icon = Icons.Outlined.TableRestaurant,
                    onClick = {
                        tablePickSheet = null
                        onOpenOrder(order.clientUuid)
                    },
                    trailing = { OrderStatusBadge(order) },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // No table master defined — free-text table number, like the POS popup.
    if (newTableSheet) {
        var label by remember { mutableStateOf("") }
        MBBottomSheet(onDismissRequest = { newTableSheet = false }, title = "New table order") {
            MBTextField(
                value = label,
                onValueChange = { label = it.take(20) },
                label = "Table number",
                placeholder = "e.g. 6",
            )
            Spacer(Modifier.height(14.dp))
            MBButton(
                text = "Start order",
                enabled = label.isNotBlank(),
                onClick = {
                    newTableSheet = false
                    onNewOrder("Table", label.trim(), "")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------------- content ----------------

@Composable
private fun OrdersContent(
    data: OrdersData,
    onOpenOrder: (String) -> Unit,
    onNewOrder: (String, String, String) -> Unit,
    onPickAmong: (List<LiveOrder>) -> Unit,
    onNewTableManual: () -> Unit,
) {
    val openOrders = data.orders.filter { it.isOpen }
    val activeTables = data.tables.filter { it.isActive }

    // Orders attach to a master table by exact label or sub-table letter
    // ("6B" belongs to tile "6" — same semantics as the POS popup).
    val tableOrders = openOrders.filter { it.orderType == "Table" }
    val byTable = remember(openOrders, activeTables) {
        val labels = activeTables.map { it.label }.toSet()
        tableOrders.groupBy { order ->
            when {
                order.tableNumber in labels -> order.tableNumber
                order.tableNumber.length > 1 && order.tableNumber.dropLast(1) in labels &&
                    order.tableNumber.last() in 'B'..'H' -> order.tableNumber.dropLast(1)
                else -> "" // "Other" group
            }
        }
    }
    val otherOrders = byTable[""].orEmpty()
    val parcelOrders = openOrders.filter { it.orderType != "Table" }

    // Section chips only when the master actually uses sections.
    val sections = remember(activeTables) {
        activeTables.map { it.section }.filter { it.isNotEmpty() }.distinct()
    }
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val chipLabels = if (sections.isEmpty()) emptyList() else listOf("All") + sections
    val visibleTables = when {
        sections.isEmpty() || sectionIndex == 0 -> activeTables
        else -> activeTables.filter { it.section == chipLabels.getOrNull(sectionIndex) }
    }

    if (chipLabels.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        SegmentedChips(
            options = chipLabels,
            selectedIndex = sectionIndex.coerceIn(0, chipLabels.lastIndex),
            onSelect = { sectionIndex = it },
        )
    }

    if (activeTables.isNotEmpty()) {
        SectionHeader("Tables")
        TableGrid(
            tables = visibleTables,
            byTable = byTable,
            onOpenOrder = onOpenOrder,
            onNewOrder = onNewOrder,
            onPickAmong = onPickAmong,
        )
    } else {
        SectionHeader("Table orders")
        if (tableOrders.isEmpty()) {
            Text(
                "No open table orders.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        tableOrders.forEach { order -> OrderRow(order, onOpenOrder) }
        ListRow(
            title = "New table order",
            icon = Icons.Outlined.Add,
            onClick = onNewTableManual,
        )
    }

    if (activeTables.isNotEmpty() && otherOrders.isNotEmpty()) {
        SectionHeader("Other tables")
        otherOrders.forEach { order -> OrderRow(order, onOpenOrder) }
    }

    SectionHeader("Parcel & self service")
    parcelOrders.forEach { order -> OrderRow(order, onOpenOrder) }
    ListRow(
        title = "New parcel order",
        icon = Icons.Outlined.Add,
        onClick = { onNewOrder("Parcel", "", "") },
    )

    if (openOrders.isEmpty() && activeTables.isEmpty()) {
        Spacer(Modifier.height(20.dp))
        MBEmptyState(
            title = "No open orders",
            subtitle = "Start a table or parcel order and it will print at the counter.",
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        )
    }
}

@Composable
private fun TableGrid(
    tables: List<TableInfo>,
    byTable: Map<String, List<LiveOrder>>,
    onOpenOrder: (String) -> Unit,
    onNewOrder: (String, String, String) -> Unit,
    onPickAmong: (List<LiveOrder>) -> Unit,
) {
    tables.chunked(3).forEach { rowTables ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rowTables.forEach { table ->
                val orders = byTable[table.label].orEmpty()
                TableTile(
                    table = table,
                    orders = orders,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            orders.isEmpty() -> onNewOrder("Table", table.label, table.section)
                            orders.size == 1 -> onOpenOrder(orders.first().clientUuid)
                            else -> onPickAmong(orders)
                        }
                    },
                )
            }
            repeat(3 - rowTables.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TableTile(
    table: TableInfo,
    orders: List<LiveOrder>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val occupied = orders.isNotEmpty()
    val bg = if (occupied) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Column(
        modifier
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                table.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (occupied) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (orders.size > 1) {
                Spacer(Modifier.weight(1f))
                Text(
                    "×${orders.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (occupied) {
            val total = orders.sumOf { it.total }
            val count = orders.sumOf { it.itemCount }
            Text(
                formatINR(total, decimals = 0),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$count items · ${elapsedText(orders.minOf { it.createdAt })}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Free",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
internal fun OrderRow(order: LiveOrder, onOpenOrder: (String) -> Unit) {
    ListRow(
        title = orderTitle(order),
        subtitle = orderSubtitle(order),
        icon = if (order.orderType == "Table") Icons.Outlined.TableRestaurant
        else Icons.Outlined.RestaurantMenu,
        onClick = { onOpenOrder(order.clientUuid) },
        trailing = { OrderStatusBadge(order) },
    )
}

@Composable
internal fun OrderStatusBadge(order: LiveOrder) {
    when {
        order.status == "queued" || order.pendingKot ->
            MBBadge("Sending…", MBBadgeStatus.Grace)
        order.printError.isNotEmpty() ->
            MBBadge("Printer issue", MBBadgeStatus.Expired)
        order.status == "billed" -> MBBadge("Billed", MBBadgeStatus.Neutral)
        order.status == "cancelled" -> MBBadge("Cancelled", MBBadgeStatus.Neutral)
    }
}

internal fun orderTitle(order: LiveOrder): String = when (order.orderType) {
    "Table" -> "Table ${order.tableNumber}"
    else -> order.orderType
} + (order.tokenNumber?.let { " · Token $it" } ?: "")

internal fun orderSubtitle(order: LiveOrder): String {
    val parts = mutableListOf<String>()
    parts += "${order.itemCount} items"
    parts += formatINR(order.total, decimals = 0)
    if (order.createdByName.isNotEmpty()) parts += order.createdByName
    elapsedText(order.createdAt).takeIf { it.isNotEmpty() }?.let { parts += it }
    return parts.joinToString(" · ")
}

/** "12 min" / "1 hr 5 min" from an ISO timestamp; "" when unparseable. */
internal fun elapsedText(iso: String): String {
    if (iso.isEmpty()) return ""
    val normalized = if (iso.endsWith("Z") || iso.contains('+')) iso else iso + "Z"
    val start = runCatching { Instant.parse(normalized).toEpochMilli() }.getOrNull() ?: return ""
    val min = ((System.currentTimeMillis() - start) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min"
        else -> "${min / 60} hr ${min % 60} min"
    }
}

// ---------------- status chip + gates ----------------

@Composable
internal fun StatusChip(online: Boolean, posOnline: Boolean, gate: OrdersGate?) {
    val (text, status) = when {
        !online -> "No internet" to MBBadgeStatus.Expired
        gate == OrdersGate.OrderingDisabled -> "Ordering off" to MBBadgeStatus.Neutral
        gate != null -> "Unavailable" to MBBadgeStatus.Neutral
        posOnline -> "Counter online" to MBBadgeStatus.Active
        else -> "Counter offline" to MBBadgeStatus.Grace
    }
    MBBadge(text, status)
}

/** Full-screen explanations for every blocked state — never a dead end. */
@Composable
private fun GateContent(gate: OrdersGate, onRetry: () -> Unit) {
    val (icon, title, message) = when (gate) {
        OrdersGate.OrderingDisabled -> Triple(
            Icons.Outlined.PowerSettingsNew,
            "Mobile ordering is off",
            "The owner can turn it on at the billing counter under " +
                "Settings → Tables & Mobile Ordering. This screen will come " +
                "alive the moment it's enabled.",
        )
        OrdersGate.Subscription -> Triple(
            Icons.Outlined.CreditCard,
            "Subscription expired",
            "The restaurant's Magic Bill subscription has expired. Renew it " +
                "to continue taking orders from the phone.",
        )
        OrdersGate.Blocked -> Triple(
            Icons.Outlined.Block,
            "This phone was blocked",
            "The owner blocked this device from taking orders. If that's a " +
                "mistake, ask them to unblock it at the billing counter.",
        )
        OrdersGate.DeviceLimit -> Triple(
            Icons.Outlined.Devices,
            "Phone limit reached",
            "The plan's connected-phone limit has been reached. Ask the " +
                "owner to block an unused phone or upgrade the plan.",
        )
    }
    GateScreen(icon, title, message, onRetry)
}

@Composable
private fun GateScreen(
    icon: ImageVector,
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            MBButton(text = "Check again", onClick = onRetry, variant = MBButtonVariant.Tonal)
        }
    }
}
