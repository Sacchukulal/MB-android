package com.magicbill.app.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.magicbill.app.core.PERMISSION_METAS
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
fun StaffProfileScreen(rootViewModel: RootViewModel, session: MBSession.Staff) {
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

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
