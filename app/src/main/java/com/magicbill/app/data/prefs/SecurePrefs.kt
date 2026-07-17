package com.magicbill.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted key-value storage for auth tokens and session state.
 * Sessions persist forever — only an explicit logout calls [clearSessionData].
 */
@Suppress("DEPRECATION") // EncryptedSharedPreferences is deprecated upstream but
// remains the supported pattern for token storage without a full Keystore setup.
@Singleton
class SecurePrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mb_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) = prefs.edit {
        if (value == null) remove(key) else putString(key, value)
    }

    fun getLong(key: String, default: Long = 0L): Long = prefs.getLong(key, default)

    fun putLong(key: String, value: Long) = prefs.edit { putLong(key, value) }

    fun remove(key: String) = prefs.edit { remove(key) }

    /**
     * Logout: clears sessions and selections but keeps device-level
     * conveniences (remembered staff code, theme, update-dismissal).
     */
    fun clearSessionData() = prefs.edit {
        remove(OWNER_SESSION)
        remove(STAFF_SESSION)
        remove(SELECTED_LICENSE)
    }

    companion object Keys {
        const val OWNER_SESSION = "owner_session"
        const val STAFF_SESSION = "staff_session"
        const val SELECTED_LICENSE = "selected_license"
        const val REMEMBERED_STAFF_CODE = "remembered_staff_code"
        const val THEME_MODE = "theme_mode"
        const val UPDATE_DISMISSED_VERSION = "update_dismissed_version"
        const val UPDATE_DISMISSED_AT = "update_dismissed_at"
    }
}
