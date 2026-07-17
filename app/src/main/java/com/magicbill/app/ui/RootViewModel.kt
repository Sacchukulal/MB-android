package com.magicbill.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.data.AccountRepository
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val auth: AuthRepository,
    private val theme: ThemeController,
    private val account: AccountRepository,
) : ViewModel() {

    val session: StateFlow<MBSession> = auth.session
    val darkTheme: StateFlow<Boolean> = theme.dark

    /** Dot on the Account tab when an update is available (wired in Phase G). */
    val updateDotVisible = kotlinx.coroutines.flow.MutableStateFlow(false)

    init {
        auth.bootstrap()
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
}
