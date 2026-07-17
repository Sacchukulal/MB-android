package com.magicbill.app.ui.screens.owner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.longDate
import com.magicbill.app.core.shortDate
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.AnimatedRupees
import com.magicbill.app.ui.components.CacheChip
import com.magicbill.app.ui.components.DeltaChip
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.ListRow
import com.magicbill.app.ui.components.MBBottomSheet
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.StackedSplitBar
import com.magicbill.app.ui.components.TrendChart
import com.magicbill.app.ui.components.TrendPoint

/**
 * The owner's morning glance: big animated revenue, how today compares,
 * where the money came from, the fortnight rhythm, and what's selling —
 * all on one open canvas. Cached data renders instantly; refreshes are silent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    rootViewModel: RootViewModel,
    owner: MBSession.Owner,
    onOpenBill: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val licenseKey = owner.active?.licenseKey ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(licenseKey) { viewModel.load(licenseKey) }

    // Multi-outlet: offer the picker once until the owner explicitly chooses.
    var pickerOpen by rememberSaveable {
        mutableStateOf(owner.restaurants.size > 1 && !rootViewModel.auth.hasStoredSelection())
    }

    GlowBackground(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.data != null,
            onRefresh = { viewModel.load(licenseKey, force = true) },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.statusBarsPadding().height(16.dp))

                // Restaurant name / switcher — quiet header, no app bar.
                Row(
                    Modifier
                        .let {
                            if (owner.restaurants.size > 1) {
                                it.clickable { pickerOpen = true }
                            } else it
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        owner.active.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (owner.restaurants.size > 1) {
                        Icon(
                            Icons.Filled.UnfoldMore,
                            contentDescription = "Switch restaurant",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    CacheChip(state.updatedAt, visible = state.fromCacheOnly)
                }

                when {
                    state.data == null && state.refreshing -> {
                        Spacer(Modifier.height(40.dp))
                        SkeletonScreen()
                    }

                    state.data == null && state.error != null -> {
                        Spacer(Modifier.height(60.dp))
                        MBErrorState(
                            state.error!!,
                            onRetry = { viewModel.load(licenseKey, force = true) },
                        )
                    }

                    state.data != null -> DashboardContent(state.data!!)
                }

                Spacer(Modifier.height(120.dp)) // room for the floating nav
            }
        }
    }

    if (pickerOpen && owner.restaurants.size > 1) {
        MBBottomSheet(
            onDismissRequest = { pickerOpen = false },
            title = "Choose restaurant",
        ) {
            owner.restaurants.forEach { r ->
                ListRow(
                    title = r.name,
                    subtitle = r.code,
                    icon = Icons.Outlined.Storefront,
                    onClick = {
                        rootViewModel.auth.switchRestaurant(r)
                        pickerOpen = false
                    },
                    trailing = {
                        if (r.licenseKey == owner.active.licenseKey) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DashboardContent(data: com.magicbill.app.core.DashboardData) {
    val today = data.today

    Spacer(Modifier.height(22.dp))
    Text(
        "TODAY · ${longDate(today.day).uppercase()}",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    AnimatedRupees(today.total, style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        DeltaChip(current = today.total, previous = data.yesterdayTotal)
        Spacer(Modifier.width(12.dp))
        Text(
            "${today.billCount} bills · ${formatINR(today.avg, decimals = 0)} avg",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Insight chips — computed, not stored; they make the number mean something.
    val insights = remember(data) { buildInsights(data) }
    if (insights.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            insights.forEach { (label, value) ->
                Column(
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }

    SectionHeader("Payment split")
    StackedSplitBar(cash = today.cash, card = today.card, upi = today.upi, credit = today.credit)

    SectionHeader("Last 14 days")
    TrendChart(
        points = data.trend.map { TrendPoint(shortDate(it.day), it.total) },
    )

    if (data.topItems.isNotEmpty()) {
        SectionHeader("Top items today")
        data.topItems.forEachIndexed { i, item ->
            ListRow(
                title = item.name,
                subtitle = "${item.quantity.toLong()} sold",
                trailing = {
                    Text(
                        formatINR(item.amount, decimals = 0),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                icon = null,
                modifier = Modifier,
            )
        }
    }
}

private fun buildInsights(data: com.magicbill.app.core.DashboardData): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()

    // Busiest hour today.
    val hours = data.hourCounts
    val peak = hours.withIndex().maxByOrNull { it.value }
    if (peak != null && peak.value > 0) {
        val h = peak.index
        fun hr(x: Int) = when {
            x == 0 -> "12am"; x < 12 -> "${x}am"; x == 12 -> "12pm"; else -> "${x - 12}pm"
        }
        list += "Peak hour" to "${hr(h)}–${hr((h + 1) % 24)}"
    }

    // Best day of the fortnight.
    val best = data.trend.maxByOrNull { it.total }
    if (best != null && best.total > 0) {
        list += "Best day" to "${shortDate(best.day)} · ${com.magicbill.app.core.formatShortINR(best.total)}"
    }

    if (data.today.gst > 0) list += "GST today" to formatINR(data.today.gst, decimals = 0)

    return list
}
