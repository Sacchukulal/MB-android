package com.magicbill.app.ui.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.data.NetworkMonitor
import com.magicbill.app.data.orders.EventResolution
import com.magicbill.app.data.orders.MenuItem
import com.magicbill.app.data.orders.OrderLine
import com.magicbill.app.data.orders.OrdersRealtime
import com.magicbill.app.data.orders.OrdersRepository
import com.magicbill.app.data.orders.OrdersUiState
import com.magicbill.app.data.orders.SubmitResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of a send, rendered inline on the builder. */
sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState

    /** Applied by the POS — KOT printed (or saved with a printer warning). */
    data class Done(val printerWarning: Boolean) : SendState

    /** Rejected/failed — the draft is kept so the waiter can retry. */
    data class Failed(val message: String, val canRetry: Boolean) : SendState
}

/**
 * Order builder: holds the DRAFT (phone-only, never leaves the device until
 * Send) and drives the create/add_items intent. Line identity = (localId,
 * note) — the same dish with a different note is its own line.
 */
@HiltViewModel
class OrderBuilderViewModel @Inject constructor(
    private val repo: OrdersRepository,
    private val realtime: OrdersRealtime,
    network: NetworkMonitor,
) : ViewModel() {

    val ordersState: StateFlow<OrdersUiState> = repo.state
    val online: StateFlow<Boolean> = network.online

    private val _draft = MutableStateFlow<List<OrderLine>>(emptyList())
    val draft: StateFlow<List<OrderLine>> = _draft.asStateFlow()

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    // Stable ids so a retry after a lost reply can never double-create.
    private var retryEventId: String? = null
    private var retryOrderUuid: String? = null
    private var sendJob: Job? = null

    fun connect() = realtime.acquire()
    fun disconnect() = realtime.release()
    fun ensureLoaded() = repo.ensureLoaded()

    // ---------------- draft edits ----------------

    fun add(item: MenuItem) {
        if (!item.isAvailable) return
        change(item.localId, note = null, delta = +1, name = item.name, price = item.price, categoryLocalId = item.categoryLocalId)
    }

    fun increment(line: OrderLine) = change(line.localId, line.note, +1, line.name, line.price, line.categoryLocalId)

    fun decrement(line: OrderLine) = change(line.localId, line.note, -1, line.name, line.price, line.categoryLocalId)

    /** Re-keys a line to a new note, merging with an existing identical line. */
    fun setNote(line: OrderLine, rawNote: String) {
        val note = rawNote.trim().take(200).ifEmpty { null }
        if (note == line.note) return
        _draft.value = _draft.value.toMutableList().apply {
            val idx = indexOfFirst { it.localId == line.localId && it.note == line.note }
            if (idx < 0) return
            val moved = removeAt(idx).copy(note = note)
            val mergeIdx = indexOfFirst { it.localId == moved.localId && it.note == moved.note }
            if (mergeIdx >= 0) {
                set(mergeIdx, this[mergeIdx].copy(quantity = this[mergeIdx].quantity + moved.quantity))
            } else {
                add(idx, moved)
            }
        }
    }

    private fun change(
        localId: Long,
        note: String?,
        delta: Int,
        name: String,
        price: Double,
        categoryLocalId: Long?,
    ) {
        _draft.value = _draft.value.toMutableList().apply {
            val idx = indexOfFirst { it.localId == localId && it.note == note }
            if (idx >= 0) {
                val next = this[idx].quantity + delta
                if (next <= 0) removeAt(idx) else set(idx, this[idx].copy(quantity = next))
            } else if (delta > 0) {
                add(OrderLine(localId, name, price, delta, categoryLocalId, note))
            }
        }
    }

    fun quantityOf(item: MenuItem): Int =
        _draft.value.filter { it.localId == item.localId }.sumOf { it.quantity }

    // ---------------- send ----------------

    /**
     * Sends the draft. New order -> `create` (with a stable order uuid);
     * existing -> `add_items` deltas. The draft is only cleared once the POS
     * APPLIES the intent — a rejection keeps every line for retry.
     */
    fun send(
        existingOrderUuid: String?,
        orderType: String,
        tableNumber: String,
        section: String,
    ) {
        if (_draft.value.isEmpty() || sendJob?.isActive == true) return
        _sendState.value = SendState.Sending
        sendJob = viewModelScope.launch {
            val lines = _draft.value
            val result = if (existingOrderUuid != null) {
                repo.submitAddItems(existingOrderUuid, lines, existingClientEventId = retryEventId)
                    .also { retryEventId = retryEventIdFrom(it) }
            } else {
                val (res, uuid) = repo.submitCreate(
                    orderType, tableNumber, section, lines,
                    existingClientEventId = retryEventId,
                    existingOrderClientUuid = retryOrderUuid,
                )
                retryOrderUuid = uuid
                retryEventId = retryEventIdFrom(res)
                res
            }
            when (result) {
                is SubmitResult.Accepted -> {
                    when (val resolution = repo.awaitResolution(result.clientEventId)) {
                        is EventResolution.Applied -> onApplied(existingOrderUuid ?: retryOrderUuid)
                        is EventResolution.Rejected -> {
                            // The POS said no — this intent is spent; a new
                            // attempt must be a NEW event.
                            resetRetryIds()
                            _sendState.value = SendState.Failed(resolution.message, canRetry = true)
                        }
                        is EventResolution.Timeout -> _sendState.value = SendState.Failed(
                            "Still waiting for the counter. Check your connection and try again — " +
                                "it's safe, the order can't be sent twice.",
                            canRetry = true,
                        )
                    }
                }
                is SubmitResult.AlreadyResolved -> {
                    if (result.status == "applied") {
                        onApplied(existingOrderUuid ?: retryOrderUuid)
                    } else {
                        resetRetryIds()
                        _sendState.value = SendState.Failed(
                            OrdersRepository.reasonCopy(result.rejectReason), canRetry = true,
                        )
                    }
                }
                is SubmitResult.Failed -> _sendState.value =
                    SendState.Failed(result.message, canRetry = true)
            }
        }
    }

    private fun retryEventIdFrom(result: SubmitResult): String? = when (result) {
        is SubmitResult.Accepted -> result.clientEventId
        else -> retryEventId
    }

    private suspend fun onApplied(orderUuid: String?) {
        val printerWarning = orderUuid?.let { uuid ->
            repo.state.value.data?.orders?.firstOrNull { it.clientUuid == uuid }
                ?.printError?.isNotEmpty()
        } ?: false
        _draft.value = emptyList()
        resetRetryIds()
        _sendState.value = SendState.Done(printerWarning)
    }

    private fun resetRetryIds() {
        retryEventId = null
        retryOrderUuid = null
    }

    fun dismissSendError() {
        if (_sendState.value is SendState.Failed) _sendState.value = SendState.Idle
    }
}
