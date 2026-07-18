package com.magicbill.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTOs mirroring the Supabase schema + Edge Function contracts (unchanged
 *  from the shipped RN app, so the backend needs zero changes). */

typealias PermissionMap = Map<String, Boolean>

fun PermissionMap?.has(key: PermissionKey): Boolean = this?.get(key.key) == true

// ---------------- bills / summaries ----------------

@Serializable
data class BillItem(
    val id: Long? = null,
    val name: String = "",
    val price: Double = 0.0,
    val quantity: Double = 0.0,
    val category_id: Long? = null,
)

@Serializable
data class BillRow(
    val id: String,
    val bill_number: String? = null,
    val token_number: Long? = null,
    val order_type: String? = null,
    val table_number: String? = null,
    val payment_mode: String? = null,
    val subtotal: Double? = null,
    val gst: Double? = null,
    val total: Double? = null,
    val items: List<BillItem>? = null,
    val billed_at: String,
    val customer_name: String? = null,
    val customer_phone: String? = null,
)

@Serializable
data class DaySummaryLite(val day: String, val total: Double? = null)

@Serializable
data class ExpenseAmount(val amount: Double? = null)

data class PaymentSplit(
    val cash: Double = 0.0,
    val card: Double = 0.0,
    val upi: Double = 0.0,
    val credit: Double = 0.0,
)

data class ItemAgg(val name: String, val quantity: Double, val amount: Double)

@Serializable
data class TodayStats(
    val day: String,
    val total: Double,
    val gst: Double,
    val billCount: Int,
    val avg: Double,
    val cash: Double,
    val card: Double,
    val upi: Double,
    val credit: Double,
)

@Serializable
data class TrendDay(val day: String, val total: Double)

@Serializable
data class ItemAggDto(val name: String, val quantity: Double, val amount: Double)

@Serializable
data class DashboardData(
    val today: TodayStats,
    val yesterdayTotal: Double? = null,
    val trend: List<TrendDay>,
    val topItems: List<ItemAggDto>,
    /** Extra insight: bills per IST hour today (0-23), for "busiest hour". */
    val hourCounts: List<Int> = emptyList(),
)

@Serializable
data class ReportData(
    val fromDay: String,
    val toDay: String,
    val total: Double,
    val subtotal: Double,
    val gst: Double,
    val billCount: Int,
    val avg: Double,
    val cash: Double,
    val card: Double,
    val upi: Double,
    val credit: Double,
    val items: List<ItemAggDto>,
    val expenseTotal: Double,
    val bills: List<BillRow>,
    /** Same-length previous period total, for the compare chip. */
    val prevTotal: Double? = null,
)

// ---------------- owner / account ----------------

@Serializable
data class RestaurantInfo(
    /** Empty for staff sessions — staff never see the license key. */
    val licenseKey: String,
    val name: String,
    val code: String? = null,
    /** License status (active/created/expired/...). Null on old cached rows. */
    val status: String? = null,
)

@Serializable
data class LicenseEmbed(
    val restaurant_name: String? = null,
    val restaurant_code: String? = null,
    val status: String? = null,
)

@Serializable
data class OwnerRestaurantRow(
    /** NULL for owners who signed up but haven't subscribed yet (migration 0002). */
    val license_key: String? = null,
    val licenses: LicenseEmbed? = null,
)

@Serializable
data class LicenseInfo(
    val key: String,
    val display_name: String? = null,
    val email: String? = null,
    val mobile_number: String? = null,
    val restaurant_name: String? = null,
    val restaurant_code: String? = null,
    val plan_id: String? = null,
    val status: String? = null,
    val next_billing_date: String? = null,
    val device_id: String? = null,
    val device_name: String? = null,
    val device_platform: String? = null,
    val device_bound_at: String? = null,
    val device_last_seen: String? = null,
    val created_at: String? = null,
)

@Serializable
data class PlanInfo(
    val id: String,
    val name: String? = null,
    val amount_paise: Long? = null,
    val interval_unit: String? = null,
    val description: String? = null,
)

@Serializable
data class AccountData(val license: LicenseInfo, val plan: PlanInfo? = null)

// ---------------- staff (owner-side management) ----------------

@Serializable
data class StaffRow(
    val id: String,
    val name: String,
    val role_label: String = "",
    val permissions: PermissionMap = emptyMap(),
    val is_active: Boolean = true,
    val created_at: String? = null,
    val last_login: String? = null,
)

@Serializable
data class StaffListData(val staff: List<StaffRow>, val restaurantCode: String? = null)

// ---------------- staff sessions + staff-data views ----------------

@Serializable
data class StaffIdentity(
    val id: String,
    val name: String,
    val roleLabel: String = "",
    val permissions: PermissionMap = emptyMap(),
)

@Serializable
data class StaffRestaurant(val code: String, val name: String)

@Serializable
data class StoredStaffSession(
    val token: String,
    val staff: StaffIdentity,
    val restaurant: StaffRestaurant,
)

@Serializable
data class StaffSplitDto(
    val kind: String, // "amounts" | "pct"
    val cash: Double = 0.0,
    val card: Double = 0.0,
    val upi: Double = 0.0,
    val credit: Double = 0.0,
)

@Serializable
data class StaffTrendPoint(val day: String, val value: Double)

@Serializable
data class StaffTopItem(val name: String, val quantity: Double, val amount: Double? = null)

@Serializable
data class StaffDashboard(
    val day: String,
    val billCount: Int,
    val total: Double? = null,
    val avg: Double? = null,
    val split: StaffSplitDto,
    val trend: List<StaffTrendPoint> = emptyList(),
    val trendIsRelative: Boolean = false,
    val topItems: List<StaffTopItem> = emptyList(),
)

@Serializable
data class StaffBillRow(
    val id: String,
    val bill_number: String? = null,
    val table_number: String? = null,
    val payment_mode: String? = null,
    val total: Double? = null,
    val billed_at: String,
)

@Serializable
data class StaffReport(
    val from: String,
    val to: String,
    val billCount: Int,
    val total: Double? = null,
    val subtotal: Double? = null,
    val gst: Double? = null,
    val avg: Double? = null,
    val split: StaffSplitDto,
    val items: List<StaffTopItem> = emptyList(),
    val expenseTotal: Double? = null,
    val bills: List<StaffBillRow>? = null,
)

@Serializable
data class StaffBillDetail(val bill: BillRow)

/**
 * Read-only plan/subscription view for staff who have `view_plan_status`.
 * Served by the staff-data `account` view (gated server-side); staff never
 * receive the license key or any billing controls. Forward-compatible: the
 * profile screen simply hides this section until the backend returns it.
 */
@Serializable
data class StaffPlanInfo(
    val planName: String? = null,
    val status: String? = null,
    val nextBillingDate: String? = null,
    val amountPaise: Long? = null,
    val intervalUnit: String? = null,
    val daysRemaining: Int? = null,
)

// ---------------- updates ----------------

@Serializable
data class UpdateInfo(
    val version: String,
    val apk_url: String,
    val release_notes: String? = null,
)
