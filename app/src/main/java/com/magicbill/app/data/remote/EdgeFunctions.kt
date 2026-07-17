package com.magicbill.app.data.remote

import com.magicbill.app.BuildConfig
import com.magicbill.app.core.PermissionMap
import com.magicbill.app.core.StaffIdentity
import com.magicbill.app.core.StaffRestaurant
import com.magicbill.app.core.StaffRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin caller for Supabase Edge Functions — anon key only, never the service
 * role. Owner-authenticated calls pass the user's access token; public calls
 * (staff login / staff data) ride on the anon key. Mirrors the shipped
 * contract exactly.
 */
@Singleton
class EdgeFunctions @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1"
    private val mediaType = "application/json".toMediaType()

    class EdgeException(val reason: String) : IOException(reason)

    suspend fun call(name: String, body: JsonObject, token: String? = null): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/$name")
                .post(body.toString().toRequestBody(mediaType))
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_ANON_KEY}")
                .build()
            client.newCall(request).execute().use { res ->
                val text = res.body?.string() ?: throw EdgeException("empty-response")
                json.parseToJsonElement(text).jsonObject
            }
        }

    inline fun <reified T> decode(element: JsonElement): T = jsonRef.decodeFromJsonElement(
        kotlinx.serialization.serializer<T>(), element,
    )

    val jsonRef: Json get() = json
}

// ---------------- typed envelopes ----------------

@Serializable
data class StaffLoginResponse(
    val ok: Boolean = false,
    val reason: String? = null,
    val token: String? = null,
    val staff: StaffIdentity? = null,
    val restaurant: StaffRestaurant? = null,
)

@Serializable
data class StaffManageResponse(
    val ok: Boolean = false,
    val reason: String? = null,
    val staff: JsonElement? = null,
    @SerialName("restaurantCode") val restaurantCode: String? = null,
)

@Serializable
data class StaffDataEnvelope(
    val ok: Boolean = false,
    val reason: String? = null,
    val permissions: PermissionMap? = null,
    val data: JsonElement? = null,
)
