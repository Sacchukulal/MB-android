package com.magicbill.app.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.MBMotion
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A sheet from the bottom: a choice, a reason, a confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(title: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mb.colors.surface,
        contentColor = Mb.colors.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gap.page, vertical = Space.s4).windowInsetsPadding(WindowInsets.navigationBars)) {
            if (title != null) {
                Text(title, style = Mb.type.section, color = Mb.colors.ink)
                VGap(Gap.field)
            }
            content()
        }
    }
}

/**
 * One reporter for the whole app: a sentence, said once, at the TOP of the screen where it
 * covers no button, gone by itself. Screens say `LocalReporter.current.say("…")` and never
 * build their own toast. A new sentence replaces the one still showing.
 */
class Reporter(private val scope: CoroutineScope) {
    private val showing = MutableStateFlow<String?>(null)
    val sentence: StateFlow<String?> get() = showing
    private var hide: Job? = null

    fun say(sentence: String) {
        if (sentence.isBlank()) return
        hide?.cancel()
        showing.value = sentence
        hide = scope.launch {
            delay(2_600)
            showing.value = null
        }
    }
}

val LocalReporter = compositionLocalOf<Reporter> { error("no reporter") }

/** Where the reporter's sentence appears: a small raised pill under the status bar. */
@Composable
fun ToastHost(reporter: Reporter, modifier: Modifier = Modifier) {
    val sentence by reporter.sentence.collectAsStateWithLifecycle()
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gap.page, vertical = Space.s2), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = sentence != null,
            enter = slideInVertically(tween(MBMotion.DurMedium, easing = MBMotion.EaseEmphasized)) { -it } + fadeIn(tween(MBMotion.DurShort)),
            exit = slideOutVertically(tween(MBMotion.DurShort)) { -it } + fadeOut(tween(MBMotion.DurShort)),
        ) {
            var shown by remember { mutableStateOf(sentence ?: "") }
            if (sentence != null) shown = sentence!!
            Text(
                shown,
                style = Mb.type.label,
                color = Mb.colors.ink,
                modifier = Modifier
                    .shadow(14.dp, RoundedCornerShape(percent = 50), spotColor = Mb.colors.bg)
                    .background(Mb.colors.raisedHigh, RoundedCornerShape(percent = 50))
                    .padding(horizontal = Space.s4, vertical = Space.s3),
            )
        }
    }
}
