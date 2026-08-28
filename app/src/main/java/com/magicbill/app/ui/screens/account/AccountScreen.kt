package com.magicbill.app.ui.screens.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.BuildConfig
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.CloudSession
import com.magicbill.app.cloud.Restaurant
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.ui.kit.DangerButton
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(private val account: Account, val sync: Sync, val clock: Clock) : ViewModel() {
    val restaurant: StateFlow<Restaurant?> = account.current
    val session: StateFlow<CloudSession?> = account.session
    val refreshedAt: StateFlow<Long> = account.lastRefreshedMs
    fun refresh() { viewModelScope.launch { account.refresh() } }
    fun signOut(done: () -> Unit) { viewModelScope.launch { account.signOut(); done() } }
}

@Composable
fun AccountScreen(back: () -> Unit, signedOut: () -> Unit, vm: AccountViewModel = hiltViewModel()) {
    val r by vm.restaurant.collectAsStateWithLifecycle()
    val s by vm.session.collectAsStateWithLifecycle()
    val refreshed by vm.refreshedAt.collectAsStateWithLifecycle()
    val sync by vm.sync.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val reporter = LocalReporter.current
    var keyShown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val lic = r?.licence

    Page("Account", r?.name, back = back) {
        Section("For staff phones", first = true)
        Panel {
            Text("Shop code", style = Mb.type.label, color = Mb.colors.inkMuted)
            Text(r?.shortCode ?: "—", style = Mb.type.code, color = Mb.colors.ink)
            VGap(Gap.field)
            Text("A staff member signs in with this code, their staff code and their PIN.", style = Mb.type.caption, color = Mb.colors.inkMuted)
            VGap(Gap.field)
            SecondaryButton("Copy the code", { r?.shortCode?.let { clipboard.setText(AnnotatedString(it)); reporter.say("Copied.") } }, Modifier.fillMaxWidth())
        }
        Section("Licence")
        if (lic == null) Notice(Tone.Warn, "This shop has no licence yet. Set one up at magicbill.in.")
        else Panel {
            KeyValue("Plan", lic.planName.ifBlank { lic.plan })
            KeyValue("Status", lic.status.replaceFirstChar { it.uppercase() }, valueColor = when (lic.status) { "active", "trial" -> Mb.colors.ok; "suspended", "revoked" -> Mb.colors.danger; else -> Mb.colors.warn })
            lic.trialEndsOn?.let { KeyValue("Trial ends", it) }
            lic.renewsOn?.let { KeyValue("Renews on", it) }
            KeyValue("Counter", if (lic.bound) (lic.boundDevice?.name ?: "Activated") else "Not activated yet")
            lic.boundDevice?.lastSeenAt?.let { Ist.parseTs(it) }?.let { KeyValue("Counter last seen", Ist.ago(it, vm.clock.now())) }
            lic.boundDevice?.appVersion?.takeIf { it.isNotBlank() }?.let { KeyValue("Counter version", it) }
            if (lic.key != null) {
                KeyValue("Licence key", if (keyShown) lic.key else "MB-••••-••••-••••")
                QuietButton(if (keyShown) "Hide the key" else "Show the key", { keyShown = !keyShown })
            }
        }
        Section("The cloud copy")
        Panel {
            KeyValue("Bills, totals, khata, staff", if (sync.lastMs > 0) "Updated " + Ist.ago(sync.lastMs, vm.clock.now()) else "Not brought down yet")
            KeyValue("Shop details", if (refreshed > 0) "Checked " + Ist.ago(refreshed, vm.clock.now()) else "—")
            if (sync.sentence != null) { VGap(Gap.field); Notice(Tone.Warn, sync.sentence!!) }
        }
        Section("Signed in as")
        Panel {
            KeyValue("Email", s?.email ?: s?.staff?.staffName ?: "—")
            KeyValue("Role", r?.role?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "—")
            KeyValue("App", BuildConfig.VERSION_NAME)
        }
        VGap(Gap.group)
        DangerButton("Sign out of this phone", { leaving = true }, Modifier.fillMaxWidth())
    }

    if (leaving) {
        Sheet("Sign out?", onDismiss = { leaving = false }) {
            Text("The shop's copy on this phone is removed. Signing in again brings it back in a minute.", style = Mb.type.body, color = Mb.colors.inkMuted)
            VGap(Gap.group)
            DangerButton("Sign out", { leaving = false; vm.signOut(signedOut) }, Modifier.fillMaxWidth())
            VGap(Gap.field)
            SecondaryButton("Stay", { leaving = false }, Modifier.fillMaxWidth())
        }
    }
}
