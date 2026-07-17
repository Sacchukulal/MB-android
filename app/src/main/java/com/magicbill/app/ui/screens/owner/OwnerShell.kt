package com.magicbill.app.ui.screens.owner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.magicbill.app.core.AllPermissions
import com.magicbill.app.core.LocalPermissions
import com.magicbill.app.data.MBSession
import com.magicbill.app.navigation.BillDetailRoute
import com.magicbill.app.navigation.OwnerTabsRoute
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.PillNavBar
import com.magicbill.app.ui.components.PillNavItem
import com.magicbill.app.ui.screens.bills.BillDetailScreen
import com.magicbill.app.ui.theme.MBMotion
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Owner world: four tabs behind a floating pill bar, with full-screen pushes
 * (bill detail) on top. The tab row has natural room for a 5th "Orders" tab.
 */
@Composable
fun OwnerShell(rootViewModel: RootViewModel) {
    val session by rootViewModel.session.collectAsStateWithLifecycle()
    val owner = session as? MBSession.Owner ?: return
    val navController = rememberNavController()

    CompositionLocalProvider(LocalPermissions provides AllPermissions) {
        NavHost(
            navController = navController,
            startDestination = OwnerTabsRoute,
            enterTransition = MBMotion.enterForward,
            exitTransition = MBMotion.exitForward,
            popEnterTransition = MBMotion.enterBack,
            popExitTransition = MBMotion.exitBack,
        ) {
            composable<OwnerTabsRoute> {
                OwnerTabs(
                    rootViewModel = rootViewModel,
                    owner = owner,
                    onOpenBill = { billId -> navController.navigate(BillDetailRoute(billId)) },
                )
            }
            composable<BillDetailRoute> { entry ->
                val route = entry.toRoute<BillDetailRoute>()
                BillDetailScreen(
                    billId = route.billId,
                    isStaff = false,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun OwnerTabs(
    rootViewModel: RootViewModel,
    owner: MBSession.Owner,
    onOpenBill: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val items = listOf(
        PillNavItem("Dashboard", Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard),
        PillNavItem("Reports", Icons.Outlined.BarChart, Icons.Filled.BarChart),
        PillNavItem("Staff", Icons.Outlined.Group, Icons.Filled.Group),
        PillNavItem(
            "Account", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle,
            showDot = rootViewModel.updateDotVisible.collectAsStateWithLifecycle().value,
        ),
    )

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { MBMotion.tabEnter togetherWith MBMotion.tabExit },
            label = "ownerTab",
        ) { current ->
            when (current) {
                0 -> DashboardScreen(rootViewModel, owner, onOpenBill)
                1 -> ReportsScreen(rootViewModel, owner, onOpenBill)
                2 -> StaffScreen(owner)
                else -> AccountScreen(rootViewModel, owner)
            }
        }
        PillNavBar(
            items = items,
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
