package com.magicbill.app.core

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

const val SITE_URL = "https://magicbill.in"

/**
 * Opens a URL in a Chrome Custom Tab (slides over the app, real browser —
 * Razorpay checkout, UPI intents and 3-D Secure all work, unlike a WebView).
 */
fun openCustomTab(context: Context, url: String, toolbarColor: Int) {
    val scheme = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(toolbarColor)
        .build()
    CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(scheme)
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .build()
        .launchUrl(context, Uri.parse(url))
}
