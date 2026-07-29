package com.magicbill.app.data.orders

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.magicbill.app.data.AuthRepository
import com.magicbill.app.data.MBSession
import com.magicbill.app.data.prefs.SecurePrefs
import com.magicbill.app.data.remote.EdgeFunctions
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
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
 *     which mints this device's credential. It runs ONCE PER INSTALL, and
 *     again only if the credential is lost or the server rejects it. Reads,
 *     writes and RPCs all go over PostgREST, which is not metered by count.
 *
 *  2. A HARD CEILING NO BUG CAN BREACH (P5). Every Edge call is recorded in
 *     a rolling log that survives restarts, and the ceilings are checked
 *     before the call is made.
 *
 * WHAT CHANGED IN 2.4.3 — this class was the bug.
 *
 * The "have I enrolled?" answer used to live in a plain field. A field is
 * empty in a fresh process, so every single app open re-minted a credential
 * that was already sitting in encrypted storage, perfectly good. Three or
 * four opens in an hour then hit the safety ceiling, and because everything
 * — the status check, order reads, the socket — went through this class, the
 * ceiling took the whole Orders tab down with it. The waiter saw "Counter
 * offline" on a counter that was working.
 *
 * The fix is to persist the answer next to the credential it describes, and
 * to re-mint only when something real says the credential is no good:
 * nothing stored, a different restaurant, or the server itself saying no.
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

    /** Elapsed-time reading of the last credential recovery. */
    @Volatile
    private var lastRecoveryAt = 0L

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
     * Rolling-hour runaway guard. It exists to stop a retry storm inside the
     * hour, nothing else.
     *
     * It was 4, chosen when enrolment really did happen once per device.
     * With the re-enrolment bug above, four ordinary app opens exhausted it
     * — a guard against a runaway became a guard against normal use. 10
     * cannot be reached by anything except a genuine fault now that a
     * healthy device enrols zero times per hour.
     */
    private val perHour = 10

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
     * May we make an Edge call? Checked BEFORE the request, because a call
     * cannot be un-made.
     *
     * The ceiling can only ever refuse an ENROLMENT. Status checks, order
     * reads and submitting an order are PostgREST and never come through
     * here — they cost nothing against the quota, so throttling them would
     * refuse a waiter to protect a budget they were not spending.
     */
    private fun checkBudget() {
        val now = System.currentTimeMillis()
        val log = callLog().filter { now - it < WINDOW_MS }
        if (log.size >= per30Days) {
            Log.e(TAG, "[BUDGET] monthly cloud-call ceiling reached — refusing to enrol again")
            throw BudgetExceeded("month")
        }
        if (log.count { now - it < HOUR_MS } >= perHour) {
            Log.e(TAG, "[BUDGET] hourly cloud-call guard hit — something is re-enrolling in a loop")
            throw BudgetExceeded("hour")
        }
    }

    /**
     * Record a call that ACTUALLY HAPPENED — after the reply is in hand.
     *
     * Recording before the request charged us for calls that never reached
     * Supabase: two socket timeouts on a flaky connection burnt half the
     * hourly allowance without ever enrolling, and the phone then blocked
     * itself out of a feature it had never used. Nothing was spent, so
     * nothing is recorded.
     */
    private fun recordCall() {
        val now = System.currentTimeMillis()
        saveLog(callLog().filter { now - it < WINDOW_MS } + now)
    }

    // ---------------- enrolment ----------------

    /** Identifies which restaurant/actor the current credential belongs to. */
    private suspend fun scopeKey(): String? = when (val s = auth.session.value) {
        is MBSession.Staff -> auth.loadStaffSession()?.let { "staff:${it.restaurant.code}" }
        is MBSession.Owner -> "owner:${s.active.licenseKey}"
        else -> null
    }

    /**
     * Do we already hold a usable credential for [scope]?
     *
     * Two facts, both on disk, both surviving a process death: the scope we
     * last enrolled for, and supabase-kt's own stored session.
     *
     * It deliberately does NOT ask `currentSessionOrNull()`. That is null
     * while the session is still loading at cold start, and null again
     * whenever a refresh has failed for want of a network — and answering
     * "not enrolled" to either of those is how a perfectly good credential
     * got thrown away and re-bought. supabase-kt deletes the stored session
     * only when the server actually rejects the refresh token; that, and
     * only that, is what should make us pay for a new one.
     */
    private fun holdsCredentialFor(scope: String): Boolean =
        prefs.getString(SecurePrefs.ORDERS_ENROLLED_SCOPE) == scope &&
            prefs.getString(SecurePrefs.OWNER_SESSION) != null

    /**
     * Makes sure this device holds a usable credential for the current
     * restaurant. Cheap and safe to call before every operation: on an
     * enrolled device it is two reads from preferences, no network, and no
     * Edge call — which is the overwhelmingly common path and the entire
     * point of this round of work.
     */
    suspend fun ensureEnrolled() {
        val scope = scopeKey() ?: throw NotEnrolled("revoked")
        if (holdsCredentialFor(scope)) {
            // Wait for supabase-kt to finish restoring the session from
            // storage, or the first call after a cold start goes out without
            // its Authorization header. Free — it is a state flow, not a
            // request — and instant once loaded.
            supabase.auth.awaitInitialization()
            return
        }
        mutex.withLock {
            if (holdsCredentialFor(scope)) return
            enroll(scope, "no credential stored for this restaurant")
        }
    }

    /**
     * Is [e] the server telling us our credential is no good, as opposed to
     * the network failing to deliver the question? Only the former is a
     * reason to buy a new credential.
     */
    fun isCredentialRejection(e: Throwable): Boolean =
        e is RestException && (e.statusCode == 401 || e.statusCode == 403)

    /**
     * The server actually said no on a real call. This is the ONLY reactive
     * trigger for re-enrolment (§4.1): we never pre-emptively re-mint.
     *
     * Recovery goes cheapest-first. A 401 is far more often an access token
     * that expired a moment ago than a dead device: refreshing costs nothing
     * against the invocation quota, so it is always tried first, and a new
     * credential is bought only when the refresh token itself is gone.
     *
     * @return true if the caller should retry its request once.
     */
    suspend fun recoverFromRejection(): Boolean {
        val scope = scopeKey() ?: throw NotEnrolled("revoked")
        mutex.withLock {
            // Several calls in flight can all come back 401 at once. Only the
            // first of them should do anything; the rest just retry.
            val now = SystemClock.elapsedRealtime()
            if (lastRecoveryAt != 0L && now - lastRecoveryAt < RECOVERY_DEBOUNCE_MS) return true
            lastRecoveryAt = now

            val refreshed = runCatching { supabase.auth.refreshCurrentSession() }.isSuccess
            if (refreshed && holdsCredentialFor(scope)) {
                Log.i(TAG, "[ENROL] credential refreshed — no cloud call needed")
                return true
            }
            enroll(scope, "the server rejected this device's credential")
        }
        return true
    }

    private suspend fun enroll(scope: String, why: String) {
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

        checkBudget()
        Log.i(TAG, "[ENROL] minting a credential — $why")

        val ownerJwt = (session as? MBSession.Owner)?.let { auth.ownerAccessToken() }
        val reply = edge.call("orders-enroll", body, token = ownerJwt)
        // The function ran and answered. Whatever it said, that is one
        // invocation and it is on the bill.
        recordCall()

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
        // Written LAST and to disk, so it can only ever claim an enrolment
        // that actually completed — and so the next app start believes it.
        prefs.putString(SecurePrefs.ORDERS_ENROLLED_SCOPE, scope)
        Log.i(TAG, "[ENROL] credential ready for this restaurant")
    }

    /** Forget the credential — a restaurant switch or a logout. */
    suspend fun forget(signOut: Boolean) {
        prefs.remove(SecurePrefs.ORDERS_ENROLLED_SCOPE)
        lastRecoveryAt = 0L
        roomId = null
        if (signOut) runCatching { supabase.auth.signOut() }
    }

    companion object {
        private const val TAG = "MB/Orders"
        private const val KEY_CALL_LOG = "orders_edge_call_log"

        private const val HOUR_MS = 60L * 60 * 1000
        private const val DAY_MS = 24 * HOUR_MS
        private const val WINDOW_MS = 30 * DAY_MS

        /** Collapses a burst of simultaneous 401s into one recovery. */
        private const val RECOVERY_DEBOUNCE_MS = 10_000L
    }
}
