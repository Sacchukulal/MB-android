package com.magicbill.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkInfo
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.magicbill.app.counter.DefaultNetworkMonitor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkInfo

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 24, 34], manifest = Config.NONE, application = Application::class)
class DefaultNetworkMonitorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val networks = shadowOf(manager)

    @Test fun monitoring_is_registered_once_and_can_be_closed() {
        val received = mutableListOf<Pair<String, Network>>()
        val monitor = DefaultNetworkMonitor(context, { received += "available" to it }, { received += "lost" to it })
        try {
            monitor.start()
            val afterStart = received.toList()
            monitor.start()
            assertEquals(afterStart, received)
            if (Build.VERSION.SDK_INT >= 24) {
                assertEquals(1, networks.networkCallbacks.size)
                val callback = networks.networkCallbacks.single()
                val wifi = ShadowNetwork.newInstance(101)
                callback.onAvailable(wifi)
                callback.onLost(wifi)
                assertEquals(listOf("available" to wifi, "lost" to wifi), received)
            } else {
                assertTrue(networks.networkCallbacks.isEmpty())
            }
        } finally {
            monitor.close()
            monitor.close()
        }
        assertTrue(networks.networkCallbacks.isEmpty())
    }

    @Config(sdk = [23])
    @Test fun legacy_broadcasts_report_default_route_changes_not_unrelated_networks() {
        fun info(type: Int) = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, type, 0, true, true)
        fun broadcast() {
            context.sendBroadcast(Intent(ConnectivityManager.CONNECTIVITY_ACTION))
            shadowOf(Looper.getMainLooper()).idle()
        }
        val received = mutableListOf<Pair<String, Network>>()
        networks.setActiveNetworkInfo(info(ConnectivityManager.TYPE_WIFI))
        val wifi = manager.activeNetwork!!
        val monitor = DefaultNetworkMonitor(context, { received += "available" to it }, { received += "lost" to it })
        monitor.start()
        assertEquals(listOf("available" to wifi), received)
        networks.addNetwork(ShadowNetwork.newInstance(105), info(ConnectivityManager.TYPE_MOBILE))
        broadcast()
        assertEquals(1, received.size)
        networks.setActiveNetworkInfo(info(ConnectivityManager.TYPE_MOBILE))
        val mobile = manager.activeNetwork!!
        broadcast()
        assertEquals("available" to mobile, received.last())
        networks.setActiveNetworkInfo(null)
        broadcast()
        assertEquals("lost" to mobile, received.last())
        assertEquals(3, received.size)
        monitor.close()
        networks.setActiveNetworkInfo(info(ConnectivityManager.TYPE_WIFI))
        broadcast()
        assertEquals(3, received.size)
    }
}
