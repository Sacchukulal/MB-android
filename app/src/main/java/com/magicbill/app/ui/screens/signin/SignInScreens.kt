package com.magicbill.app.ui.screens.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.R
import com.magicbill.app.ui.kit.Field
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PinField
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space

@Composable
fun WelcomeScreen(onOwner: () -> Unit, onStaff: () -> Unit, onPair: () -> Unit) {
    Page(null, scroll = true) {
        Column(Modifier.fillMaxWidth().padding(top = Space.s7), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Gap.field)) {
            Image(painterResource(R.drawable.splashscreen_logo), contentDescription = null, modifier = Modifier.size(96.dp))
            Text("Magic Bill", style = Mb.type.page, color = Mb.colors.ink)
            Text("Your shop, in your pocket.", style = Mb.type.body, color = Mb.colors.inkMuted, textAlign = TextAlign.Center)
        }
        VGap(Space.s7)
        PrimaryButton("I own the shop", onOwner, Modifier.fillMaxWidth())
        VGap(Gap.field)
        SecondaryButton("I work here", onStaff, Modifier.fillMaxWidth())
        VGap(Gap.group)
        Text("Taking orders at the tables?", style = Mb.type.caption, color = Mb.colors.inkMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        QuietButton("Connect this phone to the counter", onPair, Modifier.fillMaxWidth())
    }
}

@Composable
fun OwnerSignInScreen(back: () -> Unit, done: () -> Unit, vm: SignInViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val emailOk = email.contains('@') && email.contains('.')
    LaunchedEffect(state.done) { if (state.done) done() }

    Page("Sign in", "The account you made at magicbill.in", back = back) {
        VGap(Gap.group)
        Field(email, { email = it.trim() }, "Email", keyboard = KeyboardType.Email, error = if (tried && !emailOk) "That does not look like an email." else null)
        VGap(Gap.field)
        Field(password, { password = it }, "Password", keyboard = KeyboardType.Password, ime = ImeAction.Go, secret = true, onDone = { tried = true; if (emailOk && password.isNotEmpty()) vm.owner(email, password) })
        VGap(Gap.group)
        if (state.sentence != null) { Notice(Tone.Danger, state.sentence!!); VGap(Gap.field) }
        if (state.noShop) {
            Notice(Tone.Info, "This account has no shop yet. Set one up at magicbill.in, then open the app again.")
            VGap(Gap.field)
            SecondaryButton("Use another account", { vm.signOut() }, Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Sign in", { tried = true; if (emailOk && password.isNotEmpty()) vm.owner(email, password) }, Modifier.fillMaxWidth(), busy = state.busy)
        }
    }
}

@Composable
fun StaffSignInScreen(back: () -> Unit, done: () -> Unit, vm: SignInViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var shop by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.done) { if (state.done) done() }
    val ready = shop.length == 6 && code.isNotBlank() && pin.length == 4

    Page("Sign in", "The shop code is on the owner's Account screen", back = back) {
        VGap(Gap.group)
        Field(shop, { shop = it.filter { c -> c.isLetterOrDigit() }.uppercase().take(6) }, "Shop code", placeholder = "K7M2QX", capitalise = true)
        VGap(Gap.field)
        Field(code, { code = it.uppercase().take(12) }, "Your staff code", placeholder = "RAVI", capitalise = true, ime = ImeAction.Done)
        VGap(Gap.group)
        Text("Your PIN — the same one as at the counter", style = Mb.type.label, color = Mb.colors.inkMuted)
        VGap(Gap.field)
        PinField(pin, { pin = it }, onDone = { if (ready) vm.staff(shop, code, pin) })
        VGap(Gap.group)
        if (state.sentence != null) { Notice(Tone.Danger, state.sentence!!); VGap(Gap.field) }
        PrimaryButton("Sign in", { if (ready) vm.staff(shop, code, pin) }, Modifier.fillMaxWidth(), enabled = ready, busy = state.busy)
    }
}
