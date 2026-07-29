package com.magicbill.app.ui.screens.orders

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.composeTableName
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.tableNumberBelongsTo
import com.magicbill.app.data.orders.LiveOrder
import com.magicbill.app.data.orders.OrdersData
import com.magicbill.app.data.orders.OrdersGate
import com.magicbill.app.data.orders.PosStatus
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
import com.magicbill.app.ui.theme.MBMotion
import java.time.Instant

/**
 * The signed-in waiter's name, so a tile can mark the tables this person
 * opened. Empty for the owner, who does not take orders as a waiter.
 */
internal val LocalWaiterName = compositionLocalOf { "" }

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
    val waiterName by viewModel.waiterName.collectAsStateWithLifecycle()

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
                    StatusChip(online = online, posStatus = state.posStatus, gate = state.gate)
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

                    state.data != null -> CompositionLocalProvider(
                        LocalWaiterName provides waiterName,
                    ) {
                        OrdersContent(
                            data = state.data!!,
                            onOpenOrder = onOpenOrder,
                            onNewOrder = onNewOrder,
                            onPickAmong = { tablePickSheet = it },
                            onNewTableManual = { newTableSheet = true },
                        )
                    }
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
    val myName = LocalWaiterName.current

    // Orders attach to a master table by its COMPOSED name ("AC 1") or that
    // name plus a sub-table letter ("AC 1B") — same semantics as the POS.
    // Matching on the bare label instead is what made one order light up every
    // tile numbered "1" across AC, NORMAL and SELF TABLE.
    val tableOrders = openOrders.filter { it.orderType == "Table" }
    val byTable = remember(openOrders, activeTables) {
        val names = activeTables.map { composeTableName(it.section, it.label) }
        tableOrders.groupBy { order ->
            names.firstOrNull { tableNumberBelongsTo(order.tableNumber, it) } ?: "" // "" = "Other"
        }
    }
    val otherOrders = byTable[""].orEmpty()
    val parcelOrders = openOrders.filter { it.orderType != "Table" }

    // Section chips only when the master actually uses sections. They come off
    // the same grouping the tiles use, so chip order and heading order can
    // never drift apart.
    val sections = remember(activeTables) {
        groupTables(activeTables).map { it.section }.filter { it.isNotEmpty() }
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
        // Grouped by section, never one flat wall of numbers: four tiles
        // labelled "1" are indistinguishable without their section.
        val groups = remember(visibleTables) { groupTables(visibleTables) }
        groups.forEach { group ->
            SectionHeader(
                when {
                    group.section.isNotEmpty() -> group.section
                    groups.size == 1 -> "Tables"
                    else -> "No section"
                }
            )
            TableGrid(
                tables = group.tables,
                byTable = byTable,
                myName = myName,
                onOpenOrder = onOpenOrder,
                onNewOrder = onNewOrder,
                onPickAmong = onPickAmong,
            )
        }
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
        // Orders opened at the counter on a table that is not in the master —
        // including everything opened before tables carried their section.
        SectionHeader("Orders on other tables")
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

/** One section's tables, in a stable order that does not depend on the server. */
internal data class TableGroup(val section: String, val tables: List<TableInfo>)

/**
 * Sections ordered by the owner's own sort_order (lowest first), ties broken
 * alphabetically, with the unsectioned tables last. Deliberately independent
 * of the order the Edge Function happens to return.
 */
internal fun groupTables(tables: List<TableInfo>): List<TableGroup> =
    tables.groupBy { it.section }
        .map { (section, rows) ->
            TableGroup(section, rows.sortedWith(compareBy({ it.sortOrder }, { it.localId })))
        }
        .sortedWith(
            compareBy(
                { it.section.isEmpty() }, // unsectioned tables go last
                { it.tables.minOfOrNull { t -> t.sortOrder } ?: 0L },
                { it.section.lowercase() },
            ),
        )

@Composable
private fun TableGrid(
    tables: List<TableInfo>,
    byTable: Map<String, List<LiveOrder>>,
    myName: String,
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
                val name = composeTableName(table.section, table.label)
                val orders = byTable[name].orEmpty()
                TableTile(
                    table = table,
                    orders = orders,
                    mine = myName.isNotEmpty() && orders.any {
                        it.createdByKind == "staff" && it.createdByName.equals(myName, ignoreCase = true)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        when {
                            // The composed name is what the counter stores and
                            // what the kitchen slip prints.
                            orders.isEmpty() -> onNewOrder("Table", name, table.section)
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

/** Uniform so the grid reads as a floor plan rather than a ragged list. */
private val TILE_HEIGHT = 104.dp

/**
 * One table, meant to be read at arm's length in a noisy room:
 *  - a real edge (border + its own container colour), so it is an object;
 *  - free vs occupied carried by SHAPE as well as colour — occupied tiles get
 *    a filled side stripe and a solid dot, free tiles a hollow ring and no
 *    stripe — so it survives colour blindness and glare;
 *  - the section printed on the tile itself, because "1" alone is ambiguous
 *    when AC, NORMAL and SELF TABLE all have one.
 */
@Composable
private fun TableTile(
    table: TableInfo,
    orders: List<LiveOrder>,
    mine: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val occupied = orders.isNotEmpty()
    val accent = when {
        !occupied -> scheme.outline
        mine -> scheme.tertiary
        else -> scheme.primary
    }
    val container = if (occupied) scheme.surfaceContainerHigh else scheme.surfaceContainer
    val edge = if (occupied) accent else scheme.outlineVariant

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = MBMotion.bouncy(),
        label = "tableTilePress",
    )

    val total = if (occupied) orders.sumOf { it.total } else 0.0
    val count = if (occupied) orders.sumOf { it.itemCount } else 0
    val elapsed = if (occupied) elapsedText(orders.minOf { it.createdAt }) else ""
    val spoken = tileDescription(table, orders, mine, total, count, elapsed)

    Box(
        modifier
            .height(TILE_HEIGHT)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .border(if (occupied) 1.5.dp else 1.dp, edge, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        // Shape, not just colour: an occupied table carries a filled edge.
        if (occupied) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(accent),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = if (occupied) 15.dp else 12.dp, end = 12.dp)
                .padding(vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    table.section.ifEmpty { "TABLE" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (orders.size > 1) {
                    Text(
                        "×${orders.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                StatusMark(occupied = occupied, color = accent)
            }
            Text(
                table.label,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                // Free tiles are fully legible on purpose: they must read as
                // tappable, never as disabled.
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (occupied) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatINR(total, decimals = 0),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // A word, not a colour, marks this waiter's own table.
                    if (mine) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "You",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = accent,
                        )
                    }
                }
                Text(
                    listOfNotNull("$count items", elapsed.ifEmpty { null }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // A tile is ~100dp wide; anything longer than this ellipsises.
                Text(
                    "Free",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Solid dot = occupied, hollow ring = free. Readable without colour. */
@Composable
private fun StatusMark(occupied: Boolean, color: androidx.compose.ui.graphics.Color) {
    if (occupied) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
    } else {
        Box(Modifier.size(9.dp).border(1.5.dp, color, CircleShape))
    }
}

/** "AC table 1, occupied, 693 rupees, 3 items, 19 min" — one per tile. */
private fun tileDescription(
    table: TableInfo,
    orders: List<LiveOrder>,
    mine: Boolean,
    total: Double,
    count: Int,
    elapsed: String,
): String = buildString {
    append(
        if (table.section.isNotEmpty()) "${table.section} table ${table.label}"
        else "Table ${table.label}",
    )
    if (orders.isEmpty()) {
        append(", free, tap to start an order")
        return@buildString
    }
    append(", occupied")
    if (mine) append(", your order")
    append(", ${total.toLong()} rupees")
    append(", $count items")
    if (elapsed.isNotEmpty()) append(", $elapsed")
    if (orders.size > 1) append(", ${orders.size} separate orders")
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

/**
 * Three tones for three states, and never a fourth meaning smuggled into one
 * of them. "Can't reach Magic Bill" points at this phone; "Counter offline"
 * points at the owner's till. Saying the second when we mean the first is the
 * bug this release exists to fix — a waiter was told the till was dead while
 * it was sitting there taking orders perfectly happily.
 */
@Composable
internal fun StatusChip(online: Boolean, posStatus: PosStatus, gate: OrdersGate?) {
    val (text, status) = when {
        !online -> "No internet" to MBBadgeStatus.Expired
        gate == OrdersGate.OrderingDisabled -> "Ordering off" to MBBadgeStatus.Neutral
        gate != null -> "Unavailable" to MBBadgeStatus.Neutral
        posStatus == PosStatus.Online -> "Counter online" to MBBadgeStatus.Active
        posStatus == PosStatus.Offline -> "Counter offline" to MBBadgeStatus.Grace
        else -> "Can't reach Magic Bill" to MBBadgeStatus.Neutral
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
