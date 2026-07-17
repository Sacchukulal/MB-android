package com.magicbill.app.ui.screens.staff

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.magicbill.app.core.LocalPermissions
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.has
import com.magicbill.app.data.MBSession
import com.magicbill.app.navigation.BillDetailRoute
import com.magicbill.app.navigation.StaffTabsRoute
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.PillNavBar
import com.magicbill.app.ui.components.PillNavItem
import com.magicbill.app.ui.screens.bills.BillDetailScreen
import com.magicbill.app.ui.screens.owner.StaffManagerScreen
import com.magicbill.app.ui.theme.MBMotion

/**
 * Staff world. Tabs appear strictly by permission; permissions refresh with
 * every staff-data response, so owner edits reshape this UI live.
 */
@Composable
fun StaffShell(rootViewModel: RootViewModel) {
    val session by rootViewModel.session.collectAsStateWithLifecycle()
    val staff = session as? MBSession.Staff ?: return
    val navController = rememberNavController()

    val permissionSet = staff.staff.permissions.filterValues { it }.keys

    CompositionLocalProvider(LocalPermissions provides permissionSet) {
        NavHost(
            navController = navController,
            startDestination = StaffTabsRoute,
            enterTransition = MBMotion.enterForward,
            exitTransition = MBMotion.exitForward,
            popEnterTransition = MBMotion.enterBack,
            popExitTransition = MBMotion.exitBack,
        ) {
            composable<StaffTabsRoute> {
                StaffTabs(
                    rootViewModel = rootViewModel,
                    staffSession = staff,
                    onOpenBill = { billId -> navController.navigate(BillDetailRoute(billId)) },
                )
            }
            composable<BillDetailRoute> { entry ->
                val route = entry.toRoute<BillDetailRoute>()
                BillDetailScreen(
                    billId = route.billId,
                    isStaff = true,
                    onBack = { navController.popBackStack() },
                    restaurantName = staff.restaurant.name,
                )
            }
        }
    }
}

private data class StaffTab(val item: PillNavItem, val key: String)

@Composable
private fun StaffTabs(
    rootViewModel: RootViewModel,
    staffSession: MBSession.Staff,
    onOpenBill: (String) -> Unit,
) {
    val perms = staffSession.staff.permissions
    val hasAnyView = perms.has(PermissionKey.ViewDashboard) ||
        perms.has(PermissionKey.ViewReports) || perms.has(PermissionKey.TakeOrders)
    val tabs = buildList {
        // No view permissions at all → Home still exists and shows the
        // friendly "your manager will enable features" screen.
        if (perms.has(PermissionKey.ViewDashboard) || !hasAnyView) {
            add(StaffTab(PillNavItem("Home", Icons.Outlined.Home, Icons.Filled.Home), "home"))
        }
        if (perms.has(PermissionKey.ViewReports)) {
            add(StaffTab(PillNavItem("Reports", Icons.Outlined.BarChart, Icons.Filled.BarChart), "reports"))
        }
        if (perms.has(PermissionKey.ManageStaff)) {
            add(StaffTab(PillNavItem("Staff", Icons.Outlined.Group, Icons.Filled.Group), "staff"))
        }
        if (perms.has(PermissionKey.TakeOrders)) {
            add(StaffTab(PillNavItem("Orders", Icons.Outlined.RestaurantMenu, Icons.Filled.RestaurantMenu), "orders"))
        }
        add(StaffTab(PillNavItem("Profile", Icons.Outlined.Person, Icons.Filled.Person), "profile"))
    }

    var tabIndex by rememberSaveable(tabs.size) { mutableIntStateOf(0) }
    val currentKey = tabs.getOrNull(tabIndex)?.key ?: "profile"

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentKey,
            transitionSpec = { MBMotion.tabEnter togetherWith MBMotion.tabExit },
            label = "staffTab",
        ) { key ->
            when (key) {
                "home" -> StaffHomeScreen(staffSession, onOpenBill)
                "reports" -> StaffReportsScreen(staffSession, onOpenBill)
                "staff" -> StaffManagerScreen()
                "orders" -> StaffOrdersScreen()
                else -> StaffProfileScreen(rootViewModel, staffSession)
            }
        }
        // Staff with only a profile tab get the friendly empty state inside
        // Home—but when no view permissions exist at all, Profile is the sole
        // tab and the bar still renders cleanly with one item.
        PillNavBar(
            items = tabs.map { it.item },
            selectedIndex = tabIndex.coerceIn(0, tabs.lastIndex),
            onSelect = { tabIndex = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
