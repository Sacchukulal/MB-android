package com.magicbill.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBTextField
import com.magicbill.app.ui.components.PinInput
import com.magicbill.app.ui.components.ScreenHeader

/**
 * Staff door: restaurant code (remembered after first login) + 4-digit PIN.
 * When 4 digits land, sign-in fires automatically.
 */
@Composable
fun StaffLoginScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.staff.collectAsStateWithLifecycle()

    val remembered = remember { viewModel.rememberedCode }
    var code by rememberSaveable { mutableStateOf(remembered ?: "") }
    var editingCode by rememberSaveable { mutableStateOf(remembered == null) }
    var pin by rememberSaveable { mutableStateOf("") }

    // Auto-submit when the 4th digit lands.
    LaunchedEffect(pin) {
        if (pin.length == 4 && code.isNotBlank() && !state.loading) {
            viewModel.staffLogin(code, pin)
        }
    }
    // Clear the PIN after a failed attempt so retyping starts clean.
    LaunchedEffect(state.error) {
        if (state.error != null) pin = ""
    }

    GlowBackground(Modifier.fillMaxSize(), intensity = 0.7f) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            ScreenHeader(
                title = "Staff sign in",
                subtitle = "Ask your manager for the code and your PIN",
                onBack = onBack,
            )

            Spacer(Modifier.height(28.dp))

            if (editingCode) {
                MBTextField(
                    value = code,
                    onValueChange = { code = it.uppercase(); viewModel.clearStaffError() },
                    label = "Restaurant Code",
                    placeholder = "e.g. HH-4829",
                    leadingIcon = Icons.Outlined.Storefront,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Restaurant",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(code, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { editingCode = true }
                            .padding(8.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Your PIN",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            PinInput(
                value = pin,
                onValueChange = { pin = it; viewModel.clearStaffError() },
                isError = state.error != null,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(28.dp))

            MBButton(
                "Sign in",
                onClick = { viewModel.staffLogin(code, pin) },
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            Text(
                "Your access is set by the restaurant owner.\nLost your PIN? Ask your manager to reset it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }
}
