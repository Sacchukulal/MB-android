package com.magicbill.app.ui.screens.owner

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.ReportData
import com.magicbill.app.core.billClock
import com.magicbill.app.core.billTime
import com.magicbill.app.core.Exporter
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.istDayString
import com.magicbill.app.core.longDate
import com.magicbill.app.core.shiftDay
import com.magicbill.app.core.shortDate
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.AnimatedRupees
import com.magicbill.app.ui.components.CacheChip
import com.magicbill.app.ui.components.DeltaChip
import com.magicbill.app.ui.components.ListRow
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.MBPermissionGate
import com.magicbill.app.ui.components.MBTextField
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SegmentedChips
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.StackedSplitBar
import com.magicbill.app.ui.theme.PaymentColors
import java.time.Instant
import java.time.ZoneOffset

private val RANGES = listOf("Today", "Yesterday", "7 days", "This month", "Custom")

private fun rangeFor(index: Int, customFrom: String?, customTo: String?): Pair<String, String> {
    val today = istDayString()
    return when (index) {
        0 -> today to today
        1 -> shiftDay(today, -1) to shiftDay(today, -1)
        2 -> shiftDay(today, -6) to today
        3 -> today.take(8) + "01" to today
        else -> (customFrom ?: today) to (customTo ?: today)
    }
}

/**
 * Date-range reports: pick a range, read the story — totals, split,
 * item-wise sales, expenses — then drill into bills (searchable, filterable)
 * or share the whole thing as PDF/CSV.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    rootViewModel: RootViewModel,
    owner: MBSession.Owner,
    onOpenBill: (String) -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val licenseKey = owner.active.licenseKey
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val restaurantName = owner.active.name

    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    var customPickerOpen by rememberSaveable { mutableStateOf(false) }

    var billSearch by rememberSaveable { mutableStateOf("") }
    var billFilter by rememberSaveable { mutableIntStateOf(0) } // 0=All,1=Cash,2=Card,3=UPI,4=Credit
    var sortByQty by rememberSaveable { mutableStateOf(false) }

    val (fromDay, toDay) = rangeFor(rangeIndex, customFrom, customTo)
    LaunchedEffect(licenseKey, fromDay, toDay) {
        viewModel.load(licenseKey, fromDay, toDay)
    }

    // A new range means a new story — glide to the top instead of letting the
    // list clamp to a random offset when the content gets shorter.
    val listState = rememberLazyListState()
    LaunchedEffect(fromDay, toDay) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(0)
        }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing && state.data != null,
        onRefresh = { viewModel.load(licenseKey, fromDay, toDay, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            item {
                Spacer(Modifier.statusBarsPadding().height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reports", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    CacheChip(state.updatedAt, visible = state.fromCacheOnly)
                    MBPermissionGate(PermissionKey.ExportReports) {
                        val data = state.data
                        if (data != null) {
                            IconButton(onClick = { sharePdf(context, data, restaurantName) }) {
                                Icon(
                                    Icons.Outlined.IosShare,
                                    contentDescription = "Share PDF",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { shareCsv(context, data, restaurantName) }) {
                                Icon(
                                    Icons.Outlined.TableView,
                                    contentDescription = "Export CSV",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SegmentedChips(
                    options = RANGES,
                    selectedIndex = rangeIndex,
                    onSelect = { index ->
                        if (index == RANGES.lastIndex) customPickerOpen = true
                        else rangeIndex = index
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (fromDay == toDay) longDate(fromDay)
                    else "${longDate(fromDay)} — ${longDate(toDay)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val data = state.data
            when {
                data == null && state.refreshing -> item {
                    Spacer(Modifier.height(32.dp))
                    SkeletonScreen()
                }

                data == null && state.error != null -> item {
                    Spacer(Modifier.height(48.dp))
                    MBErrorState(
                        state.error!!,
                        onRetry = { viewModel.load(licenseKey, fromDay, toDay, force = true) },
                    )
                }

                data != null -> {
                    item {
                        Spacer(Modifier.height(22.dp))
                        AnimatedRupees(data.total, style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DeltaChip(
                                current = data.total,
                                previous = data.prevTotal,
                                label = "vs previous",
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${data.billCount} bills · ${formatINR(data.avg, decimals = 0)} avg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        StatLine("Subtotal", formatINR(data.subtotal))
                        StatLine("GST collected", formatINR(data.gst))
                        MBPermissionGate(PermissionKey.ViewExpenses) {
                            StatLine("Expenses", formatINR(data.expenseTotal))
                        }

                        SectionHeader("Payment split")
                        StackedSplitBar(
                            cash = data.cash, card = data.card,
                            upi = data.upi, credit = data.credit,
                        )
                    }

                    if (data.items.isNotEmpty()) {
                        item {
                            SectionHeader("Item-wise sales") {
                                TextButton(onClick = { sortByQty = !sortByQty }) {
                                    Text(
                                        if (sortByQty) "By quantity ↓" else "By amount ↓",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        val items = if (sortByQty) {
                            data.items.sortedByDescending { it.quantity }
                        } else data.items
                        items(items.size) { i ->
                            val item = items[i]
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Text(
                                    "×${item.quantity.toLong()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 14.dp),
                                )
                                Text(
                                    formatINR(item.amount, decimals = 0),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }

                    item {
                        SectionHeader("Bills")
                        MBTextField(
                            value = billSearch,
                            onValueChange = { billSearch = it },
                            label = "Search bills",
                            placeholder = "Bill number, table, item…",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        SegmentedChips(
                            options = listOf("All", "Cash", "Card", "UPI", "Credit"),
                            selectedIndex = billFilter,
                            onSelect = { billFilter = it },
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    val filtered = data.bills.filter { bill ->
                        val modeOk = when (billFilter) {
                            1 -> bill.payment_mode.equals("cash", true)
                            2 -> bill.payment_mode.equals("card", true)
                            3 -> bill.payment_mode.equals("upi", true)
                            4 -> bill.payment_mode.equals("credit", true)
                            else -> true
                        }
                        val q = billSearch.trim().lowercase()
                        val searchOk = q.isEmpty() ||
                            bill.bill_number?.lowercase()?.contains(q) == true ||
                            bill.table_number?.lowercase()?.contains(q) == true ||
                            bill.items?.any { it.name.lowercase().contains(q) } == true
                        modeOk && searchOk
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No bills match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(filtered.size, key = { filtered[it].id }) { i ->
                        val bill = filtered[i]
                        val modeColor = when ((bill.payment_mode ?: "").lowercase()) {
                            "cash" -> PaymentColors.cashDark
                            "card" -> PaymentColors.cardDark
                            "upi" -> PaymentColors.upiDark
                            "credit" -> PaymentColors.creditDark
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        MBPermissionGate(
                            PermissionKey.ViewBills,
                            fallback = {
                                BillRowContent(bill, modeColor, onClick = null)
                            },
                        ) {
                            BillRowContent(bill, modeColor, onClick = { onOpenBill(bill.id) })
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(130.dp)) }
        }
    }

    if (customPickerOpen) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { customPickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis ?: start
                        if (start != null) {
                            customFrom = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC)
                                .toLocalDate().toString()
                            customTo = Instant.ofEpochMilli(end!!).atZone(ZoneOffset.UTC)
                                .toLocalDate().toString()
                            rangeIndex = RANGES.lastIndex
                        }
                        customPickerOpen = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { customPickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BillRowContent(
    bill: com.magicbill.app.core.BillRow,
    modeColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)?,
) {
    ListRow(
        title = buildString {
            append(bill.bill_number ?: "Bill")
            if (!bill.table_number.isNullOrBlank()) append(" · Table ${bill.table_number}")
        },
        subtitle = "${billTime(bill.billed_at)} · ${bill.payment_mode ?: "—"}",
        icon = Icons.Outlined.ReceiptLong,
        iconTint = modeColor,
        onClick = onClick,
        trailing = {
            Text(
                formatINR(bill.total ?: 0.0, decimals = 0),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        },
    )
}

private fun sharePdf(context: android.content.Context, data: ReportData, restaurantName: String) {
    Exporter.shareReportPdf(
        context = context,
        restaurantName = restaurantName,
        fromDay = data.fromDay, toDay = data.toDay,
        total = data.total, subtotal = data.subtotal, gst = data.gst,
        billCount = data.billCount, avg = data.avg,
        cash = data.cash, card = data.card, upi = data.upi, credit = data.credit,
        items = data.items.map { Triple(it.name, it.quantity, it.amount) },
        expenseTotal = data.expenseTotal,
    )
}

private fun shareCsv(context: android.content.Context, data: ReportData, restaurantName: String) {
    fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
    Exporter.shareReportCsv(
        context = context,
        restaurantName = restaurantName,
        fromDay = data.fromDay, toDay = data.toDay,
        total = data.total, subtotal = data.subtotal, gst = data.gst,
        billCount = data.billCount, avg = data.avg,
        cash = data.cash, card = data.card, upi = data.upi, credit = data.credit,
        expenseTotal = data.expenseTotal,
        items = data.items.map { Triple(it.name, it.quantity, it.amount) },
        bills = data.bills.map { b ->
            arrayOf(
                q(b.bill_number ?: ""), q(billTime(b.billed_at)), q(b.order_type ?: ""),
                q(b.table_number ?: ""), q(b.payment_mode ?: ""),
                "%.2f".format(b.subtotal ?: 0.0), "%.2f".format(b.gst ?: 0.0),
                "%.2f".format(b.total ?: 0.0),
            )
        },
    )
}
