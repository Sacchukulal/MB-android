package com.magicbill.app.ui.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.data.NetworkMonitor
import com.magicbill.app.data.orders.EventResolution
import com.magicbill.app.data.orders.LiveOrder
import com.magicbill.app.data.orders.OrdersRealtime
import com.magicbill.app.data.orders.OrdersRepository
import com.magicbill.app.data.orders.SubmitResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One live order. The order itself streams from the shared OrdersRepository
 * state (so counter-side changes appear within ~1s); when it leaves the open
 * set this VM resolves whether it was billed or cancelled and swaps to a
 * closed summary instead of erroring.
 */
data class OrderDetailUi(
    val order: LiveOrder? = null,
    /** "billed" | "cancelled" once the order left the open set. */
    val closedStatus: String? = null,
    val busy: Boolean = false,
)

sealed interface OrderActionEvent {
    data class Done(val message: String) : OrderActionEvent
    data class Error(val message: String) : OrderActionEvent
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val repo: OrdersRepository,
    private val realtime: OrdersRealtime,
    network: NetworkMonitor,
) : ViewModel() {

    val online: StateFlow<Boolean> = network.online
    val ordersState = repo.state

    private val _ui = MutableStateFlow(OrderDetailUi())
    val ui: StateFlow<OrderDetailUi> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<OrderActionEvent>()
    val events: SharedFlow<OrderActionEvent> = _events.asSharedFlow()

    private var boundUuid: String? = null
    private var lastKnown: LiveOrder? = null
    private var watchJob: Job? = null
    private var actionJob: Job? = null

    fun connect() = realtime.acquire()
    fun disconnect() = realtime.release()

    fun bind(clientUuid: String) {
        if (boundUuid == clientUuid) return
        boundUuid = clientUuid
        repo.ensureLoaded()
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            repo.state.collect { state ->
                val found = state.data?.orders?.firstOrNull { it.clientUuid == clientUuid }
                when {
                    found != null -> {
                        lastKnown = found
                        _ui.value = _ui.value.copy(order = found, closedStatus = null)
                    }
                    // Was on screen, now gone from the open set — find out why
                    // (billed at the counter vs cancelled) instead of erroring.
                    lastKnown != null && _ui.value.closedStatus == null && state.data != null -> {
                        val status = lastKnown?.serverId?.let { repo.fetchOrderStatus(it) }
                        _ui.value = _ui.value.copy(
                            order = lastKnown,
                            closedStatus = status ?: "billed",
                        )
                    }
                }
            }
        }
    }

    // ---------------- actions ----------------

    fun voidItems(lines: List<Pair<Long, Int>>, reason: String) = action(
        successMessage = "Cancellation slip printed at the counter",
    ) { uuid -> repo.submitVoidItems(uuid, lines, reason) }

    fun finalizeBill(paymentMode: String, customerLocalId: Long?) = action(
        successMessage = "Bill printed at the counter",
        onApplied = { _ui.value = _ui.value.copy(closedStatus = "billed") },
    ) { uuid -> repo.submitFinalize(uuid, paymentMode, customerLocalId) }

    fun cancelOrder(reason: String) = action(
        successMessage = "Order cancelled",
        onApplied = { _ui.value = _ui.value.copy(closedStatus = "cancelled") },
    ) { uuid -> repo.submitCancelOrder(uuid, reason) }

    fun reprintKot() = action(
        successMessage = "KOT reprinted at the counter",
    ) { uuid -> repo.submitReprintKot(uuid) }

    private fun action(
        successMessage: String,
        onApplied: (() -> Unit)? = null,
        submit: suspend (String) -> SubmitResult,
    ) {
        val uuid = boundUuid ?: return
        if (actionJob?.isActive == true) return
        _ui.value = _ui.value.copy(busy = true)
        actionJob = viewModelScope.launch {
            try {
                when (val result = submit(uuid)) {
                    is SubmitResult.Accepted -> {
                        when (val res = repo.awaitResolution(result.clientEventId)) {
                            is EventResolution.Applied -> {
                                onApplied?.invoke()
                                _events.emit(OrderActionEvent.Done(successMessage))
                            }
                            is EventResolution.Rejected ->
                                _events.emit(OrderActionEvent.Error(res.message))
                            is EventResolution.Timeout -> _events.emit(
                                OrderActionEvent.Error(
                                    "Still waiting for the counter — check there before retrying.",
                                ),
                            )
                        }
                    }
                    is SubmitResult.AlreadyResolved -> {
                        if (result.status == "applied") {
                            onApplied?.invoke()
                            _events.emit(OrderActionEvent.Done(successMessage))
                        } else {
                            _events.emit(
                                OrderActionEvent.Error(OrdersRepository.reasonCopy(result.rejectReason)),
                            )
                        }
                    }
                    is SubmitResult.Failed -> _events.emit(OrderActionEvent.Error(result.message))
                }
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }
}
