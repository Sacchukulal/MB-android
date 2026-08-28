package com.magicbill.app.ui.screens.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.BillJson
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Exporter
import com.magicbill.app.core.Ist
import com.magicbill.app.core.Money
import com.magicbill.app.db.BillRow
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.nav.BillDetail
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.ChipRow
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.IconAction
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.SearchField
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.screens.Ranges
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillsViewModel @Inject constructor(private val account: Account, private val sync: Sync, private val db: MbDatabase, private val clock: Clock) : ViewModel() {
    private val chosen = MutableStateFlow("Today")
    private val query = MutableStateFlow("")
    private val mode = MutableStateFlow("All")
    val choice: StateFlow<String> get() = chosen
    val search: StateFlow<String> get() = query
    val filter: StateFlow<String> get() = mode
    val today get() = Ist.today(clock.now())
    val filters = listOf("All", "Cash", "UPI", "Card", "Credit", "Voided")

    @OptIn(ExperimentalCoroutinesApi::class)
    val bills: StateFlow<List<BillRow>> = combine(chosen, query, mode) { c, q, m -> Triple(c, q, m) }.flatMapLatest { (c, q, m) ->
        val rg = Ranges.of(c, today)
        account.perShop(emptyList<BillRow>()) { r ->
            val base = if (q.isBlank()) db.bills().between(r, Ist.key(rg.from), Ist.key(rg.to)) else db.bills().search(r, Ist.key(rg.from), Ist.key(rg.to), "%${q.trim()}%")
            kotlinx.coroutines.flow.flow { base.collect { list -> emit(list.filter { keep(it, m) }) } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun keep(b: BillRow, m: String): Boolean = when (m) {
        "All" -> true
        "Voided" -> b.status == "voided"
        else -> b.status != "voided" && BillJson.payments(b.payments).first.any { it.label.equals(m, ignoreCase = true) }
    }

    fun pick(name: String) { chosen.value = name }
    fun setSearch(q: String) { query.value = q }
    fun setFilter(m: String) { mode.value = m }
    fun opened() = sync.pullIfStale()
}

@Composable
fun BillsScreen(open: (String) -> Unit, vm: BillsViewModel = hiltViewModel()) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val choice by vm.choice.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.opened() }
    val total = bills.filter { it.status != "voided" }.sumOf { it.grandTotalPaise }

    Page("Bills", "${bills.size} bills · " + Money.rupees(total), scroll = false, bottomPadding = 0.dp) {
        SearchField(search, vm::setSearch, "Bill number, table, customer, staff")
        VGap(Gap.field)
        ChipRow(Ranges.names, choice) { vm.pick(it) }
        VGap(Gap.inline)
        ChipRow(vm.filters, filter) { vm.setFilter(it) }
        VGap(Gap.field)
        if (bills.isEmpty()) {
            Empty(if (search.isBlank()) "No bills in these days." else "Nothing matches.")
        } else {
            val grouped = bills.groupBy { it.businessDay }
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.s7)) {
                grouped.forEach { (day, list) ->
                    item(key = "h$day") {
                        Text(Ist.parseDay(day)?.let { Ist.dateWords(it, vm.today) } ?: day, style = Mb.type.label, color = Mb.colors.inkMuted, modifier = Modifier.padding(top = Gap.field, bottom = Space.s1))
                    }
                    items(list, key = { it.id }) { b -> BillRowView(b, open) }
                }
            }
        }
    }
}

@Composable
private fun BillRowView(b: BillRow, open: (String) -> Unit) {
    val where = listOfNotNull(b.tableName?.let { "Table $it" }, b.customerName, b.staffName).joinToString(" · ")
    val pays = BillJson.payments(b.payments).first.joinToString("+") { it.label }
    ListRow(
        title = b.billNumber + (b.tokenNumber?.let { "  ·  #$it" } ?: ""),
        subtitle = listOf(Ist.clock(b.createdAtMs), where, pays).filter { it.isNotBlank() }.joinToString(" · "),
        trailing = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(Money.rupees(b.grandTotalPaise), style = Mb.type.cell, color = if (b.status == "voided") Mb.colors.inkFaint else Mb.colors.ink)
                if (b.status == "voided") Badge("Voided", Tone.Danger)
                else if (b.source != "counter" && b.source.isNotBlank()) Badge(b.source.replaceFirstChar { it.uppercase() })
            }
        },
        onClick = { open(b.id) },
    )
    RowLine()
}

@HiltViewModel
class BillDetailViewModel @Inject constructor(saved: SavedStateHandle, private val account: Account, private val db: MbDatabase, val clock: Clock) : ViewModel() {
    private val id: String = saved.toRoute<BillDetail>().id
    val bill: StateFlow<BillRow?> = account.perShop<BillRow?>(null) { r -> kotlinx.coroutines.flow.flow { emit(db.bills().byId(r, id)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun receiptText(shop: String, b: BillRow): String {
        val out = ArrayList<String>()
        out += shop
        out += "Bill ${b.billNumber}" + (b.tokenNumber?.let { "  Token $it" } ?: "")
        out += Ist.moment(b.createdAtMs, Ist.today(clock.now()))
        b.tableName?.let { out += "Table $it" }
        b.customerName?.let { out += "Customer: $it" }
        out += ""
        BillJson.lines(b.lines).forEach { l ->
            out += Exporter.line("${l.qty} × ${l.name}", Money.plain(l.grossPaise))
            l.modifiers.forEach { (n, p) -> out += Exporter.line("   + $n", Money.plain(p)) }
            l.discount?.let { out += "   $it" }
            l.note?.let { out += "   ($it)" }
        }
        out += ""
        out += Exporter.line("Subtotal", Money.plain(b.subtotalPaise))
        if (b.discountPaise != 0L) out += Exporter.line("Discount", "-" + Money.plain(b.discountPaise))
        BillJson.taxes(b.taxRows).rows.forEach { t -> out += Exporter.line("GST ${t.rateText} on ${Money.plain(t.taxablePaise)}", Money.plain(t.taxPaise)) }
        if (b.chargesPaise != 0L) out += Exporter.line("Charges", Money.plain(b.chargesPaise))
        if (b.roundOffPaise != 0L) out += Exporter.line("Round off", Money.plain(b.roundOffPaise))
        out += Exporter.line("TOTAL", Money.plain(b.grandTotalPaise))
        BillJson.payments(b.payments).first.forEach { p -> out += Exporter.line("Paid by ${p.label}", Money.plain(p.paise)) }
        if (b.status == "voided") out += "VOIDED" + (b.voidReason?.let { ": $it" } ?: "")
        return out.joinToString("\n")
    }
}

@Composable
fun BillDetailScreen(back: () -> Unit, vm: BillDetailViewModel = hiltViewModel()) {
    val bill by vm.bill.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shop = hiltViewModel<BillsViewModel>().let { "" }
    val b = bill
    Page(b?.billNumber ?: "Bill", b?.let { Ist.moment(it.createdAtMs, Ist.today(vm.clock.now())) }, back = back, actions = {
        if (b != null) IconAction(Icons.Outlined.IosShare, "Share", { vm.viewModelScope.launch { context.startActivity(Exporter.text("Bill ${b.billNumber}", vm.receiptText(shop, b))) } })
    }) {
        if (b == null) { Empty("This bill is not on the phone yet. Pull down on Bills."); return@Page }
        if (b.status == "voided") { Notice(Tone.Danger, "Voided" + (b.voidReason?.let { " — $it" } ?: ".")); VGap(Gap.field) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.inline)) {
            b.tokenNumber?.let { Badge("Token $it", Tone.Info) }
            Badge(b.orderType.replace('_', ' ').replaceFirstChar { it.uppercase() })
            b.tableName?.let { Badge("Table $it") }
            if (b.source.isNotBlank() && b.source != "counter") Badge(b.source.replaceFirstChar { it.uppercase() })
        }
        if (b.customerName != null || b.staffName != null) {
            VGap(Gap.field)
            Panel {
                b.customerName?.let { KeyValue("Customer", it) }
                b.staffName?.let { KeyValue("Billed by", it) }
            }
        }
        Section("Items")
        val lines = BillJson.lines(b.lines)
        if (lines.isEmpty()) Text("No item detail on this bill.", style = Mb.type.caption, color = Mb.colors.inkMuted)
        lines.forEach { l ->
            val subtitle = listOfNotNull(
                if (l.modifiers.isNotEmpty()) l.modifiers.joinToString(", ") { it.first } else null,
                l.discount, l.note,
            ).joinToString(" · ")
            ListRow("${l.qty} × ${l.name}", subtitle.ifBlank { Money.rupees(l.unitPricePaise) + " each" }, trailing = { Text(Money.rupees(l.grossPaise), style = Mb.type.cell, color = Mb.colors.ink) })
            RowLine()
        }
        Section("Totals")
        Panel {
            KeyValue("Subtotal", Money.rupees(b.subtotalPaise))
            if (b.discountPaise != 0L) KeyValue("Discount", "− " + Money.rupees(b.discountPaise))
            val taxes = BillJson.taxes(b.taxRows)
            taxes.rows.forEach { t -> KeyValue("GST ${t.rateText} on ${Money.rupees(t.taxablePaise)}", Money.rupees(t.taxPaise)) }
            if (taxes.rows.isEmpty() && b.taxPaise != 0L) KeyValue("Tax", Money.rupees(b.taxPaise))
            if (b.chargesPaise != 0L) KeyValue("Charges", Money.rupees(b.chargesPaise))
            if (b.roundOffPaise != 0L) KeyValue("Round off", Money.rupees(b.roundOffPaise))
            KeyValue("Total", Money.rupees(b.grandTotalPaise), bold = true)
        }
        Section("Paid")
        val (pays, tip) = BillJson.payments(b.payments)
        if (pays.isEmpty()) Text("Not paid yet.", style = Mb.type.body, color = Mb.colors.inkMuted)
        pays.forEach { p ->
            ListRow(p.label, listOfNotNull(p.reference, if (p.settlesCredit) "settles credit" else null).joinToString(" · ").ifBlank { null }, trailing = { Text(Money.rupees(p.paise), style = Mb.type.cell, color = Mb.colors.ink) })
            RowLine()
        }
        if (tip > 0) KeyValue("Tip", Money.rupees(tip))
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
