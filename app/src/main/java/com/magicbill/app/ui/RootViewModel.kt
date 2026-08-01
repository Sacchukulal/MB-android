package com.magicbill.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.data.AccountRepository
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.ThemeController
import com.magicbill.app.core.PermissionKey
import com.magicbill.app.core.has
import com.magicbill.app.data.UpdateManager
import com.magicbill.app.data.UpdateUiState
import com.magicbill.app.data.orders.OrdersRealtime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val auth: AuthRepository,
    private val theme: ThemeController,
    private val account: AccountRepository,
    val updates: UpdateManager,
    /**
     * PART C — the shells declare whether this session takes orders, which
     * is what decides whether the presence line is held for the whole
     * foreground session. It lives here because both shells need it and
     * neither owns the connection.
     */
    val ordersRealtime: OrdersRealtime,
) : ViewModel() {

    val session: StateFlow<MBSession> = auth.session
    val darkTheme: StateFlow<Boolean> = theme.dark
    val updateState: StateFlow<UpdateUiState> = updates.state

    /** Dot on the Account tab: an update exists but the sheet was dismissed. */
    val updateDotVisible: StateFlow<Boolean> = updates.state
        .map { it.available != null && it.sheetSuppressed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        auth.bootstrap()
        updates.checkOnLaunch()

        // PART C1 — who holds the presence line.
        //
        // This is deliberately driven from the SESSION and not from a
        // DisposableEffect in the shells. It was written that way first and
        // hardware caught it within a minute: when a shell composable moves,
        // Compose runs the NEW effect before disposing the OLD one, so the
        // sequence was set(true) → set(true) (ignored, already true) →
        // set(false) from the stale dispose. The phone ended up with ordering
        // access switched off and did not appear in the counter's room until
        // an Orders screen forced it on. The log said it plainly:
        // "presence line up" and then "presence line down" 220ms later.
        //
        // A session is not a composable. Neither is ordering access.
        viewModelScope.launch {
            session.map { s ->
                when (s) {
                    is MBSession.Owner -> true
                    is MBSession.Staff -> s.staff.permissions.has(PermissionKey.TakeOrders)
                    else -> false
                }
            }.distinctUntilChanged().collect { ordersRealtime.setOrderingAccess(it) }
        }
        // Register this phone on the owner's row whenever an owner session
        // lands on a restaurant (login or app open) — best effort.
        viewModelScope.launch {
            session
                .map { (it as? MBSession.Owner)?.active?.licenseKey }
                .distinctUntilChanged()
                .collect { license -> license?.let { account.registerDevice(it) } }
        }
    }

    fun setDarkTheme(dark: Boolean) = theme.setDark(dark)

    /** Manual check from Account. @return "update" | "up-to-date" | "error" */
    suspend fun checkForUpdates(): String {
        val result = updates.check()
        if (result == "update") updates.reopenSheet()
        return result
    }
}
