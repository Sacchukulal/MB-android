package com.magicbill.app.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.core.SITE_URL
import com.magicbill.app.core.openCustomTab
import com.magicbill.app.ui.components.GlowBackground
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBTextField
import com.magicbill.app.ui.components.ScreenHeader
import androidx.compose.ui.graphics.toArgb

@Composable
fun OwnerLoginScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.owner.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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
                title = "Welcome back",
                subtitle = "Sign in with your Magic Bill account",
                onBack = onBack,
            )

            Spacer(Modifier.height(28.dp))

            MBTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearOwnerErrors() },
                label = "Email",
                placeholder = "you@restaurant.in",
                leadingIcon = Icons.Outlined.Email,
                errorText = state.emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            MBTextField(
                value = password,
                onValueChange = { password = it; viewModel.clearOwnerErrors() },
                label = "Password",
                isPassword = true,
                leadingIcon = Icons.Outlined.Lock,
                errorText = state.error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        viewModel.ownerLogin(email, password)
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Forgot password?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { openCustomTab(context, "$SITE_URL/reset-password", toolbarColor) }
                    .padding(8.dp),
            )

            Spacer(Modifier.height(20.dp))

            MBButton(
                "Sign in",
                onClick = {
                    keyboard?.hide()
                    viewModel.ownerLogin(email, password)
                },
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))

            Text(
                "Don't have an account? Subscribe at magicbill.in",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openCustomTab(context, "$SITE_URL/pricing", toolbarColor) }
                    .padding(vertical = 16.dp),
            )
        }
    }
}
