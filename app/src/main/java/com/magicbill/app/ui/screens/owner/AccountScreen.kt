package com.magicbill.app.ui.screens.owner

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
import com.magicbill.app.ui.components.ScreenHeader
import kotlinx.coroutines.launch

// Placeholder — implemented in Phase F. Logout works so Phase B is testable.
@Composable
fun AccountScreen(rootViewModel: RootViewModel, owner: MBSession.Owner) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader(
            title = owner.active?.name ?: "Account",
            subtitle = "Full account screen arrives in Phase F",
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
