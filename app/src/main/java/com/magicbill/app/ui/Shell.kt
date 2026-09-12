package com.magicbill.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
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
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
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
                        enterTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) { MBMotion.tabHop(initialState.id, targetState.id); MBMotion.tabEnter } else MBMotion.enterForward(this) },
                        exitTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) { MBMotion.tabHop(initialState.id, targetState.id); MBMotion.tabExit } else MBMotion.exitForward(this) },
                        popEnterTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) { MBMotion.tabHop(initialState.id, targetState.id); MBMotion.tabEnter } else MBMotion.enterBack(this) },
                        popExitTransition = { if (initialState.destination.isTab() && targetState.destination.isTab()) { MBMotion.tabHop(initialState.id, targetState.id); MBMotion.tabExit } else MBMotion.exitBack(this) },
                    ) {
                        // The two doors. An owner signs in; a staff phone scans the counter's code.
                        screen<Welcome> { WelcomeScreen(onOwner = { nav.navigate(OwnerSignIn) }, onStaff = { nav.navigate(PairCounter) }) }
                        screen<OwnerSignIn> { OwnerSignInScreen(back = { nav.popBackStack() }, signUp = { nav.navigate(OwnerSignUp) }, done = { nav.home(vm) }) }
                        screen<OwnerSignUp> { OwnerSignUpScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }
                        screen<PairCounter> { PairScreen(back = { nav.popBackStack() }, done = { nav.home(vm) }) }

                        // The five tabs. A screen that needs the cloud says so when the phone has
                        // no cloud login; Orders says so when the phone is not on a counter.
                        // The door: a shop whose plan is not running shows why, and the way to
                        // magicbill.in, in place of its data.
                        screen<Home> {
                            if (!signedIn) NeedsCloudScreen(onOwner = { nav.navigate(OwnerSignIn) }, onPair = { nav.navigate(PairCounter) })
                            else if (planDoor != null) PlanDoorScreen(planDoor!!, onCheckAgain = vm::checkPlan)
                            else { val unreadNow by vm.unread.collectAsStateWithLifecycle(); HomeScreen(onNotices = { nav.navigate(Notices) }, unread = unreadNow) }
                        }
                        screen<Reports> {
                            if (!signedIn) NeedsCloudScreen(onOwner = { nav.navigate(OwnerSignIn) }, onPair = { nav.navigate(PairCounter) })
                            else if (planDoor != null) PlanDoorScreen(planDoor!!, onCheckAgain = vm::checkPlan)
                            else ReportsScreen(openBill = { nav.navigate(BillDetail(it)) })
                        }
                        screen<Tables> {
                            if (cred == null) ConnectScreen(onPair = { nav.navigate(PairCounter) })
                            else TablesScreen(openOrder = { nav.navigate(it) }, openBuilder = { nav.navigate(it) }, onPair = { nav.navigate(PairCounter) })
                        }
                        screen<AccountScreen> { AccountScreenView(vm, onOwner = { nav.navigate(OwnerSignIn) }, onPair = { nav.navigate(PairCounter) }, onMe = { nav.navigate(Me) }, signedOut = { nav.navigate(Welcome) { popUpTo(0) { inclusive = true } } }) }
                        screen<More> { MoreScreen(vm, nav) }

                        screen<Bills> { BillsScreen(open = { nav.navigate(BillDetail(it)) }) }
                        screen<BillDetail> { BillDetailScreen(back = { nav.popBackStack() }) }
                        screen<Khata> { KhataScreen(open = { nav.navigate(CustomerDetail(it)) }) }
                        screen<CustomerDetail> { CustomerScreen(back = { nav.popBackStack() }, openBill = { nav.navigate(BillDetail(it)) }) }
                        screen<Expenses> { ExpensesScreen(back = { nav.popBackStack() }) }
                        screen<Staff> { StaffScreen(back = { nav.popBackStack() }, openMember = { nav.navigate(StaffEdit(it)) }, openRole = { nav.navigate(RoleEdit(it)) }) }
                        screen<StaffEdit> { StaffEditScreen(back = { nav.popBackStack() }) }
                        screen<RoleEdit> { RoleEditScreen(back = { nav.popBackStack() }) }
                        screen<Devices> { DevicesScreen(back = { nav.popBackStack() }) }
                        screen<Notices> { NoticesScreen(back = { nav.popBackStack() }) }

                        screen<OrderScreen> { OrderScreenView(back = { nav.popBackStack() }, addMore = { nav.navigate(it) }) }
                        screen<NewOrder> { OrderBuilderScreen(back = { nav.popBackStack() }, done = { nav.popBackStack() }) }
                        screen<Queue> { QueueScreen() }
                        screen<Me> { MeScreen(back = { nav.popBackStack() }, onPair = { nav.navigate(PairCounter) }, left = { nav.navigate(More) { popUpTo(0) { inclusive = true } } }) }
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
 * How a page moves is decided by its part in the move (see [MBMotion.Role]): a page opening or
 * closing is drawn in a window that grows out of, or shrinks into, what was tapped; a page
 * beneath, or coming back from beneath, is a picture of itself, zoomed and softened, and is
 * not composed until it has landed. Every side of a move runs on the one pace.
 */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable () -> Unit) =
    composable<T> { entry ->
        val isTab = Tab.entries.any { it.route()::class == T::class }
        LaunchedEffect(entry) { MBMotion.watch(entry) }
        // Moving from the first composition of the move (the transition's running flag is
        // still off then); going = on the way out, whether beneath another page or closed.
        val moving = transition.currentState != transition.targetState
        val going = moving && transition.targetState == EnterExitState.PostExit
        // One value for the whole move: 0 when the page is not in front, 1 when it is.
        val p by transition.animateFloat(transitionSpec = { MBMotion.pace() }, label = "move") { if (it == EnterExitState.Visible) 1f else 0f }
        LaunchedEffect(moving) { if (!moving) MBMotion.landed(entry.id) }
        val role = if (moving) MBMotion.role(entry.id) else null
        val look = MBMotion.look(entry.id)
        // Coming back from beneath with a picture: the picture comes forward, and the page is
        // composed once it has landed.
        if ((role == MBMotion.Role.Return || role == MBMotion.Role.Tab) && !going && look != null) {
            Box(Modifier.fillMaxSize().drawBehind { with(MBMotion) { drawBeneath(look, if (role == MBMotion.Role.Return) 1f - p else 0f) } })
            return@composable
        }
        // The page draws through a layer of its own, so a picture can be taken of it.
        val graphics = LocalGraphicsContext.current
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val page = remember(graphics) { graphics.createGraphicsLayer() }
        DisposableEffect(page) { onDispose { graphics.releaseGraphicsLayer(page) } }
        // Still and in front: the next press takes this page's picture (see [MBMotion.tapped]).
        LaunchedEffect(moving) { if (!moving) MBMotion.inFront(entry.id) { MBMotion.take(page, graphics, density, direction) } }
        // Going beneath: a picture now — for the blur, and for the card that grows out of it —
        // the one taken at the press if there was one, and one again near the end of the
        // move, when the press that opened the page has faded.
        val underneath = going && (role == MBMotion.Role.Beneath || role == MBMotion.Role.Tab)
        LaunchedEffect(underneath) {
            if (!underneath) return@LaunchedEffect
            MBMotion.keep(entry.id, MBMotion.staged(entry.id) ?: MBMotion.take(page, graphics, density, direction) ?: return@LaunchedEffect)
            delay((transition.totalDurationNanos / 1_000_000 - MBMotion.SnapLead).coerceAtLeast(0))
            MBMotion.take(page, graphics, density, direction)?.let { MBMotion.keep(entry.id, it) }
        }
        val windowed = role == MBMotion.Role.Open || role == MBMotion.Role.Close
        val origin = if (windowed) MBMotion.origin(entry.id) else null
        val under = if (windowed) MBMotion.look(MBMotion.beneath(entry.id)) else null
        // Beneath, or coming back, on a phone that paints no pictures: the live page, zoomed and dimmed.
        val liveBehind = moving && !windowed && role != MBMotion.Role.Tab && look == null
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    if (windowed) {
                        val f = MBMotion.frame(origin, size, p, this)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = f.scale; scaleY = f.scale
                        translationX = f.left; translationY = f.top
                        clip = true; shape = MBMotion.Window(f.height, f.radius)
                    } else {
                        val z = if (liveBehind) MBMotion.zoomBehind(1f - p) else 1f
                        transformOrigin = TransformOrigin.Center
                        scaleX = z; scaleY = z
                        translationX = 0f; translationY = 0f
                        clip = false; shape = RectangleShape
                    }
                }
                .drawBehind {
                    // The card the page grows out of and shrinks back into: its piece of the
                    // picture beneath, under the page inside the window, the window's width.
                    if (windowed && under != null && origin != null) {
                        val a = MBMotion.cardAlpha(p)
                        val b = origin.bounds
                        if (a > 0f) drawImage(
                            under.sharp,
                            srcOffset = IntOffset(b.left.roundToInt(), b.top.roundToInt()),
                            srcSize = IntSize(b.width.roundToInt(), b.height.roundToInt()),
                            dstSize = IntSize(size.width.roundToInt(), (size.width * b.height / b.width).roundToInt()),
                            alpha = a,
                        )
                    }
                },
        ) {
            Glow(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        // In the window the page crossfades with the card, and is drawn once
                        // into its own layer: the layer is what scales, not the text.
                        alpha = if (windowed) MBMotion.pageAlpha(p) else 1f
                        compositingStrategy = if (windowed) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }
                    .drawWithContent {
                        page.record { this@drawWithContent.drawContent() }
                        if (role == MBMotion.Role.Beneath && look != null) {
                            with(MBMotion) { drawBeneath(look, 1f - p) }
                        } else {
                            drawLayer(page)
                            if (liveBehind) drawRect(Color.Black, alpha = MBMotion.dimBehind(1f - p))
                        }
                    },
            ) {
                // What arrives while the page moves waits until it has landed (see [held]).
                CompositionLocalProvider(LocalPageMoving provides moving) {
                    if (isTab) {
                        val room = PaddingValues(bottom = LocalBarHeight.current)
                        Box(Modifier.fillMaxSize().padding(room).consumeWindowInsets(room)) { content() }
                    } else content()
                }
            }
        }
    }

/** After a sign-in or a pairing: the first tab this person has, with nothing to go back to. */
private fun androidx.navigation.NavHostController.home(vm: RootViewModel) {
    navigate(vm.tabsNow().first().route()) { popUpTo(0) { inclusive = true }; launchSingleTop = true }
}
