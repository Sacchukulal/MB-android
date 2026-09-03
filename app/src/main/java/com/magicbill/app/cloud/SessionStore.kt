package com.magicbill.app.cloud

import com.magicbill.app.core.MbJson
import com.magicbill.app.prefs.KeyBox
import com.magicbill.app.prefs.Secure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The cloud session, in memory and in the secure box. Loaded once; `session` is what screens
 * watch. A session is cleared ONLY when the server rejects the refresh token or the person
 * signs out — never on a network failure, and never because a read raced start-up.
 */
@Singleton
class SessionStore @Inject constructor(private val box: KeyBox) {
    private val state = MutableStateFlow<CloudSession?>(null)
    @Volatile private var loaded = false

    val session: StateFlow<CloudSession?> get() = state

    /** Reads the box. Call off the main thread, once, before anything asks. */
    fun load(): CloudSession? {
        if (!loaded) {
            // A box that cannot be read right now is NOT an empty box: the session stays
            // unknown and the next read tries again, rather than the phone looking signed out.
            val raw = try { box.get(Secure.CLOUD_SESSION) } catch (e: Exception) {
                android.util.Log.w(CloudLink.TAG, "the secure box could not be read: $e"); return null
            }
            state.value = raw?.let {
                try { MbJson.decodeFromString(CloudSession.serializer(), it) } catch (e: Exception) { null }
            }
            loaded = true
        }
        return state.value
    }

    fun current(): CloudSession? = if (loaded) state.value else load()

    fun save(session: CloudSession) {
        box.put(Secure.CLOUD_SESSION, MbJson.encodeToString(CloudSession.serializer(), session))
        state.value = session
        loaded = true
    }

    fun clear() {
        box.remove(Secure.CLOUD_SESSION)
        state.value = null
        loaded = true
    }
}
