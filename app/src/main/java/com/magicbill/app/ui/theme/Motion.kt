package com.magicbill.app.ui.theme

import android.os.Build
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavBackStackEntry
import kotlin.math.roundToInt

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
    // A page opens the way an app opens on a phone. The thing that was tapped — the table
    // card, the button — grows into a window with rounded corners; for the first moment the
    // window still shows the card itself, then the page crossfades in, drawn small and
    // growing with the window until it is the whole screen. The page beneath zooms in a
    // little, softens and darkens. Back is the same film run backwards: the page shrinks
    // into its window, the card shows through as it lands, and the page beneath comes
    // forward, sharp again. One pace for everything, a soft landing, no bounce.
    //
    // While a page is beneath another, or coming back from beneath, it is a PICTURE of
    // itself (a flat bitmap): the zoom and the blur are done on the picture, and the page
    // is not even composed until it has landed — composing and measuring a floor of cards
    // took the first quarter of the move on a slow phone. The picture is taken as the page
    // goes beneath, and once more near the end of that move, when the press that opened the
    // page over it has faded. A phone that cannot paint a picture on its graphics chip
    // (before Android 9 it would be painted on the processor, slowly) gets the zoom and the
    // dim on the live page and no blur; nothing else changes.

    /** What was tapped: its rectangle on the screen, in pixels, and its corner radius. */
    data class Launch(val bounds: Rect, val radius: Float)

    /** The part a page plays in the move it is in. */
    enum class Role { Open, Beneath, Return, Close, Tab }

    /** A page's picture of itself, and a small soft copy of it for the blur. */
    class Look(val sharp: ImageBitmap, val soft: ImageBitmap) {
        fun recycle() { sharp.asAndroidBitmap().recycle(); soft.asAndroidBitmap().recycle() }
    }

    /**
     * The one pace of every page move, on both sides of it: away quickly, a soft but definite
     * landing, no bounce. A curve, not a spring: a spring creeps for its last few percent,
     * and a window that is still 2% short of its card while the card already shows through
     * it is seen twice.
     */
    fun <T> pace(): FiniteAnimationSpec<T> = tween(MoveDuration, easing = CubicBezierEasing(0.35f, 0f, 0.15f, 1f))
    const val MoveDuration = 400

    /** Pictures are painted on the graphics chip from Android 9; before that, slowly, on the processor. */
    val pictures = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** A page opened by nothing in particular grows a little, from the middle. */
    private const val CentreScale = 0.86f
    /** How much the page beneath zooms in while another sits over it. */
    private const val ZoomBehind = 0.06f
    /** How dark the page beneath goes. */
    private const val DimBehind = 0.2f
    /** The soft copy is this many times smaller; stretched back, it is the blur. */
    private const val SoftShrink = 6
    /** The card shows in the window until here, and the page is whole from here. */
    private const val CardUntil = 0.15f
    private const val PageFrom = 0.6f
    /**
     * How long before its move ends a page going beneath has its picture taken again: late
     * enough that the press which opened the page over it has faded, early enough that the
     * page is still composed (it is dropped the moment the move ends).
     */
    const val SnapLead = 60L

    /** The last thing tapped; the next page opens from it. */
    @Volatile private var lastTap: Launch? = null
    /** Where each page opened from, by back-stack entry (null: nothing in particular), and the lock for all of these. */
    private val origins = HashMap<String, Launch?>()
    private val roles = HashMap<String, Role>()
    /** For a page opening or closing: the page under it. */
    private val beneathOf = HashMap<String, String>()
    private val watched = HashSet<String>()
    /** Each page's picture, by entry. Observable, so a page draws again the moment its picture is there. */
    private val looks = mutableStateMapOf<String, Look>()

    /**
     * The kit's clickables say what was pressed and where; nothing else needs to know. The
     * page in front takes its picture there and then: the finger is down, nothing else is
     * happening, and the press has not been drawn yet, so the picture is of the page at rest.
     * If the press opens a page, the picture is ready before the move starts.
     */
    suspend fun tapped(launch: Launch) {
        lastTap = launch
        val (id, taker) = synchronized(origins) { inFront } ?: return
        val look = taker() ?: return
        synchronized(origins) { staged?.second?.recycle(); staged = id to look; stagedAt = System.currentTimeMillis() }
    }
    /** The page in front, and how to take its picture; set by the page once it is still. */
    private var inFront: Pair<String, suspend () -> Look?>? = null
    private var staged: Pair<String, Look>? = null
    private var stagedAt = 0L
    /** For how long a picture taken at a press still counts as current. */
    private const val StagedFor = 1500L

    fun inFront(entryId: String, taker: suspend () -> Look?) = synchronized(origins) { inFront = entryId to taker }
    /** The picture taken at the press that opened the page over [entryId], if it is that recent. */
    fun staged(entryId: String): Look? = synchronized(origins) {
        val s = staged ?: return null
        staged = null
        if (s.first == entryId && System.currentTimeMillis() - stagedAt < StagedFor) s.second else { s.second.recycle(); null }
    }

    fun origin(entryId: String): Launch? = synchronized(origins) { origins[entryId] }
    fun role(entryId: String): Role? = synchronized(origins) { roles[entryId] }
    fun beneath(entryId: String): String? = synchronized(origins) { beneathOf[entryId] }
    fun look(entryId: String?): Look? = entryId?.let { looks[it] }

    /** The page's picture is this, now. */
    fun keep(entryId: String, look: Look) { looks.put(entryId, look)?.recycle() }
    /**
     * The page has landed and plays no part any more. Its picture stays until the next one
     * replaces it or the entry ends: a page over it may still be drawing the card out of it
     * for a frame, and a blank card is worse than a picture kept a little longer.
     */
    fun landed(entryId: String) { synchronized(origins) { roles.remove(entryId) } }

    // The host asks for a transition more than once per move (once for the size, once per
    // page), so these only note the parts; nothing is taken away until the entry is gone.
    private fun move(top: String, under: String, topRole: Role, underRole: Role) = synchronized(origins) {
        roles[top] = topRole; roles[under] = underRole; beneathOf[top] = under
    }

    val enterForward: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        move(targetState.id, initialState.id, Role.Open, Role.Beneath)
        synchronized(origins) { if (!origins.containsKey(targetState.id)) { origins[targetState.id] = lastTap; lastTap = null } }
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
    fun tabHop(from: String, to: String) = synchronized(origins) { roles[from] = Role.Tab; roles[to] = Role.Tab }
    val tabEnter: EnterTransition =
        slideInVertically(tween(DurShort, easing = EaseOut)) { it / 30 } + fadeIn(tween(DurShort))
    val tabExit: ExitTransition = fadeOut(tween(90))

    /** The floating tab bar slips down and away when a screen without one opens, and back up on return. */
    val barEnter: EnterTransition = slideInVertically(tween(DurShort, easing = EaseOut)) { it } + fadeIn(tween(DurShort))
    val barExit: ExitTransition = slideOutVertically(tween(DurShort, easing = EaseOut)) { it } + fadeOut(tween(DurShort))

    /**
     * Keeps what is known about [entry] for as long as it is on the back stack. A page's
     * composition comes and goes while the entry is still there — it is dropped once another
     * page has opened over it — so nothing can be let go before the entry itself ends.
     */
    fun watch(entry: NavBackStackEntry) {
        synchronized(origins) { if (!watched.add(entry.id)) return }
        if (entry.lifecycle.currentState == Lifecycle.State.DESTROYED) { forgotten(entry.id); return }
        entry.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) { source.lifecycle.removeObserver(this); forgotten(entry.id) }
            }
        })
    }

    /** The page is gone from the graph: everything about it goes with it. */
    private fun forgotten(entryId: String) {
        synchronized(origins) { origins.remove(entryId); roles.remove(entryId); beneathOf.remove(entryId); watched.remove(entryId) }
        looks.remove(entryId)?.recycle()
    }

    // ---- Painting a move -------------------------------------------------------------

    /**
     * The page's picture: its layer painted into a bitmap, and a small soft copy for the
     * blur. Null on a phone that would paint it slowly. Main thread; the layer must have
     * been drawn.
     */
    suspend fun take(page: GraphicsLayer, graphics: GraphicsContext, density: Density, direction: LayoutDirection): Look? {
        if (!pictures) return null
        val sharp = page.toImageBitmap()
        val w = (sharp.width / SoftShrink).coerceAtLeast(1)
        val h = (sharp.height / SoftShrink).coerceAtLeast(1)
        val small = graphics.createGraphicsLayer()
        try {
            // Where the phone has a real blur, it smooths the small copy; shrinking alone softens it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) small.renderEffect = BlurEffect(2f, 2f)
            small.record(density, direction, IntSize(w, h)) {
                drawImage(sharp, dstSize = IntSize(w, h), filterQuality = FilterQuality.High)
            }
            return Look(sharp, small.toImageBitmap())
        } finally {
            graphics.releaseGraphicsLayer(small)
        }
    }

    /** The page beneath, [b] of the way under (0: in front, 1: fully under): zoomed a little, softened, darkened. */
    fun DrawScope.drawBeneath(look: Look, b: Float) {
        val zoom = 1f + ZoomBehind * b
        val dst = IntSize((size.width * zoom).roundToInt(), (size.height * zoom).roundToInt())
        val at = IntOffset(((size.width - dst.width) / 2).roundToInt(), ((size.height - dst.height) / 2).roundToInt())
        drawImage(look.sharp, dstOffset = at, dstSize = dst)
        if (b > 0f) {
            drawImage(look.soft, dstOffset = at, dstSize = dst, alpha = b, filterQuality = FilterQuality.High)
            drawRect(Color.Black, alpha = DimBehind * b)
        }
    }

    /** How far the page beneath zooms in, for a page drawn live because it has no picture. */
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

    /** How much of the page shows in the window at [p]: the card until [CardUntil], the page from [PageFrom]. */
    fun pageAlpha(p: Float): Float = smooth((p - CardUntil) / (PageFrom - CardUntil))

    /**
     * How much of the card shows in the window at [p]. The card's own picture is under the
     * window on the page beneath, so as the window lands on it the two would be seen at once,
     * a few pixels apart: the window's copy dissolves over the last [CardGone] of the way,
     * and the card itself is what is left.
     */
    fun cardAlpha(p: Float): Float = (1f - pageAlpha(p)) * smooth((p - CardGone) / CardGone)
    private const val CardGone = 0.08f

    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
}
