package com.magicbill.app.ui.screens.auth

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _owner = MutableStateFlow(LoginUiState())
    val owner: StateFlow<LoginUiState> = _owner

    private val _staff = MutableStateFlow(LoginUiState())
    val staff: StateFlow<LoginUiState> = _staff

    val rememberedCode: String? get() = auth.rememberedStaffCode()

    fun ownerLogin(email: String, password: String) {
        if (_owner.value.loading) return
        if (email.isBlank() || password.isBlank()) {
            _owner.value = LoginUiState(error = "Enter your email and password")
            return
        }
        _owner.value = LoginUiState(loading = true)
        viewModelScope.launch {
            try {
                auth.ownerLogin(email, password)
                // Success: the session flow flips to Owner and the root
                // composition swaps to the owner shell on its own.
            } catch (e: AuthRepository.LoginException) {
                _owner.value = LoginUiState(error = e.message)
            } catch (e: Exception) {
                _owner.value = LoginUiState(
                    error = "Couldn't reach the server. Check your internet and try again.",
                )
            }
        }
    }

    fun staffLogin(code: String, pin: String) {
        if (_staff.value.loading) return
        if (code.isBlank() || pin.length != 4) {
            _staff.value = LoginUiState(error = "Enter your restaurant code and 4-digit PIN")
            return
        }
        _staff.value = LoginUiState(loading = true)
        viewModelScope.launch {
            try {
                auth.staffLogin(code, pin, "${Build.BRAND} ${Build.MODEL}".trim())
            } catch (e: AuthRepository.LoginException) {
                _staff.value = LoginUiState(error = "Invalid code or PIN")
            } catch (e: Exception) {
                _staff.value = LoginUiState(
                    error = "Couldn't reach the server. Check your internet and try again.",
                )
            }
        }
    }

    fun clearStaffError() {
        if (_staff.value.error != null) _staff.value = _staff.value.copy(error = null)
    }
}
