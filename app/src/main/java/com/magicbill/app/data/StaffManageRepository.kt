package com.magicbill.app.data

import com.magicbill.app.core.PermissionMap
import com.magicbill.app.core.StaffListData
import com.magicbill.app.core.StaffRow
import com.magicbill.app.data.remote.EdgeFunctions
import com.magicbill.app.data.remote.StaffManageResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

class StaffManageException(val reason: String) : IOException(
    when (reason) {
        "pin-taken" -> "That PIN is already used by another staff member — pick a different one."
        "forbidden", "unauthorized" -> "You don't have access to this restaurant."
        else -> "Something went wrong — try again."
    },
)

/** Owner-side staff management — typed wrapper over the staff-manage function. */
@Singleton
class StaffManageRepository @Inject constructor(
    private val edge: EdgeFunctions,
    private val auth: AuthRepository,
    private val json: Json,
) {
    private suspend fun send(body: JsonObject, ownerToken: String?): StaffManageResponse {
        val res = edge.call("staff-manage", body, ownerToken)
        val parsed = json.decodeFromJsonElement(StaffManageResponse.serializer(), res)
        if (!parsed.ok) throw StaffManageException(parsed.reason ?: "server")
        return parsed
    }

    private suspend fun manage(action: String, licenseKey: String, payload: JsonObject = buildJsonObject {}): StaffManageResponse {
        val token = auth.ownerAccessToken() ?: throw StaffManageException("unauthorized")
        val body = buildJsonObject {
            put("action", action)
            put("licenseKey", licenseKey)
            payload.forEach { (k, v) -> put(k, v) }
        }
        return send(body, token)
    }

    /**
     * Staff-manager path: a trusted staff member (manage_staff permission)
     * acting via their opaque session token. The Edge Function derives the
     * license from the session and enforces the manager guardrails.
     */
    private suspend fun manageAsStaff(action: String, payload: JsonObject = buildJsonObject {}): StaffManageResponse {
        val token = auth.loadStaffSession()?.token ?: throw StaffManageException("unauthorized")
        val body = buildJsonObject {
            put("action", action)
            put("token", token)
            payload.forEach { (k, v) -> put(k, v) }
        }
        return send(body, null) // rides on the anon key, like every staff call
    }

    suspend fun list(licenseKey: String): StaffListData {
        val res = manage("list", licenseKey)
        val staff = res.staff?.let {
            json.decodeFromJsonElement(ListSerializer(StaffRow.serializer()), it)
        } ?: emptyList()
        return StaffListData(staff, res.restaurantCode)
    }

    suspend fun ensureCode(licenseKey: String): String =
        manage("ensure_code", licenseKey).restaurantCode
            ?: throw StaffManageException("server")

    suspend fun create(
        licenseKey: String,
        name: String,
        roleLabel: String,
        pin: String,
        permissions: PermissionMap,
    ): StaffRow {
        val res = manage(
            "create", licenseKey,
            buildJsonObject {
                put("name", name)
                put("roleLabel", roleLabel)
                put("pin", pin)
                putJsonObject("permissions") {
                    permissions.forEach { (k, v) -> put(k, v) }
                }
            },
        )
        return json.decodeFromJsonElement(StaffRow.serializer(), res.staff!!)
    }

    suspend fun update(
        licenseKey: String,
        staffId: String,
        name: String? = null,
        roleLabel: String? = null,
        permissions: PermissionMap? = null,
        isActive: Boolean? = null,
    ) {
        manage(
            "update", licenseKey,
            buildJsonObject {
                put("staffId", staffId)
                name?.let { put("name", it) }
                roleLabel?.let { put("roleLabel", it) }
                permissions?.let { p ->
                    putJsonObject("permissions") { p.forEach { (k, v) -> put(k, v) } }
                }
                isActive?.let { put("isActive", it) }
            },
        )
    }

    suspend fun resetPin(licenseKey: String, staffId: String, pin: String) {
        manage(
            "reset_pin", licenseKey,
            buildJsonObject {
                put("staffId", staffId)
                put("pin", pin)
            },
        )
    }

    suspend fun remove(licenseKey: String, staffId: String) {
        manage("remove", licenseKey, buildJsonObject { put("staffId", staffId) })
    }

    // ---------------- staff-manager variants (session-token auth) ----------------

    suspend fun listAsStaff(): StaffListData {
        val res = manageAsStaff("list")
        val staff = res.staff?.let {
            json.decodeFromJsonElement(ListSerializer(StaffRow.serializer()), it)
        } ?: emptyList()
        return StaffListData(staff, res.restaurantCode)
    }

    suspend fun createAsStaff(
        name: String,
        roleLabel: String,
        pin: String,
        permissions: PermissionMap,
    ): StaffRow {
        val res = manageAsStaff(
            "create",
            buildJsonObject {
                put("name", name)
                put("roleLabel", roleLabel)
                put("pin", pin)
                putJsonObject("permissions") { permissions.forEach { (k, v) -> put(k, v) } }
            },
        )
        return json.decodeFromJsonElement(StaffRow.serializer(), res.staff!!)
    }

    suspend fun updateAsStaff(
        staffId: String,
        name: String? = null,
        roleLabel: String? = null,
        permissions: PermissionMap? = null,
        isActive: Boolean? = null,
    ) {
        manageAsStaff(
            "update",
            buildJsonObject {
                put("staffId", staffId)
                name?.let { put("name", it) }
                roleLabel?.let { put("roleLabel", it) }
                permissions?.let { p -> putJsonObject("permissions") { p.forEach { (k, v) -> put(k, v) } } }
                isActive?.let { put("isActive", it) }
            },
        )
    }

    suspend fun resetPinAsStaff(staffId: String, pin: String) {
        manageAsStaff(
            "reset_pin",
            buildJsonObject {
                put("staffId", staffId)
                put("pin", pin)
            },
        )
    }

    suspend fun removeAsStaff(staffId: String) {
        manageAsStaff("remove", buildJsonObject { put("staffId", staffId) })
    }

    /** Random 4-digit PIN suggestion (owner can still type their own). */
    fun generatePin(): String = (1000 + Random.nextInt(9000)).toString()
}
