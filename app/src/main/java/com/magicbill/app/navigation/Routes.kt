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

/**
 * Order builder: new order when [orderClientUuid] is null (using the given
 * type/table/section), otherwise "add items" to that open order.
 */
@Serializable
data class OrderBuilderRoute(
    val orderClientUuid: String? = null,
    val orderType: String = "Table",
    val tableNumber: String = "",
    val section: String = "",
)

/** Live order detail, pushed over either shell. */
@Serializable
data class OrderDetailRoute(val clientUuid: String)
