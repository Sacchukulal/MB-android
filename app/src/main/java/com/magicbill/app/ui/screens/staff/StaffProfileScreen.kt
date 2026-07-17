package com.magicbill.app.ui.screens.staff

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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.PERMISSION_METAS
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.StaffPlanInfo
import com.magicbill.app.core.formatINR
import com.magicbill.app.core.has
import com.magicbill.app.core.longDate
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.MBBadge
import com.magicbill.app.ui.components.MBBadgeStatus
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.SectionHeader
import com.magicbill.app.ui.theme.Emerald
import com.magicbill.app.ui.theme.Teal
import kotlinx.coroutines.launch

/**
 * Staff profile: who they are, where they work, what they can see.
 * Staff can't change their own permissions or PIN — the owner does that.
 */
@Composable
fun StaffProfileScreen(
    rootViewModel: RootViewModel,
    session: MBSession.Staff,
    accountViewModel: StaffAccountViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val dark by rootViewModel.darkTheme.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    // Plan/subscription is read-only and only for staff with view_plan_status.
    // Hooks stay unconditional; the section renders only when data arrives.
    val canSeePlan = session.staff.permissions.has(PermissionKey.ViewPlanStatus)
    val planState by accountViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(canSeePlan) { if (canSeePlan) accountViewModel.load() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.statusBarsPadding().height(28.dp))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .background(
                        Brush.linearGradient(listOf(Emerald, Teal)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    session.staff.name.split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .take(2).joinToString(""),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = androidx.compose.ui.graphics.Color(0xFF04281B),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(session.staff.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    if (session.staff.roleLabel.isNotBlank()) {
                        append(session.staff.roleLabel)
                        append(" · ")
                    }
                    append(session.restaurant.name)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader("What you can access")
        val granted = PERMISSION_METAS.filter {
            session.staff.permissions[it.key.key] == true && !it.comingSoon
        }
        if (granted.isEmpty()) {
            Text(
                "Nothing enabled yet — your manager controls this.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            granted.forEach { meta ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(meta.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        meta.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MBBadge("Access is managed by the owner", MBBadgeStatus.Neutral)

        if (canSeePlan) planState.data?.let { PlanSection(it) }

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

        Spacer(Modifier.height(36.dp))
        MBButton(
            "Log out",
            variant = MBButtonVariant.Tonal,
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(130.dp))
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You'll need your restaurant code and PIN to sign in again.") },
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

/** Read-only plan/subscription block for staff with view_plan_status. */
@Composable
private fun PlanSection(plan: StaffPlanInfo) {
    SectionHeader("Plan & subscription")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                plan.planName ?: "Current plan",
                style = MaterialTheme.typography.titleLarge,
            )
            if (plan.amountPaise != null) {
                Text(
                    "${formatINR(plan.amountPaise / 100.0, decimals = 0)} / ${plan.intervalUnit ?: "month"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        plan.status?.let { PlanStatusBadge(it) }
    }
    plan.nextBillingDate?.let { nb ->
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Next billing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(130.dp),
            )
            Text(
                runCatching { longDate(nb.take(10)) }.getOrDefault(nb),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    plan.daysRemaining?.let { days ->
        if (days >= 0) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Days remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(130.dp),
                )
                Text("$days days", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    MBBadge("Billing is managed by the owner", MBBadgeStatus.Neutral)
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
