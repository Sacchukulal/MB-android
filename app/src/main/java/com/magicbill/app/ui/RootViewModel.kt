package com.magicbill.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.data.AccountRepository
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.ThemeController
import com.magicbill.app.data.UpdateManager
import com.magicbill.app.data.UpdateUiState
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
