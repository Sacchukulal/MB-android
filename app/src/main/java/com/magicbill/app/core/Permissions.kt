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

/** Owner-facing labels for the staff permission editor. */
data class PermissionMeta(
    val key: PermissionKey,
    val label: String,
    val description: String,
    val comingSoon: Boolean = false,
)

val PERMISSION_METAS: List<PermissionMeta> = listOf(
    PermissionMeta(
        PermissionKey.ViewDashboard, "View today's sales summary",
        "See the dashboard with today's totals and trends",
    ),
    PermissionMeta(
        PermissionKey.ViewReports, "View detailed reports",
        "Date-range reports, item-wise sales, payment breakdowns",
    ),
    PermissionMeta(
        PermissionKey.ViewBills, "View individual bills",
        "Open any bill and see its full receipt",
    ),
    PermissionMeta(
        PermissionKey.ViewExpenses, "View expenses",
        "See recorded expense data",
    ),
    PermissionMeta(
        PermissionKey.ViewPlanStatus, "View plan & subscription",
        "See the restaurant's Magic Bill plan and billing status",
    ),
    PermissionMeta(
        PermissionKey.ManageStaff, "Manage other staff",
        "Add, edit, or deactivate staff members (trusted managers)",
    ),
    PermissionMeta(
        PermissionKey.TakeOrders, "Take orders",
        "Take customer orders from this app",
        comingSoon = true,
    ),
    PermissionMeta(
        PermissionKey.ViewRevenueTotals, "See revenue amounts",
        "Show actual rupee amounts (off = counts and % only)",
    ),
    PermissionMeta(
        PermissionKey.ExportReports, "Export & share reports",
        "Download or share reports as PDF/CSV",
    ),
)

/** Quick-setup presets — they pre-fill the toggles; the owner can customize. */
val PERMISSION_PRESETS: List<Pair<String, Map<String, Boolean>>> = listOf(
    "Full access" to PermissionKey.entries.associate { it.key to true },
    "Reports only" to mapOf(
        PermissionKey.ViewDashboard.key to true,
        PermissionKey.ViewReports.key to true,
        PermissionKey.ViewBills.key to true,
        PermissionKey.ViewRevenueTotals.key to true,
    ),
    "Minimal" to mapOf(PermissionKey.ViewDashboard.key to true),
)
