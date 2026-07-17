package com.magicbill.app.ui.screens.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.BillRow
import com.magicbill.app.core.DashboardData
import com.magicbill.app.core.ReportData
import com.magicbill.app.data.CachedQuery
import com.magicbill.app.data.OwnerDataRepository
import com.magicbill.app.data.local.CachedUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val query: CachedQuery,
    private val repo: OwnerDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<DashboardData>())
    val state: StateFlow<CachedUi<DashboardData>> = _state.asStateFlow()

    private var loadedKey: String? = null

    /** Cache-first load; re-runs when the selected restaurant changes. */
    fun load(licenseKey: String, force: Boolean = false) {
        if (!force && loadedKey == licenseKey && _state.value.data != null) return
        if (loadedKey != licenseKey) _state.value = CachedUi()
        loadedKey = licenseKey
        query.run(viewModelScope, "dashboard.$licenseKey", DashboardData.serializer(), _state) {
            repo.fetchDashboard(licenseKey)
        }
    }
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val query: CachedQuery,
    private val repo: OwnerDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<ReportData>())
    val state: StateFlow<CachedUi<ReportData>> = _state.asStateFlow()

    private var loadedRange: String? = null

    fun load(licenseKey: String, fromDay: String, toDay: String, force: Boolean = false) {
        val rangeKey = "report.$licenseKey.$fromDay.$toDay"
        if (!force && loadedRange == rangeKey && _state.value.data != null) return
        if (loadedRange != rangeKey) _state.value = CachedUi()
        loadedRange = rangeKey
        query.run(viewModelScope, rangeKey, ReportData.serializer(), _state) {
            repo.fetchReport(licenseKey, fromDay, toDay)
        }
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
                    "Couldn't load this bill. Check your internet and try again.",
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
