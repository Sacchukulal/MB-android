package com.magicbill.app.ui.screens.auth

import android.os.Build
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magicbill.app.core.MBErrors
import com.magicbill.app.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    /** Inline error under the email field (validation only). */
    val emailError: String? = null,
    /** Error under the password field / PIN (validation, auth, or network). */
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

        // Validate locally before touching the network.
        val trimmed = email.trim()
        val emailError = when {
            trimmed.isBlank() -> "Enter your email"
            !Patterns.EMAIL_ADDRESS.matcher(trimmed).matches() -> "Enter a valid email"
            else -> null
        }
        val passwordError = when {
            password.isBlank() -> "Enter your password"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        if (emailError != null || passwordError != null) {
            _owner.value = LoginUiState(emailError = emailError, error = passwordError)
            return
        }

        _owner.value = LoginUiState(loading = true)
        viewModelScope.launch {
            try {
                auth.ownerLogin(trimmed, password)
                // Success: the session flow flips (Owner or OwnerGate) and the
                // root composition swaps worlds on its own.
            } catch (e: AuthRepository.LoginException) {
                _owner.value = LoginUiState(error = e.message)
            } catch (e: Exception) {
                Log.w("MB/Auth", "[AUTH] owner login unexpected failure", e)
                _owner.value = LoginUiState(error = MBErrors.network(e))
            }
        }
    }

    fun clearOwnerErrors() {
        val s = _owner.value
        if (s.error != null || s.emailError != null) {
            _owner.value = s.copy(error = null, emailError = null)
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
                _staff.value = LoginUiState(error = e.message)
            } catch (e: Exception) {
                Log.w("MB/Auth", "[AUTH] staff login unexpected failure", e)
                _staff.value = LoginUiState(error = MBErrors.network(e))
            }
        }
    }

    fun clearStaffError() {
        if (_staff.value.error != null) _staff.value = _staff.value.copy(error = null)
    }
}
