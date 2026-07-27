package com.magicbill.app.ui.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.PermissionMap
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.NetworkMonitor
import com.magicbill.app.data.orders.OrdersRealtime
import com.magicbill.app.data.orders.OrdersRepository
import com.magicbill.app.data.orders.OrdersUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Thin ViewModel over the singleton OrdersRepository (which owns the state so
 * every orders screen sees the same live truth) and OrdersRealtime (which the
 * screens acquire/release so the socket only lives while orders UI is
 * visible).
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val repo: OrdersRepository,
    private val realtime: OrdersRealtime,
    network: NetworkMonitor,
    auth: AuthRepository,
) : ViewModel() {
    val state: StateFlow<OrdersUiState> = repo.state
    val permissions: StateFlow<PermissionMap> = repo.permissions
    val online: StateFlow<Boolean> = network.online

    /** Marks the tables this waiter opened. Empty for an owner session. */
    val waiterName: StateFlow<String> = auth.session
        .map { (it as? MBSession.Staff)?.staff?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun load(force: Boolean = false) = repo.ensureLoaded(force)

    fun connect() = realtime.acquire()

    fun disconnect() = realtime.release()
}
