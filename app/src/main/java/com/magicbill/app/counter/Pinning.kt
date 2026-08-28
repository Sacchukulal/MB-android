package com.magicbill.app.counter

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * The phone pins the counter's self-signed certificate (LAN_PROTOCOL.md §2). It learns the
 * fingerprint from a QR a person holds up, and from then on refuses any connection whose
 * certificate does not match. Not "warns" — refuses. The platform trust store is never asked:
 * no certificate authority can vouch for 192.168.1.7.
 */
object Fingerprints {
    fun sha256Hex(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    /**
     * Accepts every spelling the product uses — `sha256:<hex>`, bare hex, or the QR's base64url
     * of the raw 32 bytes (43 characters) — and answers lowercase hex, or null when it is none.
     */
    fun normalise(text: String?): String? {
        val t = text?.trim()?.removePrefix("sha256:")?.replace(":", "") ?: return null
        if (t.length == 64 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return t.lowercase()
        if (t.length in 43..44) {
            return try {
                val raw = Base64.getUrlDecoder().decode(t.trimEnd('='))
                if (raw.size == 32) raw.joinToString("") { "%02x".format(it) } else null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        return null
    }

    fun same(a: String?, b: String?): Boolean {
        val x = normalise(a) ?: return false
        val y = normalise(b) ?: return false
        return x == y
    }
}

/** Trusts exactly one certificate, by fingerprint. Everything else is "not the till on the code". */
class PinnedTrust(fingerprint: String) : X509TrustManager {
    private val pinned = Fingerprints.normalise(fingerprint) ?: throw IllegalArgumentException("not a fingerprint")

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("client certificates are not used")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
        if (Fingerprints.sha256Hex(leaf) != pinned) {
            throw CertificateException("That is not the till on the code.")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}

/**
 * Used for exactly one request — `/v1/hello` before pairing — over a connection that trusts
 * nothing. It records what the other side presented so the caller can compare it with the QR
 * and only then pin it.
 */
class RecordingTrust : X509TrustManager {
    @Volatile var seenFingerprint: String? = null
        private set

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("client certificates are not used")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
        seenFingerprint = Fingerprints.sha256Hex(leaf)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}

fun OkHttpClient.Builder.trusting(manager: X509TrustManager): OkHttpClient.Builder {
    val context = SSLContext.getInstance("TLS")
    context.init(null, arrayOf(manager), null)
    sslSocketFactory(context.socketFactory, manager)
    // The certificate is pinned by fingerprint; the name on it is not what identifies the counter.
    hostnameVerifier { _, _ -> true }
    return this
}
