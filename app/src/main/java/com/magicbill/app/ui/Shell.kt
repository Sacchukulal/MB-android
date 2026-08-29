package com.magicbill.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.magicbill.app.nav.AccountScreen
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
import com.magicbill.app.nav.Tables
import com.magicbill.app.nav.Welcome
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.PillNavBar
import com.magicbill.app.ui.kit.PillNavItem
import com.magicbill.app.ui.kit.Reporter
import com.magicbill.app.ui.kit.ToastHost
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
import com.magicbill.app.ui.screens.more.MoreScreen
import com.magicbill.app.ui.screens.notices.NoticesScreen
import com.magicbill.app.ui.screens.pair.ConnectScreen
import com.magicbill.app.ui.screens.pair.NeedsCloudScreen
import com.magicbill.app.ui.screens.pair.PairScreen
import com.magicbill.app.ui.screens.reports.ReportsScreen
import com.magicbill.app.ui.screens.signin.OwnerSignInScreen
import com.magicbill.app.ui.screens.signin.WelcomeScreen
import com.magicbill.app.ui.screens.staff.RoleEditScreen
import com.magicbill.app.ui.screens.staff.StaffEditScreen
import com.magicbill.app.ui.screens.staff.StaffScreen
import com.magicbill.app.ui.theme.MBMotion

private fun Tab.item(unread: Int) = when (this) {
    Tab.Home -> PillNavItem(label, Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard)
    Tab.Reports -> PillNavItem(label, Icons.Outlined.BarChart, Icons.Filled.BarChart)
    Tab.Orders -> PillNavItem(label, Icons.Outlined.RestaurantMenu, Icons.Filled.RestaurantMenu)
    Tab.Account -> PillNavItem(label, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle)
    Tab.More -> PillNavItem(label, Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, showDot = unread > 0)
}

fun Tab.route(): Any = when (this) {
    Tab.Home -> Home
    Tab.Reports -> Reports
    Tab.Orders -> Tables
    Tab.Account -> AccountScreen
    Tab.More -> More
}


@Composable
fun Shell(vm: RootViewModel) {
    val nav = rememberNavController()
    val hasAnything by vm.hasAnything.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val cred by vm.credential.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reporter = remember { Reporter(scope) }
    val backStack by nav.currentBackStackEntryAsState()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val onTab = tabs.any { t -> backStack?.destination?.hasRoute(t.route()::class) == true }
    val showBar = hasAnything && onTab

    // What the counter said — once, wherever the phone is.
    LaunchedEffect(Unit) { vm.counterSays.collect { reporter.say(it) } }

    // Back on a tab: twice within two seconds leaves the app. Never a dead end, never a surprise.
    var lastBack by remember { mutableLongStateOf(0L) }
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    BackHandler(enabled = showBar) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2_000) activity?.finish() else { lastBack = now; reporter.say("Press back again to leave.") }
    }

    CompositionLocalProvider(LocalReporter provides reporter) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                // Transparent, so the glow behind the whole app shows through.
                containerColor = Color.Transparent,
                bottomBar = {
                    if (showBar) {
                        val selectedIndex = tabs.indexOfFirst { t -> backStack?.destination?.hasRoute(t.route()::class) == true }.coerceAtLeast(0)
                        PillNavBar(
                            items = tabs.map { it.item(unread) },
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
                    // coming back reverses.
                    fun androidx.navigation.NavDestination?.isTab() = this != null && Tab.entries.any { hasRoute(it.route()::class) }
                    // Decided ONCE. A start destination that follows the session would rebuild the
                    // graph the moment a pairing lands and cancel the screen still finishing it.
                    val start = remember { if (hasAnything) vm.tabsNow().first().route() else Welcome }
                    NavHost(
                        nav,
                        startDestination = start,
                        enterTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabEnter else MBMotion.enterForward(this) },
                        exitTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabExit else MBMotion.exitForward(this) },
                        popEnterTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabEnter else MBMotion.enterBack(this) },
                        popExitTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) MBMotion.tabExit else MBMotion.exitBack(this) },
                    ) {
                        // The two doors. An owner signs in; a staff phone scans the counter's code.
                        composable<Welcome> { WelcomeScreen(onOwner = { nav.navigate(OwnerSignIn) }, onStaff = { nav.navigate(PairCounter) }) }
                        composable<OwnerSignIn> { OwnerSignInScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }
                        composable<PairCounter> { PairScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }

                        // The five tabs. A screen that needs the cloud says so when the phone has
                        // no cloud login; Orders says so when the phone is not on a counter.
                        composable<Home> {
                            if (!signedIn) NeedsCloudScreen(onOwner = { nav.navigate(OwnerSignIn) }, onPair = { nav.navigate(PairCounter) })
                            else { val unreadNow by vm.unread.collectAsStateWithLifecycle(); HomeScreen(onNotices = { nav.navigate(Notices) }, unread = unreadNow) }
                        }
                        composable<Reports> {
                            if (!signedIn) NeedsCloudScreen(onOwner = { nav.navigate(OwnerSignIn) }, onPair = { nav.navigate(PairCounter) })
                            else ReportsScreen(openBill = { nav.navigate(BillDetail(it)) })
                        }
                        composable<Tables> {
                            if (cred == null) ConnectScreen(onPair = { nav.navigate(PairCounter) })
                            else TablesScreen(openOrder = { nav.navigate(OrderScreen(it)) }, openBuilder = { nav.navigate(it) }, onPair = { nav.navigate(PairCounter) })
                        }
                        composable<AccountScreen> { AccountScreenView(vm, onOwner = { nav.navigate(OwnerSignIn) }, onPair = { nav.navigate(PairCounter) }, onMe = { nav.navigate(Me) }, signedOut = { nav.navigate(Welcome) { popUpTo(0) { inclusive = true } } }) }
                        composable<More> { MoreScreen(vm, nav) }

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

                        composable<OrderScreen> { OrderScreenView(back = { nav.popBackStack() }, addMore = { nav.navigate(it) }) }
                        composable<NewOrder> { OrderBuilderScreen(back = { nav.popBackStack() }, done = { nav.popBackStack() }) }
                        composable<Queue> { QueueScreen() }
                        composable<Me> { MeScreen(back = { nav.popBackStack() }, onPair = { nav.navigate(PairCounter) }, left = { nav.navigate(More) { popUpTo(0) { inclusive = true } } }) }
                    }
                }
            }
            // The counter's sentence, over everything, under the status bar — never over a button.
            ToastHost(reporter, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** After a sign-in or a pairing: the first tab this person has, with nothing to go back to. */
private fun androidx.navigation.NavHostController.home(vm: RootViewModel) {
    navigate(vm.tabsNow().first().route()) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
}
