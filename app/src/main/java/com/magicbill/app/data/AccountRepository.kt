package com.magicbill.app.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.magicbill.app.BuildConfig
import com.magicbill.app.core.AccountData
import com.magicbill.app.core.LicenseInfo
import com.magicbill.app.core.PlanInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import com.magicbill.app.data.remote.EdgeFunctions
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** License + plan reads, and best-effort phone registration. */
@Singleton
class AccountRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val edge: EdgeFunctions,
    private val auth: AuthRepository,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun fetchAccount(licenseKey: String): AccountData {
        val license = supabase.from("licenses").select(
            Columns.raw(
                "key, display_name, email, mobile_number, restaurant_name, restaurant_code, " +
                    "plan_id, status, next_billing_date, device_id, device_name, device_platform, " +
                    "device_bound_at, device_last_seen, created_at",
            ),
        ) {
            filter { eq("key", licenseKey) }
        }.decodeList<LicenseInfo>().firstOrNull() ?: throw IllegalStateException("License not found")

        val plan = license.plan_id?.let { planId ->
            runCatching {
                supabase.from("plans")
                    .select(Columns.raw("id, name, amount_paise, interval_unit, description")) {
                        filter { eq("id", planId) }
                    }.decodeList<PlanInfo>().firstOrNull()
            }.getOrNull()
        }
        return AccountData(license, plan)
    }

    /**
     * Registers this phone on the owner's row (mobile-device Edge Function).
     * Fire-and-forget: called after login and on app open; failures retry
     * next open.
     */
    fun registerDevice(licenseKey: String) {
        if (licenseKey.isEmpty()) return
        scope.launch {
            runCatching {
                val token = auth.ownerAccessToken() ?: return@launch
                @Suppress("HardwareIds")
                val deviceId = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ANDROID_ID,
                ) ?: "unknown"
                edge.call(
                    "mobile-device",
                    buildJsonObject {
                        put("licenseKey", licenseKey)
                        put("deviceId", deviceId)
                        put("deviceName", "${Build.BRAND} ${Build.MODEL}".trim())
                        put("platform", "android ${Build.VERSION.RELEASE}")
                        put("appVersion", BuildConfig.VERSION_NAME)
                    },
                    token,
                )
            }
        }
    }
}

/** Maps a license status to a badge tone (mirrors the website's StatusBadge). */
fun statusTone(status: String?): String = when ((status ?: "").lowercase()) {
    "active" -> "success"
    "trial" -> "info"
    "grace", "halted", "pending", "created" -> "warning"
    "cancelled", "expired", "revoked", "suspended", "completed" -> "danger"
    else -> "neutral"
}

fun daysUntil(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val target = java.time.Instant.parse(if (iso.contains('T')) iso else "${iso}T00:00:00Z")
        java.time.temporal.ChronoUnit.DAYS.between(java.time.Instant.now(), target)
            .let { if (it < 0) it else it + 1 }
    }.getOrNull()
}
