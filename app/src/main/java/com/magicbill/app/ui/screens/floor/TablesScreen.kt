package com.magicbill.app.ui.screens.floor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.Stream
import com.magicbill.app.db.FloorOrderRow
import com.magicbill.app.db.FloorTableRow
import com.magicbill.app.nav.NewOrder
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.IconDisc
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.kit.pressScale
import com.magicbill.app.ui.kit.pulse
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Radius
import com.magicbill.app.ui.theme.Space
import com.magicbill.app.ui.theme.isWide
import com.magicbill.app.ui.theme.person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TablesViewModel @Inject constructor(private val floor: Floor, val stream: Stream, private val counter: Counter) : ViewModel() {
    data class View(
        val tables: List<FloorTableRow> = emptyList(),
        /** Every open order on a table — anybody's — by table id. */
        val onTable: Map<String, FloorOrderRow> = emptyMap(),
        val noTable: List<FloorOrderRow> = emptyList(),
    )

    val view: StateFlow<View> = combine(floor.tables, floor.openOrders) { t, orders ->
        View(t, orders.filter { it.tableId != null }.associateBy { it.tableId!! }, orders.filter { it.tableId == null })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    val streamState: StateFlow<Stream.State> = stream.state
    val revoked: StateFlow<String?> = counter.revokedSays
    /** Warn and late, in minutes — the counter's own numbers, so both screens turn amber and red together. */
    val thresholds: StateFlow<Pair<Int, Int>> = floor.thresholds
    val shopName: String get() = counter.credential.value?.shopName ?: "the counter"

    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> get() = refreshing

    /** The live line carries the floor; opening the screen only sends what was queued. */
    fun opened() { stream.ensure(); viewModelScope.launch { floor.flush() } }

    /** Pull down: the snapshot, now. */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            floor.refreshCatalogue(); floor.refreshFloor(); floor.flush()
            refreshing.value = false
        }
    }
}

/**
 * The Orders floor: sections as overlines, and the same table card the counter draws — the
 * number, the money and whose it is, the timer and the seats, the person's colour on the edge.
 * Every open order is here, anybody's; tap any taken table to see it. Tapping a FREE table
 * opens the ORDER BUILDER: nothing reaches the counter until "Send to kitchen", so there are
 * no 0.00 ghost orders and no wait per dish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablesScreen(openOrder: (String) -> Unit, openBuilder: (NewOrder) -> Unit, onPair: () -> Unit, vm: TablesViewModel = hiltViewModel()) {
    val view by vm.view.collectAsStateWithLifecycle()
    val stream by vm.streamState.collectAsStateWithLifecycle()
    val revoked by vm.revoked.collectAsStateWithLifecycle()
    val refreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val thresholds by vm.thresholds.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.opened() }
    val wide = isWide()
    // The timers on the cards tick by themselves — the counter's minute plus the time since it spoke.
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh) {
        Page("Orders", vm.shopName, scroll = false, bottomPadding = 0.dp, actions = { StreamBadge(stream) }) {
            if (revoked != null) {
                Notice(Tone.Danger, revoked!!, action = { SecondaryButton("Connect again", onPair) })
                VGap(Gap.field)
            }
            if (view.tables.isEmpty() && view.noTable.isEmpty()) {
                Empty(
                    if (stream == Stream.State.Off || stream == Stream.State.Connecting) "Bringing the floor from the counter…"
                    else "This shop has no tables set up. Parcel and delivery still work.",
                    action = { SecondaryButton("New parcel order", { openBuilder(NewOrder(orderType = "parcel")) }) },
                )
                return@Page
            }
            val sections = view.tables.groupBy { it.section.ifBlank { "No section" } }
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (wide) 5 else 3),
                horizontalArrangement = Arrangement.spacedBy(Gap.field),
                verticalArrangement = Arrangement.spacedBy(Gap.field),
                contentPadding = PaddingValues(bottom = Space.s7),
            ) {
                sections.forEach { (section, tables) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            section.uppercase(),
                            style = Mb.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
                            color = Mb.colors.inkMuted,
                            modifier = Modifier.padding(top = Space.s3),
                        )
                    }
                    items(tables, key = { it.id }) { t ->
                        val order = view.onTable[t.id]
                        TableCard(t, order, now, thresholds) {
                            if (order != null && !order.orderId.startsWith(Floor.PENDING_PREFIX)) openOrder(order.orderId)
                            else if (order == null) openBuilder(NewOrder(tableId = t.id, tableLabel = t.label, orderType = "dine_in"))
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(
                            "PARCEL & SELF SERVICE",
                            style = Mb.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
                            color = Mb.colors.inkMuted,
                            modifier = Modifier.padding(top = Space.s4),
                        )
                        view.noTable.forEach { o ->
                            val pending = o.orderId.startsWith(Floor.PENDING_PREFIX)
                            ListRow(
                                title = o.orderType.replace('_', ' ').replaceFirstChar { it.uppercase() } + (o.token?.let { " · Token $it" } ?: ""),
                                subtitle = "${Floor.parseLines(o.lines).size} items · ₹" + o.total + (o.by?.let { " · $it" } ?: ""),
                                leading = { IconDisc(Icons.Outlined.Restaurant, tint = Mb.colors.person(o.byId)) },
                                trailing = {
                                    when {
                                        pending || o.sending -> Badge("Sending", Tone.Info)
                                        o.settleAsked -> Badge("Settle", Tone.Ok)
                                        o.billAsked -> Badge("Bill", Tone.Ok)
                                    }
                                },
                                onClick = if (pending) null else ({ openOrder(o.orderId) }),
                            )
                        }
                        ListRow(title = "New parcel order", leading = { IconDisc(Icons.Outlined.Add) }, onClick = { openBuilder(NewOrder(orderType = "parcel")) })
                        ListRow(title = "New delivery order", leading = { IconDisc(Icons.Outlined.DeliveryDining) }, onClick = { openBuilder(NewOrder(orderType = "delivery")) })
                    }
                }
            }
        }
    }
}

@Composable
fun StreamBadge(state: Stream.State) {
    when (state) {
        Stream.State.Live -> Badge("Live", Tone.Ok)
        Stream.State.Connecting -> Badge("Connecting", Tone.Info)
        Stream.State.Lost -> Badge("Reconnecting", Tone.Warn)
        Stream.State.Off -> {}
    }
}

/** "12m", "1h 05m" — the counter's own wording for a timer. */
fun minutesText(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

/**
 * The table card — the same card the counter draws, so a waiter and a cashier are looking at
 * one thing:
 *
 *   number ………………… dot
 *   money · whose          (a free table: "4 seats")
 *   timer · chip …… seats
 *
 * A taken table wears its PERSON's colour as one stripe down the left edge, on the dot and on
 * the name — the same colour that person has on the counter. No fills: waiting and late live
 * in the timer, amber then bold red. One still on its way to the counter breathes.
 */
@Composable
private fun TableCard(t: FloorTableRow, order: FloorOrderRow?, now: Long, thresholds: Pair<Int, Int>, onClick: () -> Unit) {
    val c = Mb.colors
    val shape = RoundedCornerShape(Radius.md)
    val big = t.label.removePrefix(t.section).ifBlank { t.label }.trim()
    val sending = order != null && (order.sending || order.orderId.startsWith(Floor.PENDING_PREFIX))
    val person = if (order == null) c.lineSoft else c.person(order.byId)
    val ring by animateColorAsState(person, label = "tableRing")
    val minutes = order?.minutes?.let { it + ((now - order.updatedMs) / 60_000).toInt().coerceAtLeast(0) }
    val (warnAfter, lateAfter) = thresholds
    val late = minutes != null && minutes >= lateAfter
    val waiting = !late && minutes != null && minutes >= warnAfter
    val interaction = remember { MutableInteractionSource() }
    val stripe = with(LocalDensity.current) { 3.dp.toPx() }
    Column(
        Modifier.aspectRatio(1.05f).pressScale(interaction).clip(shape)
            .background(c.surface)
            .border(1.dp, c.lineSoft, shape)
            // ONE stripe down the left edge in the person's colour — no fill, ever.
            .drawBehind { if (order != null) drawRect(ring, size = Size(stripe, size.height)) }
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(start = Space.s3 + 3.dp, end = Space.s3, top = Space.s3, bottom = Space.s3),
    ) {
        // The number, and the person's dot.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(big, style = Mb.type.stat.copy(fontWeight = FontWeight.Bold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (order != null) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(person).pulse(sending))
        }
        // The money and whose it is — or, free, how big the table is.
        if (order != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("₹" + order.total, style = Mb.type.cell.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1)
                Spacer(Modifier.width(Space.s2))
                Text(
                    if (order.mine) "You" else order.by ?: "",
                    style = Mb.type.caption, color = person, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        } else {
            Text(if (t.seats > 0) "${t.seats} seats" else "Free", style = Mb.type.caption, color = c.inkMuted, maxLines = 1)
        }
        Box(Modifier.weight(1f))
        // The timer and the chip on the left, the seats on the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (order != null) {
                when {
                    sending -> Text("Sending…", style = Mb.type.caption, color = c.accent, maxLines = 1)
                    minutes != null -> Text(
                        minutesText(minutes),
                        style = Mb.type.caption.copy(fontWeight = if (late || waiting) FontWeight.Bold else FontWeight.Medium),
                        color = when { late -> c.danger; waiting -> c.warn; else -> c.inkMuted },
                        maxLines = 1,
                    )
                }
                when {
                    sending -> {}
                    order.settleAsked -> { Spacer(Modifier.width(Space.s2)); TinyChip("Settle", c.ok, c.okSoft) }
                    order.billAsked -> { Spacer(Modifier.width(Space.s2)); TinyChip("Bill", c.accent, c.accentSoft) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (t.seats > 0) {
                Icon(Icons.Outlined.Group, contentDescription = null, tint = c.inkFaint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("${t.seats}", style = Mb.type.caption, color = c.inkMuted, maxLines = 1)
            }
        }
    }
}

/** A one-word chip on a card: "Bill", "Settle". */
@Composable
private fun TinyChip(text: String, ink: Color, fill: Color) {
    Box(Modifier.clip(RoundedCornerShape(Radius.sm)).background(fill).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(text, style = Mb.type.caption.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
    }
}
