package com.magicbill.app.ui.screens.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.magicbill.app.nav.AccountScreen
import com.magicbill.app.nav.Appearance
import com.magicbill.app.nav.Bills
import com.magicbill.app.nav.Devices
import com.magicbill.app.nav.Expenses
import com.magicbill.app.nav.Home
import com.magicbill.app.nav.Khata
import com.magicbill.app.nav.Me
import com.magicbill.app.nav.Notices
import com.magicbill.app.nav.PairCounter
import com.magicbill.app.nav.Queue
import com.magicbill.app.nav.Reports
import com.magicbill.app.nav.Staff
import com.magicbill.app.nav.Tables
import com.magicbill.app.ui.RootViewModel
import com.magicbill.app.ui.Tab
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.theme.IconSize
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.ThemeController
import com.magicbill.app.ui.kit.ChipRow
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import androidx.compose.foundation.layout.size
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

/** Everything that did not fit on the bar, and the phone's own things. */
@Composable
fun MoreScreen(root: RootViewModel, nav: NavHostController) {
    val restaurant by root.restaurant.collectAsStateWithLifecycle()
    val cred by root.credential.collectAsStateWithLifecycle()
    val signedIn by root.signedIn.collectAsStateWithLifecycle()
    val overflow by root.overflow.collectAsStateWithLifecycle()
    val unread by root.unread.collectAsStateWithLifecycle()
    val perms = restaurant?.permissions ?: emptySet()

    Page("More", restaurant?.name ?: cred?.shopName) {
        if (overflow.isNotEmpty()) {
            Section("Also here", first = true)
            overflow.forEach { tab ->
                when (tab) {
                    Tab.Home -> Entry(Icons.Outlined.Home, "Home") { nav.navigate(Home) }
                    Tab.Tables -> Entry(Icons.Outlined.TableRestaurant, "Tables") { nav.navigate(Tables) }
                    Tab.Reports -> Entry(Icons.Outlined.Assessment, "Reports") { nav.navigate(Reports) }
                    Tab.Bills -> Entry(Icons.Outlined.Receipt, "Bills") { nav.navigate(Bills) }
                    Tab.Khata -> Entry(Icons.Outlined.MenuBook, "Khata") { nav.navigate(Khata) }
                    Tab.Queue -> Entry(Icons.Outlined.PendingActions, "Queue") { nav.navigate(Queue) }
                    Tab.Account -> Entry(Icons.Outlined.AccountCircle, "Account") { nav.navigate(AccountScreen) }
                    Tab.More -> {}
                }
            }
        }
        if (signedIn) {
            Section("The shop", first = overflow.isEmpty())
            if ("phone.reports" in perms) Entry(Icons.Outlined.ReceiptLong, "Expenses") { nav.navigate(Expenses) }
            if ("phone.staff" in perms || "staff.manage" in perms) Entry(Icons.Outlined.Badge, "Staff and roles") { nav.navigate(Staff) }
            if (restaurant?.isOwner == true) Entry(Icons.Outlined.PhoneAndroid, "Phones and the counter") { nav.navigate(Devices) }
            Entry(Icons.Outlined.Notifications, "Notices", badge = if (unread > 0) unread.toString() else null) { nav.navigate(Notices) }
            Entry(Icons.Outlined.AccountCircle, "Account", restaurant?.let { "Shop code ${it.shortCode}" }) { nav.navigate(AccountScreen) }
        }
        Section("This phone", first = !signedIn && overflow.isEmpty())
        if (cred != null) Entry(Icons.Outlined.Person, "Me at the counter", cred?.shopName) { nav.navigate(Me) }
        else Entry(Icons.Outlined.WifiTethering, "Connect to the counter", "Take orders at the tables") { nav.navigate(PairCounter) }
        Entry(Icons.Outlined.Palette, "Appearance") { nav.navigate(Appearance) }
        if (!signedIn) Entry(Icons.Outlined.AccountCircle, "Sign in to the shop's account", "Reports, bills, staff") { nav.navigate(com.magicbill.app.nav.Welcome) }
    }
}

@HiltViewModel
class AppearanceViewModel @Inject constructor(val theme: ThemeController) : ViewModel()

@Composable
fun AppearanceScreen(back: () -> Unit, vm: AppearanceViewModel = hiltViewModel()) {
    val mode by vm.theme.mode.collectAsStateWithLifecycle()
    val scale by vm.theme.textScale.collectAsStateWithLifecycle()
    val modes = listOf("Follow the phone" to ThemeController.SYSTEM, "Light" to ThemeController.LIGHT, "Dark" to ThemeController.DARK)
    Page("Appearance", back = back) {
        Section("Theme", first = true)
        ChipRow(modes.map { it.first }, modes.first { it.second == mode }.first) { picked -> vm.theme.setMode(modes.first { it.first == picked }.second) }
        Section("Text size")
        val sizes = listOf("Normal" to 1f, "Larger" to 1.15f, "Largest" to 1.3f)
        ChipRow(sizes.map { it.first }, sizes.minBy { kotlin.math.abs(it.second - scale) }.first) { picked -> vm.theme.setTextScale(sizes.first { it.first == picked }.second) }
        VGap(Gap.group)
        Column(Modifier.fillMaxWidth()) {
            Text("The quick brown fox jumps over the lazy dog.", style = Mb.type.body, color = Mb.colors.ink)
            Text("₹12,34,567.89", style = Mb.type.hero, color = Mb.colors.ink)
        }
    }
}
