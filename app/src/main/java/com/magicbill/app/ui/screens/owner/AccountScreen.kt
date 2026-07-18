package com.magicbill.app.ui.screens.owner

import android.os.Build
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.BuildConfig
import com.magicbill.app.core.AccountData
import com.magicbill.app.core.LicenseInfo
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
import com.magicbill.app.ui.components.MBSnackbarKind
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.showMBSnackbar
import kotlinx.coroutines.launch

/**
 * Account, open-canvas style: identity hero, subscription story, POS license
 * (masked), this phone — each separated only by SectionHeader + whitespace.
 * Theme toggle lives as a small sun/moon icon in the header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    rootViewModel: RootViewModel,
    owner: MBSession.Owner,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val licenseKey = owner.active.licenseKey
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dark by rootViewModel.darkTheme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val toolbarColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var checkingUpdates by remember { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(licenseKey) { viewModel.load(licenseKey) }

    // Returning from the billing tab → silently re-fetch license state.
    DisposableEffect(lifecycleOwner, licenseKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume(licenseKey)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
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
                // Theme toggle: one quiet icon — moon in light mode, sun in dark.
                IconButton(onClick = { rootViewModel.setDarkTheme(!dark) }) {
                    Icon(
                        if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

            SectionHeader("This phone")
            QuietLine(
                "${Build.BRAND.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · " +
                    "Android ${Build.VERSION.RELEASE} · v${BuildConfig.VERSION_NAME}",
            )
            Spacer(Modifier.height(14.dp))
            MBButton(
                "Check for updates",
                variant = MBButtonVariant.Outline,
                loading = checkingUpdates,
                onClick = {
                    scope.launch {
                        checkingUpdates = true
                        val result = rootViewModel.checkForUpdates()
                        checkingUpdates = false
                        when (result) {
                            "up-to-date" -> snackbar.showMBSnackbar(
                                "You're up to date (v${BuildConfig.VERSION_NAME})",
                                MBSnackbarKind.Success,
                            )
                            "error" -> snackbar.showMBSnackbar(
                                "Couldn't check for updates — try again later.",
                                MBSnackbarKind.Error,
                            )
                            // "update": the update sheet opens by itself.
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

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
    com.magicbill.app.ui.components.MBSnackbarHost(
        snackbar,
        Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
    )
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

/**
 * Friendly plan title. The plans table is RLS-limited to publicly visible
 * plans, so hidden/archived plans resolve to null — infer the billing cycle
 * from the renewal horizon instead of ever showing a raw "plan_…" id.
 */
private fun friendlyPlanName(data: AccountData): String {
    data.plan?.name?.let { return it }
    val planId = data.license.plan_id
    if (planId.isNullOrBlank()) return "No plan"
    if (!planId.startsWith("plan_")) {
        // Legacy readable ids ("trial", "premium") — just tidy them up.
        return planId.replace('-', ' ').replace('_', ' ')
            .replaceFirstChar { it.uppercase() }
    }
    val days = daysUntil(data.license.next_billing_date)
    return when {
        days != null && days > 45 -> "Yearly Plan"
        days != null -> "Monthly Plan"
        else -> "Magic Bill Plan"
    }
}

@Composable
private fun AccountContent(
    data: AccountData,
    onBilling: (String) -> Unit,
) {
    val license = data.license
    val plan = data.plan

    // ---- Identity hero: the restaurant, then its people, as quiet lines ----
    Spacer(Modifier.height(20.dp))
    Text(
        license.restaurant_name ?: "My Restaurant",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    license.display_name?.let {
        Spacer(Modifier.height(2.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(14.dp))
    license.mobile_number?.let { IconLine(Icons.Outlined.Call, it) }
    license.email?.let { IconLine(Icons.Outlined.MailOutline, it) }
    license.restaurant_code?.let {
        IconLine(Icons.Outlined.Storefront, "Staff code  $it", mono = true)
    }

    // ---- Subscription ----
    SectionHeader("Subscription")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                friendlyPlanName(data),
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

    val status = (license.status ?: "").lowercase()
    val statusMessage = when (status) {
        "active", "trial" -> null
        "created", "pending" ->
            "Your subscription is being activated. This usually takes a few seconds."
        "grace" -> "Your payment is overdue — renew soon to avoid interruption."
        "halted" -> "Your subscription is paused because a payment failed. Renew to continue."
        "cancelled" -> "Your subscription was cancelled. Resubscribe anytime to continue."
        "completed", "expired" ->
            "Your subscription has expired. Renew at magicbill.in to continue."
        else -> null
    }
    statusMessage?.let {
        Spacer(Modifier.height(10.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }

    license.next_billing_date?.let { nb ->
        Spacer(Modifier.height(8.dp))
        val date = runCatching { longDate(nb.take(10)) }.getOrDefault(nb)
        val days = daysUntil(nb)
        QuietLine(
            if (days != null && days >= 0) "Renews $date · $days days left" else "Renews $date",
        )
    }

    Spacer(Modifier.height(16.dp))
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
    Spacer(Modifier.height(6.dp))
    Text(
        "Payments open securely on magicbill.in — you're already signed in.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // ---- POS license: the secret key, masked until peeked ----
    SectionHeader("POS license")
    LicenseKeyRow(license.key)
    Spacer(Modifier.height(4.dp))
    Text(
        "Activates Magic Bill POS on your billing PC. Keep it private.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (license.device_name != null || license.device_id != null) {
        Spacer(Modifier.height(12.dp))
        DeviceLockLine(license)
    }
}

/** The license key, "MB-••••-••••-••••" until the eye reveals it. */
@Composable
private fun LicenseKeyRow(key: String) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val masked = remember(key) {
        key.split("-").mapIndexed { i, part ->
            if (i == 0) part else "•".repeat(part.length)
        }.joinToString("-")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (visible) key else masked,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { visible = !visible }) {
            Icon(
                if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (visible) "Hide license key" else "Show license key",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceLockLine(license: LicenseInfo) {
    val lastSeen = license.device_last_seen?.let {
        runCatching { com.magicbill.app.core.billTime(it) }.getOrNull()
    }
    IconLine(
        Icons.Outlined.Computer,
        buildString {
            append(license.device_name ?: "Billing PC")
            lastSeen?.let { append(" · last seen $it") }
        },
    )
    license.device_id?.let {
        Spacer(Modifier.height(2.dp))
        QuietLine("Hardware ${it.take(12)}…")
    }
}

/** One quiet icon + text line — the open-canvas replacement for label grids. */
@Composable
private fun IconLine(icon: ImageVector, text: String, mono: Boolean = false) {
    Row(
        Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = if (mono) {
                MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
