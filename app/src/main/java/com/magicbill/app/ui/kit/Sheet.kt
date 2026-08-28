package com.magicbill.app.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A sheet from the bottom: a choice, a reason, a confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(title: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
 * One reporter for the whole app: a sentence, shown once at the bottom, in the product's
 * voice. Screens say `LocalReporter.current.say("…")` and never build their own toast.
 */
class Reporter(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun say(sentence: String) {
        if (sentence.isBlank()) return
        scope.launch { host.currentSnackbarData?.dismiss(); host.showSnackbar(sentence) }
    }
}

val LocalReporter = compositionLocalOf<Reporter> { error("no reporter") }
