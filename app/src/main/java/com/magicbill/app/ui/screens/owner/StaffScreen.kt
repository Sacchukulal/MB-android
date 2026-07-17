package com.magicbill.app.ui.screens.owner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.components.MBLoadingState

// Placeholder — implemented in Phase D.
@Composable
fun StaffScreen(owner: MBSession.Owner) {
    MBLoadingState(Modifier.fillMaxSize(), label = "Staff management arrives in Phase D")
}
