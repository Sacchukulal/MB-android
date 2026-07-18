package com.magicbill.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.screens.auth.OwnerGateScreen
import com.magicbill.app.ui.screens.auth.OwnerLoginScreen
import com.magicbill.app.ui.screens.auth.StaffLoginScreen
import com.magicbill.app.ui.screens.auth.WelcomeScreen
import com.magicbill.app.ui.screens.owner.OwnerShell
import com.magicbill.app.ui.screens.staff.StaffShell
import com.magicbill.app.ui.components.UpdateSheet
import com.magicbill.app.ui.theme.MBMotion

/**
 * Top-level composition: the session state picks which world is showing
 * (auth flow / owner shell / staff shell) with a gentle crossfade between
 * worlds. Each world runs its own NavHost.
 */
@Composable
fun MagicBillRoot(viewModel: RootViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = session::class,
            transitionSpec = {
                fadeIn(tween(MBMotion.DurMedium)) togetherWith fadeOut(tween(MBMotion.DurShort))
            },
            label = "rootWorld",
        ) { _ ->
            when (val s = session) {
                is MBSession.Loading -> Unit // native splash is covering

                is MBSession.None -> AuthFlow(viewModel, revoked = s.revoked)

                is MBSession.OwnerGate -> OwnerGateScreen(gate = s.gate)

                is MBSession.Owner -> OwnerShell(rootViewModel = viewModel)

                is MBSession.Staff -> StaffShell(rootViewModel = viewModel)
            }
        }

        // Update prompt floats over any signed-in world (never over login).
        if (session !is MBSession.None && session !is MBSession.Loading &&
            updateState.available != null && !updateState.sheetSuppressed
        ) {
            UpdateSheet(
                state = updateState,
                onUpdateNow = { viewModel.updates.downloadAndInstall() },
                onDismiss = { viewModel.updates.dismiss() },
                onOpenInstallSettings = { viewModel.updates.openInstallPermissionSettings() },
            )
        }
    }
}

@Composable
private fun AuthFlow(viewModel: RootViewModel, revoked: Boolean) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = WelcomeRoute,
        enterTransition = MBMotion.enterForward,
        exitTransition = MBMotion.exitForward,
        popEnterTransition = MBMotion.enterBack,
        popExitTransition = MBMotion.exitBack,
    ) {
        composable<WelcomeRoute> {
            WelcomeScreen(
                revoked = revoked,
                onAckRevoked = { viewModel.auth.ackRevoked() },
                onOwner = { navController.navigate(OwnerLoginRoute) },
                onStaff = { navController.navigate(StaffLoginRoute) },
            )
        }
        composable<OwnerLoginRoute> {
            OwnerLoginScreen(onBack = { navController.popBackStack() })
        }
        composable<StaffLoginRoute> {
            StaffLoginScreen(onBack = { navController.popBackStack() })
        }
    }
}
