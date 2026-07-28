package com.magicbill.app.data.orders

import android.os.Build
import android.util.Log
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.prefs.SecurePrefs
import com.magicbill.app.data.remote.EdgeFunctions
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONE seam between the phone and the cloud for live ordering (decision
 * D10). Everything above this class talks in orders and intents; only this
 * class knows the transport happens to be Supabase.
 *
 * Two rules define the design:
 *
 *  1. EDGE FUNCTION INVOCATIONS ARE THE ONLY METERED CALL. Exactly one Edge
 *     Function is ever called from the ordering path — `orders-enroll`,
 *     which mints this device's credential. It runs once per install (and
 *     again only if the refresh token is lost or rejected). Reads, writes
 *     and RPCs all go over PostgREST, which is not metered by count.
 *
 *  2. A HARD CEILING NO BUG CAN BREACH (P5). Every Edge call is recorded in
 *     a rolling log that survives restarts, and the ceilings are checked
 *     before the call is made.
 */
@Singleton
class OrdersCloud @Inject constructor(
    private val supabase: SupabaseClient,
    private val auth: AuthRepository,
    private val edge: EdgeFunctions,
    private val prefs: SecurePrefs,
    private val json: Json,
) {
    private val mutex = Mutex()

    /** Set once the credential for [enrolledScope] is usable. */
    @Volatile
    private var enrolledScope: String? = null

    @Volatile
    var roomId: String? = null
        private set

    @Volatile
    var actorName: String = ""
        private set

    class NotEnrolled(val reason: String) : Exception(reason)
    class BudgetExceeded(val window: String) : Exception("edge-budget-$window")

    // ---------------- the invocation ceiling (P5) ----------------

    /**
     * Rolling-hour runaway guard. A retry storm can burn an hour's worth of
     * calls in seconds; this stops it inside the hour.
     */
    private val perHour = 4

    /**
     * The real budget. Arithmetic, against the goal of under 50,000 Edge
     * invocations per month across 30 restaurants (10% of the free plan's
     * 500,000):
     *
     *     30 restaurants x 7 clients (1 counter + up to 6 phones) x 60
     *       = 12,600 invocations per 30 days
     *
     * 25% of the goal and 2.5% of the free plan, WITH EVERY CLIENT PINNED AT
     * ITS CEILING permanently. Steady state is one call per device, ever.
     */
    private val per30Days = 60

    private val logSerializer = ListSerializer(Long.serializer())

    private fun callLog(): List<Long> {
        val raw = prefs.getString(KEY_CALL_LOG) ?: return emptyList()
        return runCatching { json.decodeFromString(logSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun saveLog(log: List<Long>) {
        prefs.putString(KEY_CALL_LOG, json.encodeToString(logSerializer, log))
    }

    /** Plain-English usage readout for the staff profile screen (5.4). */
    data class Usage(
        val lastHour: Int,
        val last24h: Int,
        val last30Days: Int,
        val hourlyCeiling: Int,
        val monthlyCeiling: Int,
    )

    fun usage(): Usage {
        val now = System.currentTimeMillis()
        val log = callLog().filter { now - it < WINDOW_MS }
        return Usage(
            lastHour = log.count { now - it < HOUR_MS },
            last24h = log.count { now - it < DAY_MS },
            last30Days = log.size,
            hourlyCeiling = perHour,
            monthlyCeiling = per30Days,
        )
    }

    /**
     * @param essential this call carries something a person just asked for.
     * It may exceed the hourly guard — a waiter must never be refused by a
     * rate limiter — but never the 30-day budget.
     */
    private fun spendEdgeCall(essential: Boolean) {
        val now = System.currentTimeMillis()
        val log = callLog().filter { now - it < WINDOW_MS }.toMutableList()
        if (log.size >= per30Days) {
            Log.e(TAG, "[BUDGET] monthly cloud-call ceiling reached — refusing to enrol again")
            throw BudgetExceeded("month")
        }
        if (!essential && log.count { now - it < HOUR_MS } >= perHour) {
            throw BudgetExceeded("hour")
        }
        log.add(now)
        saveLog(log)
    }

    // ---------------- enrolment ----------------

    /** Identifies which restaurant/actor the current credential belongs to. */
    private suspend fun scopeKey(): String? = when (val s = auth.session.value) {
        is MBSession.Staff -> auth.loadStaffSession()?.let { "staff:${it.restaurant.code}" }
        is MBSession.Owner -> "owner:${s.active.licenseKey}"
        else -> null
    }

    /**
     * Makes sure this device holds a usable credential for the current
     * restaurant, enrolling if it does not. Cheap and safe to call before
     * every operation: after the first success it is a field comparison.
     */
    suspend fun ensureEnrolled(essential: Boolean = false) {
        val scope = scopeKey() ?: throw NotEnrolled("revoked")
        if (enrolledScope == scope && supabase.auth.currentSessionOrNull() != null) return
        mutex.withLock {
            if (enrolledScope == scope && supabase.auth.currentSessionOrNull() != null) return
            enroll(scope, essential)
        }
    }

    private suspend fun enroll(scope: String, essential: Boolean) {
        val session = auth.session.value
        val body = buildJsonObject {
            put("installId", prefs.installId())
            put("deviceLabel", "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(120))
            when (session) {
                is MBSession.Staff -> {
                    put("role", "staff")
                    put("token", auth.loadStaffSession()?.token ?: throw NotEnrolled("revoked"))
                }
                is MBSession.Owner -> {
                    put("role", "owner")
                    put("licenseKey", session.active.licenseKey)
                }
                else -> throw NotEnrolled("revoked")
            }
        }

        spendEdgeCall(essential)

        val ownerJwt = (session as? MBSession.Owner)?.let { auth.ownerAccessToken() }
        val reply = edge.call("orders-enroll", body, token = ownerJwt)

        val ok = reply["ok"]?.jsonPrimitive?.booleanOrNull == true
        if (!ok) throw NotEnrolled(reply["reason"]?.jsonPrimitive?.content ?: "server")

        roomId = reply["roomId"]?.jsonPrimitive?.content
        actorName = reply["actorName"]?.jsonPrimitive?.content ?: ""

        // An OWNER keeps the Supabase session they are already signed in
        // with — the Edge Function only added the mapping row. A STAFF
        // device receives its own session, which supabase-kt then persists
        // and refreshes on its own, for free, forever.
        if (reply["usesCallerSession"]?.jsonPrimitive?.booleanOrNull != true) {
            val accessToken = reply["accessToken"]?.jsonPrimitive?.content
                ?: throw NotEnrolled("server")
            val refreshToken = reply["refreshToken"]?.jsonPrimitive?.content
                ?: throw NotEnrolled("server")
            val expiresIn = reply["expiresIn"]?.jsonPrimitive?.longOrNull ?: 3600L
            supabase.auth.importSession(
                UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    tokenType = "bearer",
                ),
                autoRefresh = true,
            )
        }
        enrolledScope = scope
        Log.i(TAG, "[ENROL] credential ready for this restaurant")
    }

    /** Forget the credential — a restaurant switch or a logout. */
    suspend fun forget(signOut: Boolean) {
        enrolledScope = null
        roomId = null
        if (signOut) runCatching { supabase.auth.signOut() }
    }

    companion object {
        private const val TAG = "MB/Orders"
        private const val KEY_CALL_LOG = "orders_edge_call_log"
        private const val HOUR_MS = 60L * 60 * 1000
        private const val DAY_MS = 24 * HOUR_MS
        private const val WINDOW_MS = 30 * DAY_MS
    }
}
