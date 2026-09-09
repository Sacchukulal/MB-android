package com.magicbill.app.ui.screens.signin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap

/**
 * A signed-in owner whose account owns no shop yet. The trial and the plans live on the
 * website, so the one door here opens it in the phone's browser; when the app is in front
 * again the cloud is asked once more without a press. Everything the shop needs comes from
 * `mb_my_restaurants`, so there is nothing to type back into the phone.
 */
@Composable
fun NoShopYet(checking: Boolean, onCheckAgain: () -> Unit, onUseAnother: (() -> Unit)? = null) {
    val context = LocalContext.current
    val reporter = LocalReporter.current
    var wentToSite by remember { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && wentToSite) { wentToSite = false; onCheckAgain() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Notice(Tone.Info, "Your account is ready. Your shop needs a plan: choose one at magicbill.in, signed in there with the same email and password. This phone opens the shop when you come back.")
    VGap(Gap.field)
    PrimaryButton("Choose a plan at magicbill.in", {
        if (openInBrowser(context, CloudLink.PLAN_PAGE)) wentToSite = true else reporter.say("This phone has no browser to open magicbill.in with.")
    }, Modifier.fillMaxWidth(), icon = Icons.Outlined.OpenInBrowser)
    VGap(Gap.field)
    SecondaryButton(if (checking) "Checking…" else "Check again", onCheckAgain, Modifier.fillMaxWidth(), enabled = !checking)
    if (onUseAnother != null) {
        VGap(Gap.field)
        QuietButton("Use another account", onUseAnother)
    }
}

/** A web page, in whatever the phone has for it. False when it has nothing. */
fun openInBrowser(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}
