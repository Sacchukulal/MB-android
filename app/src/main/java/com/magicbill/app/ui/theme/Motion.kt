package com.magicbill.app.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavBackStackEntry

/** Shared motion vocabulary — every animated thing in the app draws from here
 *  so the whole app moves with one personality: quick, springy, never floaty. */
object MBMotion {
    val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EaseEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard interactive spring (chips, toggles, selection pills). */
    fun <T> snappy() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Bouncier spring for playful bits (press scale, badges appearing). */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.6f, stiffness = 500f)

    const val DurShort = 200
    const val DurMedium = 350
    const val DurLong = 600

    // ---- Page moves ------------------------------------------------------------------
    // A page opens the way an app opens on a phone: a window with rounded corners grows out
    // of the thing that was tapped, and the page fades in inside it as it grows. The page
    // beneath zooms in a little and darkens; the tapped card is seen through the window
    // until the page covers it. Back is the same film run backwards. One pace, a soft
    // landing, no bounce.
    //
    // Nothing is copied to a picture. Every page already draws through a layer of its own;
    // a page beneath another simply keeps its last layer and stops drawing new ones, and the
    // move transforms that layer. A page coming back is drawn live.

    /** What was tapped: its rectangle on the screen, in pixels, its corner radius, and when. */
    data class Launch(val bounds: Rect, val radius: Float, val atMs: Long)

    /** The part a page plays in the move it is in. A tab hop has no parts: both tabs draw live. */
    enum class Role { Open, Beneath, Return, Close }

    /** The one pace of every page move: away quickly, a soft but definite landing. */
    fun <T> pace(): FiniteAnimationSpec<T> = tween(MoveDuration, easing = CubicBezierEasing(0.35f, 0f, 0.15f, 1f))
    const val MoveDuration = 400

    /** A page opened by nothing in particular grows a little, from the middle. */
    private const val CentreScale = 0.86f
    /** How much the page beneath zooms in while another sits over it. */
    private const val ZoomBehind = 0.06f
    /** How dark the page beneath goes. */
    private const val DimBehind = 0.2f
    /** The page is unseen in its window until here, and whole from here. */
    private const val PageFrom = 0.05f
    private const val PageWhole = 0.5f
    /** A tap older than this opened nothing: the next page grows from the middle. */
    private const val TapFor = 600L

    /** The last thing tapped; the next page opens from it. */
    @Volatile private var pending: Launch? = null
    /** Where each page opened from, by back-stack entry (null: nothing in particular). */
    private val origins = mutableStateMapOf<String, Launch?>()
    private val roles = mutableStateMapOf<String, Role>()
    private val watched = HashSet<String>()

    /** The kit's tappable says what was pressed and where, at the press that opens a page. */
    fun opensFrom(launch: Launch) { pending = launch }

    /**
     * Whether a finger is down on something. A page keeps recording its layer only while
     * nothing is pressed, so the layer it leaves behind is the page at rest — never a
     * half-drawn ripple or a card mid-squish.
     */
    var pressing by mutableStateOf(false)
        private set
    private var presses = 0
    fun pressBegan() { synchronized(this) { presses++; pressing = true } }
    fun pressEnded() { synchronized(this) { presses = (presses - 1).coerceAtLeast(0); pressing = presses > 0 } }

    fun origin(entryId: String): Launch? = origins[entryId]
    fun role(entryId: String): Role? = roles[entryId]

    /** The page has landed and plays no part any more. */
    fun landed(entryId: String) { roles.remove(entryId) }

    // The host asks for a transition more than once per move (once for the size, once per
    // page), so these only note the parts; nothing is taken away until the entry is gone.
    private fun move(top: String, under: String, topRole: Role, underRole: Role) {
        roles[top] = topRole; roles[under] = underRole
    }

    val enterForward: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        move(targetState.id, initialState.id, Role.Open, Role.Beneath)
        if (!origins.containsKey(targetState.id)) {
            val tap = pending
            origins[targetState.id] = tap?.takeIf { System.currentTimeMillis() - it.atMs < TapFor }
            pending = null
        }
        EnterTransition.None
    }
    val exitForward: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        move(targetState.id, initialState.id, Role.Open, Role.Beneath)
        ExitTransition.None
    }
    val enterBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        move(initialState.id, targetState.id, Role.Close, Role.Return)
        EnterTransition.None
    }
    val exitBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        move(initialState.id, targetState.id, Role.Close, Role.Return)
        ExitTransition.None
    }

    /** A tab hop: the old tab is gone at once, the new one fades up with a small lift. */
    val tabEnter: EnterTransition =
        slideInVertically(tween(DurShort, easing = EaseOut)) { it / 30 } + fadeIn(tween(DurShort))
    val tabExit: ExitTransition = fadeOut(tween(90))

    /** The floating tab bar slips down and away when a screen without one opens, and back up on return. */
    val barEnter: EnterTransition = slideInVertically(tween(DurShort, easing = EaseOut)) { it } + fadeIn(tween(DurShort))
    val barExit: ExitTransition = slideOutVertically(tween(DurShort, easing = EaseOut)) { it } + fadeOut(tween(DurShort))

    /** Keeps what is known about [entry] for as long as it is on the back stack. */
    fun watch(entry: NavBackStackEntry) {
        synchronized(watched) { if (!watched.add(entry.id)) return }
        if (entry.lifecycle.currentState == Lifecycle.State.DESTROYED) { forgotten(entry.id); return }
        entry.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) { source.lifecycle.removeObserver(this); forgotten(entry.id) }
            }
        })
    }

    /** The page is gone from the graph: everything about it goes with it. */
    private fun forgotten(entryId: String) {
        synchronized(watched) { watched.remove(entryId) }
        origins.remove(entryId); roles.remove(entryId)
        drop(entryId)
    }

    // ---- The picture a page leaves behind -------------------------------------------
    // A page beneath another is taken apart by the host once the move ends, and put back
    // together only when it returns. A layer does not outlive that (it comes back without
    // its children), so as the page goes beneath ONE picture of its layer is taken — once per
    // move, off the drawing thread's critical path — and the return draws that picture, the
    // page as it was, until the page has landed and is whole. The picture is dropped, never
    // recycled: a recycled bitmap still on its way to the screen is a crash.

    private val kept = HashMap<String, ImageBitmap>()

    fun keep(entryId: String, picture: ImageBitmap) { synchronized(kept) { kept[entryId] = picture } }

    fun kept(entryId: String): ImageBitmap? = synchronized(kept) { kept[entryId] }

    /** The page is whole again, or gone: the picture is let go. */
    fun drop(entryId: String) { synchronized(kept) { kept.remove(entryId) } }

    // ---- The geometry of a move ------------------------------------------------------

    /** How far the page beneath zooms in, [b] of the way under (0: in front, 1: fully under). */
    fun zoomBehind(b: Float): Float = 1f + ZoomBehind * b
    fun dimBehind(b: Float): Float = DimBehind * b

    /** The window a page opens in, [p] of the way from its launch to the whole page: where it is, how big, how tall, how round. */
    class Frame(val scale: Float, val left: Float, val top: Float, val height: Float, val radius: Float)

    fun frame(origin: Launch?, size: Size, p: Float, density: Density): Frame {
        val at = origin?.bounds ?: Rect(
            size.width * (1f - CentreScale) / 2f, size.height * (1f - CentreScale) / 2f,
            size.width * (1f + CentreScale) / 2f, size.height * (1f + CentreScale) / 2f,
        )
        // The page beneath is zoomed while this one is over it, so the card is where the
        // zoom puts it: the window follows it there and lands on it exactly.
        val zoom = zoomBehind(p)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val from = Rect(cx + (at.left - cx) * zoom, cy + (at.top - cy) * zoom, cx + (at.right - cx) * zoom, cy + (at.bottom - cy) * zoom)
        val fromRadius = (origin?.radius ?: with(density) { Radius.xl.toPx() }) * zoom
        val r = lerp(from, Rect(0f, 0f, size.width, size.height), p)
        val s = (r.width / size.width).coerceAtLeast(0.01f)
        return Frame(s, r.left, r.top, r.height / s, lerp(fromRadius, 0f, p) / s)
    }

    /** A rounded rectangle at the top of the page, the page's width and [height] tall: the page is clipped to it while it moves. */
    class Window(private val height: Float, private val radius: Float) : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            Outline.Rounded(RoundRect(0f, 0f, size.width, height, CornerRadius(radius)))
    }

    /** How much of the page shows in its window at [p]: nothing until [PageFrom], all of it from [PageWhole]. */
    fun pageAlpha(p: Float): Float = smooth((p - PageFrom) / (PageWhole - PageFrom))

    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
}
