package com.magicbill.app.ui.screens.owner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.MBLoadingState

// Placeholder — implemented in Phase C.
@Composable
fun ReportsScreen(
    rootViewModel: RootViewModel,
    owner: MBSession.Owner,
    onOpenBill: (String) -> Unit,
) {
    MBLoadingState(Modifier.fillMaxSize(), label = "Reports arrive in Phase C")
}
