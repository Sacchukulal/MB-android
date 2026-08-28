package com.magicbill.app.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.magicbill.app.core.newId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A place for strings that must not be read off the disk by anybody else. */
interface KeyBox {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/**
 * The phone's keystore-backed box: sessions, the counter credential, the install id. Nothing
 * that goes in here is ever written to the database — a database is what a backup carries.
 * EncryptedSharedPreferences is deprecated upstream and used on purpose: it is what every
 * Android from 8 up has, and its replacement is not out yet.
 */
@Singleton
class Secure @Inject constructor(@ApplicationContext private val context: Context) : KeyBox {

    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "mb.secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Opens the box off the main thread at start-up, so the first read is not a disk read on it. */
    fun warm() {
        try {
            prefs.all
        } catch (e: Exception) {
            // A phone whose keystore is broken finds out at sign-in, where the sentence belongs.
            android.util.Log.w("MagicBill", "the secure box could not be opened at start: $e")
        }
    }

    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()

    /** One id per install, made once. It names this phone to the cloud and the counter. */
    fun installId(): String = get(INSTALL_ID) ?: newId().also { put(INSTALL_ID, it) }

    companion object {
        const val INSTALL_ID = "install.id"
        const val CLOUD_SESSION = "cloud.session"
        const val COUNTER_CREDENTIAL = "counter.credential"
        const val STREAM_SEQ = "counter.seq"
        const val CATALOGUE_VERSION = "counter.catalogue.version"
    }
}

/** For tests: a box that is a map. */
class MemoryBox : KeyBox {
    private val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}
