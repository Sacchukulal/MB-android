package com.magicbill.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.ReportMath
import com.magicbill.app.cloud.Restaurant
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.BillJson
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.core.Money
import com.magicbill.app.core.paiseToRupees
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.IconAction
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.Legend
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.SplitBar
import com.magicbill.app.ui.kit.Stat
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import com.magicbill.app.ui.theme.payment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val account: Account,
    private val syncer: Sync,
    private val db: MbDatabase,
    private val clock: Clock,
) : ViewModel() {
    data class Today(
        val totals: ReportMath.Totals = ReportMath.Totals(),
        val yesterday: ReportMath.Totals = ReportMath.Totals(),
        val fortnight: List<Pair<java.time.LocalDate, Long>> = emptyList(),
        val top: List<ReportMath.ItemTotal> = emptyList(),
        val ready: Boolean = false,
    )

    val restaurant: StateFlow<Restaurant?> = account.current
    val restaurants: StateFlow<List<Restaurant>> = account.restaurants
    val sync: StateFlow<Sync.State> = syncer.state

    val today: StateFlow<Today> = account.perShop(Today()) { r ->
        val t = Ist.today(clock.now())
        val y = t.minusDays(1)
        val fortnight = Ist.Range(t.minusDays(13), t, "14 days")
        combine(
            db.totals().days(r, Ist.key(fortnight.from), Ist.key(t)),
            db.totals().items(r, Ist.key(t), Ist.key(t)),
        ) { days, items ->
            Today(
                totals = ReportMath.totals(days.filter { it.businessDay == Ist.key(t) }),
                yesterday = ReportMath.totals(days.filter { it.businessDay == Ist.key(y) }),
                fortnight = ReportMath.perDay(days, fortnight),
                top = ReportMath.topItems(items, 5),
                ready = true,
            )
        }.flowOn(Dispatchers.Default)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Today())

    fun opened() = syncer.pullIfStale()
    fun refresh() { viewModelScope.launch { syncer.pullNow(); account.refresh() } }
    fun choose(id: String) { account.choose(id); viewModelScope.launch { syncer.pullNow() } }
    fun now(): Long = clock.now()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNotices: () -> Unit, unread: Int, vm: HomeViewModel = hiltViewModel()) {
    val r by vm.restaurant.collectAsStateWithLifecycle()
    val all by vm.restaurants.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    var switching by remember { mutableStateOf(false) }
    LaunchedEffect(r?.id) { vm.opened() }

    PullToRefreshBox(isRefreshing = sync.pulling, onRefresh = vm::refresh) {
        Page(
            r?.name ?: "Home",
            subtitle = if (sync.lastMs > 0) "Updated " + Ist.ago(sync.lastMs, vm.now()) else "Not updated yet — pull down",
            actions = {
                if (all.size > 1) IconAction(Icons.Outlined.SwapHoriz, "Switch shop", { switching = true })
                IconAction(Icons.Outlined.Notifications, "Notices", onNotices, tint = if (unread > 0) Mb.colors.accent else Mb.colors.ink)
            },
        ) {
            val lic = r?.licence
            if (lic != null && lic.status != "active") {
                Notice(
                    when (lic.status) { "trial" -> Tone.Info; "suspended", "revoked" -> Tone.Danger; else -> Tone.Warn },
                    when (lic.status) {
                        "trial" -> "Free trial" + (lic.trialEndsOn?.let { " until $it" } ?: "") + "."
                        "suspended" -> "This shop's licence is suspended. The counter still bills; reports stop when it lapses."
                        "revoked" -> "This shop's licence has been revoked."
                        "cancelled" -> "Renewal is switched off" + (lic.renewsOn?.let { "; runs until $it" } ?: "") + "."
                        else -> "Licence: ${lic.status}."
                    },
                )
                VGap(Gap.field)
            }
            if (sync.sentence != null) { Notice(Tone.Warn, sync.sentence!!); VGap(Gap.field) }

            val t = today.totals
            Text(
                "TODAY · " + Ist.today(vm.now()).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy")).uppercase(),
                style = Mb.type.label.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = Mb.colors.inkMuted,
            )
            com.magicbill.app.ui.kit.AnimatedRupees(t.netPaise.paiseToRupees())
            VGap(Space.s2)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.inline)) {
                com.magicbill.app.ui.kit.DeltaChip(t.netPaise.paiseToRupees(), today.yesterday.netPaise.takeIf { it > 0 }?.paiseToRupees())
            }
            VGap(Gap.group)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.field)) {
                Stat("Bills", t.bills.toString(), Modifier.weight(1f))
                Stat("Avg bill", Money.whole(t.averageBillPaise), Modifier.weight(1f))
                Stat("Expenses", Money.whole(t.expensesPaise), Modifier.weight(1f))
            }
            if (today.fortnight.any { it.second > 0 }) {
                Section("Last 14 days")
                com.magicbill.app.ui.kit.TrendChart(
                    today.fortnight.map { (d, v) -> com.magicbill.app.ui.kit.TrendPoint(Ist.dateWords(d, Ist.today(vm.now())), v.paiseToRupees()) },
                )
            }
            // The 2.x payment split: the four modes always listed, each with its share and money.
            Section("Payment split")
            val byMode = t.byPayment.toMap()
            val fixed = listOf("cash", "card", "upi", "credit")
            val others = t.byPayment.filter { it.first !in fixed }.sumOf { it.second }
            val parts = fixed.map { m -> Triple(BillJson.modeLabel(m), byMode[m] ?: 0L, Mb.colors.payment(m)) } +
                (if (others > 0) listOf(Triple("Other", others, Mb.colors.otherPay)) else emptyList())
            val paidTotal = parts.sumOf { it.second }.coerceAtLeast(1)
            SplitBar(parts)
            VGap(Gap.field)
            Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
                parts.forEach { (name, paise, color) ->
                    Legend(name, color, "${paise * 100 / paidTotal}%   " + Money.rupees(paise))
                }
            }
            if (t.creditGivenPaise > 0 || t.creditCollectedPaise > 0) {
                VGap(Gap.field)
                Panel {
                    KeyValue("Credit given today", Money.rupees(t.creditGivenPaise))
                    KeyValue("Credit collected", Money.rupees(t.creditCollectedPaise))
                }
            }
            if (today.top.isNotEmpty()) {
                Section("Selling today")
                today.top.forEachIndexed { i, item ->
                    ListRow(item.name, Money.qty(item.qtyThousandths) + " sold", trailing = { Text(Money.rupees(item.salesPaise), style = Mb.type.cell, color = Mb.colors.ink) })
                    if (i < today.top.lastIndex) RowLine()
                }
            }
            if (today.ready && t.bills == 0 && today.top.isEmpty()) {
                VGap(Gap.group)
                Empty(if (sync.lastMs == 0L) "Pull down to bring the shop onto this phone." else "No bills yet today.")
            }
        }
    }

    if (switching) {
        Sheet("Which shop?", onDismiss = { switching = false }) {
            all.forEach { s ->
                ListRow(s.name, "Shop code ${s.shortCode}", onClick = { vm.choose(s.id); switching = false }, titleColor = if (s.id == r?.id) Mb.colors.accent else null)
                RowLine()
            }
        }
    }
}
