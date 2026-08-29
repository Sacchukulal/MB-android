package com.magicbill.app.ui.screens.pair

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.core.Answer
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.Discovery
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.PairCode
import com.magicbill.app.ui.kit.Busy
import com.magicbill.app.ui.kit.CodeField
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Radius
import com.magicbill.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pairing, the whole of it from the phone's side: scan the counter's code (or type it), then
 * wait while somebody at the counter says whose phone this is and presses Allow. The phone
 * types no name, no code, no PIN. Once the counter lets it in, the counter also hands over the
 * person's cloud login, so Home and Reports work without a second sign-in.
 */
@HiltViewModel
class PairViewModel @Inject constructor(
    private val counter: Counter,
    private val floor: Floor,
    private val account: Account,
    private val discovery: Discovery,
) : ViewModel() {
    sealed interface Step {
        /** The camera and the code box. */
        data object Scan : Step
        data class Busy(val says: String) : Step
        data object Done : Step
    }

    private val stepFlow = MutableStateFlow<Step>(Step.Scan)
    val step: StateFlow<Step> get() = stepFlow
    private val sentenceFlow = MutableStateFlow<String?>(null)
    /** The last refusal, under the scanner. */
    val sentence: StateFlow<String?> get() = sentenceFlow

    /** From the QR. */
    fun scanned(text: String) {
        if (stepFlow.value !is Step.Scan) return
        val code = PairCode.parse(text) ?: run { sentenceFlow.value = "That is not a Magic Bill pairing code."; return }
        present(code)
    }

    /** Typed: find the counter on this WiFi, then present the code. */
    fun typed(token: String) {
        if (stepFlow.value !is Step.Scan) return
        stepFlow.value = Step.Busy("Looking for the counter on this WiFi…")
        viewModelScope.launch {
            val found = discovery.findAny()
            if (found == null || found.fingerprint == null) {
                back("Could not find the counter on this WiFi. Scan the code on its screen instead.")
                return@launch
            }
            present(PairCode(found.host, found.port, found.fingerprint, token.trim()))
        }
    }

    private fun present(code: PairCode) {
        sentenceFlow.value = null
        stepFlow.value = Step.Busy("Checking the counter…")
        viewModelScope.launch {
            val presented = when (val a = counter.present(code, account.phoneName(), account.installId())) {
                is Answer.Ok -> a.value
                else -> { back(a.sentenceOrNull); return@launch }
            }
            val shop = presented.shopName.ifBlank { "the counter" }
            val paired = counter.waitForAllow(presented) {
                stepFlow.value = Step.Busy("Waiting for somebody at $shop to say whose phone this is and press Allow…")
            }
            when (paired) {
                is Answer.Ok -> {
                    // The person's cloud login, from the counter. A miss here is not a failed
                    // pairing: the phone is on the floor, and asks again on its next start.
                    stepFlow.value = Step.Busy("Signing in…")
                    // The counter has just written the credential; a first miss is tried once more.
                    android.util.Log.i("MagicBill", "paired; asking for the cloud login")
                    if (account.signInThroughCounter(counter) !is Answer.Ok) {
                        kotlinx.coroutines.delay(1_500)
                        account.signInThroughCounter(counter)
                    }
                    floor.refreshCatalogue(force = true)
                    stepFlow.value = Step.Done
                }
                else -> back(paired.sentenceOrNull)
            }
        }
    }

    private fun back(says: String?) {
        sentenceFlow.value = says
        stepFlow.value = Step.Scan
    }
}

/** "8GFCVC" → "8GF-CVC": the dash the counter shows, put in for the person typing. */
internal fun dashed(raw: String): String {
    val clean = raw.filter { it.isLetterOrDigit() }.uppercase().take(6)
    return if (clean.length > 3) clean.substring(0, 3) + "-" + clean.substring(3) else clean
}

@Composable
fun PairScreen(back: () -> Unit, done: () -> Unit, vm: PairViewModel = hiltViewModel()) {
    val step by vm.step.collectAsStateWithLifecycle()
    val sentence by vm.sentence.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var token by rememberSaveable { mutableStateOf("") }
    var typing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(step) { if (step is PairViewModel.Step.Done) done() }

    Page("Scan the code", "It is on the counter: Settings › Phones", back = back) {
        Column(Modifier.imePadding()) {
            VGap(Gap.field)
            when (val s = step) {
                is PairViewModel.Step.Busy -> {
                    Busy()
                    Text(s.says, style = Mb.type.body, color = Mb.colors.inkMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                else -> {
                    if (granted && !typing) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.lg)).background(Mb.colors.raised)) {
                            QrScanner(onCode = vm::scanned)
                        }
                        VGap(Gap.field)
                        Text("Hold the phone up to the code on the counter's screen. Then somebody at the counter picks your name and presses Allow.", style = Mb.type.caption, color = Mb.colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        VGap(Gap.group)
                        QuietButton("Type the code instead", { typing = true }, Modifier.fillMaxWidth())
                    } else {
                        if (!granted) {
                            Notice(Tone.Info, "Without the camera, type the code instead.", action = { QuietButton("Allow camera", { ask.launch(Manifest.permission.CAMERA) }) })
                            VGap(Gap.field)
                        }
                        CodeField(token, { token = dashed(it) }, "The code under the QR", placeholder = "8GF-CVC", onDone = { if (token.length >= 7) vm.typed(token) })
                        VGap(Gap.field)
                        PrimaryButton("Connect", { vm.typed(token) }, Modifier.fillMaxWidth(), enabled = token.length >= 7)
                        if (granted) { VGap(Gap.field); QuietButton("Scan it instead", { typing = false }, Modifier.fillMaxWidth()) }
                    }
                }
            }
            if (sentence != null) { VGap(Gap.field); Notice(Tone.Danger, sentence!!) }
        }
    }
}

/** The Orders tab on a phone that is not on a counter yet: one door, the scanner. */
@Composable
fun ConnectScreen(onPair: () -> Unit) {
    Page("Orders") {
        Column(Modifier.fillMaxWidth().padding(top = Space.s7), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = Mb.colors.accent, modifier = Modifier.size(64.dp))
            VGap(Gap.group)
            Text("Take orders at the tables", style = Mb.type.section, color = Mb.colors.ink, textAlign = TextAlign.Center)
            VGap(Gap.field)
            Text("Scan the code on the counter's screen once. Somebody at the counter picks your name and presses Allow — that is all.", style = Mb.type.body, color = Mb.colors.inkMuted, textAlign = TextAlign.Center)
            VGap(Gap.group)
            PrimaryButton("Scan the counter's code", onPair, Modifier.fillMaxWidth(), icon = Icons.Outlined.QrCodeScanner)
        }
    }
}

/** Home or Reports on a phone with no cloud login — a shared tablet, or a counter that was offline. */
@Composable
fun NeedsCloudScreen(onOwner: () -> Unit, onPair: () -> Unit) {
    Page("Magic Bill") {
        Column(Modifier.fillMaxWidth().padding(top = Space.s7), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This phone is not signed in to the shop", style = Mb.type.section, color = Mb.colors.ink, textAlign = TextAlign.Center)
            VGap(Gap.field)
            Text("Reports and bills come from the shop's account. A staff phone gets it when the counter allows it under a name; the owner signs in with email and password.", style = Mb.type.body, color = Mb.colors.inkMuted, textAlign = TextAlign.Center)
            VGap(Gap.group)
            PrimaryButton("I own the shop", onOwner, Modifier.fillMaxWidth())
            VGap(Gap.field)
            SecondaryButton("Scan the counter's code again", onPair, Modifier.fillMaxWidth())
        }
    }
}

