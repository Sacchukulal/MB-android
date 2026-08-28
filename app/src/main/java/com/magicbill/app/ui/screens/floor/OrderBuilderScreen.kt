package com.magicbill.app.ui.screens.floor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Money
import com.magicbill.app.core.formatINR
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.Stream
import com.magicbill.app.db.FloorItemRow
import com.magicbill.app.nav.NewOrder
import com.magicbill.app.ui.kit.ChipRow
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.PageHeader
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.SearchField
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The order builder — the 2.x screen, kept: the whole menu with + and − on each dish, the
 * running count and total at the bottom, and ONE "Send to kitchen" press that sends the whole
 * order in one round trip. Nothing reaches the counter until that press.
 */
@HiltViewModel
class OrderBuilderViewModel @Inject constructor(saved: SavedStateHandle, private val floor: Floor, val stream: Stream) : ViewModel() {
    val route: NewOrder = saved.toRoute()

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow("All")
    /** itemId → qty in thousandths. The cart lives HERE until Send. */
    private val cart = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val busyFlow = MutableStateFlow(false)
    private val sentenceFlow = MutableStateFlow<String?>(null)

    val search: StateFlow<String> get() = query
    val picked: StateFlow<String> get() = category
    val busy: StateFlow<Boolean> get() = busyFlow
    val sentence: StateFlow<String?> get() = sentenceFlow

    data class MenuRow(val item: FloorItemRow, val qtyThousandths: Long)

    val rows: StateFlow<List<MenuRow>> = combine(floor.items, query, category) { items, q, c ->
        items.filter { (c == "All" || it.category == c) && (q.isBlank() || it.name.contains(q, true)) }
    }.combine(cart) { items, inCart ->
        items.map { MenuRow(it, inCart[it.id] ?: 0L) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = floor.items.combine(cart) { items, _ ->
        listOf("All") + items.map { it.category }.filter { it.isNotBlank() }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("All"))

    data class Tally(val items: Int, val estimatePaise: Long)

    /** The bottom bar's figures — an estimate from the catalogue; the counter's total is the truth after Send. */
    val tally: StateFlow<Tally> = combine(cart, floor.items) { inCart, items ->
        val prices = items.associate { it.id to (Money.parsePlain(it.price) ?: 0L) }
        Tally(
            items = inCart.values.count { it > 0 },
            estimatePaise = inCart.entries.sumOf { (id, q) -> (prices[id] ?: 0L) * q / 1000 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Tally(0, 0))

    fun setSearch(q: String) { query.value = q }
    fun pick(c: String) { category.value = c }

    fun plus(id: String) = bump(id, +1000)
    fun minus(id: String) = bump(id, -1000)
    private fun bump(id: String, by: Long) {
        cart.value = cart.value.toMutableMap().also { m ->
            val n = ((m[id] ?: 0L) + by).coerceAtLeast(0L)
            if (n == 0L) m.remove(id) else m[id] = n
        }
    }

    /** ONE press: open (if new) + every dish + fire, as one batch. */
    fun send(done: (String) -> Unit) {
        if (busyFlow.value) return
        busyFlow.value = true
        viewModelScope.launch {
            val items = rowsNow()
            val lines = cart.value.mapNotNull { (id, q) ->
                items.firstOrNull { it.id == id }?.let { Floor.StagedLine(id, it.name, Money.qty(q), null) }
            }
            val place = Floor.Place(route.tableId, route.tableLabel, route.orderType)
            when (val a = floor.stageOrder(route.orderId, place, lines, null)) {
                is Answer.Ok -> { busyFlow.value = false; done(a.value?.says ?: "Sent to the kitchen.") }
                else -> { busyFlow.value = false; sentenceFlow.value = a.sentenceOrNull }
            }
        }
    }

    private suspend fun rowsNow(): List<FloorItemRow> = floor.items.first()

    fun opened() { viewModelScope.launch { floor.refreshCatalogue() } }
}

@Composable
fun OrderBuilderScreen(back: () -> Unit, done: () -> Unit, vm: OrderBuilderViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val picked by vm.picked.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val tally by vm.tally.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val sentence by vm.sentence.collectAsStateWithLifecycle()
    val stream by vm.stream.state.collectAsStateWithLifecycle()
    val reporter = com.magicbill.app.ui.kit.LocalReporter.current
    OnTheFloor(vm.stream)
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.opened() }

    val title = vm.route.tableLabel?.let { "Table $it" }
        ?: vm.route.orderType.replace('_', ' ').replaceFirstChar { it.uppercase() }

    Column(Modifier.fillMaxSize()) {
        PageHeader(title, if (vm.route.orderId == null) "New order" else "Adding to the order", back = back, actions = { StreamBadge(stream) })
        Column(Modifier.padding(horizontal = Gap.page)) {
            SearchField(search, vm::setSearch, "Search menu…")
            VGap(Gap.field)
            ChipRow(categories, picked) { vm.pick(it) }
            VGap(Gap.field)
        }
        if (rows.isEmpty()) {
            Empty(if (search.isBlank()) "The menu has not come from the counter yet." else "No dish by that name.")
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Gap.page, end = Gap.page, bottom = Space.s5)) {
            items(rows, key = { it.item.id }) { row -> DishRow(row, enabled = !busy, onPlus = { vm.plus(row.item.id) }, onMinus = { vm.minus(row.item.id) }) }
        }
        // The bottom bar: the count, the estimate, and the one press — exactly the 2.x bar.
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = Gap.page, vertical = Space.s3).navigationBarsPadding()) {
            if (sentence != null) {
                Text(sentence!!, style = Mb.type.caption, color = Mb.colors.danger)
                VGap(Space.s2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (tally.items == 1) "1 item" else "${tally.items} items", style = Mb.type.caption, color = Mb.colors.inkMuted)
                    Text(formatINR(tally.estimatePaise / 100.0, 0), style = Mb.type.stat, color = Mb.colors.ink)
                }
                PrimaryButton("Send to kitchen", { vm.send { says -> reporter.say(says); done() } }, enabled = tally.items > 0, busy = busy)
            }
        }
    }
}

/** One dish: name and price left; − qty + on the right, the 2.x way. No sheet, no extra taps. */
@Composable
private fun DishRow(row: OrderBuilderViewModel.MenuRow, enabled: Boolean, onPlus: () -> Unit, onMinus: () -> Unit) {
    val qty = row.qtyThousandths
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.item.name, style = MaterialTheme.typography.bodyLarge, color = if (row.item.isAvailable) Mb.colors.ink else Mb.colors.inkFaint)
            Text(
                if (row.item.isAvailable) "₹" + row.item.price.removeSuffix(".00") else "Sold out",
                style = MaterialTheme.typography.bodySmall,
                color = Mb.colors.inkMuted,
            )
        }
        if (!row.item.isAvailable) return@Row
        if (qty > 0) {
            RoundStep(Icons.Outlined.Remove, "Less", enabled, onMinus)
            Text(Money.qty(qty), style = MaterialTheme.typography.titleMedium, color = Mb.colors.ink, modifier = Modifier.padding(horizontal = Space.s3))
        }
        RoundStep(Icons.Outlined.Add, "More", enabled, onPlus)
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, what: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp).clip(CircleShape).background(Mb.colors.accent.copy(alpha = 0.14f))) {
        Icon(icon, contentDescription = what, tint = Mb.colors.accent)
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
