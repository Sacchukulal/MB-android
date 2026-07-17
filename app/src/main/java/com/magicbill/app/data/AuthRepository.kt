package com.magicbill.app.data

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
    data class Owner(
        val restaurants: List<RestaurantInfo>,
        val active: RestaurantInfo?,
    ) : MBSession
    data class Staff(
        val staff: StaffIdentity,
        val restaurant: RestaurantInfo,
    ) : MBSession
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

    // ---------------- bootstrap (cold start, under the splash) ----------------

    /**
     * Resolves the stored session OPTIMISTICALLY: any locally stored session
     * navigates straight to its dashboard; validation happens in the
     * background and only a definitive server-side rejection boots the user.
     * Must be fast — the splash covers it.
     */
    fun bootstrap() {
        scope.launch {
            // Staff session?
            loadStaffSession()?.let { stored ->
                _session.value = MBSession.Staff(
                    stored.staff,
                    RestaurantInfo(licenseKey = "", name = stored.restaurant.name, code = stored.restaurant.code),
                )
                return@launch
            }

            // Owner session? (supabase-kt loads it from SecureSessionManager)
            val hasOwnerSession = prefs.getString(SecurePrefs.OWNER_SESSION) != null
            if (hasOwnerSession) {
                // Render instantly from cached restaurant list; refresh silently.
                val cached = cache.read("owner.restaurants", ListSerializer(RestaurantInfo.serializer()))
                val restaurants = cached?.data ?: emptyList()
                _session.value = MBSession.Owner(restaurants, pickActive(restaurants))
                refreshOwnerRestaurants()
                return@launch
            }

            _session.value = MBSession.None()
        }
    }

    private fun pickActive(restaurants: List<RestaurantInfo>): RestaurantInfo? {
        val storedKey = prefs.getString(SecurePrefs.SELECTED_LICENSE)
        return restaurants.firstOrNull { it.licenseKey == storedKey } ?: restaurants.firstOrNull()
    }

    // ---------------- owner ----------------

    class LoginException(message: String) : Exception(message)

    suspend fun ownerLogin(email: String, password: String): List<RestaurantInfo> {
        try {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            throw LoginException(
                when {
                    msg.contains("invalid", ignoreCase = true) ->
                        "Wrong email or password. New to Magic Bill? Sign up at magicbill.in"
                    else -> "Couldn't reach the server. Check your internet and try again."
                },
            )
        }
        val restaurants = fetchOwnerRestaurants()
        cache.write("owner.restaurants", ListSerializer(RestaurantInfo.serializer()), restaurants)
        _session.value = MBSession.Owner(restaurants, pickActive(restaurants))
        return restaurants
    }

    private suspend fun fetchOwnerRestaurants(): List<RestaurantInfo> {
        val rows = supabase.from("owners")
            .select(Columns.raw("license_key, licenses(restaurant_name, restaurant_code)"))
            .decodeList<OwnerRestaurantRow>()
        return rows.map {
            RestaurantInfo(
                licenseKey = it.license_key,
                name = it.licenses?.restaurant_name ?: "My Restaurant",
                code = it.licenses?.restaurant_code,
            )
        }
    }

    /** Background restaurant refresh — keeps the switcher fresh, never boots on network failure. */
    fun refreshOwnerRestaurants() {
        scope.launch {
            runCatching {
                val restaurants = withTimeoutOrNull(8_000) { fetchOwnerRestaurants() } ?: return@launch
                cache.write("owner.restaurants", ListSerializer(RestaurantInfo.serializer()), restaurants)
                val current = _session.value
                if (current is MBSession.Owner) {
                    val active = current.active?.let { a ->
                        restaurants.firstOrNull { it.licenseKey == a.licenseKey }
                    } ?: pickActive(restaurants)
                    _session.value = MBSession.Owner(restaurants, active)
                }
            }
        }
    }

    fun switchRestaurant(restaurant: RestaurantInfo) {
        prefs.putString(SecurePrefs.SELECTED_LICENSE, restaurant.licenseKey)
        val current = _session.value
        if (current is MBSession.Owner) {
            _session.value = current.copy(active = restaurant)
        }
    }

    // ---------------- staff ----------------

    suspend fun staffLogin(code: String, pin: String, deviceInfo: String): StoredStaffSession {
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
            throw LoginException("Invalid code or PIN")
        }
        val session = StoredStaffSession(parsed.token, parsed.staff, parsed.restaurant)
        saveStaffSession(session)
        prefs.putString(SecurePrefs.REMEMBERED_STAFF_CODE, parsed.restaurant.code)
        _session.value = MBSession.Staff(
            session.staff,
            RestaurantInfo(licenseKey = "", name = session.restaurant.name, code = session.restaurant.code),
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
        scope.launch { cache.clearAll() }
        _session.value = MBSession.None(revoked = true)
    }

    fun ackRevoked() {
        val current = _session.value
        if (current is MBSession.None) _session.value = MBSession.None(revoked = false)
    }

    // ---------------- logout ----------------

    suspend fun logout() {
        runCatching { supabase.auth.signOut() }
        prefs.clearSessionData()
        cache.clearAll()
        _session.value = MBSession.None()
    }

    suspend fun ownerAccessToken(): String? =
        supabase.auth.currentSessionOrNull()?.accessToken

    suspend fun ownerRefreshToken(): String? =
        supabase.auth.currentSessionOrNull()?.refreshToken
}
