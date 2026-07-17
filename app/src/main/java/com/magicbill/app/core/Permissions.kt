package com.magicbill.app.core

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Staff permission keys — mirrors the owner-controlled jsonb map in the
 * `staff` table. A missing key always means "not allowed".
 */
enum class PermissionKey(val key: String) {
    ViewDashboard("view_dashboard"),
    ViewReports("view_reports"),
    ViewBills("view_bills"),
    ViewExpenses("view_expenses"),
    ViewPlanStatus("view_plan_status"),
    ManageStaff("manage_staff"),
    TakeOrders("take_orders"),
    ViewRevenueTotals("view_revenue_totals"),
    ExportReports("export_reports"),
}

/** Every permission — what an owner session gets. */
val AllPermissions: Set<String> = PermissionKey.entries.map { it.key }.toSet()

/**
 * The current session's granted permission keys. Owners get [AllPermissions];
 * staff get the set from their session. Consumed by MBPermissionGate.
 * Server-side checks remain authoritative — this only hides UI.
 */
val LocalPermissions = staticCompositionLocalOf<Set<String>> { emptySet() }
