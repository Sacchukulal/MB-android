package com.magicbill.app.ui.screens.floor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Money
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.LineView
import com.magicbill.app.counter.Ops
import com.magicbill.app.counter.Outcome
import com.magicbill.app.counter.Stream
import com.magicbill.app.db.FloorItemRow
import com.magicbill.app.db.FloorOrderRow
import com.magicbill.app.db.FloorTableRow
import com.magicbill.app.nav.OrderScreen
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.ChipRow
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.Field
import com.magicbill.app.ui.kit.IconAction
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.Stepper
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/**
 * One order, anybody's, from the phone. Every button is an intent; every answer is the
 * counter's sentence, said once by the reporter. The order on screen is what the counter last
 * said it was; a passing sentence is never pinned to the screen.
 */
@HiltViewModel
class OrderViewModel @Inject constructor(saved: SavedStateHandle, private val floor: Floor, val stream: Stream, private val counter: Counter) : ViewModel() {
    val orderId: String = saved.toRoute<OrderScreen>().orderId

    data class View(val order: FloorOrderRow? = null, val lines: List<LineView> = emptyList(), val items: List<FloorItemRow> = emptyList(), val tables: List<FloorTableRow> = emptyList())

    val view: StateFlow<View> = combine(floor.order(orderId), floor.items, floor.tables) { o, items, tables ->
        View(o, o?.let { Floor.parseLines(it.lines) } ?: emptyList(), items, tables)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    private val busyFlow = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = busyFlow

    val may: Set<String> get() = counter.me.value?.may ?: emptySet()

    fun send(what: JsonObject, label: String, say: (String) -> Unit) {
        if (busyFlow.value) return
        busyFlow.value = true
        viewModelScope.launch {
            val o = view.value.order
            when (val a = floor.submit(what, orderId, label, Floor.Place(o?.tableId, o?.tableLabel, o?.orderType ?: ""))) {
                is Answer.Ok -> when (val out = a.value) {
                    is Outcome.Ok -> if (!out.note.isNullOrBlank()) say(out.note)
                    is Outcome.Refused -> say(out.message)
                    is Outcome.Held -> say(out.message)
                }
                is Answer.Unreachable -> say("Could not reach the counter. This is queued and goes when it is back.")
                else -> say(a.sentenceOrNull ?: "")
            }
            busyFlow.value = false
        }
    }

    fun opened() { stream.ensure(); viewModelScope.launch { floor.flush() } }
}

@Composable
fun OrderScreenView(back: () -> Unit, addMore: (com.magicbill.app.nav.NewOrder) -> Unit, vm: OrderViewModel = hiltViewModel()) {
    val view by vm.view.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val stream by vm.stream.state.collectAsStateWithLifecycle()
    val reporter = LocalReporter.current
    LaunchedEffect(Unit) { vm.opened() }
    var lineMenu by remember { mutableStateOf<LineView?>(null) }
    var more by remember { mutableStateOf(false) }
    var reasonFor by remember { mutableStateOf<String?>(null) } // "void:<line>" | "cancel"
    var moving by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    val o = view.order
    val closed = o?.closedSays
    val title = o?.tableLabel?.let { "Table $it" } ?: o?.orderType?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Order"
    val subtitle = listOfNotNull(o?.token?.let { "Token #$it" }, o?.by?.takeIf { o.mine != true }?.let { "$it's order" }).joinToString(" · ").ifBlank { null }

    Page(title, subtitle, back = back, scroll = false, bottomPadding = 0.dp, actions = {
        if (o?.billAsked == true) Badge("Bill printed", Tone.Ok)
        StreamBadge(stream)
        if (closed == null) IconAction(Icons.Outlined.MoreVert, "More", { more = true })
    }) {
        if (closed != null) { Notice(Tone.Info, closed, action = { SecondaryButton("Back", back) }); VGap(Gap.field) }
        if (o == null) { Empty("This order is not on the phone."); return@Page }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().animateContentSize()) {
            if (view.lines.isEmpty()) item { Empty("Nothing on this order yet. Add the first dish.") }
            items(view.lines, key = { it.line }) { l ->
                ListRow(
                    "${l.qty} × ${l.name}", l.note,
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            if (l.amount.isNotBlank()) Text("₹" + l.amount, style = Mb.type.cell, color = Mb.colors.ink)
                            when {
                                o.sending && l.amount.isBlank() -> Badge("Sending", Tone.Info)
                                l.sentToKitchen -> Badge("In kitchen", Tone.Ok)
                                else -> Badge("Not sent", Tone.Warn)
                            }
                        }
                    },
                    onClick = if (closed == null && !o.sending) ({ lineMenu = l }) else null,
                )
            }
            item {
                VGap(Gap.field)
                KeyValue("Total", "₹" + o.total, bold = true)
                o.note?.takeIf { it.isNotBlank() }?.let { KeyValue("Note", it) }
                VGap(Space.s7)
            }
        }
        if (closed == null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.field)) {
                PrimaryButton(
                    "Add dishes",
                    { addMore(com.magicbill.app.nav.NewOrder(tableId = o.tableId, tableLabel = o.tableLabel, orderType = o.orderType, orderId = vm.orderId)) },
                    Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Add,
                    enabled = !busy && !o.sending,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.field)) {
                    val unsent = view.lines.any { !it.sentToKitchen }
                    if (unsent) SecondaryButton("Send to kitchen", { vm.send(Ops.sendToKitchen(), "Send to kitchen", reporter::say) }, Modifier.weight(1f), enabled = !busy && !o.sending)
                    SecondaryButton(if (o.billAsked) "Print bill again" else "Print bill", { vm.send(Ops.printBill(), "Print the bill", reporter::say) }, Modifier.weight(1f), enabled = !busy && !o.sending && view.lines.isNotEmpty())
                }
                VGap(Space.s2)
            }
        }
    }

    lineMenu?.let { l ->
        Sheet("${l.qty} × ${l.name}", onDismiss = { lineMenu = null }) {
            var qty by remember { mutableStateOf(l.qty) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("How many", style = Mb.type.body, color = Mb.colors.ink)
                Stepper(qty, onMinus = { qty = step(qty, -1) }, onPlus = { qty = step(qty, +1) })
            }
            VGap(Gap.group)
            PrimaryButton("Change to $qty", { lineMenu = null; vm.send(Ops.setQty(l.line, qty), "$qty × ${l.name}", reporter::say) }, Modifier.fillMaxWidth(), enabled = qty != l.qty)
            VGap(Gap.field)
            SecondaryButton("Take it off the order", { lineMenu = null; reasonFor = "void:${l.line}:${l.name}" }, Modifier.fillMaxWidth())
        }
    }

    if (more) {
        Sheet(null, onDismiss = { more = false }) {
            ListRow("Move to another table", onClick = { more = false; moving = true })
            ListRow("Note for the kitchen", o?.note, onClick = { more = false; noting = true })
            ListRow("Cancel this order", onClick = { more = false; reasonFor = "cancel" }, titleColor = Mb.colors.danger)
        }
    }

    reasonFor?.let { key ->
        val isCancel = key == "cancel"
        val lineName = key.split(":").getOrNull(2)
        Sheet(if (isCancel) "Cancel the order — why?" else "Take off $lineName — why?", onDismiss = { reasonFor = null }) {
            var reason by remember { mutableStateOf("") }
            ChipRow(listOf("Customer changed mind", "Wrong item", "Not available", "Too long"), reason) { reason = it }
            VGap(Gap.field)
            Field(reason, { reason = it }, "Reason", ime = ImeAction.Done)
            VGap(Gap.group)
            PrimaryButton(if (isCancel) "Cancel the order" else "Take it off", {
                reasonFor = null
                if (isCancel) vm.send(Ops.cancelOrder(reason), "Cancel the order", reporter::say)
                else vm.send(Ops.voidItem(key.split(":")[1].toInt(), reason), "Take off $lineName", reporter::say)
            }, Modifier.fillMaxWidth(), enabled = reason.isNotBlank())
        }
    }

    if (moving) {
        Sheet("Move to which table?", onDismiss = { moving = false }) {
            val free = view.tables.filter { it.state.isBlank() || it.state == "free" }
            if (free.isEmpty()) Text("No free table right now.", style = Mb.type.body, color = Mb.colors.inkMuted)
            Column(Modifier.heightIn(max = 360.dp)) {
                LazyColumn { items(free, key = { it.id }) { t -> ListRow("Table ${t.label}", t.section, onClick = { moving = false; vm.send(Ops.moveTable(t.id), "Move to table ${t.label}", reporter::say) }) } }
            }
        }
    }

    if (noting) {
        Sheet("Note for the kitchen", onDismiss = { noting = false }) {
            var note by remember { mutableStateOf(o?.note ?: "") }
            Field(note, { note = it }, "Note", placeholder = "Less spicy, serve together…", ime = ImeAction.Done)
            VGap(Gap.group)
            PrimaryButton("Save the note", { noting = false; vm.send(Ops.setOrderNote(note.ifBlank { null }), "Order note", reporter::say) }, Modifier.fillMaxWidth())
        }
    }
}

/** "2" → "3"; "0.5" → "1.5"; never below 0.5. */
internal fun step(qty: String, by: Int): String {
    val t = Money.parseQty(qty) ?: 1000L
    val n = (t + by * 1000L).coerceAtLeast(500L)
    return Money.qty(n)
}
