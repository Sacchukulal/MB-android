package com.magicbill.app.cloud

import com.magicbill.app.core.bool
import com.magicbill.app.core.obj
import com.magicbill.app.core.objects
import com.magicbill.app.core.str
import com.magicbill.app.core.strOrNull
import com.magicbill.app.core.strings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Who this phone is signed in as at the cloud. Kept in the secure box, never in the database. */
@Serializable
data class CloudSession(
    val kind: Kind,
    val accessToken: String,
    val refreshToken: String,
    /** When the access token stops working, ms. Refreshed a minute before. */
    val expiresAtMs: Long,
    val email: String? = null,
    /** Set for a staff sign-in: the one restaurant and person the token is for. */
    val staff: StaffIdentity? = null,
    /** The cloud's device row for this phone (staff sign-in only). */
    val deviceId: String? = null,
) {
    enum class Kind { OWNER, STAFF }
}

@Serializable
data class StaffIdentity(
    val restaurantId: String,
    val restaurantName: String,
    val shortCode: String,
    val staffId: String,
    val staffName: String,
)

/** One row of `mb_my_restaurants()`. */
data class Restaurant(
    val id: String,
    val name: String,
    val shortCode: String,
    val address: String,
    val gstin: String,
    /** owner · co_owner · staff · admin. */
    val role: String,
    val staff: StaffOnRestaurant?,
    /** What this caller may open on the phone: phone.reports, phone.khata, phone.staff, staff.manage, reports.view. */
    val permissions: Set<String>,
    val licence: Licence?,
) {
    fun may(code: String): Boolean = code in permissions
    val isOwner: Boolean get() = role == "owner" || role == "co_owner" || role == "admin"

    companion object {
        fun parse(list: JsonElement): List<Restaurant> = list.objects().map(::one)

        fun one(o: JsonObject): Restaurant = Restaurant(
            id = o.str("id"),
            name = o.str("name"),
            shortCode = o.str("short_code"),
            address = o.str("address"),
            gstin = o.str("gstin"),
            role = o.str("role"),
            staff = o.obj("staff")?.let {
                StaffOnRestaurant(it.str("id"), it.str("name"), it.strOrNull("role_id"), it.strOrNull("role_name"))
            },
            permissions = o.strings("permissions").toSet(),
            licence = o.obj("licence")?.let(Licence::parse),
        )
    }
}

data class StaffOnRestaurant(val id: String, val name: String, val roleId: String?, val roleName: String?)

data class Licence(
    /** active · trial · suspended · revoked · cancelled. Read before any date. */
    val status: String,
    val plan: String,
    val planName: String,
    val features: List<String>,
    val renewsOn: String?,
    val trialEndsOn: String?,
    /** Owners only; null for staff. Shown masked, revealed on a press. */
    val key: String?,
    val bound: Boolean,
    val boundDevice: BoundDevice?,
) {
    companion object {
        fun parse(o: JsonObject) = Licence(
            status = o.str("status"),
            plan = o.str("plan"),
            planName = o.str("plan_name"),
            features = o.strings("features"),
            renewsOn = o.strOrNull("renews_on"),
            trialEndsOn = o.strOrNull("trial_ends_on"),
            key = o.strOrNull("key"),
            bound = o.bool("bound"),
            boundDevice = o.obj("bound_device")?.let {
                BoundDevice(it.str("id"), it.str("name"), it.strOrNull("last_seen_at"), it.str("app_version"))
            },
        )
    }
}

data class BoundDevice(val id: String, val name: String, val lastSeenAt: String?, val appVersion: String)

/** A phone or the counter, from `GET /rest/v1/devices`. */
data class Device(
    val id: String,
    val kind: String,
    val staffId: String?,
    val name: String,
    val appVersion: String,
    val lastSeenAt: String?,
    val revokedAt: String?,
) {
    companion object {
        fun parse(list: JsonElement): List<Device> = list.objects().map { o ->
            Device(o.str("id"), o.str("kind"), o.strOrNull("staff_id"), o.str("name"), o.str("app_version"), o.strOrNull("last_seen_at"), o.strOrNull("revoked_at"))
        }
    }
}

/** One permission the cloud knows, with the counter's own wording. */
data class PermissionCode(val code: String, val name: String, val scope: String) {
    companion object {
        fun parse(list: JsonElement): List<PermissionCode> = list.objects().map { o ->
            PermissionCode(o.str("code"), o.str("name"), o.str("scope"))
        }
    }
}

/** A release on the shelf, `app = android`, published. */
data class Release(val version: String, val notes: String, val url: String, val sha256: String) {
    companion object {
        fun parse(list: JsonElement): List<Release> = list.objects().map { o ->
            Release(o.str("version"), o.str("notes"), o.str("url"), o.str("sha256"))
        }
    }
}
