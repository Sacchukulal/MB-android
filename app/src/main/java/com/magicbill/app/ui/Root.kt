package com.magicbill.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.ui.theme.MagicBillTheme

@Composable
fun MagicBillRoot() {
    val vm: RootViewModel = hiltViewModel()
    val mode by vm.themeMode.collectAsStateWithLifecycle()
    val scale by vm.textScale.collectAsStateWithLifecycle()
    MagicBillTheme(mode, scale) { Shell(vm) }
}
