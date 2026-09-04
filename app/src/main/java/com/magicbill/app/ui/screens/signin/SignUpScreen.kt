package com.magicbill.app.ui.screens.signin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.magicbill.app.BuildConfig
import com.magicbill.app.ui.kit.Busy
import com.magicbill.app.ui.kit.IconAction
import com.magicbill.app.ui.kit.IconDisc
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.PageHeader
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Radius
import com.magicbill.app.ui.theme.Space
import java.net.URISyntaxException

/*
 * The sign-up, inside the app. magicbill.in already knows how to make an account, take the
 * payment and issue the licence key — none of that is worth writing twice on a phone — so the
 * app opens that page in a window of its own, with the app's header above it and no browser
 * around it. When the shop has its licence, the website hands this window the session it made
 * and the phone signs itself in with it.
 *
 * The bridge is `androidx.webkit`'s message listener, which the WebView injects ONLY into
 * frames served from [SITE_ORIGINS] — a payment page, a bank's page, anything else, never sees
 * it. That is why it is used instead of a JavaScript interface.
 */

private const val SITE = "https://www.magicbill.in"
private const val BRIDGE = "MagicBillApp"
private val SITE_ORIGINS = setOf("https://www.magicbill.in", "https://magicbill.in")

@Composable
fun OwnerSignUpScreen(back: () -> Unit, done: () -> Unit, vm: SignInViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reporter = LocalReporter.current
    val canvas = Mb.colors.bg.toArgb()
    val dark = Mb.colors.isDark
    val progress = remember { mutableIntStateOf(0) }
    val popup = remember { mutableStateOf<WebView?>(null) }
    val failed = remember { mutableStateOf(false) }

    val client = remember { SiteClient(context, vm::fromWebsite) { failed.value = it } }
    val web = remember {
        webView(context, canvas).apply {
            webViewClient = client
            webChromeClient = SiteChrome(context, canvas, client, { progress.intValue = it }, { popup.value = it }, popupsAllowed = true)
            // Added before the first load, or the page is drawn with nothing to hand back to.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(this, BRIDGE, SITE_ORIGINS) { _, message, _, isMainFrame, _ ->
                    if (isMainFrame) message.data?.let(vm::fromWebsite)
                }
            }
            // `plan` takes the finished account straight to the plan cards: the trial is not
            // what this door is for, and the site never offers it on that page.
            loadUrl("$SITE/signup?plan=monthly&theme=" + if (dark) "dark" else "light")
        }
    }

    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
    }

    // The key is the one thing this phone cannot give back later without being asked for it:
    // it is typed into the counter's PC. So the window closes onto the key, not onto the tabs.
    LaunchedEffect(state.done) { if (state.done && state.licenceKey == null) done() }
    LaunchedEffect(state.sentence) { state.sentence?.let(reporter::say) }
    LaunchedEffect(state.noShop) {
        if (state.noShop) reporter.say("Your shop is not ready yet. Finish the plan here, then it will open.")
    }

    val goBack = { if (web.canGoBack()) web.goBack() else back() }
    BackHandler { if (popup.value != null) popup.value = null else goBack() }

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Sign up", "at magicbill.in", back = goBack,
            actions = {
                IconAction(Icons.Outlined.OpenInBrowser, "Open in the browser", {
                    openOutside(context, null, web.url ?: "$SITE/signup")
                }, tint = Mb.colors.inkMuted)
            },
        )
        // How far the page has come, as a hairline. A spinner over a half-drawn form is worse.
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progress.intValue in 1..99) {
                Box(Modifier.fillMaxWidth(progress.intValue / 100f).height(2.dp).background(Mb.colors.accent))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()) {
            AndroidView({ web }, Modifier.fillMaxSize())
            // A bank's page opens in a window of its own; it sits over the sign-up until it closes.
            popup.value?.let { p -> key(p) { AndroidView({ p }, Modifier.fillMaxSize(), onRelease = { it.destroy() }) } }
            if (failed.value) {
                Column(Modifier.fillMaxSize().background(Mb.colors.bg).padding(Gap.page), verticalArrangement = Arrangement.Center) {
                    Notice(Tone.Danger, "Could not reach magicbill.in. Check this phone's internet and try again.")
                    VGap(Gap.group)
                    PrimaryButton("Try again", { failed.value = false; web.reload() }, Modifier.fillMaxWidth())
                }
            }
            if (state.busy) {
                Box(Modifier.fillMaxSize().background(Mb.colors.bg), contentAlignment = Alignment.Center) { Busy() }
            }
            state.licenceKey?.takeIf { state.done }?.let { key -> Activated(key, done) }
        }
    }
}

/** The end of the sign-up: the phone is already signed in, and here is what the counter needs. */
@Composable
private fun Activated(licenceKey: String, done: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val reporter = LocalReporter.current
    Column(
        Modifier.fillMaxSize().background(Mb.colors.bg).padding(Gap.page).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconDisc(Icons.Outlined.CheckCircle, tint = Mb.colors.ok)
        VGap(Gap.group)
        Text("Your shop is live.", style = Mb.type.page, color = Mb.colors.ink)
        VGap(Gap.inline)
        Text(
            "Type this key into Magic Bill on the counter's computer to activate it. It stays on Account › Licence.",
            style = Mb.type.body, color = Mb.colors.inkMuted, textAlign = TextAlign.Center,
        )
        VGap(Gap.group)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(Mb.colors.raised).padding(vertical = Space.s5, horizontal = Space.s4),
            contentAlignment = Alignment.Center,
        ) {
            Text(licenceKey, style = Mb.type.page.copy(letterSpacing = 2.sp), color = Mb.colors.accent, textAlign = TextAlign.Center)
        }
        VGap(Gap.field)
        QuietButton("Copy the key", { clipboard.setText(AnnotatedString(licenceKey)); reporter.say("Copied.") })
        VGap(Gap.section)
        PrimaryButton("Continue", done, Modifier.fillMaxWidth())
    }
}



/** Every WebView this screen makes — the sign-up and any window a payment opens — is this one. */
private fun webView(context: Context, canvas: Int): WebView = WebView(context).apply {
    setBackgroundColor(canvas)
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        loadWithOverviewMode = true
        useWideViewPort = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        allowFileAccess = false
        allowContentAccess = false
        // The site reads this to drop its own header and to know where to hand the session.
        userAgentString = "$userAgentString $BRIDGE/${BuildConfig.VERSION_NAME}"
    }
    CookieManager.getInstance().setAcceptCookie(true)
    // A card payment's 3-D Secure step is served by the bank, not by magicbill.in.
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

/** What the window may open, and what leaves the app. */
private class SiteClient(
    private val context: Context,
    private val onPayload: (String) -> Unit,
    private val onFailed: (Boolean) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        return when (url.scheme?.lowercase()) {
            "http", "https" -> false
            // The way back for a WebView too old for the message listener. The payload rides in
            // the fragment, which never leaves the phone.
            "magicbill" -> {
                if (url.host == "signed-in") url.fragment?.let { onPayload(android.net.Uri.decode(it)) }
                true
            }
            // UPI, Google Pay, a bank's own app: the payment leaves the app and comes back.
            else -> { openOutside(context, view, url.toString()); true }
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { onFailed(false) }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onFailed(true)
    }
}

/** Progress, and the second window a payment page opens. */
private class SiteChrome(
    private val context: Context,
    private val canvas: Int,
    private val client: WebViewClient,
    private val onProgress: (Int) -> Unit,
    private val onPopup: (WebView?) -> Unit,
    /** False on the popup itself: one window deep is every payment there is. */
    private val popupsAllowed: Boolean,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (!popupsAllowed) return false
        val child = webView(context, canvas).apply {
            webViewClient = client
            webChromeClient = SiteChrome(context, canvas, client, onProgress, onPopup, popupsAllowed = false)
        }
        onPopup(child)
        (resultMsg.obj as WebView.WebViewTransport).webView = child
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) {
        // Only the popup closes itself; the sign-up window is the screen's, not the page's.
        if (!popupsAllowed) onPopup(null)
    }
}

/**
 * A link this window cannot open, handed to whatever the phone has for it. A page must not be
 * able to name a component of this app, so the intent is stripped to what any browser would
 * open before it is sent.
 */
private fun openOutside(context: Context, view: WebView?, url: String): Boolean {
    val intent = try {
        if (url.startsWith("intent:")) Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        else Intent(Intent.ACTION_VIEW, url.toUri())
    } catch (e: URISyntaxException) {
        return false
    }
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        val fallback = intent.getStringExtra("browser_fallback_url")
        if (fallback != null && view != null) { view.loadUrl(fallback); true } else false
    }
}
