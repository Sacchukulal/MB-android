package com.magicbill.app.ui.screens.owner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.AccountData
import com.magicbill.app.core.SITE_URL
import com.magicbill.app.core.openCustomTab
import com.magicbill.app.data.AccountRepository
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.CachedQuery
import com.magicbill.app.data.local.CachedUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val query: CachedQuery,
    private val account: AccountRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CachedUi<AccountData>())
    val state: StateFlow<CachedUi<AccountData>> = _state.asStateFlow()

    private var loadedKey: String? = null
    private var billingOpened = false

    fun load(licenseKey: String, force: Boolean = false) {
        if (!force && loadedKey == licenseKey && _state.value.data != null) return
        if (loadedKey != licenseKey) _state.value = CachedUi()
        loadedKey = licenseKey
        query.run(viewModelScope, "account.$licenseKey", AccountData.serializer(), _state) {
            account.fetchAccount(licenseKey)
        }
    }

    /**
     * Opens magicbill.in already signed-in (Chrome Custom Tab + session
     * handoff). The handoff endpoint validates the tokens server-side and
     * sets cookies, landing the owner straight on their billing page.
     */
    fun openBilling(context: Context, destination: String, toolbarColor: Int) {
        viewModelScope.launch {
            val access = auth.ownerAccessToken()
            val refresh = auth.ownerRefreshToken()
            val url = if (access != null && refresh != null) {
                "$SITE_URL/auth/mobile-handoff" +
                    "?token=${URLEncoder.encode(access, "UTF-8")}" +
                    "&refresh=${URLEncoder.encode(refresh, "UTF-8")}" +
                    "&redirect=${URLEncoder.encode(destination, "UTF-8")}"
            } else {
                "$SITE_URL$destination"
            }
            billingOpened = true
            openCustomTab(context, url, toolbarColor)
        }
    }

    /** Called on every resume; refreshes only after a billing round-trip. */
    fun onResume(licenseKey: String) {
        if (billingOpened) {
            billingOpened = false
            load(licenseKey, force = true)
        }
    }
}
