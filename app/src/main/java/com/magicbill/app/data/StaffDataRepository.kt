package com.magicbill.app.data

import com.magicbill.app.core.BillRow
import com.magicbill.app.core.FriendlyException
import com.magicbill.app.core.MBErrors
import com.magicbill.app.core.StaffBillDetail
import com.magicbill.app.core.StaffDashboard
import com.magicbill.app.core.StaffPlanInfo
import com.magicbill.app.core.StaffReport
import com.magicbill.app.data.remote.EdgeFunctions
import com.magicbill.app.data.remote.StaffDataEnvelope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class RevokedException : IOException("Access revoked — contact your manager")

/**
 * Staff-side data access — everything through the staff-data Edge Function
 * with the stored session token. Every response carries fresh permissions
 * (owner edits apply immediately); a "revoked" response clears the session
 * and boots to Welcome.
 */
@Singleton
class StaffDataRepository @Inject constructor(
    private val edge: EdgeFunctions,
    private val auth: AuthRepository,
    private val json: Json,
) {
    private suspend fun <T> call(
        view: String,
        serializer: KSerializer<T>,
        extra: Map<String, String> = emptyMap(),
    ): T {
        val stored = auth.loadStaffSession() ?: throw RevokedException()
        val res = edge.call(
            "staff-data",
            buildJsonObject {
                put("token", stored.token)
                put("view", view)
                extra.forEach { (k, v) -> put(k, v) }
            },
        )
        val envelope = json.decodeFromJsonElement(StaffDataEnvelope.serializer(), res)
        if (!envelope.ok) {
            if (envelope.reason == "revoked") {
                auth.markStaffRevoked()
                throw RevokedException()
            }
            throw FriendlyException(
                when (envelope.reason) {
                    "forbidden" -> "You don't have permission to view this. Ask your manager."
                    "not-found" -> "This item is no longer available."
                    else -> MBErrors.SERVER_DOWN
                },
            )
        }
        // Keep permissions fresh everywhere (owner edits apply immediately).
        envelope.permissions?.let { fresh ->
            auth.saveStaffSession(stored.copy(staff = stored.staff.copy(permissions = fresh)))
        }
        val data = envelope.data ?: throw IOException("empty")
        return json.decodeFromJsonElement(serializer, data)
    }

    suspend fun dashboard(): StaffDashboard =
        call("dashboard", StaffDashboard.serializer())

    suspend fun report(from: String, to: String): StaffReport =
        call("report", StaffReport.serializer(), mapOf("from" to from, "to" to to))

    suspend fun bill(billId: String): BillRow =
        call("bill", StaffBillDetail.serializer(), mapOf("billId" to billId)).bill

    /** Read-only plan/subscription info — gated server-side by view_plan_status. */
    suspend fun account(): StaffPlanInfo =
        call("account", StaffPlanInfo.serializer())
}
