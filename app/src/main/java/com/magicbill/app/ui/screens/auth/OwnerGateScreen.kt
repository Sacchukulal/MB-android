package com.magicbill.app.ui.screens.auth

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.SITE_URL
import com.magicbill.app.core.openCustomTab
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.GateState
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.theme.Emerald
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class OwnerGateViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    fun retry() = auth.resolveOwnerGate()

    fun quietRetry() = auth.resolveOwnerGate(quiet = true)

    fun logout() = viewModelScope.launch { auth.logout() }

    /** Opens billing on magicbill.in already signed in (token handoff). */
    fun openSubscribe(context: Context, toolbarColor: Int) {
        viewModelScope.launch {
            val access = auth.ownerAccessToken()
            val refresh = auth.ownerRefreshToken()
            val destination = "/dashboard/billing"
            val url = if (access != null && refresh != null) {
                "$SITE_URL/auth/mobile-handoff" +
                    "?token=${URLEncoder.encode(access, "UTF-8")}" +
                    "&refresh=${URLEncoder.encode(refresh, "UTF-8")}" +
                    "&redirect=${URLEncoder.encode(destination, "UTF-8")}"
            } else {
                "$SITE_URL$destination"
            }
            openCustomTab(context, url, toolbarColor)
        }
    }
}

/**
 * Shown when the owner is authenticated but has no usable license yet:
 * checking, no subscription, payment pending, or server unreachable.
 * Every state has clear actions — there is no dead end here.
 */
@Composable
fun OwnerGateScreen(
    gate: GateState,
    viewModel: OwnerGateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Coming back from the browser (subscribe flow) → re-check automatically.
    // Only a REAL return (pause → resume) counts: addObserver replays the
    // current lifecycle state, so a naive ON_RESUME check fires on every
    // registration and spams checks.
    val currentGate by androidx.compose.runtime.rememberUpdatedState(gate)
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        var sawPause = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when {
                event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> sawPause = true
                event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && sawPause -> {
                    sawPause = false
                    if (currentGate is GateState.NoSubscription ||
                        currentGate is GateState.PendingActivation
                    ) {
                        viewModel.quietRetry()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Pending payment: quietly re-check every 10s (webhooks usually land fast),
    // then relax the copy once we've waited a while.
    var pendingChecks by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(gate is GateState.PendingActivation) {
        if (gate is GateState.PendingActivation) {
            while (pendingChecks < 6) {
                delay(10_000)
                pendingChecks++
                viewModel.quietRetry()
            }
        } else {
            pendingChecks = 0
        }
    }

    GlowBackground(Modifier.fillMaxSize(), intensity = 0.9f) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            when (gate) {
                is GateState.Checking -> {
                    CircularProgressIndicator(
                        Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(top = 20.dp))
                    Text(
                        "Checking your subscription…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is GateState.NoSubscription -> GateContent(
                    icon = Icons.Outlined.WorkspacePremium,
                    title = "No Active Subscription",
                    message = "Your account was created successfully! To start using " +
                        "Magic Bill, you need an active subscription.",
                    primaryLabel = "Subscribe Now",
                    onPrimary = { viewModel.openSubscribe(context, toolbarColor) },
                    secondaryLabel = "Already subscribed? Refresh",
                    onSecondary = { viewModel.retry() },
                )

                is GateState.PendingActivation -> GateContent(
                    icon = Icons.Outlined.HourglassTop,
                    title = "Activating your subscription",
                    message = if (pendingChecks < 6) {
                        "Your subscription is being activated. This usually takes " +
                            "a few seconds — we'll check automatically."
                    } else {
                        "Payment may still be processing. Please try again in a " +
                            "little while, or check your subscription at magicbill.in."
                    },
                    primaryLabel = "Refresh",
                    onPrimary = { viewModel.retry() },
                    secondaryLabel = "View subscription on magicbill.in",
                    onSecondary = { viewModel.openSubscribe(context, toolbarColor) },
                    busy = pendingChecks < 6,
                )

                is GateState.Unreachable -> GateContent(
                    icon = Icons.Outlined.CloudOff,
                    title = "Couldn't check your subscription",
                    message = gate.message,
                    primaryLabel = "Try again",
                    onPrimary = { viewModel.retry() },
                )
            }

            Spacer(Modifier.weight(1f))

            if (gate !is GateState.Checking) {
                Text(
                    "Log out",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { viewModel.logout() }
                        .padding(16.dp),
                )
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun GateContent(
    icon: ImageVector,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    Box(
        Modifier
            .size(96.dp)
            .background(Emerald.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.padding(top = 14.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.padding(top = 6.dp))
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (busy) {
        Spacer(Modifier.padding(top = 14.dp))
        CircularProgressIndicator(
            Modifier.size(22.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.padding(top = 22.dp))
    MBButton(primaryLabel, onClick = onPrimary, modifier = Modifier.fillMaxWidth())
    if (secondaryLabel != null && onSecondary != null) {
        Spacer(Modifier.padding(top = 10.dp))
        MBButton(
            secondaryLabel,
            onClick = onSecondary,
            variant = MBButtonVariant.Tonal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
