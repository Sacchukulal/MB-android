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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
import com.magicbill.app.counter.Person
import com.magicbill.app.ui.kit.Busy
import com.magicbill.app.ui.kit.Chip
import com.magicbill.app.ui.kit.CodeField
import com.magicbill.app.ui.kit.Field
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PinField
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.Section
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
 * Pairing, in the order a waiter lives it: scan the counter's code (or type it) → tap your
 * name → type your PIN → you are in. Nobody at the counter presses anything. A shared tablet
 * picks "Nobody's" and waits for Allow instead.
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
        /** The code was good: who are you? */
        data class Claim(val presented: Counter.Presented, val wrong: String? = null) : Step
        data object Done : Step
    }

    private val stepFlow = MutableStateFlow<Step>(Step.Scan)
    val step: StateFlow<Step> get() = stepFlow
    private val sentenceFlow = MutableStateFlow<String?>(null)
    /** The last refusal, under the scanner. */
    val sentence: StateFlow<String?> get() = sentenceFlow
    var phoneName: String = account.phoneName()
        private set

    fun setPhoneName(name: String) { phoneName = name; account.setPhoneName(name) }

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
        stepFlow.value = Step.Busy("Checking the counter…")
        viewModelScope.launch {
            when (val a = counter.present(code, phoneName)) {
                is Answer.Ok -> stepFlow.value = Step.Claim(a.value)
                else -> back(a.sentenceOrNull)
            }
        }
    }

    /** The person: their name and their PIN. */
    fun claim(person: Person, pin: String) {
        val now = stepFlow.value as? Step.Claim ?: return
        stepFlow.value = Step.Busy("Checking with ${now.presented.shopName.ifBlank { "the counter" }}…")
        viewModelScope.launch {
            when (val a = counter.claim(now.presented, person.id, pin)) {
                is Answer.Ok -> finished()
                is Answer.Refused -> {
                    // A wrong PIN with tries left stays on this step; the last one goes back to the scanner.
                    if (a.code == "403") stepFlow.value = now.copy(wrong = a.sentence) else back(a.sentence)
                }
                else -> back(a.sentenceOrNull)
            }
        }
    }

    /** A shared tablet: nobody's — somebody at the counter presses Allow. */
    fun shared() {
        val now = stepFlow.value as? Step.Claim ?: return
        stepFlow.value = Step.Busy("Checking with the counter…")
        viewModelScope.launch {
            val a = counter.waitForAllow(now.presented) {
                stepFlow.value = Step.Busy("Waiting for somebody at ${now.presented.shopName.ifBlank { "the counter" }} to press Allow…")
            }
            when (a) {
                is Answer.Ok -> finished()
                else -> back(a.sentenceOrNull)
            }
        }
    }

    private suspend fun finished() {
        floor.refreshCatalogue(force = true)
        stepFlow.value = Step.Done
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
    var name by rememberSaveable { mutableStateOf(vm.phoneName) }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(step) { if (step is PairViewModel.Step.Done) done() }

    when (val s = step) {
        is PairViewModel.Step.Claim -> ClaimScreen(s, back = { vm.scanned("") }, onPerson = vm::claim, onShared = vm::shared)
        else -> Page("Connect to the counter", "On the counter: Settings → Phones", back = back) {
            // The keyboard must never hide what is being typed: the code box sits at the top and
            // the page gives way to the keyboard.
            Column(Modifier.imePadding()) {
                VGap(Gap.field)
                Field(name, { name = it; vm.setPhoneName(it) }, "This phone's name", placeholder = "Ravi's phone", ime = ImeAction.Next)
                VGap(Gap.field)
                if (s is PairViewModel.Step.Busy) {
                    Busy()
                    Text(s.says, style = Mb.type.body, color = Mb.colors.inkMuted, modifier = Modifier.fillMaxWidth())
                } else {
                    CodeField(token, { token = dashed(it) }, "The code on the counter", placeholder = "8GF-CVC", onDone = { if (token.length >= 7) vm.typed(token) })
                    VGap(Gap.field)
                    PrimaryButton("Connect", { vm.typed(token) }, Modifier.fillMaxWidth(), enabled = token.length >= 7 && name.isNotBlank())
                    Section("Or scan it")
                    if (granted) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.lg)).background(Mb.colors.raised)) {
                            QrScanner(onCode = vm::scanned)
                        }
                    } else {
                        Notice(Tone.Info, "Without the camera, type the code instead.", action = { QuietButton("Allow camera", { ask.launch(Manifest.permission.CAMERA) }) })
                    }
                }
                if (sentence != null) { VGap(Gap.field); Notice(Tone.Danger, sentence!!) }
            }
        }
    }
}

/** "Who are you?" — the names off the counter's staff list, then the PIN. */
@Composable
private fun ClaimScreen(step: PairViewModel.Step.Claim, back: () -> Unit, onPerson: (Person, String) -> Unit, onShared: () -> Unit) {
    var chosen by remember { mutableStateOf<Person?>(null) }
    var pin by remember { mutableStateOf("") }
    Page("Who are you?", step.presented.shopName.ifBlank { "The counter" }, back = back) {
        Column(Modifier.imePadding()) {
            VGap(Gap.field)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s2), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s2)) {
                step.presented.asked.people.forEach { p -> Chip(p.name, chosen?.id == p.id, { chosen = p; pin = "" }) }
            }
            if (chosen != null) {
                Section("Your PIN")
                PinField(pin, { pin = it }, onDone = { if (pin.length == 4) onPerson(chosen!!, pin) })
                if (step.wrong != null) { VGap(Gap.field); Notice(Tone.Danger, step.wrong) }
                VGap(Gap.group)
                PrimaryButton("Connect as ${chosen!!.name}", { onPerson(chosen!!, pin) }, Modifier.fillMaxWidth(), enabled = pin.length == 4)
            }
            Section("Or")
            QuietButton("A shared tablet — nobody's", onShared)
            Text("Somebody at the counter presses Allow for a shared tablet.", style = Mb.type.caption, color = Mb.colors.inkMuted)
        }
    }
}

@Suppress("unused")
private val keepFloor: Class<Floor> = Floor::class.java
