package com.magicbill.app.counter

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * mDNS (LAN_PROTOCOL.md §1a): `_magicbill._tcp.local.` with TXT `id`, `v`, `fp`. Used to find a
 * counter whose address changed. It is absent on a network with client isolation and flaky on
 * some phones, so the last known address is always tried first and this is the second try.
 */
@Singleton
class Discovery @Inject constructor(@ApplicationContext private val context: Context) {

    data class Found(val serverId: String, val host: String, val port: Int, val fingerprint: String?)

    /** Any counter on this WiFi — for a typed code, when there is no QR to say where. */
    suspend fun findAny(timeoutMs: Long = 5_000): Found? = find(null, timeoutMs)

    /** Looks for [serverId] (or any counter when null) for up to [timeoutMs]. Null when nothing answered in time. */
    suspend fun find(serverId: String?, timeoutMs: Long = 5_000): Found? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var listener: NsdManager.DiscoveryListener? = null
                fun finish(found: Found?) {
                    listener?.let { l -> try { nsd.stopServiceDiscovery(l) } catch (e: Exception) { } }
                    if (cont.isActive) cont.resume(found)
                }
                listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = finish(null)
                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                    override fun onDiscoveryStarted(serviceType: String?) {}
                    override fun onDiscoveryStopped(serviceType: String?) {}
                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                        serviceInfo ?: return
                        @Suppress("DEPRECATION")
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {}
                            override fun onServiceResolved(info: NsdServiceInfo?) {
                                info ?: return
                                val attrs = info.attributes ?: emptyMap()
                                val id = attrs["id"]?.let { String(it, Charsets.UTF_8) } ?: return
                                if (serverId != null && id != serverId) return
                                val host = info.host?.hostAddress ?: return
                                val fp = attrs["fp"]?.let { String(it, Charsets.UTF_8) }
                                finish(Found(id, host, info.port, fp))
                            }
                        })
                    }
                }
                try {
                    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    finish(null)
                }
                cont.invokeOnCancellation { finish(null) }
            }
        }
    }

    companion object {
        const val SERVICE_TYPE = "_magicbill._tcp."
    }
}
