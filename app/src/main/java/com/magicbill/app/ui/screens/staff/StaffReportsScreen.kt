package com.magicbill.app.ui.screens.staff

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.Exporter
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.StaffReport
import com.magicbill.app.core.billTime
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.has
import com.magicbill.app.core.istDayString
import com.magicbill.app.core.longDate
import com.magicbill.app.core.shiftDay
import com.magicbill.app.data.MBSession
import java.time.Instant
import java.time.ZoneOffset
import com.magicbill.app.ui.components.AnimatedCount
import com.magicbill.app.ui.components.AnimatedRupees
import com.magicbill.app.ui.components.CacheChip
import com.magicbill.app.ui.components.ListRow
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SegmentedChips
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.StackedSplitBar
import com.magicbill.app.ui.theme.PaymentColors

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

/** Staff reports — same story as the owner's, shaped by permissions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffReportsScreen(
    session: MBSession.Staff,
    onOpenBill: (String) -> Unit,
    viewModel: StaffReportsViewModel = hiltViewModel(),
) {
    val perms = session.staff.permissions
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    var customFrom by rememberSaveable { mutableStateOf<String?>(null) }
    var customTo by rememberSaveable { mutableStateOf<String?>(null) }
    var customPickerOpen by rememberSaveable { mutableStateOf(false) }
    val (fromDay, toDay) = rangeFor(rangeIndex, customFrom, customTo)

    LaunchedEffect(fromDay, toDay) { viewModel.load(fromDay, toDay) }

    PullToRefreshBox(
        isRefreshing = state.refreshing && state.data != null,
        onRefresh = { viewModel.load(fromDay, toDay, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            item {
                Spacer(Modifier.statusBarsPadding().height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reports", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    CacheChip(state.updatedAt, visible = state.fromCacheOnly)
                    val data = state.data
                    if (data != null && data.total != null &&
                        perms.has(PermissionKey.ExportReports)
                    ) {
                        IconButton(onClick = { staffSharePdf(context, session.restaurant.name, data) }) {
                            Icon(
                                Icons.Outlined.IosShare,
                                contentDescription = "Share PDF",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { staffShareCsv(context, session.restaurant.name, data) }) {
                            Icon(
                                Icons.Outlined.TableView,
                                contentDescription = "Export CSV",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                    if (fromDay == toDay) longDate(fromDay) else "${longDate(fromDay)} — ${longDate(toDay)}",
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
                        onRetry = { viewModel.load(fromDay, toDay, force = true) },
                    )
                }

                data != null -> {
                    item {
                        Spacer(Modifier.height(22.dp))
                        if (data.total != null) {
                            AnimatedRupees(data.total!!, style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${data.billCount} bills" +
                                    (data.avg?.let { " · ${formatINR(it, decimals = 0)} avg" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                AnimatedCount(data.billCount, style = MaterialTheme.typography.displaySmall)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "bills",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                        }

                        SectionHeader("Payment split")
                        if (data.split.kind == "amounts") {
                            StackedSplitBar(
                                cash = data.split.cash, card = data.split.card,
                                upi = data.split.upi, credit = data.split.credit,
                            )
                        } else {
                            StackedSplitBar(
                                cash = null, card = null, upi = null, credit = null,
                                pctFallback = listOf(
                                    data.split.cash.toFloat(), data.split.card.toFloat(),
                                    data.split.upi.toFloat(), data.split.credit.toFloat(),
                                ),
                            )
                        }

                        if (perms.has(PermissionKey.ViewExpenses) && data.expenseTotal != null) {
                            SectionHeader("Expenses")
                            StatLine("Total expenses", formatINR(data.expenseTotal))
                        }
                    }

                    if (data.items.isNotEmpty()) {
                        item { SectionHeader("Item-wise sales") }
                        items(data.items.size) { i ->
                            val item = data.items[i]
                            ListRow(
                                title = item.name,
                                subtitle = "${item.quantity.toLong()} sold",
                                trailing = {
                                    item.amount?.let {
                                        Text(
                                            formatINR(it, decimals = 0),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                },
                            )
                        }
                    }

                    val bills = data.bills
                    if (bills != null) {
                        item { SectionHeader("Bills · ${bills.size}") }
                        items(bills.size, key = { bills[it].id }) { i ->
                            val bill = bills[i]
                            val canOpen = perms.has(PermissionKey.ViewBills)
                            val modeColor = when ((bill.payment_mode ?: "").lowercase()) {
                                "cash" -> PaymentColors.cashDark
                                "card" -> PaymentColors.cardDark
                                "upi" -> PaymentColors.upiDark
                                "credit" -> PaymentColors.creditDark
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            ListRow(
                                title = buildString {
                                    append(bill.bill_number ?: "Bill")
                                    if (!bill.table_number.isNullOrBlank()) {
                                        append(" · Table ${bill.table_number}")
                                    }
                                },
                                subtitle = "${billTime(bill.billed_at)} · ${bill.payment_mode ?: "—"}",
                                icon = Icons.Outlined.ReceiptLong,
                                iconTint = modeColor,
                                onClick = if (canOpen) {
                                    { onOpenBill(bill.id) }
                                } else null,
                                trailing = {
                                    bill.total?.let {
                                        Text(
                                            formatINR(it, decimals = 0),
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                        )
                                    }
                                },
                            )
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

private fun staffSharePdf(context: android.content.Context, restaurantName: String, data: StaffReport) {
    Exporter.shareReportPdf(
        context = context,
        restaurantName = restaurantName,
        fromDay = data.from, toDay = data.to,
        total = data.total ?: 0.0,
        subtotal = data.subtotal ?: 0.0,
        gst = data.gst ?: 0.0,
        billCount = data.billCount,
        avg = data.avg ?: 0.0,
        cash = data.split.cash, card = data.split.card,
        upi = data.split.upi, credit = data.split.credit,
        items = data.items.map { Triple(it.name, it.quantity, it.amount ?: 0.0) },
        expenseTotal = data.expenseTotal ?: 0.0,
    )
}

private fun staffShareCsv(context: android.content.Context, restaurantName: String, data: StaffReport) {
    fun q(s: String) = "\"${s.replace("\"", "\"\"")}\""
    // Staff bill rows carry fewer columns than the owner's; the Type/Subtotal/GST
    // cells stay blank so the CSV keeps the same shape as owner exports.
    val bills = data.bills.orEmpty().map { b ->
        arrayOf(
            q(b.bill_number ?: ""), q(billTime(b.billed_at)), "",
            q(b.table_number ?: ""), q(b.payment_mode ?: ""),
            "", "", "%.2f".format(b.total ?: 0.0),
        )
    }
    Exporter.shareReportCsv(
        context = context,
        restaurantName = restaurantName,
        fromDay = data.from, toDay = data.to,
        total = data.total ?: 0.0,
        subtotal = data.subtotal ?: 0.0,
        gst = data.gst ?: 0.0,
        billCount = data.billCount,
        avg = data.avg ?: 0.0,
        cash = data.split.cash, card = data.split.card,
        upi = data.split.upi, credit = data.split.credit,
        expenseTotal = data.expenseTotal ?: 0.0,
        items = data.items.map { Triple(it.name, it.quantity, it.amount ?: 0.0) },
        bills = bills,
    )
}
