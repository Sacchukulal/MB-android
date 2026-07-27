package com.magicbill.app.ui.screens.orders

import androidx.lifecycle.ViewModel
import com.magicbill.app.core.PermissionMap
import com.magicbill.app.data.NetworkMonitor
import com.magicbill.app.data.orders.OrdersRealtime
import com.magicbill.app.data.orders.OrdersRepository
import com.magicbill.app.data.orders.OrdersUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {
    val state: StateFlow<OrdersUiState> = repo.state
    val permissions: StateFlow<PermissionMap> = repo.permissions
    val online: StateFlow<Boolean> = network.online

    fun load(force: Boolean = false) = repo.ensureLoaded(force)

    fun connect() = realtime.acquire()

    fun disconnect() = realtime.release()
}
