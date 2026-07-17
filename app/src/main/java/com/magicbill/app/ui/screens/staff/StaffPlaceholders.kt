package com.magicbill.app.ui.screens.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magicbill.app.data.MBSession
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.components.MBButton
import com.magicbill.app.ui.components.MBButtonVariant
import com.magicbill.app.ui.components.MBLoadingState
import com.magicbill.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch

// Placeholders — implemented in Phase E.

@Composable
fun StaffHomeScreen(session: MBSession.Staff, onOpenBill: (String) -> Unit) {
    MBLoadingState(Modifier.fillMaxSize(), label = "Staff home arrives in Phase E")
}

@Composable
fun StaffReportsScreen(session: MBSession.Staff, onOpenBill: (String) -> Unit) {
    MBLoadingState(Modifier.fillMaxSize(), label = "Staff reports arrive in Phase E")
}

@Composable
fun StaffOrdersScreen() {
    MBLoadingState(Modifier.fillMaxSize(), label = "Orders placeholder arrives in Phase E")
}

@Composable
fun StaffProfileScreen(rootViewModel: RootViewModel, session: MBSession.Staff) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(
            title = session.staff.name,
            subtitle = session.restaurant.name,
        )
        Spacer(Modifier.height(24.dp))
        MBButton(
            "Log out",
            variant = MBButtonVariant.Tonal,
            onClick = { scope.launch { rootViewModel.auth.logout() } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
