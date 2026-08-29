package com.magicbill.app.ui.screens.floor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.Answer
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.Credential
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.Me
import com.magicbill.app.counter.Stream
import com.magicbill.app.ui.kit.DangerButton
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
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
class MeViewModel @Inject constructor(private val counter: Counter, private val floor: Floor, val stream: Stream) : ViewModel() {
    val credential: StateFlow<Credential?> = counter.credential
    val me: StateFlow<Me?> = counter.me
    val revoked: StateFlow<String?> = counter.revokedSays

    fun check(say: (String) -> Unit) {
        viewModelScope.launch {
            when (val a = counter.refreshMe()) {
                is Answer.Ok -> say("The counter knows this phone.")
                is Answer.Unreachable -> { val found = counter.rediscover(); say(if (found) "Found the counter again." else a.sentence) }
                else -> say(a.sentenceOrNull ?: "")
            }
        }
    }

    fun leave(done: () -> Unit) { viewModelScope.launch { floor.forgetAll(); done() } }
}

@Composable
fun MeScreen(back: () -> Unit, onPair: () -> Unit, left: () -> Unit, vm: MeViewModel = hiltViewModel()) {
    val cred by vm.credential.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val revoked by vm.revoked.collectAsStateWithLifecycle()
    val stream by vm.stream.state.collectAsStateWithLifecycle()
    val reporter = LocalReporter.current
    var leaving by remember { mutableStateOf(false) }

    Page("Me at the counter", cred?.shopName, back = back) {
        if (revoked != null) { Notice(Tone.Danger, revoked!!, action = { SecondaryButton("Connect again", onPair) }); VGap(Gap.field) }
        Section("This phone", first = true)
        Panel {
            KeyValue("Known as", me?.staffName ?: "Shared — nobody's")
            KeyValue("This phone", me?.name ?: "—")
            KeyValue("Counter", cred?.let { "${it.host}:${it.port}" } ?: "—")
            KeyValue("Connection", when (stream) { Stream.State.Live -> "Live"; Stream.State.Connecting -> "Connecting"; Stream.State.Lost -> "Reconnecting"; Stream.State.Off -> "Off the floor" })
        }
        Section("What I may do here")
        if (me == null || me!!.may.isEmpty()) Text("The counter has not said yet. Check the connection.", style = Mb.type.body, color = Mb.colors.inkMuted)
        else Panel { me!!.may.sorted().forEach { code -> KeyValue(code, "✓") } }
        VGap(Gap.group)
        SecondaryButton("Check the connection", { vm.check(reporter::say) }, Modifier.fillMaxWidth())
        VGap(Gap.field)
        DangerButton("Disconnect this phone", { leaving = true }, Modifier.fillMaxWidth())
    }

    if (leaving) {
        Sheet("Disconnect from ${cred?.shopName ?: "the counter"}?", onDismiss = { leaving = false }) {
            Text("The counter forgets this phone until somebody pairs it again with a new code.", style = Mb.type.body, color = Mb.colors.inkMuted)
            VGap(Gap.group)
            DangerButton("Disconnect", { leaving = false; vm.leave(left) }, Modifier.fillMaxWidth())
            VGap(Gap.field)
            SecondaryButton("Stay connected", { leaving = false }, Modifier.fillMaxWidth())
        }
    }
}
