package com.magicbill.app.nav

import kotlinx.serialization.Serializable

/* Every screen, by name. A route carries an id, never a row. */

@Serializable object Welcome
@Serializable object OwnerSignIn
/** A new owner's account, made with the website's own sign-up route; the phone then signs in. */
@Serializable object OwnerSignUp
/** The one door for a staff phone, and the owner's door to the floor: scan the counter's code. */
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

@Serializable object Tables
/** [title] is what the floor already knows, so the page has its name before the database answers. */
@Serializable data class OrderScreen(val orderId: String, val title: String = "Order")
/** The order builder: a new order on a table (or parcel/delivery), or adding to an open one. */
@Serializable data class NewOrder(val tableId: String? = null, val tableLabel: String? = null, val orderType: String = "dine_in", val orderId: String? = null)
@Serializable object Queue
@Serializable object Me
