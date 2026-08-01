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
import com.magicbill.app.navigation.OrderBuilderRoute
import com.magicbill.app.navigation.OrderDetailRoute
import com.magicbill.app.navigation.StaffTabsRoute
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.PillNavBar
import com.magicbill.app.ui.components.PillNavItem
import com.magicbill.app.ui.screens.bills.BillDetailScreen
import com.magicbill.app.ui.screens.orders.OrderBuilderScreen
import com.magicbill.app.ui.screens.orders.OrderDetailScreen
import com.magicbill.app.ui.screens.orders.OrdersScreen
import com.magicbill.app.ui.screens.owner.StaffManagerScreen
import com.magicbill.app.ui.screens.profile.ProfileScreen
import com.magicbill.app.ui.screens.profile.ProfileSession
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

    // PART C1 — the presence line is held for the whole foreground session
    // for any session with ordering access, so the counter's phone count
    // stops flickering as a waiter moves between tabs. A staff member
    // without take_orders holds nothing at all.
    val canOrder = staff.staff.permissions.has(PermissionKey.TakeOrders)
    val realtime = rootViewModel.ordersRealtime
    androidx.compose.runtime.DisposableEffect(canOrder) {
        realtime.setOrderingAccess(canOrder)
        onDispose { realtime.setOrderingAccess(false) }
    }

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
                    onOpenOrder = { uuid -> navController.navigate(OrderDetailRoute(uuid)) },
                    onNewOrder = { type, table, section ->
                        navController.navigate(
                            OrderBuilderRoute(orderType = type, tableNumber = table, section = section),
                        )
                    },
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
            composable<OrderDetailRoute> { entry ->
                val route = entry.toRoute<OrderDetailRoute>()
                OrderDetailScreen(
                    clientUuid = route.clientUuid,
                    onAddItems = { uuid -> navController.navigate(OrderBuilderRoute(orderClientUuid = uuid)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<OrderBuilderRoute> { entry ->
                val route = entry.toRoute<OrderBuilderRoute>()
                OrderBuilderScreen(
                    orderClientUuid = route.orderClientUuid,
                    orderType = route.orderType,
                    tableNumber = route.tableNumber,
                    section = route.section,
                    onBack = { navController.popBackStack() },
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
    onOpenOrder: (String) -> Unit,
    onNewOrder: (orderType: String, tableNumber: String, section: String) -> Unit,
) {
    val perms = staffSession.staff.permissions
    val updateDot = rootViewModel.updateDotVisible.collectAsStateWithLifecycle().value
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
        // A4 — the update dot, mirroring OwnerShell. Without it a staff phone
        // that dismissed the update sheet had no indication an update existed
        // at all, and staff rarely leave the Orders tab to find out.
        add(
            StaffTab(
                PillNavItem(
                    "Profile", Icons.Outlined.Person, Icons.Filled.Person,
                    showDot = updateDot,
                ),
                "profile",
            ),
        )
    }

    var tabIndex by rememberSaveable(tabs.size) { mutableIntStateOf(0) }
    val currentKey = tabs.getOrNull(tabIndex)?.key ?: "profile"

    // Back: inner tab → first tab; first tab → confirm-exit.
    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBackPress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    androidx.activity.compose.BackHandler {
        when {
            tabIndex != 0 -> tabIndex = 0
            System.currentTimeMillis() - lastBackPress < 2_000 ->
                (context as? android.app.Activity)?.finish()
            else -> {
                lastBackPress = System.currentTimeMillis()
                android.widget.Toast
                    .makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

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
                "orders" -> OrdersScreen(
                    restaurantName = staffSession.restaurant.name,
                    onOpenOrder = onOpenOrder,
                    onNewOrder = onNewOrder,
                )
                else -> ProfileScreen(rootViewModel, ProfileSession.Staff(staffSession))
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
