package com.magicbill.app.ui.screens.floor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import com.magicbill.app.ui.theme.isWide
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The stream is wanted while a floor screen is on. Screens call this from a DisposableEffect. */
@Composable
fun OnTheFloor(stream: Stream) {
    DisposableEffect(Unit) {
        stream.wanted(true)
        onDispose { stream.wanted(false) }
    }
}

@HiltViewModel
class TablesViewModel @Inject constructor(private val floor: Floor, val stream: Stream, private val counter: Counter) : ViewModel() {
    data class View(
        val tables: List<FloorTableRow> = emptyList(),
        val mine: Map<String, FloorOrderRow> = emptyMap(),
        val noTable: List<FloorOrderRow> = emptyList(),
    )

    val view: StateFlow<View> = combine(floor.tables, floor.openOrders) { t, orders ->
        View(t, orders.filter { it.tableId != null }.associateBy { it.tableId!! }, orders.filter { it.tableId == null })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    val streamState: StateFlow<Stream.State> = stream.state
    val revoked: StateFlow<String?> = counter.revokedSays
    val shopName: String get() = counter.credential.value?.shopName ?: "the counter"

    fun opened() { viewModelScope.launch { floor.refreshCatalogue(); floor.refreshFloor(); floor.flush(); counter.refreshMe() } }
}

/**
 * The Orders floor, in the 2.x card style: sections as overlines, cards with the section
 * small, the number big, Free or the money — and a status dot. Tapping a FREE table opens the
 * ORDER BUILDER: nothing reaches the counter until "Send to kitchen", so there are no 0.00
 * ghost orders and no wait per dish.
 */
@Composable
fun TablesScreen(openOrder: (String) -> Unit, openBuilder: (NewOrder) -> Unit, onPair: () -> Unit, vm: TablesViewModel = hiltViewModel()) {
    val view by vm.view.collectAsStateWithLifecycle()
    val stream by vm.streamState.collectAsStateWithLifecycle()
    val revoked by vm.revoked.collectAsStateWithLifecycle()
    OnTheFloor(vm.stream)
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.opened() }
    val wide = isWide()

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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.s7),
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
                    val mine = view.mine[t.id]
                    val taken = mine == null && t.state.isNotBlank() && t.state != "free"
                    TableCard(t, mine, taken) {
                        if (mine != null) openOrder(mine.orderId)
                        else openBuilder(NewOrder(tableId = t.id, tableLabel = t.label, orderType = "dine_in"))
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
                        ListRow(
                            title = o.orderType.replace('_', ' ').replaceFirstChar { it.uppercase() } + (o.token?.let { " · Token $it" } ?: ""),
                            subtitle = "${Floor.parseLines(o.lines).size} items · ₹" + o.total,
                            leading = { IconDisc(Icons.Outlined.Restaurant) },
                            onClick = { openOrder(o.orderId) },
                        )
                    }
                    ListRow(
                        title = "New parcel order",
                        leading = { IconDisc(Icons.Outlined.Add) },
                        onClick = { openBuilder(NewOrder(orderType = "parcel")) },
                    )
                    ListRow(
                        title = "New delivery order",
                        leading = { IconDisc(Icons.Outlined.DeliveryDining) },
                        onClick = { openBuilder(NewOrder(orderType = "delivery")) },
                    )
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

/** The 2.x table card: section small, number big, Free or the money, a status dot top-right. */
@Composable
private fun TableCard(t: FloorTableRow, mine: FloorOrderRow?, taken: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val big = t.label.removePrefix(t.section).ifBlank { t.label }.trim()
    val dot = when { mine != null -> Mb.colors.accent; taken -> Mb.colors.warn; else -> Mb.colors.lineSoft }
    Column(
        Modifier.aspectRatio(0.92f).clip(shape)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            .border(if (mine != null) 1.5.dp else 0.dp, if (mine != null) Mb.colors.accent else androidx.compose.ui.graphics.Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(Space.s3),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(t.section.uppercase().ifBlank { "TABLE" }, style = Mb.type.caption, color = Mb.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        }
        Text(big, style = Mb.type.stat, color = Mb.colors.ink, maxLines = 1)
        Box(Modifier.weight(1f))
        when {
            mine != null -> {
                Text("₹" + mine.total, style = Mb.type.cell.copy(fontWeight = FontWeight.SemiBold), color = Mb.colors.ink, maxLines = 1)
                val n = Floor.parseLines(mine.lines).size
                Text((mine.token?.let { "#$it · " } ?: "") + if (n == 1) "1 item" else "$n items", style = Mb.type.caption, color = Mb.colors.inkMuted, maxLines = 1)
            }
            taken -> Text("Taken", style = Mb.type.caption, color = Mb.colors.warn)
            else -> Text("Free", style = Mb.type.caption, color = Mb.colors.inkMuted)
        }
    }
}
