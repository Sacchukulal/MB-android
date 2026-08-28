package com.magicbill.app.ui.screens.pair

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.magicbill.app.ui.kit.Busy
import com.magicbill.app.ui.kit.Field
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Radius
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairViewModel @Inject constructor(
    private val counter: Counter,
    private val floor: Floor,
    private val account: Account,
    private val discovery: Discovery,
) : ViewModel() {
    data class State(val busy: Boolean = false, val step: String? = null, val sentence: String? = null, val done: Boolean = false)

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> get() = stateFlow
    var phoneName: String = account.phoneName()
        private set

    fun setPhoneName(name: String) { phoneName = name; account.setPhoneName(name) }

    /** From the QR. */
    fun scanned(text: String) {
        if (stateFlow.value.busy || stateFlow.value.done) return
        val code = PairCode.parse(text) ?: run { stateFlow.value = State(sentence = "That is not a Magic Bill pairing code."); return }
        pair(code)
    }

    /** Typed: find the counter on this WiFi, then present the code. */
    fun typed(token: String) {
        if (stateFlow.value.busy) return
        stateFlow.value = State(busy = true, step = "Looking for the counter on this WiFi…")
        viewModelScope.launch {
            val found = discovery.findAny()
            if (found == null || found.fingerprint == null) {
                stateFlow.value = State(sentence = "Could not find the counter on this WiFi. Scan the code on its screen instead.")
                return@launch
            }
            pair(PairCode(found.host, found.port, found.fingerprint, token.trim()))
        }
    }

    private fun pair(code: PairCode) {
        stateFlow.value = State(busy = true, step = "Checking the counter…")
        viewModelScope.launch {
            val a = counter.pair(code, phoneName) { step ->
                stateFlow.value = State(busy = true, step = when (step) {
                    is Counter.Step.Checking -> "Checking the counter…"
                    is Counter.Step.Waiting -> "Waiting for somebody at ${step.shopName.ifBlank { "the counter" }} to press Allow…"
                    is Counter.Step.Done -> "Connected."
                })
            }
            when (a) {
                is Answer.Ok -> { floor.refreshCatalogue(force = true); stateFlow.value = State(done = true) }
                else -> stateFlow.value = State(sentence = a.sentenceOrNull)
            }
        }
    }
}

@Composable
fun PairScreen(back: () -> Unit, done: () -> Unit, vm: PairViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var token by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(vm.phoneName) }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(state.done) { if (state.done) done() }

    Page("Connect to the counter", "On the counter: Settings → Network → Phones", back = back) {
        VGap(Gap.field)
        Field(name, { name = it; vm.setPhoneName(it) }, "This phone's name", placeholder = "Ravi's phone", ime = ImeAction.Done)
        VGap(Gap.group)
        if (state.busy) {
            Busy()
            Text(state.step ?: "", style = Mb.type.body, color = Mb.colors.inkMuted, modifier = Modifier.fillMaxWidth())
        } else {
            if (granted) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Radius.lg)).background(Mb.colors.raised)) {
                    QrScanner(onCode = vm::scanned)
                }
                VGap(Gap.field)
                Text("Point the camera at the code on the counter's screen.", style = Mb.type.caption, color = Mb.colors.inkMuted)
            } else {
                Notice(Tone.Info, "Without the camera, type the code instead.", action = { QuietButton("Allow camera", { ask.launch(Manifest.permission.CAMERA) }) })
            }
            Section("Or type the code")
            Field(token, { token = it.uppercase().take(9) }, "Code on the counter", placeholder = "8GF-CVC", capitalise = true, ime = ImeAction.Go, onDone = { if (token.length >= 6) vm.typed(token) })
            VGap(Gap.field)
            PrimaryButton("Connect", { vm.typed(token) }, Modifier.fillMaxWidth(), enabled = token.length >= 6 && name.isNotBlank())
        }
        if (state.sentence != null) { VGap(Gap.field); Notice(Tone.Danger, state.sentence!!) }
    }
}
