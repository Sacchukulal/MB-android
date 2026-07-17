package com.magicbill.app.data.remote

import com.magicbill.app.data.prefs.SecurePrefs
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Supabase Auth session persistence backed by EncryptedSharedPreferences —
 * the owner login survives forever (until explicit logout), encrypted at rest.
 */
class SecureSessionManager(
    private val prefs: SecurePrefs,
    private val json: Json,
) : SessionManager {

    override suspend fun saveSession(session: UserSession) {
        prefs.putString(SecurePrefs.OWNER_SESSION, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession {
        // Contract (supabase-kt 3.6): throw when nothing is stored;
        // loadSessionOrNull() upstream converts this to null.
        val raw = prefs.getString(SecurePrefs.OWNER_SESSION) ?: error("No session stored")
        return runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
            ?: error("Stored session unreadable")
    }

    override suspend fun deleteSession() {
        prefs.remove(SecurePrefs.OWNER_SESSION)
    }
}
