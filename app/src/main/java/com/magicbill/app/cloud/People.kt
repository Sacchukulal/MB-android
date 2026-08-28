package com.magicbill.app.cloud

import com.magicbill.app.core.Answer
import com.magicbill.app.core.Argon
import com.magicbill.app.core.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The people desk (PHONE_API.md §3): one staff list, edited from the phone or the counter,
 * newest edit wins. Every write goes up as an RPC and comes back down through the mirror, so
 * the screen shows what the cloud has, never what the phone hoped.
 */
@Singleton
class People @Inject constructor(private val cloud: CloudLink, private val sync: Sync) {

    suspend fun saveStaff(restaurantId: String, body: JsonObject): Answer<Unit> =
        cloud.rpc("mb_save_staff", buildJsonObject { put("restaurant", restaurantId); put("body", body) }).after("staff")

    suspend fun saveRole(restaurantId: String, body: JsonObject): Answer<Unit> =
        cloud.rpc("mb_save_role", buildJsonObject { put("restaurant", restaurantId); put("body", body) }).after("roles")

    /** The PIN is hashed here with the counter's parameters; it never travels. */
    suspend fun setPin(restaurantId: String, staffId: String, pin: String): Answer<Unit> {
        val hash = withContext(Dispatchers.Default) { Argon.hashPin(pin) }
        return cloud.rpc("mb_set_staff_pin", buildJsonObject { put("restaurant", restaurantId); put("staff_id", staffId); put("pin_hash", hash) }).map { }
    }

    suspend fun revokeDevice(restaurantId: String, deviceId: String, reason: String = "removed by the owner"): Answer<Unit> =
        cloud.rpc("mb_revoke_device", buildJsonObject { put("restaurant", restaurantId); put("device", deviceId); put("reason", reason) }).map { }

    suspend fun devices(restaurantId: String): Answer<List<Device>> =
        cloud.select("devices?restaurant_id=eq.$restaurantId&order=kind,name&select=id,kind,staff_id,name,app_version,last_seen_at,revoked_at").map(Device::parse)

    @Volatile private var codes: List<PermissionCode>? = null

    /** Every permission the cloud knows, in the counter's wording. Fetched once per run. */
    suspend fun permissionCodes(): Answer<List<PermissionCode>> {
        codes?.let { return Answer.Ok(it) }
        return cloud.selectAnon("permissions?select=code,name,scope&order=code").map { PermissionCode.parse(it).also { list -> codes = list } }
    }

    private suspend fun Answer<kotlinx.serialization.json.JsonElement>.after(vararg tables: String): Answer<Unit> = when (this) {
        is Answer.Ok -> { sync.pullNow(tables.toSet()); Answer.Ok(Unit) }
        is Answer.Refused -> this
        is Answer.Unreachable -> this
        is Answer.SignedOut -> this
    }
}
