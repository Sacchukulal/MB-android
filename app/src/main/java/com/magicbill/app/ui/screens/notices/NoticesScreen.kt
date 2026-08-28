package com.magicbill.app.ui.screens.notices

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.db.NoticeReadRow
import com.magicbill.app.db.NoticeRow
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoticesViewModel @Inject constructor(private val account: Account, private val sync: Sync, private val db: MbDatabase, val clock: Clock) : ViewModel() {
    data class Item(val notice: NoticeRow, val read: Boolean)

    val items: StateFlow<List<Item>> = account.perShop(emptyList<Item>()) { r ->
        combine(db.notices().forShop(r), db.notices().reads()) { n, reads ->
            val seen = reads.map { it.id }.toSet()
            val now = clock.now()
            n.filter { it.endsAtMs == null || it.endsAtMs > now }.map { Item(it, it.id in seen) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opening the screen is reading them: a bell that stays lit after you looked is a nag. */
    fun opened() {
        viewModelScope.launch(Dispatchers.IO) {
            sync.pullNow(setOf("notices"))
            val unread = items.first().filter { !it.read }
            if (unread.isNotEmpty()) db.notices().markRead(unread.map { NoticeReadRow(it.notice.id, clock.now()) })
        }
    }
}

@Composable
fun NoticesScreen(back: () -> Unit, vm: NoticesViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.opened() }
    val today = Ist.today(vm.clock.now())
    Page("Notices", "From Magic Bill", back = back) {
        if (items.isEmpty()) { Empty("Nothing from us right now."); return@Page }
        items.forEach { (n, read) ->
            Panel {
                Text(n.title, style = Mb.type.section, color = Mb.colors.ink)
                VGap(Space.s1)
                Text(n.body, style = Mb.type.body, color = Mb.colors.ink)
                VGap(Gap.field)
                Text(Ist.moment(n.startsAtMs, today), style = Mb.type.caption, color = Mb.colors.inkMuted)
                if (!read) { VGap(Space.s1); Badge("New", Tone.Info) }
            }
            VGap(Gap.field)
        }
    }
}
