package com.magicbill.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.magicbill.app.update.InstallAccess
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 25, 26, 34], manifest = Config.NONE, application = Application::class)
class InstallAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val access = InstallAccess(context)

    @Suppress("DEPRECATION")
    @Test fun permission_changes_are_read_again_after_returning_from_settings() {
        fun allow(value: Boolean) {
            if (Build.VERSION.SDK_INT >= 26) shadowOf(context.packageManager).setCanRequestPackageInstalls(value)
            else Settings.Secure.putInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS, if (value) 1 else 0)
        }
        allow(false)
        assertFalse(access.allowed())
        allow(true)
        assertTrue(access.allowed())
        allow(false)
        assertFalse(access.allowed())
    }

    @Test fun settings_and_instructions_match_the_devices_permission_model() {
        val intent = access.settingsIntent()
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
            assertEquals("package:${context.packageName}", intent.data.toString())
            assertFalse(access.message.contains("whole device"))
        } else {
            assertEquals(Settings.ACTION_SECURITY_SETTINGS, intent.action)
            assertNull(intent.data)
            assertTrue(access.message.contains("whole device"))
        }
    }
}
