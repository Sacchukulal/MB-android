package com.magicbill.app.ui.screens.signin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space

/**
 * Home or Reports while the shop's plan is not running: the cloud's sentence, the way to
 * magicbill.in, and Check again. The same door as a new account with no shop, with a
 * different reason on it. Orders on the counter's WiFi are the counter's to refuse.
 */
@Composable
fun PlanDoorScreen(sentence: String, onCheckAgain: () -> Unit) {
    var checking by remember { mutableStateOf(false) }
    Page("Magic Bill") {
        Column(Modifier.fillMaxWidth().padding(top = Space.s7), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This shop needs a running plan", style = Mb.type.section, color = Mb.colors.ink, textAlign = TextAlign.Center)
            VGap(Gap.group)
            NoShopYet(
                checking = checking,
                onCheckAgain = { checking = true; onCheckAgain(); checking = false },
                sentence = sentence,
                tone = Tone.Warn,
            )
        }
    }
}
