package com.magicbill.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.magicbill.app.ui.Boot
import com.magicbill.app.ui.MagicBillRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays until the boxes are read, so the first screen is the right one:
        // a signed-in phone opens on its tabs, never on Welcome.
        installSplashScreen().setKeepOnScreenCondition { !Boot.ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A phone is held one way; a tablet is turned to suit the job.
        val smallest = resources.configuration.smallestScreenWidthDp
        requestedOrientation = if (smallest >= 600) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent { MagicBillRoot() }
    }
}
