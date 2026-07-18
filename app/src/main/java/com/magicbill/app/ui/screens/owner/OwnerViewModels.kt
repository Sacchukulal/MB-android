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
 *
 * The previous state is intentionally NOT cleared here: on a range switch the
 * old report stays on screen for the ~20ms the local compute takes, then the
 * new one swaps in as a single emission — no collapse, no skeleton flash.
 */
private fun <T> localFirstLoad(
    scope: kotlinx.coroutines.CoroutineScope,
    state: MutableStateFlow<CachedUi<T>>,
    sync: OwnerSync,
    licenseKey: String,
    force: Boolean,
    compute: suspend () -> T?,
    lastSyncAt: suspend () -> Long?,
): kotlinx.coroutines.Job = scope.launch {
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

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: OwnerDataRepository,
    private val sync: OwnerSync,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<DashboardData>())
    val state: StateFlow<CachedUi<DashboardData>> = _state.asStateFlow()

    private var loadedKey: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null

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
        // Only a RESTAURANT switch clears the screen (different business —
        // skeleton is correct); otherwise old content stays until fresh lands.
        if (loadedKey != licenseKey) _state.value = CachedUi()
        loadedKey = licenseKey
        sync.activeLicense = licenseKey
        loadJob?.cancel()
        loadJob = localFirstLoad(
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
    private var loadedLicense: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null
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
        // Only a RESTAURANT switch clears the screen; a range switch keeps the
        // previous report visible until the new one computes (~20ms), so the
        // list never collapses and re-expands.
        if (loadedLicense != licenseKey) _state.value = CachedUi()
        loadedLicense = licenseKey
        loadedRange = rangeKey
        current = Triple(licenseKey, fromDay, toDay)
        sync.activeLicense = licenseKey
        loadJob?.cancel()
        loadJob = localFirstLoad(
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
