package com.magicbill.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.Restaurant
import com.magicbill.app.cloud.Sync
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.Credential
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.ui.theme.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Start-up is done: the boxes are read, the screens may draw. The splash waits on this. */
object Boot {
    @Volatile var ready = false
}

/** Which tabs this phone gets: what the cloud lets its person open, and whether it is paired. */
enum class Tab(val label: String) {
    Home("Dashboard"), Tables("Orders"), Reports("Reports"), Account("Account"), Bills("Bills"), Khata("Khata"), Queue("Queue"), More("More")
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val account: Account,
    private val counter: Counter,
    private val sync: Sync,
    private val theme: ThemeController,
    private val db: MbDatabase,
) : ViewModel() {
    val themeMode: StateFlow<String> = theme.mode
    val textScale: StateFlow<Float> = theme.textScale
    val restaurant: StateFlow<Restaurant?> = account.current
    val credential: StateFlow<Credential?> = counter.credential
    val signedIn: StateFlow<Boolean> = account.session.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Unread notices, for the badge. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val unread: StateFlow<Int> = account.current.flatMapLatest { r ->
        if (r == null) flowOf(0)
        else combine(db.notices().forShop(r.id), db.notices().reads()) { notices, reads ->
            val seen = reads.map { it.id }.toSet()
            notices.count { it.id !in seen }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val tabs: StateFlow<List<Tab>> = combine(account.session, account.current, counter.credential) { session, r, cred ->
        tabsFor(session != null, r, cred != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf(Tab.More))

    /** The same answer, now — for the moment right after a sign-in, before the flow has caught up. */
    fun tabsNow(): List<Tab> = tabsFor(account.session.value != null, account.current.value, counter.credential.value != null)

    /** Tabs that did not fit on the bar live in More. */
    val overflow: StateFlow<List<Tab>> = combine(account.session, account.current, counter.credential, tabs) { session, r, cred, shown ->
        allTabsFor(session != null, r, cred != null).filter { it !in shown }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        /**
         * Every tab this phone could show, in the owner's chosen order: Dashboard, Reports,
         * Orders, Account — the rest live in More. Bills is not a tab: it lives inside Reports.
         */
        fun allTabsFor(cloud: Boolean, r: Restaurant?, paired: Boolean): List<Tab> {
            val perms = r?.permissions ?: emptySet()
            return buildList {
                if (cloud && "phone.reports" in perms) { add(Tab.Home); add(Tab.Reports) }
                if (paired) add(Tab.Tables)
                if (cloud) add(Tab.Account)
                if (cloud && "phone.khata" in perms) add(Tab.Khata)
                if (paired) add(Tab.Queue)
            }
        }

        /** The bar: at most four, then More. */
        fun tabsFor(cloud: Boolean, r: Restaurant?, paired: Boolean): List<Tab> = allTabsFor(cloud, r, paired).take(4) + Tab.More
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            account.load()
            counter.load()
            sync.load()
            Boot.ready = true
            if (account.session.value != null) {
                account.refresh()
                sync.pullIfStale()
            }
            if (counter.isPaired) counter.refreshMe()
        }
    }

    val hasAnything: StateFlow<Boolean> = combine(signedIn, credential) { s, c -> s || c != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
