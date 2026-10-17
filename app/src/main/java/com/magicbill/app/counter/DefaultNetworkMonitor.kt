package com.magicbill.app.counter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build

/** Watches the default route, not whichever of several connected networks reported last. */
internal class DefaultNetworkMonitor(
    context: Context,
    private val available: (Network) -> Unit,
    private val lost: (Network) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(ConnectivityManager::class.java)
    private var started = false
    private var legacyNetwork: Network? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = available(network)
        override fun onLost(network: Network) = lost(network)
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshLegacyRoute()
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (started || manager == null) return
        if (Build.VERSION.SDK_INT >= 24) {
            manager.registerDefaultNetworkCallback(callback)
        } else {
            // API 23 has activeNetwork, but no registerDefaultNetworkCallback. A dynamic
            // system broadcast lets us inspect the default route, including LAN-only Wi-Fi.
            context.registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
            refreshLegacyRoute()
        }
        started = true
    }

    private fun refreshLegacyRoute() {
        val now = manager?.activeNetwork
        val before = legacyNetwork
        if (now == before) return
        legacyNetwork = now
        if (now != null) available(now) else if (before != null) lost(before)
    }

    override fun close() {
        if (!started) return
        if (Build.VERSION.SDK_INT >= 24) manager?.unregisterNetworkCallback(callback)
        else context.unregisterReceiver(receiver)
        started = false
        legacyNetwork = null
    }
}
