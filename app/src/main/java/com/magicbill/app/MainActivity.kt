package com.magicbill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
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
            val view = LocalView.current
            // Edge-to-edge draws behind the system bars, so we own the icon
            // appearance: dark icons on a light theme, light icons on dark.
            // Keyed on `dark` so the in-app toggle flips the bars immediately.
            LaunchedEffect(dark) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            MagicBillTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    MagicBillRoot(viewModel)
                    // Edge-to-edge scrim: scrolled content passes under the
                    // transparent status bar, colliding with the clock/battery.
                    // This theme-colored strip sits on top so the system icons
                    // always have a clean backdrop.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(MaterialTheme.colorScheme.background)
                            .align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}
