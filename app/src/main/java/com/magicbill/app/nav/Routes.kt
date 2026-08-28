package com.magicbill.app.nav

import kotlinx.serialization.Serializable

/* Every screen, by name. A route carries an id, never a row. */

@Serializable object Welcome
@Serializable object OwnerSignIn
@Serializable object StaffSignIn
@Serializable object PairCounter

@Serializable object Home
@Serializable object Reports
@Serializable object Bills
@Serializable data class BillDetail(val id: String)
@Serializable object Khata
@Serializable data class CustomerDetail(val id: String)
@Serializable object Expenses
@Serializable object Staff
@Serializable data class StaffEdit(val id: String? = null)
@Serializable data class RoleEdit(val id: String? = null)
@Serializable object Devices
@Serializable object Notices
@Serializable object AccountScreen
@Serializable object More
@Serializable object Appearance

@Serializable object Tables
@Serializable data class OrderScreen(val orderId: String)
/** The order builder: a new order on a table (or parcel/delivery), or adding to an open one. */
@Serializable data class NewOrder(val tableId: String? = null, val tableLabel: String? = null, val orderType: String = "dine_in", val orderId: String? = null)
@Serializable object Queue
@Serializable object Me
