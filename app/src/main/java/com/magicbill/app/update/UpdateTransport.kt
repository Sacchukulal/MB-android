package com.magicbill.app.update

import android.content.Context
import android.os.Build
import com.magicbill.app.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** The public update client. Counter certificate pinning and cloud trust remain independent. */
internal object UpdateTransport {
    fun client(context: Context): OkHttpClient = OkHttpClient.Builder().apply {
        if (Build.VERSION.SDK_INT < 26) {
            val trust = legacyTrustManager(context)
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            sslSocketFactory(tls.socketFactory, trust)
        }
        // Keep OkHttp's normal hostname verification and certificate-chain validation.
        connectTimeout(Duration.ofSeconds(8))
        readTimeout(Duration.ofSeconds(15))
        callTimeout(Duration.ofSeconds(25))
    }.build()

    internal fun legacyTrustManager(context: Context): X509TrustManager {
        fun manager(store: KeyStore?): X509TrustManager =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(store)
            }.trustManagers.filterIsInstance<X509TrustManager>().single()

        // Android 6 lacks ISRG Root X1, used by GitHub's release-assets download host.
        // Retain platform anchors, adding only this audited public CA to this client.
        // Source and fingerprint: docs/ANDROID_6_IMPLEMENTATION_2026-10-12.md.
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            manager(null).acceptedIssuers.forEachIndexed { i, cert -> setCertificateEntry("system-$i", cert) }
            val root = context.resources.openRawResource(R.raw.isrg_root_x1).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
            }
            setCertificateEntry("isrg-root-x1", root)
        }
        return manager(store)
    }
}
