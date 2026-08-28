package com.magicbill.app.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.BuildConfig
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.Answer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Both sign-ins. One state shape: busy, a sentence, done. */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val cloud: CloudLink,
    private val account: Account,
    private val sync: Sync,
) : ViewModel() {
    data class State(val busy: Boolean = false, val sentence: String? = null, val done: Boolean = false, val noShop: Boolean = false)

    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> get() = stateFlow

    fun owner(email: String, password: String) {
        if (stateFlow.value.busy) return
        stateFlow.value = State(busy = true)
        viewModelScope.launch {
            when (val a = cloud.passwordLogin(email, password)) {
                is Answer.Ok -> afterSignIn()
                else -> stateFlow.value = State(sentence = a.sentenceOrNull)
            }
        }
    }

    fun staff(restaurantCode: String, staffCode: String, pin: String) {
        if (stateFlow.value.busy) return
        stateFlow.value = State(busy = true)
        viewModelScope.launch {
            when (val a = cloud.staffLogin(restaurantCode, staffCode, pin, account.installId(), account.phoneName(), BuildConfig.VERSION_NAME)) {
                is Answer.Ok -> afterSignIn()
                else -> stateFlow.value = State(sentence = a.sentenceOrNull)
            }
        }
    }

    private suspend fun afterSignIn() {
        when (val r = account.refresh()) {
            is Answer.Ok -> {
                if (r.value.isEmpty()) { stateFlow.value = State(noShop = true); return }
                sync.pullIfStale(minAgeMs = 0)
                stateFlow.value = State(done = true)
            }
            // Signed in, but the list could not come: let the person in; the screens say so.
            else -> stateFlow.value = State(done = true, sentence = r.sentenceOrNull)
        }
    }

    fun signOut() { viewModelScope.launch { account.signOut(); stateFlow.value = State() } }
}
