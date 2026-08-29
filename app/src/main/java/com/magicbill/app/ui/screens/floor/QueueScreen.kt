package com.magicbill.app.ui.screens.floor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.Answer
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.Outcome
import com.magicbill.app.counter.Stream
import com.magicbill.app.db.IntentRow
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What this phone asked the counter, and what the counter said. Nothing here is retried by hand except a held one, which is released as a new decision. */
@HiltViewModel
class QueueViewModel @Inject constructor(private val floor: Floor, val stream: Stream, val clock: Clock) : ViewModel() {
    data class View(val queued: List<IntentRow> = emptyList(), val held: List<IntentRow> = emptyList(), val recent: List<IntentRow> = emptyList())

    val view: StateFlow<View> = combine(floor.recent(60), floor.held) { recent, held ->
        View(recent.filter { it.state == "queued" }, held, recent.filter { it.state != "queued" && it.state != "held" })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    fun sendNow(say: (String) -> Unit) { viewModelScope.launch { when (val a = floor.flush()) { is Answer.Ok -> a.value?.let { say(it.says) }; else -> say(a.sentenceOrNull ?: "") } } }
    fun release(id: String, say: (String) -> Unit) { viewModelScope.launch { when (val a = floor.release(id)) { is Answer.Ok -> say(a.value.sentence.ifBlank { "Sent." }); else -> say(a.sentenceOrNull ?: "") } } }
}

@Composable
fun QueueScreen(vm: QueueViewModel = hiltViewModel()) {
    val view by vm.view.collectAsStateWithLifecycle()
    val stream by vm.stream.state.collectAsStateWithLifecycle()
    val reporter = LocalReporter.current
    val today = Ist.today(vm.clock.now())

    Page("Queue", "What this phone sent the counter", actions = { StreamBadge(stream) }) {
        if (view.queued.isNotEmpty()) {
            Notice(Tone.Warn, "${view.queued.size} waiting to reach the counter.", action = { QuietButton("Send now", { vm.sendNow(reporter::say) }) })
            VGap(Gap.field)
            view.queued.forEach { r -> QueueRow(r, today, { Badge("Waiting", Tone.Warn) }) }
        }
        if (view.held.isNotEmpty()) {
            Section("Held by the counter", first = view.queued.isEmpty())
            Text("Held more than twelve hours between typing and sending. Send again only if it still applies.", style = Mb.type.caption, color = Mb.colors.inkMuted)
            VGap(Gap.field)
            view.held.forEach { r ->
                QueueRow(r, today, { Badge("Held", Tone.Warn) })
                SecondaryButton("Send again", { vm.release(r.id, reporter::say) }, Modifier.fillMaxWidth())
                VGap(Gap.field)
            }
        }
        Section("Answered", first = view.queued.isEmpty() && view.held.isEmpty())
        if (view.recent.isEmpty()) Empty("Nothing sent yet.")
        view.recent.forEach { r ->
            val outcome = r.outcome?.let(Outcome::fromJson)
            QueueRow(r, today, { when (outcome) { is Outcome.Ok -> Badge("Done", Tone.Ok); is Outcome.Refused -> Badge("Refused", Tone.Danger); else -> Badge(r.state) } }, outcome?.sentence)
        }
    }
}

@Composable
private fun QueueRow(r: IntentRow, today: java.time.LocalDate, badge: @Composable () -> Unit, says: String? = null) {
    ListRow(
        r.label,
        listOfNotNull(r.tableLabel?.let { "Table $it" }, Ist.moment(r.createdMs, today), says?.takeIf { it.isNotBlank() }).joinToString(" · "),
        trailing = badge,
    )
}
