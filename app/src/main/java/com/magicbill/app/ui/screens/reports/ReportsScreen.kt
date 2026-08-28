package com.magicbill.app.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.ReportMath
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.BillJson
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Exporter
import com.magicbill.app.core.Ist
import com.magicbill.app.core.Money
import com.magicbill.app.core.paiseToRupees
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.ChipRow
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.IconAction
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.Legend
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.SplitBar
import com.magicbill.app.ui.kit.Stat
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.screens.Ranges
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import com.magicbill.app.ui.theme.payment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val account: Account,
    private val sync: Sync,
    private val db: MbDatabase,
    private val clock: Clock,
) : ViewModel() {
    data class Report(
        val range: Ist.Range,
        val totals: ReportMath.Totals = ReportMath.Totals(),
        val before: ReportMath.Totals = ReportMath.Totals(),
        val perDay: List<Pair<LocalDate, Long>> = emptyList(),
        val items: List<ReportMath.ItemTotal> = emptyList(),
        val categories: List<ReportMath.CategoryTotal> = emptyList(),
        val expenses: List<ReportMath.ExpenseGroup> = emptyList(),
        val ready: Boolean = false,
    )

    val today: LocalDate get() = Ist.today(clock.now())
    private val chosen = MutableStateFlow("Today")
    private val custom = MutableStateFlow<Ist.Range?>(null)
    val choice: StateFlow<String> get() = chosen

    private val range: kotlinx.coroutines.flow.Flow<Ist.Range> = combine(chosen, custom) { name, c -> if (name == Ranges.CUSTOM && c != null) c else Ranges.of(name, today) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val report: StateFlow<Report> = range.flatMapLatest { rg ->
        account.perShop(Report(rg)) { r ->
            val prev = rg.previous()
            combine(
                db.totals().days(r, Ist.key(prev.from), Ist.key(rg.to)),
                db.totals().items(r, Ist.key(rg.from), Ist.key(rg.to)),
                db.totals().categories(r, Ist.key(rg.from), Ist.key(rg.to)),
                db.expenses().between(r, Ist.key(rg.from), Ist.key(rg.to)),
            ) { days, items, cats, expenses ->
                val inRange = days.filter { d -> Ist.parseDay(d.businessDay)?.let { rg.contains(it) } == true }
                val beforeRows = days.filter { d -> Ist.parseDay(d.businessDay)?.let { prev.contains(it) } == true }
                Report(
                    range = rg,
                    totals = ReportMath.totals(inRange),
                    before = ReportMath.totals(beforeRows),
                    perDay = ReportMath.perDay(inRange, rg),
                    items = ReportMath.topItems(items),
                    categories = ReportMath.categories(cats),
                    expenses = ReportMath.expensesByCategory(expenses),
                    ready = true,
                )
            }.flowOn(Dispatchers.Default)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Report(Ist.today(today)))

    fun pick(name: String) { chosen.value = name }
    fun pickCustom(from: LocalDate, to: LocalDate) { custom.value = Ist.Range(from, to, Ranges.CUSTOM); chosen.value = Ranges.CUSTOM }
    fun opened() = sync.pullIfStale()

    /** One row per day straight from the mirror's totals, then one per item. */
    suspend fun csv(context: android.content.Context): android.content.Intent {
        val r = report.value
        val header = listOf("Day", "Bills", "Gross", "Discount", "Tax", "Charges", "Net", "Expenses")
        val shop = account.current.value?.id
        val days = if (shop == null) emptyList() else db.totals().days(shop, Ist.key(r.range.from), Ist.key(r.range.to)).first()
        val body = days.map { d -> listOf(d.businessDay, d.bills.toString(), Money.plain(d.grossPaise), Money.plain(d.discountPaise), Money.plain(d.taxPaise), Money.plain(d.chargesPaise), Money.plain(d.netPaise), Money.plain(d.expensesPaise)) }
        val items = r.items.map { listOf("ITEM " + it.name, Money.qty(it.qtyThousandths), "", "", "", "", Money.plain(it.salesPaise), "") }
        return Exporter.csv(context, "magic-bill-${r.range.from}-${r.range.to}", header, body + items)
    }

    suspend fun pdf(context: android.content.Context): android.content.Intent {
        val r = report.value
        val t = r.totals
        val lines = ArrayList<String>()
        lines += Ranges.words(r.range, today)
        lines += ""
        lines += Exporter.line("Bills", t.bills.toString())
        lines += Exporter.line("Gross sales", Money.plain(t.grossPaise))
        lines += Exporter.line("Discount", Money.plain(t.discountPaise))
        lines += Exporter.line("Tax", Money.plain(t.taxPaise))
        lines += Exporter.line("Charges", Money.plain(t.chargesPaise))
        lines += Exporter.line("Net sales", Money.plain(t.netPaise))
        lines += Exporter.line("Expenses", Money.plain(t.expensesPaise))
        lines += ""
        lines += "Money in"
        t.byPayment.forEach { (m, p) -> lines += Exporter.line("  " + BillJson.modeLabel(m), Money.plain(p)) }
        lines += ""
        lines += "Items"
        r.items.forEach { lines += Exporter.line("  " + it.name + " × " + Money.qty(it.qtyThousandths), Money.plain(it.salesPaise)) }
        lines += ""
        lines += "Categories"
        r.categories.forEach { lines += Exporter.line("  " + it.name, Money.plain(it.salesPaise)) }
        lines += ""
        lines += "Expenses"
        r.expenses.forEach { lines += Exporter.line("  " + it.category, Money.plain(it.paise)) }
        return Exporter.pdf(context, "magic-bill-${r.range.from}-${r.range.to}", (account.current.value?.name ?: "Magic Bill") + " — " + Ranges.words(r.range, today), lines)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(openBill: (String) -> Unit, vm: ReportsViewModel = hiltViewModel()) {
    val billsVm: com.magicbill.app.ui.screens.bills.BillsViewModel = hiltViewModel()
    val report by vm.report.collectAsStateWithLifecycle()
    val choice by vm.choice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var showAllItems by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.opened() }
    val t = report.totals

    Page("Reports", Ranges.words(report.range, vm.today), actions = { IconAction(Icons.Outlined.IosShare, "Share", { sharing = true }) }) {
        ChipRow(Ranges.names + Ranges.CUSTOM, choice) { if (it == Ranges.CUSTOM) picking = true else vm.pick(it) }
        VGap(Gap.group)
        Text("Net sales", style = Mb.type.label, color = Mb.colors.inkMuted)
        com.magicbill.app.ui.components.AnimatedRupees(t.netPaise.paiseToRupees())
        VGap(Space.s2)
        com.magicbill.app.ui.components.DeltaChip(
            t.netPaise.paiseToRupees(),
            report.before.netPaise.takeIf { it > 0 }?.paiseToRupees(),
            label = if (report.range.days == 1L) "vs the day before" else "vs the ${report.range.days} days before",
        )
        VGap(Gap.group)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.field)) {
            Stat("Bills", t.bills.toString(), Modifier.weight(1f), sub = if (t.voids > 0) "${t.voids} voided" else null)
            Stat("Avg bill", Money.whole(t.averageBillPaise), Modifier.weight(1f))
            Stat("Per day", Money.whole(if (t.daysWithSales > 0) t.netPaise / t.daysWithSales else 0), Modifier.weight(1f), sub = "${t.daysWithSales} open")
        }
        if (report.range.days > 1) {
            Section("By day")
            val fmt = DateTimeFormatter.ofPattern(if (report.range.days <= 7) "EEE" else "d MMM")
            com.magicbill.app.ui.components.TrendChart(
                report.perDay.map { (d, v) -> com.magicbill.app.ui.components.TrendPoint(d.format(fmt), v.paiseToRupees()) },
            )
            report.perDay.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { (d, v) ->
                VGap(Gap.field)
                Text("Best day: " + Ist.dateWords(d, vm.today) + " · " + Money.rupees(v), style = Mb.type.caption, color = Mb.colors.inkMuted)
            }
        }
        Section("Breakdown")
        Panel {
            KeyValue("Gross sales", Money.rupees(t.grossPaise))
            KeyValue("Discount", "− " + Money.rupees(t.discountPaise))
            KeyValue("Tax", Money.rupees(t.taxPaise))
            KeyValue("Charges", Money.rupees(t.chargesPaise))
            KeyValue("Net sales", Money.rupees(t.netPaise), bold = true)
            KeyValue("Expenses", "− " + Money.rupees(t.expensesPaise))
            KeyValue("After expenses", Money.rupees(t.afterExpensesPaise), bold = true, valueColor = if (t.afterExpensesPaise < 0) Mb.colors.danger else null)
            if (t.creditGivenPaise > 0 || t.creditCollectedPaise > 0) {
                KeyValue("Credit given", Money.rupees(t.creditGivenPaise))
                KeyValue("Credit collected", Money.rupees(t.creditCollectedPaise))
            }
        }
        if (t.byPayment.isNotEmpty()) {
            Section("Money in")
            SplitBar(t.byPayment.map { (m, p) -> Triple(BillJson.modeLabel(m), p, Mb.colors.payment(m)) })
            VGap(Gap.field)
            Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) { t.byPayment.forEach { (m, p) -> Legend(BillJson.modeLabel(m), Mb.colors.payment(m), Money.rupees(p)) } }
        }
        if (report.items.isNotEmpty()) {
            Section("Item-wise", trailing = { if (report.items.size > 8) QuietButton(if (showAllItems) "Fewer" else "All ${report.items.size}", { showAllItems = !showAllItems }) })
            report.items.take(if (showAllItems) report.items.size else 8).forEachIndexed { i, item ->
                ListRow(item.name, Money.qty(item.qtyThousandths) + " sold", trailing = { Text(Money.rupees(item.salesPaise), style = Mb.type.cell, color = Mb.colors.ink) })
                RowLine()
            }
        }
        if (report.categories.isNotEmpty()) {
            Section("Category-wise")
            val total = report.categories.sumOf { it.salesPaise }.coerceAtLeast(1)
            report.categories.forEach { c ->
                ListRow(c.name, "${c.salesPaise * 100 / total}% · " + Money.qty(c.qtyThousandths) + " sold", trailing = { Text(Money.rupees(c.salesPaise), style = Mb.type.cell, color = Mb.colors.ink) })
                RowLine()
            }
        }
        if (report.expenses.isNotEmpty()) {
            Section("Expenses")
            report.expenses.forEach { e ->
                ListRow(e.category, "${e.count} entries", trailing = { Text(Money.rupees(e.paise), style = Mb.type.cell, color = Mb.colors.ink) })
                RowLine()
            }
        }
        if (report.ready && t.bills == 0 && report.items.isEmpty()) { VGap(Gap.group); Empty("Nothing sold in these days.") }

        // Bills live INSIDE Reports — one place, as the 2.x app had it, following the same days.
        androidx.compose.runtime.LaunchedEffect(choice) { if (choice != Ranges.CUSTOM) billsVm.pick(choice) }
        val bills by billsVm.bills.collectAsStateWithLifecycle()
        val billSearch by billsVm.search.collectAsStateWithLifecycle()
        val billFilter by billsVm.filter.collectAsStateWithLifecycle()
        Section("Bills", trailing = { Text("${bills.size}", style = Mb.type.caption, color = Mb.colors.inkMuted) })
        com.magicbill.app.ui.kit.SearchField(billSearch, billsVm::setSearch, "Bill number, table, customer, staff")
        VGap(Gap.field)
        ChipRow(billsVm.filters, billFilter) { billsVm.setFilter(it) }
        VGap(Gap.field)
        if (bills.isEmpty()) {
            Text(if (billSearch.isBlank()) "No bills in these days." else "Nothing matches.", style = Mb.type.body, color = Mb.colors.inkMuted)
        }
        bills.take(100).forEach { b ->
            com.magicbill.app.ui.kit.ListRow(
                title = b.billNumber + (b.tokenNumber?.let { "  ·  #$it" } ?: ""),
                subtitle = listOfNotNull(Ist.clock(b.createdAtMs), b.tableName?.let { "Table $it" }, b.customerName).joinToString(" · "),
                trailing = {
                    Text(Money.rupees(b.grandTotalPaise), style = Mb.type.cell, color = if (b.status == "voided") Mb.colors.inkFaint else Mb.colors.ink)
                },
                onClick = { openBill(b.id) },
            )
        }
    }

    if (picking) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = state.selectedStartDateMillis; val b = state.selectedEndDateMillis
                    if (a != null) {
                        val from = java.time.Instant.ofEpochMilli(a).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        val to = java.time.Instant.ofEpochMilli(b ?: a).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        vm.pickCustom(from, to)
                    }
                    picking = false
                }) { Text("Use these days") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DateRangePicker(state, showModeToggle = false) }
    }

    if (sharing) {
        Sheet("Share this report", onDismiss = { sharing = false }) {
            ListRow("As a PDF", "For a person", onClick = { sharing = false; vm.viewModelScope.launch { context.startActivity(vm.pdf(context)) } })
            RowLine()
            ListRow("As a CSV", "For a spreadsheet", onClick = { sharing = false; vm.viewModelScope.launch { context.startActivity(vm.csv(context)) } })
        }
    }
}
