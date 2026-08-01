package com.magicbill.app.ui.screens.profile

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.magicbill.app.core.PERMISSION_METAS
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.StaffPlanInfo
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.has
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
import com.magicbill.app.ui.components.MBSnackbarHost
import com.magicbill.app.ui.components.MBSnackbarKind
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.components.SkeletonScreen
import com.magicbill.app.ui.components.showMBSnackbar
import com.magicbill.app.ui.screens.owner.AccountViewModel
import com.magicbill.app.ui.screens.staff.StaffAccountViewModel
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.Teal
import kotlinx.coroutines.launch

/**
 * ONE profile screen, for both worlds.
 *
 * WHY IT WAS MERGED. `owner/AccountScreen.kt` and `staff/StaffProfileScreen.kt`
 * were written separately and drifted into two different design languages:
 * the owner got a sun/moon icon in the header, staff got a labelled Switch
 * with a subtitle; the owner got a "This phone" line and a "Check for
 * updates" button, staff got neither and never even saw the app version; the
 * owner's Account tab carried the update dot, the staff Profile tab did not,
 * so a staff phone with a dismissed update sheet had no indication at all.
 *
 * The chrome — header, theme toggle, "This phone", the update button and the
 * log-out block — is now written ONCE, here. Only the middle differs:
 *
 *   OWNER  identity hero · Subscription (+ billing) · POS license (masked,
 *          eye reveal) · device lock line
 *   STAFF  identity hero (avatar, name, role · restaurant) · What you can
 *          access · Plan & subscription (view_plan_status only, read-only,
 *          no billing button)
 *
 * THE LICENCE KEY CANNOT REACH A STAFF PHONE. It is rendered inside the
 * Owner branch of a `when` over a sealed type, from data only the owner
 * screen loads. There is no staff code path that could draw it even if the
 * data appeared.
 *
 * Explanation lines are gone, by the rule the owner set: if a line tells you
 * a fact you could not otherwise know, keep it; if it only explains what the
 * button obviously does, delete it.
 */

/** Which world this screen is rendering. The only thing that differs. */
sealed interface ProfileSession {
    data class Owner(val session: MBSession.Owner) : ProfileSession
    data class Staff(val session: MBSession.Staff) : ProfileSession
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    rootViewModel: RootViewModel,
    session: ProfileSession,
    ownerViewModel: AccountViewModel = hiltViewModel(),
    staffViewModel: StaffAccountViewModel = hiltViewModel(),
) {
    val dark by rootViewModel.darkTheme.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmLogout by remember { mutableStateOf(false) }
    var checkingUpdates by remember { mutableStateOf(false) }

    // Hooks stay unconditional — a Composable's hook order may not depend on
    // which branch is rendering.
    val ownerState by ownerViewModel.state.collectAsStateWithLifecycle()
    val staffState by staffViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val toolbarColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()

    val licenseKey = (session as? ProfileSession.Owner)?.session?.active?.licenseKey
    val canSeePlan = (session as? ProfileSession.Staff)
        ?.session?.staff?.permissions?.has(PermissionKey.ViewPlanStatus) == true

    LaunchedEffect(licenseKey) { licenseKey?.let { ownerViewModel.load(it) } }
    LaunchedEffect(canSeePlan) { if (canSeePlan) staffViewModel.load() }

    // Returning from the billing tab → silently re-fetch license state.
    DisposableEffect(lifecycleOwner, licenseKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) licenseKey?.let { ownerViewModel.onResume(it) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val refreshing = when (session) {
        is ProfileSession.Owner -> ownerState.refreshing && ownerState.data != null
        is ProfileSession.Staff -> canSeePlan && staffState.refreshing && staffState.data != null
    }
    val cachedAt = when (session) {
        is ProfileSession.Owner -> ownerState.updatedAt
        is ProfileSession.Staff -> staffState.updatedAt
    }
    val fromCacheOnly = when (session) {
        is ProfileSession.Owner -> ownerState.fromCacheOnly
        is ProfileSession.Staff -> canSeePlan && staffState.fromCacheOnly
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                when (session) {
                    is ProfileSession.Owner -> ownerViewModel.load(session.session.active.licenseKey, force = true)
                    // A7 — every staff-data reply carries fresh permissions,
                    // so pulling here is also how an owner's permission change
                    // lands without a relaunch.
                    is ProfileSession.Staff -> if (canSeePlan) staffViewModel.load(force = true)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.statusBarsPadding().height(16.dp))

                // ---- header: title, cache chip, sun/moon. Identical for both. ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (session is ProfileSession.Owner) "Account" else "Profile",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    CacheChip(cachedAt, visible = fromCacheOnly)
                    // Theme toggle: one quiet icon — moon in light mode, sun in dark.
                    IconButton(onClick = { rootViewModel.setDarkTheme(!dark) }) {
                        Icon(
                            if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription =
                                if (dark) "Switch to light theme" else "Switch to dark theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ---- the only part that differs ----
                when (session) {
                    is ProfileSession.Owner -> {
                        val data = ownerState.data
                        when {
                            data == null && ownerState.refreshing -> {
                                Spacer(Modifier.height(32.dp))
                                SkeletonScreen()
                            }

                            data == null && ownerState.error != null -> {
                                Spacer(Modifier.height(48.dp))
                                MBErrorState(ownerState.error!!, onRetry = {
                                    ownerViewModel.load(session.session.active.licenseKey, force = true)
                                })
                            }

                            data != null -> OwnerBody(
                                data = data,
                                onBilling = { destination ->
                                    ownerViewModel.openBilling(context, destination, toolbarColor)
                                },
                            )
                        }
                    }

                    is ProfileSession.Staff -> StaffBody(
                        staff = session.session,
                        plan = if (canSeePlan) staffState.data else null,
                    )
                }

                // ---- this phone. Identical for both, and staff never had it. ----
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
        MBSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = {
                Text(
                    when (session) {
                        is ProfileSession.Owner -> "Are you sure? You'll need to log in again."
                        is ProfileSession.Staff ->
                            "You'll need your restaurant code and PIN to sign in again."
                    },
                )
            },
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

/* ------------------------------- owner ------------------------------- */

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
private fun OwnerBody(data: AccountData, onBilling: (String) -> Unit) {
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
            Text(friendlyPlanName(data), style = MaterialTheme.typography.titleLarge)
            if (plan?.amount_paise != null) {
                Text(
                    "${formatINR(plan.amount_paise / 100.0, decimals = 0)} / " +
                        (plan.interval_unit ?: "month"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusBadge(license.status)
    }

    val status = (license.status ?: "").lowercase()
    // Kept: these say what has happened to the subscription and what to do
    // about it — facts you could not otherwise know, not button captions.
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
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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

    // ---- POS license: the secret key, masked until peeked ----
    SectionHeader("POS license")
    LicenseKeyRow(license.key)
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

/* ------------------------------- staff ------------------------------- */

@Composable
private fun StaffBody(staff: MBSession.Staff, plan: StaffPlanInfo?) {
    // ---- Identity hero ----
    Spacer(Modifier.height(20.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(84.dp)
                .background(Brush.linearGradient(listOf(Emerald, Teal)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                staff.staff.name.split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .take(2).joinToString(""),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF04281B),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(staff.staff.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                if (staff.staff.roleLabel.isNotBlank()) {
                    append(staff.staff.roleLabel)
                    append(" · ")
                }
                append(staff.restaurant.name)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // ---- What you can access: NAMES ONLY ----
    // The per-permission description lines are gone: the label already says
    // what it is, and eight paragraphs of restatement pushed the real
    // information off the screen.
    SectionHeader("What you can access")
    val granted = PERMISSION_METAS.filter {
        staff.staff.permissions[it.key.key] == true && !it.comingSoon
    }
    if (granted.isEmpty()) {
        // Kept: this is a fact, and it tells a confused new starter who to ask.
        Text(
            "Nothing enabled yet — your manager controls this.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        granted.forEach { meta ->
            Text(
                meta.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }

    // ---- Plan & subscription: read-only, and only with view_plan_status ----
    plan?.let { PlanSection(it) }
}

/** Read-only plan/subscription block for staff with view_plan_status. */
@Composable
private fun PlanSection(plan: StaffPlanInfo) {
    SectionHeader("Plan & subscription")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(plan.planName ?: "Current plan", style = MaterialTheme.typography.titleLarge)
            if (plan.amountPaise != null) {
                Text(
                    "${formatINR(plan.amountPaise / 100.0, decimals = 0)} / " +
                        (plan.intervalUnit ?: "month"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        plan.status?.let { PlanStatusBadge(it) }
    }
    plan.nextBillingDate?.let { nb ->
        Spacer(Modifier.height(8.dp))
        val date = runCatching { longDate(nb.take(10)) }.getOrDefault(nb)
        val days = plan.daysRemaining
        QuietLine(
            if (days != null && days >= 0) "Renews $date · $days days left" else "Renews $date",
        )
    }
}

@Composable
private fun PlanStatusBadge(status: String) {
    val label = status.replaceFirstChar { it.uppercase() }
    val badge = when (status.lowercase()) {
        "active" -> MBBadgeStatus.Active
        "trial" -> MBBadgeStatus.Trial
        "grace", "pending", "created" -> MBBadgeStatus.Grace
        "halted", "expired", "cancelled", "canceled" -> MBBadgeStatus.Expired
        else -> MBBadgeStatus.Neutral
    }
    MBBadge(label, badge)
}

/* ------------------------------- shared ------------------------------- */

/** One quiet icon + text line — the open-canvas replacement for label grids. */
@Composable
private fun IconLine(icon: ImageVector, text: String, mono: Boolean = false) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
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
