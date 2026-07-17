package com.magicbill.app.ui.screens.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.PermissionMap
import com.magicbill.app.core.StaffListData
import com.magicbill.app.core.StaffRow
import com.magicbill.app.data.CachedQuery
import com.magicbill.app.data.StaffManageRepository
import com.magicbill.app.data.local.CachedUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StaffEvent {
    data class PinCreated(val staffName: String, val pin: String) : StaffEvent
    data class PinReset(val staffName: String, val pin: String) : StaffEvent
    data class Error(val message: String) : StaffEvent
    data class Saved(val message: String) : StaffEvent
}

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val query: CachedQuery,
    private val repo: StaffManageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<StaffListData>())
    val state: StateFlow<CachedUi<StaffListData>> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StaffEvent>()
    val events: SharedFlow<StaffEvent> = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var loadedKey: String? = null

    fun load(licenseKey: String, force: Boolean = false) {
        if (!force && loadedKey == licenseKey && _state.value.data != null) return
        if (loadedKey != licenseKey) _state.value = CachedUi()
        loadedKey = licenseKey
        query.run(viewModelScope, "staff.$licenseKey", StaffListData.serializer(), _state) {
            val list = repo.list(licenseKey)
            // Backfill: older licenses may not have a code yet.
            if (list.restaurantCode == null) {
                val code = runCatching { repo.ensureCode(licenseKey) }.getOrNull()
                list.copy(restaurantCode = code)
            } else list
        }
    }

    fun generatePin(): String = repo.generatePin()

    fun create(
        licenseKey: String,
        name: String,
        roleLabel: String,
        pin: String,
        permissions: PermissionMap,
    ) = mutate(licenseKey) {
        val staff = repo.create(licenseKey, name.trim(), roleLabel.trim(), pin, permissions)
        _events.emit(StaffEvent.PinCreated(staff.name, pin))
    }

    fun update(
        licenseKey: String,
        staff: StaffRow,
        name: String,
        roleLabel: String,
        permissions: PermissionMap,
    ) = mutate(licenseKey) {
        repo.update(licenseKey, staff.id, name.trim(), roleLabel.trim(), permissions)
        _events.emit(StaffEvent.Saved("${name.trim()} updated"))
    }

    fun setActive(licenseKey: String, staff: StaffRow, active: Boolean) = mutate(licenseKey) {
        repo.update(licenseKey, staff.id, isActive = active)
        _events.emit(
            StaffEvent.Saved(if (active) "${staff.name} activated" else "${staff.name} deactivated"),
        )
    }

    fun resetPin(licenseKey: String, staff: StaffRow, pin: String) = mutate(licenseKey) {
        repo.resetPin(licenseKey, staff.id, pin)
        _events.emit(StaffEvent.PinReset(staff.name, pin))
    }

    fun remove(licenseKey: String, staff: StaffRow) = mutate(licenseKey) {
        repo.remove(licenseKey, staff.id)
        _events.emit(StaffEvent.Saved("${staff.name} removed"))
    }

    private fun mutate(licenseKey: String, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
                load(licenseKey, force = true) // changes take effect immediately
            } catch (e: Exception) {
                _events.emit(StaffEvent.Error(e.message ?: "Something went wrong — try again."))
            } finally {
                _busy.value = false
            }
        }
    }
}
