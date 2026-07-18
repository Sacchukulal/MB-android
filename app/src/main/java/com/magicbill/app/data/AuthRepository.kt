package com.magicbill.app.data

import android.util.Log
import com.magicbill.app.core.FriendlyException
import com.magicbill.app.core.MBErrors
import com.magicbill.app.core.OwnerRestaurantRow
import com.magicbill.app.core.RestaurantInfo
import com.magicbill.app.core.StaffIdentity
import com.magicbill.app.core.StoredStaffSession
import com.magicbill.app.data.local.CacheStore
import com.magicbill.app.data.prefs.SecurePrefs
import com.magicbill.app.data.remote.EdgeFunctions
import com.magicbill.app.data.remote.StaffLoginResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** The app-level session. Bootstrap resolves it under the native splash. */
sealed interface MBSession {
    data object Loading : MBSession

    /** [revoked] true when the previous staff session was killed server-side. */
    data class None(val revoked: Boolean = false) : MBSession

    /** Authenticated owner with at least one usable license. [active] is never null. */
    data class Owner(
        val restaurants: List<RestaurantInfo>,
        val active: RestaurantInfo,
    ) : MBSession

    /** Authenticated owner who doesn't have a usable license (yet). */
    data class OwnerGate(val gate: GateState) : MBSession

    data class Staff(
        val staff: StaffIdentity,
        val restaurant: RestaurantInfo,
    ) : MBSession
}

/** Sub-states of the owner gate — each renders a real screen, never blank. */
sealed interface GateState {
    /** Verifying the subscription against the server (brief). */
    data object Checking : GateState

    /** Signed up, zero license rows — needs to subscribe. */
    data object NoSubscription : GateState

    /** License exists but payment hasn't confirmed yet (status 'created'). */
    data object PendingActivation : GateState

    /** Couldn't verify (offline/server issue) and nothing cached to show. */
    data class Unreachable(val message: String) : GateState
}

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val edge: EdgeFunctions,
    private val prefs: SecurePrefs,
    private val cache: CacheStore,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow<MBSession>(MBSession.Loading)
    val session: StateFlow<MBSession> = _session

    private fun setSession(value: MBSession, why: String) {
        Log.i(TAG, "[STATE] session -> ${value::class.simpleName} ($why)")
        _session.value = value
    }

    // ---------------- bootstrap (cold start, under the splash) ----------------

    /**
     * Resolves the stored session OPTIMISTICALLY: any locally stored session
     * navigates straight to its dashboard (cached data, instant, works fully
     * offline); validation happens in the background and only a definitive
     * server-side rejection boots the user. NEVER throws, NEVER leaves the
     * session on Loading — a blank screen is not a valid outcome.
     */
    fun bootstrap() {
        scope.launch {
            try {
                // Staff session?
                loadStaffSession()?.let { stored ->
                    setSession(
                        MBSession.Staff(
                            stored.staff,
                            RestaurantInfo(licenseKey = "", name = stored.restaurant.name, code = stored.restaurant.code),
                        ),
                        "bootstrap: stored staff session",
                    )
                    return@launch
                }

                // Owner session? (supabase-kt loads it from SecureSessionManager)
                if (prefs.getString(SecurePrefs.OWNER_SESSION) != null) {
                    val cached = cache.read("owner.restaurants", ListSerializer(RestaurantInfo.serializer()))
                        ?.data.orEmpty().filter { it.licenseKey.isNotEmpty() }
                    if (cached.isNotEmpty()) {
                        // Instant open from cache — even fully offline.
                        setSession(MBSession.Owner(cached, pickActive(cached)), "bootstrap: cached restaurants")
                        refreshOwnerRestaurants()
                    } else {
                        // Stored auth but no cached license — resolve via the gate.
                        resolveOwnerGate()
                    }
                    return@launch
                }

                setSession(MBSession.None(), "bootstrap: no stored session")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Corrupt prefs/cache must never brick the app — recover to
                // login. The stored session is left alone: if it's healthy the
                // next launch (or a fresh login) picks it up again.
                Log.e(TAG, "[AUTH] bootstrap failed, falling back to Welcome", e)
                setSession(MBSession.None(), "bootstrap: recovered from error")
            }
        }
    }

    private fun pickActive(restaurants: List<RestaurantInfo>): RestaurantInfo {
        val storedKey = prefs.getString(SecurePrefs.SELECTED_LICENSE)
        return restaurants.firstOrNull { it.licenseKey == storedKey } ?: restaurants.first()
    }

    // ---------------- owner ----------------

    class LoginException(message: String) : Exception(message)

    /**
     * Owner sign-in. Auth errors throw a [LoginException] with an accurate,
     * user-friendly message. Once auth succeeds the user IS logged in — any
     * follow-up problem (no subscription, pending payment, network blip while
     * fetching licenses) is handled on the gate screen with retry/subscribe/
     * logout actions, never by bouncing back to the password form.
     */
    suspend fun ownerLogin(email: String, password: String) {
        Log.i(TAG, "[AUTH] owner login attempt")
        try {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[AUTH] owner sign-in failed: ${e::class.simpleName}")
            throw LoginException(MBErrors.signIn(e))
        }
        resolveOwnerGate()
    }

    @Volatile
    private var resolveJob: kotlinx.coroutines.Job? = null

    /**
     * Resolves what an authenticated owner should see: dashboard (usable
     * license), pending-activation, no-subscription, or unreachable-with-retry.
     * Runs in the repository scope so it survives screen/ViewModel teardown.
     * @param quiet keep the current gate screen while re-checking (auto-retry)
     */
    fun resolveOwnerGate(quiet: Boolean = false) {
        if (!quiet) setSession(MBSession.OwnerGate(GateState.Checking), "gate: checking")
        if (resolveJob?.isActive == true) return // a check is already in flight
        resolveJob = scope.launch {
            try {
                val rows = withTimeoutOrNull(12_000) { fetchOwnerRows() }
                if (rows == null) {
                    Log.w(TAG, "[NET] owners fetch timed out")
                    onGateUnreachable(MBErrors.TIMEOUT)
                    return@launch
                }
                applyOwnerRows(rows, "gate: resolved", demote = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[NET] owners fetch failed: ${e::class.simpleName}: ${e.message}")
                onGateUnreachable(MBErrors.network(e))
            }
        }
    }

    /** Network failed while resolving — keep any signed-in world; degrade the gate. */
    private fun onGateUnreachable(message: String) {
        val current = _session.value
        if (current is MBSession.Owner) return // dashboard already showing cache
        setSession(MBSession.OwnerGate(GateState.Unreachable(message)), "gate: unreachable")
    }

    /**
     * Classifies owner rows into the right session state. Empty results only
     * ever downgrade the session when [demote] is set (explicit gate check) —
     * a background refresh must never yank a working dashboard away or
     * overwrite good cache because of a transient empty response.
     */
    private suspend fun applyOwnerRows(rows: List<OwnerRestaurantRow>, why: String, demote: Boolean) {
        val usable = rows
            .filter { it.license_key != null }
            .filter { !(it.licenses?.status ?: "").equals("created", ignoreCase = true) }
            .map {
                RestaurantInfo(
                    licenseKey = it.license_key!!,
                    name = it.licenses?.restaurant_name ?: "My Restaurant",
                    code = it.licenses?.restaurant_code,
                    status = it.licenses?.status,
                )
            }
        when {
            usable.isNotEmpty() -> {
                cache.write("owner.restaurants", ListSerializer(RestaurantInfo.serializer()), usable)
                val current = _session.value
                val active = (current as? MBSession.Owner)?.active?.let { a ->
                    usable.firstOrNull { it.licenseKey == a.licenseKey }
                } ?: pickActive(usable)
                setSession(MBSession.Owner(usable, active), why)
            }
            !demote -> Log.w(TAG, "[AUTH] refresh returned no usable licenses — keeping current session")
            rows.any { it.license_key != null } -> {
                cache.write("owner.restaurants", ListSerializer(RestaurantInfo.serializer()), usable)
                setSession(MBSession.OwnerGate(GateState.PendingActivation), "$why: pending")
            }
            else -> {
                cache.write("owner.restaurants", ListSerializer(RestaurantInfo.serializer()), usable)
                setSession(MBSession.OwnerGate(GateState.NoSubscription), "$why: no subscription")
            }
        }
    }

    /**
     * Fetches the owner's license rows. MUST wait for auth to finish loading
     * the stored session first: a query fired before initialization goes out
     * without the user JWT and RLS silently returns ZERO rows — indistinguishable
     * from "no subscription" and the cause of a nasty gate-flash race.
     */
    private suspend fun fetchOwnerRows(): List<OwnerRestaurantRow> {
        withTimeoutOrNull(5_000) { supabase.auth.awaitInitialization() }
        if (supabase.auth.currentSessionOrNull() == null) {
            Log.w(TAG, "[AUTH] no auth session after initialization")
            throw FriendlyException(MBErrors.SESSION_EXPIRED)
        }
        return supabase.from("owners")
            .select(Columns.raw("license_key, licenses(restaurant_name, restaurant_code, status)"))
            .decodeList<OwnerRestaurantRow>()
    }

    /** Background restaurant refresh — keeps the switcher fresh, never boots on network failure. */
    fun refreshOwnerRestaurants() {
        scope.launch {
            runCatching {
                val rows = withTimeoutOrNull(8_000) { fetchOwnerRows() } ?: return@launch
                applyOwnerRows(rows, "refresh", demote = false)
            }.onFailure { Log.w(TAG, "[NET] silent refresh failed: ${it.message}") }
        }
    }

    /** True once the owner has explicitly picked an outlet (multi-outlet). */
    fun hasStoredSelection(): Boolean =
        prefs.getString(SecurePrefs.SELECTED_LICENSE) != null

    fun switchRestaurant(restaurant: RestaurantInfo) {
        prefs.putString(SecurePrefs.SELECTED_LICENSE, restaurant.licenseKey)
        val current = _session.value
        if (current is MBSession.Owner) {
            setSession(current.copy(active = restaurant), "switch restaurant")
        }
    }

    // ---------------- staff ----------------

    suspend fun staffLogin(code: String, pin: String, deviceInfo: String): StoredStaffSession {
        Log.i(TAG, "[AUTH] staff login attempt")
        val res = edge.call(
            "staff-login",
            buildJsonObject {
                put("restaurantCode", code.trim().uppercase())
                put("pin", pin)
                put("deviceInfo", deviceInfo)
            },
        )
        val parsed = json.decodeFromJsonElement(StaffLoginResponse.serializer(), res)
        if (!parsed.ok || parsed.token == null || parsed.staff == null || parsed.restaurant == null) {
            throw LoginException(
                when (parsed.reason) {
                    null, "invalid" -> "Invalid restaurant code or PIN. Please check and try again."
                    else -> MBErrors.SERVER_DOWN
                },
            )
        }
        val session = StoredStaffSession(parsed.token, parsed.staff, parsed.restaurant)
        saveStaffSession(session)
        prefs.putString(SecurePrefs.REMEMBERED_STAFF_CODE, parsed.restaurant.code)
        setSession(
            MBSession.Staff(
                session.staff,
                RestaurantInfo(licenseKey = "", name = session.restaurant.name, code = session.restaurant.code),
            ),
            "staff login",
        )
        return session
    }

    fun rememberedStaffCode(): String? = prefs.getString(SecurePrefs.REMEMBERED_STAFF_CODE)

    fun loadStaffSession(): StoredStaffSession? {
        val raw = prefs.getString(SecurePrefs.STAFF_SESSION) ?: return null
        return runCatching { json.decodeFromString(StoredStaffSession.serializer(), raw) }
            .getOrNull()?.takeIf { it.token.isNotEmpty() }
    }

    fun saveStaffSession(session: StoredStaffSession) {
        prefs.putString(SecurePrefs.STAFF_SESSION, json.encodeToString(StoredStaffSession.serializer(), session))
        val current = _session.value
        if (current is MBSession.Staff) {
            _session.value = current.copy(staff = session.staff)
        }
    }

    /** Server killed the staff session — wipe and land on Welcome with notice. */
    fun markStaffRevoked() {
        prefs.remove(SecurePrefs.STAFF_SESSION)
        scope.launch { runCatching { cache.clearAll() } }
        setSession(MBSession.None(revoked = true), "staff revoked")
    }

    fun ackRevoked() {
        val current = _session.value
        if (current is MBSession.None) _session.value = MBSession.None(revoked = false)
    }

    // ---------------- logout ----------------

    suspend fun logout() {
        // Server-side sign-out is best effort — never let it block the user
        // from leaving (e.g. offline logout).
        runCatching { withTimeoutOrNull(5_000) { supabase.auth.signOut() } }
        runCatching { prefs.clearSessionData() }
        runCatching { cache.clearAll() }
        setSession(MBSession.None(), "logout")
    }

    suspend fun ownerAccessToken(): String? =
        supabase.auth.currentSessionOrNull()?.accessToken

    suspend fun ownerRefreshToken(): String? =
        supabase.auth.currentSessionOrNull()?.refreshToken

    companion object {
        private const val TAG = "MB/Auth"
    }
}
