package com.magicbill.app.ui.screens.signin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.cloud.SignUp
import com.magicbill.app.ui.kit.Field
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb

/**
 * A new owner's account: the six things magicbill.in's own sign-up asks for, posted to that
 * same route, then the phone signs in with the email and password just typed. The account has
 * no shop until a plan is chosen on the website, and this screen ends on that door.
 */
@Composable
fun OwnerSignUpScreen(back: () -> Unit, done: () -> Unit, vm: SignInViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var restaurantName by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }

    val phoneOk = phone.length == 10
    val emailOk = email.contains('@') && email.contains('.')
    val passwordOk = password.length >= 8
    val allOk = name.isNotBlank() && restaurantName.isNotBlank() && address.isNotBlank() && phoneOk && emailOk && passwordOk
    val submit = {
        tried = true
        if (allOk) vm.signUp(SignUp(name, restaurantName, address, phone, email, password))
    }
    LaunchedEffect(state.done) { if (state.done) done() }

    Page("Create an account", "The same account works at magicbill.in", back = back) {
        VGap(Gap.group)
        if (state.noShop) {
            if (state.sentence != null) { Notice(Tone.Danger, state.sentence!!); VGap(Gap.field) }
            NoShopYet(state.busy, vm::checkAgain, onUseAnother = vm::signOut)
        } else {
            Field(name, { name = it }, "Your name", error = if (tried && name.isBlank()) "Your name, please." else null)
            VGap(Gap.field)
            Field(restaurantName, { restaurantName = it }, "Restaurant name", error = if (tried && restaurantName.isBlank()) "The restaurant's name, please." else null)
            VGap(Gap.field)
            Field(address, { address = it }, "Restaurant address", placeholder = "Printed at the top of every bill.", singleLine = false, error = if (tried && address.isBlank()) "The restaurant's address, please." else null)
            VGap(Gap.field)
            Field(phone, { phone = it.filter(Char::isDigit).take(10) }, "Mobile number", placeholder = "10 digits", keyboard = KeyboardType.Phone, error = if (tried && !phoneOk) "Enter your 10-digit mobile number." else null)
            VGap(Gap.field)
            Field(email, { email = it.trim() }, "Email", keyboard = KeyboardType.Email, error = if (tried && !emailOk) "That does not look like an email." else null)
            VGap(Gap.field)
            Field(password, { password = it }, "Password", placeholder = "At least 8 characters.", keyboard = KeyboardType.Password, ime = ImeAction.Go, secret = true, onDone = submit, error = if (tried && !passwordOk) "Password must be at least 8 characters." else null)
            VGap(Gap.group)
            if (state.sentence != null) { Notice(Tone.Danger, state.sentence!!); VGap(Gap.field) }
            PrimaryButton("Create account", submit, Modifier.fillMaxWidth(), busy = state.busy)
            VGap(Gap.group)
            Text("Already have an account?", style = Mb.type.caption, color = Mb.colors.inkMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            VGap(Gap.field)
            SecondaryButton("Sign in", back, Modifier.fillMaxWidth())
        }
    }
}
