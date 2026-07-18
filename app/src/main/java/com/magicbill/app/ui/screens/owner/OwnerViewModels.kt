package com.magicbill.app.ui.screens.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.BillRow
import com.magicbill.app.core.DashboardData
import com.magicbill.app.core.ReportData
import com.magicbill.app.data.OwnerDataRepository
import com.magicbill.app.data.OwnerSync
import com.magicbill.app.data.local.CachedUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Local-first loading, shared by Dashboard and Reports:
 *  1. compute from the SQLite mirror and render IMMEDIATELY (works offline)
 *  2. run a sync; when it lands, recompute and update silently
 *  3. on sync failure keep the local data and quietly flag `fromCacheOnly`
 * A blocking error exists only when this license has never synced at all.
 */
private fun <T> localFirstLoad(
    scope: kotlinx.coroutines.CoroutineScope,
    state: MutableStateFlow<CachedUi<T>>,
    sync: OwnerSync,
    licenseKey: String,
    force: Boolean,
    compute: suspend () -> T?,
    lastSyncAt: suspend () -> Long?,
) {
    scope.launch {
        val local = compute()
        state.value = CachedUi(data = local, updatedAt = lastSyncAt(), refreshing = true)

        val error = sync.sync(licenseKey, force)
        val fresh = compute()
        state.value = CachedUi(
            data = fresh,
            updatedAt = lastSyncAt(),
            refreshing = false,
            fromCacheOnly = error != null && fresh != null,
            error = if (fresh == null) error ?: "Something went wrong — pull to retry." else null,
        )
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: OwnerDataRepository,
    private val sync: OwnerSync,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<DashboardData>())
    val state: StateFlow<CachedUi<DashboardData>> = _state.asStateFlow()

    private var loadedKey: String? = null

    init {
        // Another screen (or connectivity returning) synced — recompute quietly.
        viewModelScope.launch {
            sync.tick.drop(1).collect {
                loadedKey?.let { key ->
                    val fresh = repo.dashboardLocal(key) ?: return@let
                    _state.value = _state.value.copy(
                        data = fresh,
                        updatedAt = repo.lastSyncAt(key),
                        fromCacheOnly = false,
                    )
                }
            }
        }
    }

    fun load(licenseKey: String, force: Boolean = false) {
        if (!force && loadedKey == licenseKey && _state.value.data != null) return
        if (loadedKey != licenseKey) _state.value = CachedUi()
        loadedKey = licenseKey
        sync.activeLicense = licenseKey
        localFirstLoad(
            viewModelScope, _state, sync, licenseKey, force,
            compute = { repo.dashboardLocal(licenseKey) },
            lastSyncAt = { repo.lastSyncAt(licenseKey) },
        )
    }
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repo: OwnerDataRepository,
    private val sync: OwnerSync,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<ReportData>())
    val state: StateFlow<CachedUi<ReportData>> = _state.asStateFlow()

    private var loadedRange: String? = null
    private var current: Triple<String, String, String>? = null

    init {
        viewModelScope.launch {
            sync.tick.drop(1).collect {
                current?.let { (key, from, to) ->
                    val fresh = repo.reportLocal(key, from, to) ?: return@let
                    _state.value = _state.value.copy(
                        data = fresh,
                        updatedAt = repo.lastSyncAt(key),
                        fromCacheOnly = false,
                    )
                }
            }
        }
    }

    fun load(licenseKey: String, fromDay: String, toDay: String, force: Boolean = false) {
        val rangeKey = "report.$licenseKey.$fromDay.$toDay"
        if (!force && loadedRange == rangeKey && _state.value.data != null) return
        if (loadedRange != rangeKey) _state.value = CachedUi()
        loadedRange = rangeKey
        current = Triple(licenseKey, fromDay, toDay)
        sync.activeLicense = licenseKey
        localFirstLoad(
            viewModelScope, _state, sync, licenseKey, force,
            compute = { repo.reportLocal(licenseKey, fromDay, toDay) },
            lastSyncAt = { repo.lastSyncAt(licenseKey) },
        )
    }
}

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    private val repo: OwnerDataRepository,
    private val staffRepo: com.magicbill.app.data.StaffDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<BillDetailState>(BillDetailState.Loading)
    val state: StateFlow<BillDetailState> = _state.asStateFlow()

    fun load(billId: String, isStaff: Boolean) {
        _state.value = BillDetailState.Loading
        viewModelScope.launch {
            try {
                val bill = if (isStaff) staffRepo.bill(billId) else repo.fetchBill(billId)
                _state.value = bill?.let { BillDetailState.Ready(it) }
                    ?: BillDetailState.Error("Bill not found")
            } catch (e: Exception) {
                _state.value = BillDetailState.Error(
                    com.magicbill.app.core.MBErrors.network(e),
                )
            }
        }
    }
}

sealed interface BillDetailState {
    data object Loading : BillDetailState
    data class Ready(val bill: BillRow) : BillDetailState
    data class Error(val message: String) : BillDetailState
}
