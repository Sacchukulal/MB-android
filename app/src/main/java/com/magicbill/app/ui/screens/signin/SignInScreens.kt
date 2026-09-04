package com.magicbill.app.ui.screens.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space

/** First screen: the brand on the glowing canvas, two clear doors. Nothing else. */
@Composable
fun WelcomeScreen(onOwner: () -> Unit, onStaff: () -> Unit) {
    Page(null, scroll = true) {
        Column(Modifier.fillMaxWidth().padding(top = Space.s7 + Space.s6), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Gap.field)) {
            Box(Modifier.size(120.dp).background(Mb.colors.accent.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.splashscreen_logo), contentDescription = "Magic Bill", modifier = Modifier.size(84.dp))
            }
            VGap(Gap.field)
            Text("Magic Bill", style = Mb.type.brand, color = Mb.colors.ink)
            Text("Your shop, in your pocket.", style = Mb.type.body, color = Mb.colors.inkMuted, textAlign = TextAlign.Center)
        }
        VGap(Space.s7 + Space.s6)
        PrimaryButton("I own the shop", onOwner, Modifier.fillMaxWidth())
        VGap(Gap.field)
        SecondaryButton("I work here", onStaff, Modifier.fillMaxWidth())
        VGap(Gap.group)
        Text("Staff: scan the code on the counter's screen. The owner or manager lets you in.", style = Mb.type.caption, color = Mb.colors.inkMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun OwnerSignInScreen(back: () -> Unit, signUp: () -> Unit, done: () -> Unit, vm: SignInViewModel = hiltViewModel()) {
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
            // No account yet: magicbill.in's sign-up opens inside the app, and the phone signs
            // itself in the moment the shop has its licence.
            VGap(Gap.group)
            Text("New to Magic Bill?", style = Mb.type.caption, color = Mb.colors.inkMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            VGap(Gap.field)
            SecondaryButton("Create an account", signUp, Modifier.fillMaxWidth())
        }
    }
}
