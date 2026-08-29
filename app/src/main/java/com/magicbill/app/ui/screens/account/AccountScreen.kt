package com.magicbill.app.ui.screens.account

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
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
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.Credential
import com.magicbill.app.counter.Me
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.kit.DangerButton
import com.magicbill.app.ui.kit.IconAction
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
import com.magicbill.app.ui.kit.PrimaryButton
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
class AccountViewModel @Inject constructor(private val account: Account, private val counter: Counter, private val floor: com.magicbill.app.counter.Floor, val sync: Sync, val clock: Clock) : ViewModel() {
    val restaurant: StateFlow<Restaurant?> = account.current
    val session: StateFlow<CloudSession?> = account.session
    val refreshedAt: StateFlow<Long> = account.lastRefreshedMs
    val credential: StateFlow<Credential?> = counter.credential
    val me: StateFlow<Me?> = counter.me
    val checking = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Ask the cloud for the shop again, and the copy behind it. */
    fun checkAgain(say: (String) -> Unit) {
        viewModelScope.launch {
            checking.value = true
            when (val a = account.refresh()) {
                is com.magicbill.app.core.Answer.Ok -> {
                    sync.pullIfStale(minAgeMs = 0)
                    say(if (a.value.isEmpty()) "Signed in, but this account has no shop." else "Checked.")
                }
                else -> say(a.sentenceOrNull ?: "Magic Bill could not be reached.")
            }
            checking.value = false
        }
    }

    /** A paired phone with no cloud login yet: ask the counter again. */
    fun signInThroughCounter(say: (String) -> Unit) {
        viewModelScope.launch {
            checking.value = true
            say(when (val a = account.signInThroughCounter(counter)) { is com.magicbill.app.core.Answer.Ok -> "Signed in."; else -> a.sentenceOrNull ?: "The counter could not sign this phone in." })
            checking.value = false
        }
    }

    /** Signing out is the whole phone: the cloud login AND the seat at the counter. */
    fun signOut(done: () -> Unit) { viewModelScope.launch { floor.forgetAll(); account.signOut(); done() } }
}

/**
 * ONE account screen, for everybody: the sun/moon in the header, who this phone is, the shop,
 * the licence (owners see the key), the cloud copy, this phone, sign out. A phone that is only
 * on a counter (no cloud login) sees its counter identity and the two doors to a login.
 */
@Composable
fun AccountScreen(root: RootViewModel, onOwner: () -> Unit, onPair: () -> Unit, onMe: () -> Unit, signedOut: () -> Unit, vm: AccountViewModel = hiltViewModel()) {
    val r by vm.restaurant.collectAsStateWithLifecycle()
    val s by vm.session.collectAsStateWithLifecycle()
    val refreshed by vm.refreshedAt.collectAsStateWithLifecycle()
    val sync by vm.sync.state.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val cred by vm.credential.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val dark by root.dark.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val reporter = LocalReporter.current
    var keyShown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val lic = r?.licence

    Page(
        "Account", r?.name ?: cred?.shopName,
        actions = {
            // The theme toggle: one quiet icon — moon in light mode, sun in dark.
            IconAction(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, if (dark) "Switch to light theme" else "Switch to dark theme", { root.toggleTheme() }, tint = Mb.colors.inkMuted)
        },
    ) {
        Section("Signed in as", first = true)
        Panel {
            val who = s?.email ?: s?.staff?.staffName ?: me?.staffName ?: me?.name
            KeyValue("Name", who ?: "—")
            KeyValue("Role", r?.role?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: if (cred != null) "At the counter" else "—")
            if (cred != null) KeyValue("Counter", cred!!.shopName.ifBlank { "${cred!!.host}:${cred!!.port}" })
        }
        if (s == null) {
            // On a counter, but no cloud login: the counter was offline when it let this phone
            // in, or the phone is a shared tablet.
            VGap(Gap.field)
            Notice(Tone.Info, if (cred != null) "Reports and bills need the shop's account. Ask the counter again, or sign in as the owner." else "Sign in as the owner, or scan the counter's code.")
            VGap(Gap.field)
            if (cred != null) SecondaryButton(if (checking) "Asking…" else "Ask the counter again", { if (!checking) vm.signInThroughCounter(reporter::say) }, Modifier.fillMaxWidth())
            VGap(Gap.field)
            PrimaryButton("I own the shop", onOwner, Modifier.fillMaxWidth())
            if (cred == null) { VGap(Gap.field); SecondaryButton("Scan the counter's code", onPair, Modifier.fillMaxWidth()) }
        }
        if (s != null) {
            // The truth, in order: the shop was never read; the account has no shop; the shop has
            // no licence. Only the last one is about a licence.
            if (r == null) {
                Section("Your shop")
                Notice(
                    if (refreshed > 0) Tone.Warn else Tone.Danger,
                    if (refreshed > 0) "This account has no shop yet. Set one up at magicbill.in, then check again."
                    else "Your shop has not been read from Magic Bill yet. Check again when this phone is online.",
                )
                VGap(Gap.field)
                SecondaryButton(if (checking) "Checking…" else "Check again", { if (!checking) vm.checkAgain { reporter.say(it) } }, Modifier.fillMaxWidth())
            } else {
                Section("The shop")
                Panel {
                    KeyValue("Name", r!!.name)
                    KeyValue("Shop code", r!!.shortCode)
                    QuietButton("Copy the shop code", { clipboard.setText(AnnotatedString(r!!.shortCode)); reporter.say("Copied.") })
                }
            }
            Section("Licence")
            if (r == null) KeyValue("Licence", "—")
            else if (lic == null) Notice(Tone.Warn, "This shop has no licence yet. Set one up at magicbill.in.")
            else Panel {
                KeyValue("Plan", lic.planName.ifBlank { lic.plan })
                KeyValue("Status", lic.status.replaceFirstChar { it.uppercase() }, valueColor = when (lic.status) { "active", "trial" -> Mb.colors.ok; "suspended", "revoked" -> Mb.colors.danger; else -> Mb.colors.warn })
                lic.trialEndsOn?.let { KeyValue("Trial ends", it) }
                lic.renewsOn?.let { KeyValue("Renews on", it) }
                KeyValue("Counter", if (lic.bound) (lic.boundDevice?.name ?: "Activated") else "Not activated yet")
                lic.boundDevice?.lastSeenAt?.let { Ist.parseTs(it) }?.let { KeyValue("Counter last spoke to the cloud", Ist.ago(it, vm.clock.now())) }
                lic.boundDevice?.appVersion?.takeIf { it.isNotBlank() }?.let { KeyValue("Counter version", it) }
                if (lic.key != null) {
                    KeyValue("Licence key", if (keyShown) lic.key else "MB-••••-••••-••••")
                    QuietButton(if (keyShown) "Hide the key" else "Show the key", { keyShown = !keyShown })
                }
            }
            Section("The cloud copy")
            Panel {
                KeyValue("Bills, totals, khata, staff", if (sync.lastMs > 0) "Updated " + Ist.ago(sync.lastMs, vm.clock.now()) else "Not brought down yet")
                KeyValue("Shop details", if (refreshed > 0) "Checked " + Ist.ago(refreshed, vm.clock.now()) else "Not read yet")
                VGap(Gap.field)
                SecondaryButton(if (checking) "Checking…" else "Check again", { if (!checking) vm.checkAgain { reporter.say(it) } }, Modifier.fillMaxWidth())
                if (sync.sentence != null) { VGap(Gap.field); Notice(Tone.Warn, sync.sentence!!) }
            }
        }
        Section("This phone")
        Panel {
            KeyValue("Phone", (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim())
            KeyValue("Android", Build.VERSION.RELEASE)
            KeyValue("Magic Bill", BuildConfig.VERSION_NAME)
            if (cred != null) { VGap(Gap.field); SecondaryButton("Me at the counter", onMe, Modifier.fillMaxWidth()) }
        }
        if (s != null) {
            VGap(Gap.group)
            DangerButton("Sign out of this phone", { leaving = true }, Modifier.fillMaxWidth())
        }
    }

    if (leaving) {
        Sheet("Sign out?", onDismiss = { leaving = false }) {
            Text("The shop's copy on this phone is removed, and the counter forgets this phone. Scanning the code again brings both back.", style = Mb.type.body, color = Mb.colors.inkMuted)
            VGap(Gap.group)
            DangerButton("Sign out", { leaving = false; vm.signOut(signedOut) }, Modifier.fillMaxWidth())
            VGap(Gap.field)
            SecondaryButton("Stay", { leaving = false }, Modifier.fillMaxWidth())
        }
    }
}
