package com.magicbill.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
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
import com.magicbill.app.nav.OwnerSignUp
import com.magicbill.app.nav.PairCounter
import com.magicbill.app.nav.Queue
import com.magicbill.app.nav.Reports
import com.magicbill.app.nav.RoleEdit
import com.magicbill.app.nav.Staff
import com.magicbill.app.nav.StaffEdit
import com.magicbill.app.nav.Tables
import com.magicbill.app.nav.Welcome
import com.magicbill.app.ui.kit.Glow
import com.magicbill.app.ui.kit.LocalPageMoving
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
import com.magicbill.app.ui.screens.more.UpdateSheet
import com.magicbill.app.ui.screens.notices.NoticesScreen
import com.magicbill.app.ui.screens.pair.ConnectScreen
import com.magicbill.app.ui.screens.pair.NeedsCloudScreen
import com.magicbill.app.ui.screens.pair.PairScreen
import com.magicbill.app.ui.screens.reports.ReportsScreen
import com.magicbill.app.ui.screens.signin.OwnerSignInScreen
import com.magicbill.app.ui.screens.signin.OwnerSignUpScreen
import com.magicbill.app.ui.screens.signin.PlanDoorScreen
import com.magicbill.app.ui.screens.signin.WelcomeScreen
import com.magicbill.app.ui.screens.staff.RoleEditScreen
import com.magicbill.app.ui.screens.staff.StaffEditScreen
import com.magicbill.app.ui.screens.staff.StaffScreen
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Mb

/** [moreDot]: an unread notice, or an update waiting behind "Not now". */
private fun Tab.item(moreDot: Boolean) = when (this) {
    Tab.Home -> PillNavItem(label, Icons.Outlined.SpaceDashboard, Icons.Filled.SpaceDashboard)
    Tab.Reports -> PillNavItem(label, Icons.Outlined.BarChart, Icons.Filled.BarChart)
    Tab.Orders -> PillNavItem(label, Icons.Outlined.RestaurantMenu, Icons.Filled.RestaurantMenu)
    Tab.Account -> PillNavItem(label, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle)
    Tab.More -> PillNavItem(label, Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, showDot = moreDot)
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
    // Nothing is decided before the boxes are read: a signed-in phone would otherwise start
    // on Welcome every time it was opened. The splash stays up until this is true.
    val booted by vm.booted.collectAsStateWithLifecycle()
    if (!booted) return
    val nav = rememberNavController()
    val hasAnything by vm.hasAnything.collectAsStateWithLifecycle()
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val planDoor by vm.planDoor.collectAsStateWithLifecycle()
    val cred by vm.credential.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reporter = remember { Reporter(scope) }
    val backStack by nav.currentBackStackEntryAsState()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updateOpen by vm.updateOpen.collectAsStateWithLifecycle()
    val updateWaiting by vm.updateWaiting.collectAsStateWithLifecycle()
    val onTab = tabs.any { t -> backStack?.destination?.hasRoute(t.route()::class) == true }
    val showBar = hasAnything && onTab

    // In front again: who this phone is may have changed while it was away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.resumed() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // What the counter said — once, wherever the phone is.
    LaunchedEffect(Unit) { vm.counterSays.collect { reporter.say(it) } }

    // A login that lands while the phone sits on Welcome — the counter signing a paired phone
    // in at start — moves it to its first tab. Only from Welcome, which has nothing in flight;
    // a sign-in or pairing screen finishes itself.
    LaunchedEffect(hasAnything, backStack) {
        if (hasAnything && backStack?.destination?.hasRoute(Welcome::class) == true) nav.home(vm)
    }

    // Back on a tab: twice within two seconds leaves the app. Never a dead end, never a surprise.
    var lastBack by remember { mutableLongStateOf(0L) }
    val activity = androidx.activity.compose.LocalActivity.current
    BackHandler(enabled = showBar) {
        val now = System.currentTimeMillis()
        if (now - lastBack < 2_000) activity?.finish() else { lastBack = now; reporter.say("Press back again to leave.") }
    }

    CompositionLocalProvider(LocalReporter provides reporter) {
        Box(Modifier.fillMaxSize()) {
            // The bar's height, kept once measured, so a tab screen leaves it room even while
            // the bar animates away: nothing beneath re-lays out while a page opens.
            var barHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalBarHeight provides barHeight) {
                Box(Modifier.fillMaxSize()) {
                    // Tab hops drift-and-fade; a page grows out of what was tapped and shrinks
                    // back into it.
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
                        screen<Welcome> { WelcomeScreen(onOwner = { nav.open(OwnerSignIn) }, onStaff = { nav.open(PairCounter) }) }
                        screen<OwnerSignIn> { OwnerSignInScreen(back = { nav.popBackStack() }, signUp = { nav.open(OwnerSignUp) }, done = { nav.home(vm) }) }
                        screen<OwnerSignUp> { OwnerSignUpScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }
                        screen<PairCounter> { PairScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }

                        // The five tabs. A screen that needs the cloud says so when the phone has
                        // no cloud login; Orders says so when the phone is not on a counter.
                        // The door: a shop whose plan is not running shows why, and the way to
                        // magicbill.in, in place of its data.
                        screen<Home> {
                            if (!signedIn) NeedsCloudScreen(onOwner = { nav.open(OwnerSignIn) }, onPair = { nav.open(PairCounter) })
                            else if (planDoor != null) PlanDoorScreen(planDoor!!, onCheckAgain = vm::checkPlan)
                            else { val unreadNow by vm.unread.collectAsStateWithLifecycle(); HomeScreen(onNotices = { nav.open(Notices) }, unread = unreadNow) }
                        }
                        screen<Reports> {
                            if (!signedIn) NeedsCloudScreen(onOwner = { nav.open(OwnerSignIn) }, onPair = { nav.open(PairCounter) })
                            else if (planDoor != null) PlanDoorScreen(planDoor!!, onCheckAgain = vm::checkPlan)
                            else ReportsScreen(openBill = { nav.open(BillDetail(it)) })
                        }
                        screen<Tables> {
                            if (cred == null) ConnectScreen(onPair = { nav.open(PairCounter) })
                            else TablesScreen(openOrder = { nav.open(it) }, openBuilder = { nav.open(it) }, onPair = { nav.open(PairCounter) })
                        }
                        screen<AccountScreen> { AccountScreenView(vm, onOwner = { nav.open(OwnerSignIn) }, onPair = { nav.open(PairCounter) }, onMe = { nav.open(Me) }, signedOut = { nav.navigate(Welcome) { popUpTo(0) { inclusive = true } } }) }
                        screen<More> { MoreScreen(vm, nav) }

                        screen<Bills> { BillsScreen(open = { nav.open(BillDetail(it)) }) }
                        screen<BillDetail> { BillDetailScreen(back = { nav.popBackStack() }) }
                        screen<Khata> { KhataScreen(open = { nav.open(CustomerDetail(it)) }) }
                        screen<CustomerDetail> { CustomerScreen(back = { nav.popBackStack() }, openBill = { nav.open(BillDetail(it)) }) }
                        screen<Expenses> { ExpensesScreen(back = { nav.popBackStack() }) }
                        screen<Staff> { StaffScreen(back = { nav.popBackStack() }, openMember = { nav.open(StaffEdit(it)) }, openRole = { nav.open(RoleEdit(it)) }) }
                        screen<StaffEdit> { StaffEditScreen(back = { nav.popBackStack() }) }
                        screen<RoleEdit> { RoleEditScreen(back = { nav.popBackStack() }) }
                        screen<Devices> { DevicesScreen(back = { nav.popBackStack() }) }
                        screen<Notices> { NoticesScreen(back = { nav.popBackStack() }) }

                        screen<OrderScreen> { OrderScreenView(back = { nav.popBackStack() }, addMore = { nav.open(it) }) }
                        screen<NewOrder> { OrderBuilderScreen(back = { nav.popBackStack() }, done = { nav.popBackStack() }) }
                        screen<Queue> { QueueScreen() }
                        screen<Me> { MeScreen(back = { nav.popBackStack() }, onPair = { nav.open(PairCounter) }, left = { nav.navigate(More) { popUpTo(0) { inclusive = true } } }) }
                    }
                }
            }
            // The bar floats over the screens and slips away when a screen without one opens.
            AnimatedVisibility(showBar, Modifier.align(Alignment.BottomCenter), enter = MBMotion.barEnter, exit = MBMotion.barExit) {
                val selectedIndex = tabs.indexOfFirst { t -> backStack?.destination?.hasRoute(t.route()::class) == true }.coerceAtLeast(0)
                PillNavBar(
                    items = tabs.map { it.item(unread > 0 || updateWaiting) },
                    selectedIndex = selectedIndex,
                    onSelect = { i ->
                        nav.navigate(tabs[i].route()) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                )
            }
            // The counter's sentence, over everything, under the status bar — never over a button.
            ToastHost(reporter, Modifier.align(Alignment.TopCenter))
            // A newer build on the shelf — only over the tabs, never over a sign-in or a pairing.
            if (updateOpen && showBar) UpdateSheet(update, vm.updater)
        }
    }
}

/** The floating bar's height, for the screens that sit behind it. */
private val LocalBarHeight = compositionLocalOf { 0.dp }

/**
 * One destination of the graph, on its own opaque canvas: the glow and the background are
 * drawn per screen, so a screen sliding in over another is never seen through. A tab screen
 * leaves the bar's room at the bottom; the bar's inset is taken there, so a keyboard below
 * is not counted twice.
 *
 * How a page moves is decided by its part in the move (see [MBMotion.Role]). Every page draws
 * through a layer of its own. A page opening or closing is that layer in a window that grows
 * out of, or shrinks into, what was tapped, fading in as it grows. A page beneath keeps its
 * last layer and draws nothing new while another sits over it; the move zooms and dims the
 * layer. A page coming back is drawn live. Nothing is copied to a picture.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable () -> Unit) =
    composable<T> { entry ->
        val isTab = Tab.entries.any { it.route()::class == T::class }
        LaunchedEffect(entry) { MBMotion.watch(entry) }
        // Moving from the first composition of the move (the transition's running flag is
        // still off then). One value for the whole move: 0 when the page is not in front, 1 when it is.
        val moving = transition.currentState != transition.targetState
        val p by transition.animateFloat(transitionSpec = { MBMotion.pace() }, label = "move") { if (it == EnterExitState.Visible) 1f else 0f }
        LaunchedEffect(moving) { if (!moving) MBMotion.landed(entry.id) }
        val role = if (moving) MBMotion.role(entry.id) else null
        val windowed = role == MBMotion.Role.Open || role == MBMotion.Role.Close
        val origin = if (windowed) MBMotion.origin(entry.id) else null
        val graphics = LocalGraphicsContext.current
        val scrim = Mb.colors.scrim
        // The page draws through a layer of its own.
        val page = remember(graphics) { graphics.createGraphicsLayer() }
        DisposableEffect(page) { onDispose { graphics.releaseGraphicsLayer(page) } }
        // Gone beneath and the move over: the host composes the page once more before taking
        // it down. Nothing is composed or recorded then.
        if (!moving && transition.currentState == EnterExitState.PostExit) {
            Box(Modifier.fillMaxSize())
            return@composable
        }
        // Beneath another page the page is its layer and nothing else, and one picture of that
        // layer is taken for the return; coming back, the page is that picture. Either way it
        // is zoomed a little and dimmed, and nothing of it is composed until it has landed.
        val picture = if (role == MBMotion.Role.Return) MBMotion.kept(entry.id) else null
        if (role == MBMotion.Role.Beneath || picture != null) {
            if (role == MBMotion.Role.Beneath) {
                LaunchedEffect(Unit) { try { MBMotion.keep(entry.id, page.toImageBitmap()) } catch (e: IllegalStateException) { /* nothing drawn yet: the return composes live */ } }
            }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { val z = MBMotion.zoomBehind(1f - p); transformOrigin = TransformOrigin.Center; scaleX = z; scaleY = z }
                    .drawBehind {
                        if (picture != null) drawImage(picture) else drawLayer(page)
                        drawRect(scrim, alpha = MBMotion.dimBehind(1f - p))
                    },
            )
            return@composable
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                if (windowed) {
                    val f = MBMotion.frame(origin, size, p, this)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = f.scale; scaleY = f.scale
                    translationX = f.left; translationY = f.top
                    clip = true; shape = MBMotion.Window(f.height, f.radius)
                    alpha = MBMotion.pageAlpha(p)
                    // Drawn once into a texture that the window then scales: the text is not
                    // rasterised again at every new size.
                    compositingStrategy = CompositingStrategy.Offscreen
                } else {
                    transformOrigin = TransformOrigin.Center
                    scaleX = 1f; scaleY = 1f
                    translationX = 0f; translationY = 0f
                    clip = false; shape = RectangleShape
                    alpha = 1f
                    compositingStrategy = CompositingStrategy.Auto
                }
            },
        ) {
            Glow(
                Modifier.fillMaxSize().drawWithContent {
                    // The layer is the page at rest: while a finger is down the page draws
                    // live and the layer keeps the frame from before the press.
                    if (MBMotion.pressing) {
                        drawContent()
                    } else {
                        page.record { this@drawWithContent.drawContent() }
                        drawLayer(page)
                    }
                },
            ) {
                CompositionLocalProvider(LocalPageMoving provides moving) {
                    if (isTab) {
                        val room = PaddingValues(bottom = LocalBarHeight.current)
                        Box(Modifier.fillMaxSize().padding(room).consumeWindowInsets(room)) { content() }
                    } else content()
                }
            }
            // Landed with the picture still up: the page is built beneath it and the picture
            // fades away — a page that changed while it was beneath (a dish sent, a table
            // taken) crossfades to what it is now; one that did not looks untouched.
            val landedWith = if (!moving && transition.currentState == EnterExitState.Visible) MBMotion.kept(entry.id) else null
            if (landedWith != null) {
                var veiled by remember(landedWith) { mutableStateOf(true) }
                if (veiled) {
                    val veil = remember(landedWith) { Animatable(1f) }
                    LaunchedEffect(landedWith) {
                        veil.animateTo(0f, tween(MBMotion.DurShort, easing = MBMotion.EaseOut))
                        MBMotion.drop(entry.id)
                        veiled = false
                    }
                    Box(Modifier.matchParentSize().drawBehind { drawImage(landedWith, alpha = veil.value) })
                }
            }
        }
    }

/** After a sign-in or a pairing: the first tab this person has, with nothing to go back to. */
private fun androidx.navigation.NavHostController.home(vm: RootViewModel) {
    navigate(vm.tabsNow().first().route()) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
}

/**
 * Opens a page over the one in front — once. A second tap while the first page is still on
 * its way is dropped: the page in front is not resumed until it has landed.
 */
private fun androidx.navigation.NavHostController.open(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}
