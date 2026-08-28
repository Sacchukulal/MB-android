package com.magicbill.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.magicbill.app.nav.AccountScreen
import com.magicbill.app.nav.Appearance
import com.magicbill.app.nav.BillDetail
import com.magicbill.app.nav.Bills
import com.magicbill.app.nav.CustomerDetail
import com.magicbill.app.nav.Devices
import com.magicbill.app.nav.Expenses
import com.magicbill.app.nav.Home
import com.magicbill.app.nav.Khata
import com.magicbill.app.nav.Me
import com.magicbill.app.nav.More
import com.magicbill.app.nav.NewOrder
import com.magicbill.app.nav.Notices
import com.magicbill.app.nav.OrderScreen
import com.magicbill.app.nav.OwnerSignIn
import com.magicbill.app.nav.PairCounter
import com.magicbill.app.nav.Queue
import com.magicbill.app.nav.Reports
import com.magicbill.app.nav.RoleEdit
import com.magicbill.app.nav.Staff
import com.magicbill.app.nav.StaffEdit
import com.magicbill.app.nav.StaffSignIn
import com.magicbill.app.nav.Tables
import com.magicbill.app.nav.Welcome
import com.magicbill.app.ui.components.PillNavBar
import com.magicbill.app.ui.components.PillNavItem
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Reporter
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.screens.Placeholder
import com.magicbill.app.ui.screens.account.AccountScreen as AccountScreenView
import com.magicbill.app.ui.screens.bills.BillDetailScreen
import com.magicbill.app.ui.screens.bills.BillsScreen
import com.magicbill.app.ui.screens.devices.DevicesScreen
import com.magicbill.app.ui.screens.expenses.ExpensesScreen
import com.magicbill.app.ui.screens.floor.MeScreen
import com.magicbill.app.ui.screens.floor.OrderBuilderScreen
import com.magicbill.app.ui.screens.floor.OrderScreenView
import com.magicbill.app.ui.screens.floor.QueueScreen
import com.magicbill.app.ui.screens.floor.TablesScreen
import com.magicbill.app.ui.screens.home.HomeScreen
import com.magicbill.app.ui.screens.khata.CustomerScreen
import com.magicbill.app.ui.screens.khata.KhataScreen
import com.magicbill.app.ui.screens.notices.NoticesScreen
import com.magicbill.app.ui.screens.reports.ReportsScreen
import com.magicbill.app.ui.screens.staff.RoleEditScreen
import com.magicbill.app.ui.screens.staff.StaffEditScreen
import com.magicbill.app.ui.screens.staff.StaffScreen
import com.magicbill.app.ui.screens.more.AppearanceScreen
import com.magicbill.app.ui.screens.more.MoreScreen
import com.magicbill.app.ui.screens.pair.PairScreen
import com.magicbill.app.ui.screens.signin.OwnerSignInScreen
import com.magicbill.app.ui.screens.signin.StaffSignInScreen
import com.magicbill.app.ui.screens.signin.WelcomeScreen
import com.magicbill.app.ui.theme.Mb

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Home -> Icons.Outlined.SpaceDashboard
    Tab.Tables -> Icons.Outlined.Restaurant
    Tab.Reports -> Icons.Outlined.Assessment
    Tab.Account -> Icons.Outlined.AccountCircle
    Tab.Bills -> Icons.Outlined.Receipt
    Tab.Khata -> Icons.Outlined.MenuBook
    Tab.Queue -> Icons.Outlined.PendingActions
    Tab.More -> Icons.Outlined.MoreHoriz
}

fun Tab.route(): Any = when (this) {
    Tab.Home -> Home
    Tab.Tables -> Tables
    Tab.Reports -> Reports
    Tab.Account -> AccountScreen
    Tab.Bills -> Bills
    Tab.Khata -> Khata
    Tab.Queue -> Queue
    Tab.More -> More
}

@Composable
fun Shell(vm: RootViewModel) {
    val nav = rememberNavController()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val hasAnything by vm.hasAnything.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reporter = remember { Reporter(host, scope) }
    val backStack by nav.currentBackStackEntryAsState()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val onTab = tabs.any { t -> backStack?.destination?.hasRoute(t.route()::class) == true }
    val showBar = hasAnything && onTab

    // Back on a tab: twice within two seconds leaves the app. Never a dead end, never a surprise.
    var lastBack by remember { mutableLongStateOf(0L) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    BackHandler(enabled = showBar) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2_000) activity?.finish() else { lastBack = now; reporter.say("Press back again to leave.") }
    }

    CompositionLocalProvider(LocalReporter provides reporter) {
        Scaffold(
            // Transparent, so the GlowBackground behind the whole app shows through — an opaque
            // fill here was exactly what hid the glow.
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(host) { data -> Snackbar(data, containerColor = Mb.colors.ink, contentColor = Mb.colors.bg) } },
            bottomBar = {
                if (showBar) {
                    val selectedIndex = tabs.indexOfFirst { t -> backStack?.destination?.hasRoute(t.route()::class) == true }.coerceAtLeast(0)
                    PillNavBar(
                        items = tabs.map { t -> PillNavItem(t.label, t.icon(), showDot = t == Tab.More && unread > 0) },
                        selectedIndex = selectedIndex,
                        onSelect = { i ->
                            nav.navigate(tabs[i].route()) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                // Tab hops drift-and-fade; drilling into a screen slides from the right, and
                // coming back reverses — the 2.x motion, exactly.
                val tabRoutes = listOf(Home::class, Tables::class, Reports::class, AccountScreen::class, Bills::class, Khata::class, Queue::class, More::class)
                fun androidx.navigation.NavDestination?.isTab() = this != null && tabRoutes.any { hasRoute(it) }
                NavHost(
                    nav,
                    startDestination = if (hasAnything) tabs.first().route() else Welcome,
                    enterTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabEnter else MBMotion.enterForward(this) },
                    exitTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabExit else MBMotion.exitForward(this) },
                    popEnterTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabEnter else MBMotion.enterBack(this) },
                    popExitTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabExit else MBMotion.exitBack(this) },
                ) {
                    composable<Welcome> { WelcomeScreen(onOwner = { nav.navigate(OwnerSignIn) }, onStaff = { nav.navigate(StaffSignIn) }, onPair = { nav.navigate(PairCounter) }) }
                    composable<OwnerSignIn> { OwnerSignInScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }
                    composable<StaffSignIn> { StaffSignInScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }
                    composable<PairCounter> { PairScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }

                    composable<Home> { val unread by vm.unread.collectAsStateWithLifecycle(); HomeScreen(onNotices = { nav.navigate(Notices) }, unread = unread) }
                    composable<Reports> { ReportsScreen(openBill = { nav.navigate(BillDetail(it)) }) }
                    composable<Bills> { BillsScreen(open = { nav.navigate(BillDetail(it)) }) }
                    composable<BillDetail> { BillDetailScreen(back = { nav.popBackStack() }) }
                    composable<Khata> { KhataScreen(open = { nav.navigate(CustomerDetail(it)) }) }
                    composable<CustomerDetail> { CustomerScreen(back = { nav.popBackStack() }, openBill = { nav.navigate(BillDetail(it)) }) }
                    composable<Expenses> { ExpensesScreen(back = { nav.popBackStack() }) }
                    composable<Staff> { StaffScreen(back = { nav.popBackStack() }, openMember = { nav.navigate(StaffEdit(it)) }, openRole = { nav.navigate(RoleEdit(it)) }) }
                    composable<StaffEdit> { StaffEditScreen(back = { nav.popBackStack() }) }
                    composable<RoleEdit> { RoleEditScreen(back = { nav.popBackStack() }) }
                    composable<Devices> { DevicesScreen(back = { nav.popBackStack() }) }
                    composable<Notices> { NoticesScreen(back = { nav.popBackStack() }) }
                    composable<AccountScreen> { AccountScreenView(back = { nav.popBackStack() }, signedOut = { nav.navigate(Welcome) { popUpTo(0) { inclusive = true } } }) }
                    composable<More> { MoreScreen(vm, nav) }
                    composable<Appearance> { AppearanceScreen(back = { nav.popBackStack() }) }

                    composable<Tables> { TablesScreen(openOrder = { nav.navigate(OrderScreen(it)) }, openBuilder = { nav.navigate(it) }, onPair = { nav.navigate(PairCounter) }) }
                    composable<OrderScreen> { OrderScreenView(back = { nav.popBackStack() }, addMore = { nav.navigate(it) }) }
                    composable<NewOrder> { OrderBuilderScreen(back = { nav.popBackStack() }, done = { nav.popBackStack() }) }
                    composable<Queue> { QueueScreen() }
                    composable<Me> { MeScreen(back = { nav.popBackStack() }, onPair = { nav.navigate(PairCounter) }, left = { nav.navigate(More) { popUpTo(0) { inclusive = true } } }) }
                }
            }
        }
    }
}

/** After a sign-in or a pairing: the first tab, with nothing to go back to. */
private fun androidx.navigation.NavHostController.home(vm: RootViewModel) {
    val first = vm.tabsNow().first().route()
    navigate(first) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
}
