package com.magicbill.app.ui.screens.more

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.magicbill.app.BuildConfig
import com.magicbill.app.nav.Bills
import com.magicbill.app.nav.Devices
import com.magicbill.app.nav.Expenses
import com.magicbill.app.nav.Khata
import com.magicbill.app.nav.Me
import com.magicbill.app.nav.Notices
import com.magicbill.app.nav.PairCounter
import com.magicbill.app.nav.Queue
import com.magicbill.app.nav.Staff
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.theme.IconSize
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.update.Updater

@Composable
private fun Entry(icon: ImageVector, title: String, subtitle: String? = null, badge: String? = null, onClick: () -> Unit) {
    ListRow(
        title, subtitle,
        leading = { Icon(icon, contentDescription = null, tint = Mb.colors.inkMuted, modifier = Modifier.size(IconSize.lg)) },
        trailing = {
            if (badge != null) Badge(badge, Tone.Info)
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Mb.colors.inkFaint)
        },
        onClick = onClick,
    )
    RowLine()
}

/** Everything that is not one of the four tabs: the shop's other screens, and this phone's. */
@Composable
fun MoreScreen(root: RootViewModel, nav: NavHostController) {
    val restaurant by root.restaurant.collectAsStateWithLifecycle()
    val cred by root.credential.collectAsStateWithLifecycle()
    val signedIn by root.signedIn.collectAsStateWithLifecycle()
    val unread by root.unread.collectAsStateWithLifecycle()
    val perms = restaurant?.permissions ?: emptySet()

    Page("More", restaurant?.name ?: cred?.shopName) {
        if (signedIn) {
            Section("The shop", first = true)
            if ("reports.view" in perms) Entry(Icons.Outlined.Receipt, "Bills") { nav.navigate(Bills) }
            if ("credit.collect" in perms) Entry(Icons.Outlined.MenuBook, "Khata", "Who owes the shop") { nav.navigate(Khata) }
            if ("reports.view" in perms) Entry(Icons.Outlined.ReceiptLong, "Expenses") { nav.navigate(Expenses) }
            if ("staff.manage" in perms) Entry(Icons.Outlined.Badge, "Staff and roles") { nav.navigate(Staff) }
            if (restaurant?.isOwner == true) Entry(Icons.Outlined.PhoneAndroid, "Phones and the counter") { nav.navigate(Devices) }
            Entry(Icons.Outlined.Notifications, "Notices", badge = if (unread > 0) unread.toString() else null) { nav.navigate(Notices) }
        }
        Section("This phone", first = !signedIn)
        if (cred != null) {
            Entry(Icons.Outlined.PendingActions, "Sending queue", "Orders on their way to the counter") { nav.navigate(Queue) }
            Entry(Icons.Outlined.Person, "Me at the counter", cred?.shopName) { nav.navigate(Me) }
        } else {
            Entry(Icons.Outlined.QrCodeScanner, "Connect to the counter", "Scan the code to take orders") { nav.navigate(PairCounter) }
        }
        // The app itself: which build this is, and the newer one on GitHub when there is one.
        val update by root.update.collectAsStateWithLifecycle()
        val newer = update.releaseOrNull?.takeIf { update is Updater.State.Available || update is Updater.State.Ready || update is Updater.State.Downloading }
        Entry(
            Icons.Outlined.SystemUpdate, "App update",
            subtitle = when {
                update is Updater.State.Downloading -> "Downloading v${newer?.name}…"
                newer != null -> "v${newer.name} is ready · you have ${BuildConfig.VERSION_NAME}"
                else -> "You have ${BuildConfig.VERSION_NAME} · tap to check GitHub"
            },
            badge = if (newer != null) "New" else null,
        ) { root.updater.checkNow() }
    }
}
