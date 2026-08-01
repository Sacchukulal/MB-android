package com.magicbill.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.layout.windowInsetsStartWidth
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

                    // EDGE-TO-EDGE SCRIMS, on all four possible system-bar
                    // edges.
                    //
                    // Edge-to-edge draws the app under the system bars, so
                    // scrolled content passes beneath the clock and the
                    // navigation buttons. The top strip has been here since
                    // 2.4.x; there was no equivalent at the bottom, so
                    // content scrolled visibly under the navigation bar in
                    // both button and gesture modes — the pill cleared it
                    // because PillNavBar has navigationBarsPadding(), but
                    // whatever was behind the pill showed through.
                    //
                    // The colour is the THEME BACKGROUND (#0B1120 dark,
                    // #F8FAFC light) — the same value the top strip already
                    // uses, so top and bottom match with no seam. Not black
                    // or white: those would be a visible band against the
                    // canvas.
                    //
                    // Every strip MEASURES its inset rather than hard-coding
                    // a height, so a strip whose inset is zero occupies
                    // nothing. That is what makes this correct in landscape,
                    // where the navigation bar moves to whichever side the
                    // device was rotated away from, and in gesture mode,
                    // where the bottom inset is a few dp instead of 48.
                    val barColor = MaterialTheme.colorScheme.background
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(barColor)
                            .align(Alignment.TopCenter),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(barColor)
                            .align(Alignment.BottomCenter),
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .windowInsetsStartWidth(WindowInsets.navigationBars)
                            .background(barColor)
                            .align(Alignment.CenterStart),
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .windowInsetsEndWidth(WindowInsets.navigationBars)
                            .background(barColor)
                            .align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}
