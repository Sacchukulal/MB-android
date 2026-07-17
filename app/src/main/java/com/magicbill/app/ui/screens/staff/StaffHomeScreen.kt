package com.magicbill.app.ui.screens.staff

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.StaffDashboard
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.has
import com.magicbill.app.core.longDate
import com.magicbill.app.core.shortDate
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.components.AnimatedCount
import com.magicbill.app.ui.components.AnimatedRupees
import com.magicbill.app.ui.components.CacheChip
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.ListRow
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.StackedSplitBar
import com.magicbill.app.ui.components.TrendChart
import com.magicbill.app.ui.components.TrendPoint

/**
 * Staff home: today's picture shaped by permissions. With revenue hidden it
 * leads with the bill count and percentage split — still useful, never
 * leaking amounts. Server re-checks every permission per call.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHomeScreen(
    session: MBSession.Staff,
    onOpenBill: (String) -> Unit,
    viewModel: StaffHomeViewModel = hiltViewModel(),
) {
    val perms = session.staff.permissions
    if (!perms.has(PermissionKey.ViewDashboard)) {
        NoAccessScreen(session)
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    GlowBackground(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing && state.data != null,
            onRefresh = { viewModel.load(force = true) },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.statusBarsPadding().height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.restaurant.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Hi ${session.staff.name.substringBefore(' ')} 👋",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    CacheChip(state.updatedAt, visible = state.fromCacheOnly)
                }

                when {
                    state.data == null && state.refreshing -> {
                        Spacer(Modifier.height(40.dp))
                        SkeletonScreen()
                    }

                    state.data == null && state.error != null -> {
                        Spacer(Modifier.height(60.dp))
                        MBErrorState(state.error!!, onRetry = { viewModel.load(force = true) })
                    }

                    state.data != null -> StaffHomeContent(state.data!!)
                }

                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun StaffHomeContent(data: StaffDashboard) {
    val masked = data.total == null

    Spacer(Modifier.height(22.dp))
    Text(
        "TODAY · ${longDate(data.day).uppercase()}",
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))

    if (masked) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedCount(data.billCount, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                "bills today",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    } else {
        AnimatedRupees(data.total!!, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "${data.billCount} bills" +
                (data.avg?.let { " · ${formatINR(it, decimals = 0)} avg" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    if (data.trend.isNotEmpty()) {
        SectionHeader(if (data.trendIsRelative) "Trend (relative)" else "Last days")
        TrendChart(points = data.trend.map { TrendPoint(shortDate(it.day), it.value) })
    }

    if (data.topItems.isNotEmpty()) {
        SectionHeader("Top items today")
        data.topItems.forEach { item ->
            ListRow(
                title = item.name,
                subtitle = "${item.quantity.toLong()} sold",
                trailing = {
                    item.amount?.let {
                        Text(formatINR(it, decimals = 0), style = MaterialTheme.typography.titleSmall)
                    }
                },
            )
        }
    }
}

/** Friendly landing for staff whose owner hasn't enabled any views yet. */
@Composable
fun NoAccessScreen(session: MBSession.Staff) {
    GlowBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                session.restaurant.name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "You're all set, ${session.staff.name.substringBefore(' ')}! Your manager " +
                    "will enable features for you.\n\nMobile ordering is coming soon!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
