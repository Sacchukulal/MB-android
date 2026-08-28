package com.magicbill.app.ui.screens.devices

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.Device
import com.magicbill.app.cloud.People
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Busy
import com.magicbill.app.ui.kit.DangerButton
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(private val account: Account, private val people: People, val clock: Clock) : ViewModel() {
    data class State(val busy: Boolean = true, val devices: List<Device> = emptyList(), val sentence: String? = null)

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> get() = stateFlow

    fun load() {
        val r = account.current.value ?: return
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(busy = true)
            stateFlow.value = when (val a = people.devices(r.id)) {
                is Answer.Ok -> State(false, a.value)
                else -> State(false, stateFlow.value.devices, a.sentenceOrNull)
            }
        }
    }

    fun revoke(id: String, onDone: (String) -> Unit) {
        val r = account.current.value ?: return
        viewModelScope.launch {
            when (val a = people.revokeDevice(r.id, id)) {
                is Answer.Ok -> { onDone("Removed. It is refused from now on."); load(); account.refresh() }
                else -> onDone(a.sentenceOrNull ?: "")
            }
        }
    }
}

@Composable
fun DevicesScreen(back: () -> Unit, vm: DevicesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reporter = LocalReporter.current
    var removing by remember { mutableStateOf<Device?>(null) }
    LaunchedEffect(Unit) { vm.load() }
    val live = state.devices.filter { it.revokedAt == null }
    val counters = live.filter { it.kind == "counter" }
    val phones = live.filter { it.kind != "counter" }

    Page("Phones and the counter", "Who is signed in to this shop", back = back) {
        if (state.sentence != null) { Notice(Tone.Warn, state.sentence!!); VGap(Gap.field) }
        if (state.busy && state.devices.isEmpty()) Busy()
        Section("The counter", first = true)
        if (counters.isEmpty()) Text("No counter is activated on this licence.", style = Mb.type.body, color = Mb.colors.inkMuted)
        counters.forEach { d -> DeviceRow(d, vm.clock.now()) { removing = d } }
        Section("Staff phones")
        if (phones.isEmpty()) Text("No staff phone has signed in yet.", style = Mb.type.body, color = Mb.colors.inkMuted)
        phones.forEach { d -> DeviceRow(d, vm.clock.now()) { removing = d } }
        if (!state.busy && live.isEmpty() && state.sentence == null) { VGap(Gap.group); Empty("When a phone signs in with the shop code it appears here.") }
    }

    removing?.let { d ->
        Sheet(if (d.kind == "counter") "Unbind the counter?" else "Remove this phone?", onDismiss = { removing = null }) {
            Text(
                if (d.kind == "counter") "${d.name} is refused by the cloud from now on and the licence is free to activate on another computer. The counter keeps billing until its grace ends."
                else "${d.name} is signed out and refused from now on. The person can sign in again with the shop code if you allow it.",
                style = Mb.type.body, color = Mb.colors.inkMuted,
            )
            VGap(Gap.group)
            DangerButton(if (d.kind == "counter") "Unbind" else "Remove", { removing = null; vm.revoke(d.id) { reporter.say(it) } }, Modifier.fillMaxWidth())
            VGap(Gap.field)
            SecondaryButton("Keep it", { removing = null }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DeviceRow(d: Device, now: Long, onRemove: () -> Unit) {
    val seen = Ist.parseTs(d.lastSeenAt)?.let { "seen " + Ist.ago(it, now) } ?: "never seen"
    ListRow(
        d.name.ifBlank { if (d.kind == "counter") "Counter" else "Phone" },
        listOf(seen, d.appVersion.takeIf { it.isNotBlank() }?.let { "v$it" }).filterNotNull().joinToString(" · "),
        trailing = { Badge(if (d.kind == "counter") "Counter" else "Phone", if (d.kind == "counter") Tone.Info else Tone.Quiet) },
        onClick = onRemove,
    )
    RowLine()
}
