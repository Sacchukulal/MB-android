package com.magicbill.app.ui.screens.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.StaffDashboard
import com.magicbill.app.core.StaffPlanInfo
import com.magicbill.app.core.StaffReport
import com.magicbill.app.data.CachedQuery
import com.magicbill.app.data.StaffDataRepository
import com.magicbill.app.data.local.CachedUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class StaffHomeViewModel @Inject constructor(
    private val query: CachedQuery,
    private val repo: StaffDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<StaffDashboard>())
    val state: StateFlow<CachedUi<StaffDashboard>> = _state.asStateFlow()

    private var loaded = false

    fun load(force: Boolean = false) {
        if (!force && loaded && _state.value.data != null) return
        loaded = true
        query.run(viewModelScope, "staff.dashboard", StaffDashboard.serializer(), _state) {
            repo.dashboard()
        }
    }
}

@HiltViewModel
class StaffReportsViewModel @Inject constructor(
    private val query: CachedQuery,
    private val repo: StaffDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<StaffReport>())
    val state: StateFlow<CachedUi<StaffReport>> = _state.asStateFlow()

    private var loadedRange: String? = null

    fun load(fromDay: String, toDay: String, force: Boolean = false) {
        val key = "staff.report.$fromDay.$toDay"
        if (!force && loadedRange == key && _state.value.data != null) return
        if (loadedRange != key) _state.value = CachedUi()
        loadedRange = key
        query.run(viewModelScope, key, StaffReport.serializer(), _state) {
            repo.report(fromDay, toDay)
        }
    }
}

/**
 * Read-only plan/subscription for staff with `view_plan_status`. Forward-
 * compatible: if the backend hasn't shipped the `account` view yet the call
 * fails and the profile screen simply keeps the section hidden.
 */
@HiltViewModel
class StaffAccountViewModel @Inject constructor(
    private val query: CachedQuery,
    private val repo: StaffDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<StaffPlanInfo>())
    val state: StateFlow<CachedUi<StaffPlanInfo>> = _state.asStateFlow()

    private var loaded = false

    fun load(force: Boolean = false) {
        if (!force && loaded && _state.value.data != null) return
        loaded = true
        query.run(viewModelScope, "staff.account", StaffPlanInfo.serializer(), _state) {
            repo.account()
        }
    }
}
