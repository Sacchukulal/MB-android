package com.magicbill.app.ui.screens.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magicbill.app.ui.components.MBLoadingState
import com.magicbill.app.ui.components.ScreenHeader

// Placeholder — implemented in Phase C.
@Composable
fun BillDetailScreen(billId: String, isStaff: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(title = "Receipt", onBack = onBack)
        MBLoadingState(label = "Receipt view arrives in Phase C")
    }
}
