package com.magicbill.app.ui.screens.owner

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.BuildConfig
import com.magicbill.app.core.AccountData
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.longDate
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.daysUntil
import com.magicbill.app.data.statusTone
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.CacheChip
import com.magicbill.app.ui.components.MBBadge
import com.magicbill.app.ui.components.MBBadgeStatus
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.MBErrorState
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SkeletonScreen
import kotlinx.coroutines.launch

/**
 * Account: restaurant identity, subscription state with one context-aware
 * billing action (Custom Tab handoff to magicbill.in), device bindings,
 * theme preference, logout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    rootViewModel: RootViewModel,
    owner: MBSession.Owner,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val licenseKey = owner.active?.licenseKey ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark by rootViewModel.darkTheme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val toolbarColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(licenseKey) { viewModel.load(licenseKey) }

    // Returning from the billing tab → silently re-fetch license state.
    DisposableEffect(lifecycleOwner, licenseKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume(licenseKey)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing && state.data != null,
        onRefresh = { viewModel.load(licenseKey, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.statusBarsPadding().height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Account", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                CacheChip(state.updatedAt, visible = state.fromCacheOnly)
            }

            val data = state.data
            when {
                data == null && state.refreshing -> {
                    Spacer(Modifier.height(32.dp))
                    SkeletonScreen()
                }

                data == null && state.error != null -> {
                    Spacer(Modifier.height(48.dp))
                    MBErrorState(state.error!!, onRetry = { viewModel.load(licenseKey, force = true) })
                }

                data != null -> AccountContent(
                    data = data,
                    onBilling = { destination ->
                        viewModel.openBilling(context, destination, toolbarColor)
                    },
                )
            }

            SectionHeader("Appearance")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dark theme", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Easier on the eyes in dim restaurants",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = dark, onCheckedChange = { rootViewModel.setDarkTheme(it) })
            }

            SectionHeader("This phone")
            InfoLine("Device", "${Build.BRAND.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
            InfoLine("Android", Build.VERSION.RELEASE)
            InfoLine("App version", BuildConfig.VERSION_NAME)

            Spacer(Modifier.height(32.dp))
            MBButton(
                "Log out",
                variant = MBButtonVariant.Tonal,
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(130.dp))
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("Are you sure? You'll need to log in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch { rootViewModel.auth.logout() }
                }) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AccountContent(
    data: AccountData,
    onBilling: (String) -> Unit,
) {
    val license = data.license
    val plan = data.plan

    SectionHeader("Restaurant")
    InfoLine("Name", license.restaurant_name ?: "—")
    license.display_name?.let { InfoLine("Owner", it) }
    license.mobile_number?.let { InfoLine("Phone", it) }
    license.email?.let { InfoLine("Email", it) }
    license.restaurant_code?.let { InfoLine("Staff code", it) }

    SectionHeader("Subscription")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                plan?.name ?: license.plan_id ?: "No plan",
                style = MaterialTheme.typography.titleLarge,
            )
            if (plan?.amount_paise != null) {
                Text(
                    "${formatINR(plan.amount_paise / 100.0, decimals = 0)} / ${plan.interval_unit ?: "month"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusBadge(license.status)
    }
    Spacer(Modifier.height(8.dp))
    license.next_billing_date?.let { nb ->
        InfoLine("Next billing", runCatching { longDate(nb.take(10)) }.getOrDefault(nb))
        daysUntil(nb)?.let { days ->
            if (days >= 0) InfoLine("Days remaining", "$days days")
        }
    }
    Spacer(Modifier.height(14.dp))

    val status = (license.status ?: "").lowercase()
    val (actionLabel, destination) = when (status) {
        "active" -> "Manage subscription" to "/dashboard/billing"
        "trial" -> "Subscribe now" to "/dashboard/billing"
        "grace", "halted", "pending", "created" -> "Renew now" to "/dashboard/billing"
        "" -> "Subscribe at magicbill.in" to "/pricing"
        else -> "Resubscribe" to "/dashboard/billing"
    }
    MBButton(
        actionLabel,
        onClick = { onBilling(destination) },
        variant = if (status == "active") MBButtonVariant.Tonal else MBButtonVariant.Primary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Payments open securely on magicbill.in — you're already signed in.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (license.device_name != null || license.device_id != null) {
        SectionHeader("Billing PC (device lock)")
        license.device_name?.let { InfoLine("Computer", it) }
        license.device_id?.let { InfoLine("Hardware ID", it.take(12) + "…") }
        license.device_last_seen?.let {
            InfoLine("Last seen", runCatching { com.magicbill.app.core.billTime(it) }.getOrDefault("—"))
        }
    }
}

@Composable
private fun StatusBadge(status: String?) {
    val label = (status ?: "unknown").replaceFirstChar { it.uppercase() }
    val badge = when (statusTone(status)) {
        "success" -> MBBadgeStatus.Active
        "info" -> MBBadgeStatus.Trial
        "warning" -> MBBadgeStatus.Grace
        "danger" -> MBBadgeStatus.Expired
        else -> MBBadgeStatus.Neutral
    }
    MBBadge(label, badge)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
