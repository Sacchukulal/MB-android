package com.magicbill.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.Restaurant
import com.magicbill.app.cloud.Sync
import com.magicbill.app.counter.Counter
import com.magicbill.app.counter.Credential
import com.magicbill.app.counter.Floor
import com.magicbill.app.counter.Stream
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.ui.theme.ThemeController
import com.magicbill.app.update.Updater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The tabs, in this order. Home and Reports are there for a person who may see reports. */
enum class Tab(val label: String) {
    Home("Home"), Reports("Reports"), Orders("Orders"), Account("Account"), More("More")
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val account: Account,
    private val counter: Counter,
    private val sync: Sync,
    private val theme: ThemeController,
    private val db: MbDatabase,
    private val floor: Floor,
    private val stream: Stream,
    val updater: Updater,
) : ViewModel() {
    /** The shelf's answer, and whether its sheet is up. */
    val update: StateFlow<Updater.State> = updater.state
    val updateOpen: StateFlow<Boolean> = updater.open
    /** A newer build waits behind "Not now": the dot on More. */
    val updateWaiting: StateFlow<Boolean> = updater.state.map { it is Updater.State.Available || it is Updater.State.Ready }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** What the counter said, once each — the shell shows them. */
    val counterSays: kotlinx.coroutines.flow.SharedFlow<String> = floor.sentences
    val dark: StateFlow<Boolean> = theme.dark
    val restaurant: StateFlow<Restaurant?> = account.current
    val credential: StateFlow<Credential?> = counter.credential
    val signedIn: StateFlow<Boolean> = account.session.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleTheme() = theme.toggle()

    /** May this phone's person see reports? Owners always; staff by their role, the counter's code. */
    val mayReport: StateFlow<Boolean> = account.current.map { r -> r != null && (r.isOwner || "reports.view" in r.permissions) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The bar: Home and Reports only when they would show something. */
    val tabs: StateFlow<List<Tab>> = mayReport.map { may -> tabsFor(may) }.stateIn(viewModelScope, SharingStarted.Eagerly, tabsFor(false))

    /** The same answer, now — for the moment right after a sign-in, before the flow has caught up. */
    fun tabsNow(): List<Tab> = tabsFor(account.current.value?.let { it.isOwner || "reports.view" in it.permissions } ?: false)

    /** Unread notices, for the badge. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val unread: StateFlow<Int> = account.current.flatMapLatest { r ->
        if (r == null) flowOf(0)
        else combine(db.notices().forShop(r.id), db.notices().reads()) { notices, reads ->
            val seen = reads.map { it.id }.toSet()
            notices.count { it.id !in seen }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The boxes are read: the session and the counter credential are known, so the shell may
     * decide where to start. Before this a signed-in phone looks signed out — the shell
     * decides its first screen ONCE, so it must not look until this is true.
     */
    private val bootedFlow = MutableStateFlow(false)
    val booted: StateFlow<Boolean> get() = bootedFlow

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                account.load()
                counter.load()
                sync.load()
            } catch (e: Exception) {
                // A broken box must not hold the splash forever; sign-in says what is wrong.
                android.util.Log.w("MagicBill", "start-up read failed: $e")
            } finally {
                Boot.ready = true
                bootedFlow.value = true
            }
            if (account.session.value != null) {
                account.refresh()
                sync.pullIfStale()
            }
            if (counter.isPaired) {
                counter.refreshMe()
                stream.ensure()
                // A phone the counter let in but could not sign in to the cloud at the time
                // (the counter was offline): ask again, every start, until it lands.
                if (account.session.value == null) account.signInThroughCounter(counter)
            }
            // A phone that is somebody's looks at the shelf once per start. Never over the login flow.
            if (account.session.value != null || counter.isPaired) updater.checkQuietly()
        }
    }

    companion object {
        fun tabsFor(mayReport: Boolean): List<Tab> =
            if (mayReport) Tab.entries else listOf(Tab.Orders, Tab.Account, Tab.More)
    }

    /** Signed in to the cloud, or paired with a counter: something to show behind the tabs. */
    val hasAnything: StateFlow<Boolean> = combine(signedIn, credential) { s, c -> s || c != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
