package com.magicbill.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.magicbill.app.cloud.CloudLink
import com.magicbill.app.core.Argon
import com.magicbill.app.core.Exporter
import com.magicbill.app.core.Ist
import com.magicbill.app.counter.Fingerprints
import com.magicbill.app.db.FloorTableRow
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.prefs.Secure
import com.magicbill.app.update.UpdateTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Runs the compiled/desugared app code on the device. No accounts or persistent test DBs. */
@RunWith(AndroidJUnit4::class)
class CompatibilityDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun date_base64_and_pin_apis_work_in_the_device_apk() {
        val instant = Instant.parse("2026-08-27T18:35:00Z")
        assertEquals("2026-08-28", Ist.key(Ist.day(instant.toEpochMilli())))
        assertEquals(120_000L, Duration.ofMinutes(2).toMillis())
        val bytes = ByteArray(32) { it.toByte() }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertEquals("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", Fingerprints.normalise(encoded))
        assertArrayEquals(bytes, Base64.getUrlDecoder().decode(encoded))
        val started = SystemClock.elapsedRealtime()
        val hash = Argon.hashPin("1234", ByteArray(16) { it.toByte() })
        assertTrue(hash.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$AAECAwQFBgcICQoLDA0ODw\$"))
        assertTrue(Argon.verify("1234", hash))
        assertFalse(Argon.verify("1235", hash))
        Log.i(TAG, "PIN hash and two verifications: ${SystemClock.elapsedRealtime() - started} ms")
    }

    @Test fun room_upsert_and_transaction_rollback_work_with_device_sqlite() = runBlocking<Unit> {
        // Deliberately in memory: never clear or populate the user's magicbill3.db.
        val db = MbDatabase.inMemory(context)
        try {
            val row = FloorTableRow("compatibility", "1", "Test", 2, "free", 0)
            db.floor().putTables(listOf(row))
            db.floor().putTables(listOf(row.copy(state = "taken")))
            assertEquals(listOf(row.copy(state = "taken")), db.floor().tables().first())
            try {
                db.withTransaction {
                    db.floor().clearTables()
                    throw IllegalStateException("rollback probe")
                }
                fail("Transaction should throw")
            } catch (e: IllegalStateException) {
                assertEquals("rollback probe", e.message)
            }
            assertEquals(1, db.floor().tables().first().size)
            db.openHelper.readableDatabase.query("SELECT sqlite_version()").use {
                assertTrue(it.moveToFirst())
                Log.i(TAG, "SQLite ${it.getString(0)}; schema ${db.openHelper.readableDatabase.version}")
            }
        } finally {
            db.close()
        }
    }

    @Test fun encrypted_install_identity_can_be_reopened() {
        val first = Secure(context).installId()
        assertTrue(first.isNotBlank())
        assertEquals(first, Secure(context).installId())
        // Never log the identity or change an existing session/pairing credential.
    }

    @Test fun pdf_and_csv_can_be_read_through_the_file_provider() = runBlocking<Unit> {
        val csv = Exporter.csv(context, "compatibility-check", listOf("Name", "Amount"), listOf(listOf("Test, item", "1.00")))
        val pdf = Exporter.pdf(context, "compatibility-check", "Compatibility check", listOf("Test item  1.00"))
        fun bytes(chooser: Intent): ByteArray {
            @Suppress("DEPRECATION")
            val share = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            @Suppress("DEPRECATION")
            val uri = share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            return context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        }
        try {
            assertTrue(bytes(csv).toString(Charsets.UTF_8).contains("\"Test, item\",1.00"))
            assertEquals("%PDF", bytes(pdf).take(4).toByteArray().toString(Charsets.US_ASCII))
        } finally {
            java.io.File(context.cacheDir, "exports/compatibility-check.csv").delete()
            java.io.File(context.cacheDir, "exports/compatibility-check.pdf").delete()
        }
    }

    @Test fun cloud_and_update_hosts_complete_verified_tls_handshakes() {
        // Public/read-only requests: no login, account creation or shop writes.
        val requests = listOf(
            CloudLink.client() to (BuildConfig.CLOUD_URL.trimEnd('/') + "/auth/v1/health"),
            UpdateTransport.client(context) to com.magicbill.app.update.Updater.SHELF_URL,
        )
        for ((client, url) in requests) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertNotNull("TLS handshake for ${response.request.url.host}", response.handshake)
                assertTrue("HTTP ${response.code}", response.code in 200..499)
                Log.i(TAG, "Verified TLS ${response.handshake!!.tlsVersion} ${response.request.url.host}: HTTP ${response.code}")
            }
        }
    }

    @Test fun legacy_update_trust_rejects_an_unrelated_self_signed_certificate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val certificate = instrumentation.context.assets.open("untrusted-counter.pem").use {
            java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(it) as java.security.cert.X509Certificate
        }
        val trust = UpdateTransport.legacyTrustManager(context)
        val root = trust.acceptedIssuers.single { it.subjectX500Principal.name.contains("CN=ISRG Root X1") }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(root.encoded)
        assertEquals("96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6", digest.joinToString("") { "%02x".format(it.toInt() and 255) })
        assertTrue(trust.acceptedIssuers.size > 1)
        try {
            trust.checkServerTrusted(arrayOf(certificate), "RSA")
            fail("The unrelated self-signed counter certificate must not be trusted for updates")
        } catch (expected: java.security.cert.CertificateException) {
            // Certificate validation stays active; the counter has its own pinned client.
        }
        assertSame(okhttp3.OkHttpClient().hostnameVerifier, UpdateTransport.client(context).hostnameVerifier)
    }

    companion object { private const val TAG = "MagicBillCompat" }
}
