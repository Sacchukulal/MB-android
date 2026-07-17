package com.magicbill.app.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation routes.

@Serializable
data object WelcomeRoute

@Serializable
data object OwnerLoginRoute

@Serializable
data object StaffLoginRoute

/** Owner tab shell (Dashboard/Reports/Staff/Account behind the pill bar). */
@Serializable
data object OwnerTabsRoute

/** Staff tab shell; visible tabs depend on the session's permissions. */
@Serializable
data object StaffTabsRoute

/** Full-screen receipt view, pushed over either shell. */
@Serializable
data class BillDetailRoute(val billId: String)
