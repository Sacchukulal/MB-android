package com.magicbill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magicbill.app.data.MBSession
import com.magicbill.app.navigation.MagicBillRoot
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.theme.MagicBillTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The native splash covers the cold-start session check — the user
        // never sees a login flash or a spinner.
        splash.setKeepOnScreenCondition { viewModel.session.value is MBSession.Loading }
        enableEdgeToEdge()
        setContent {
            val dark by viewModel.darkTheme.collectAsStateWithLifecycle()
            MagicBillTheme(darkTheme = dark) {
                MagicBillRoot(viewModel)
            }
        }
    }
}
