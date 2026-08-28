package com.magicbill.app.ui.screens.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.Sync
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.db.RoleRow
import com.magicbill.app.db.StaffRow
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Chip
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.IconAction
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.QuietButton
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.SearchField
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The counter's vocabulary for a person, so a row saved here reads the same at the till. */
object StaffVocab {
    val statuses = listOf("active" to "Active", "suspended" to "Suspended", "left" to "Left")
    val employmentTypes = listOf("full_time" to "Full time", "part_time" to "Part time", "casual" to "Casual")
    fun statusWord(s: String) = statuses.firstOrNull { it.first == s }?.second ?: s.replaceFirstChar { it.uppercase() }
    fun typeWord(s: String) = employmentTypes.firstOrNull { it.first == s }?.second ?: s.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@HiltViewModel
class StaffViewModel @Inject constructor(private val account: Account, private val sync: Sync, private val db: MbDatabase) : ViewModel() {
    data class View(val staff: List<StaffRow> = emptyList(), val roles: List<RoleRow> = emptyList(), val mayManage: Boolean = false)

    private val query = MutableStateFlow("")
    val search: StateFlow<String> get() = query

    val view: StateFlow<View> = combine(
        account.perShop(Pair(emptyList<StaffRow>(), emptyList<RoleRow>())) { r -> combine(db.people().staff(r), db.people().roles(r)) { s, ro -> s to ro } },
        account.current, query,
    ) { (staff, roles), r, q ->
        val may = r?.isOwner == true || r?.may("staff.manage") == true
        val filtered = staff.filter { q.isBlank() || it.name.contains(q, true) || it.code?.contains(q, true) == true }
        View(filtered, roles, may)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    fun setSearch(q: String) { query.value = q }
    fun opened() { viewModelScope.launch { sync.pullNow(setOf("staff", "roles")) } }
}

@Composable
fun StaffScreen(back: () -> Unit, openMember: (String?) -> Unit, openRole: (String?) -> Unit, vm: StaffViewModel = hiltViewModel()) {
    val view by vm.view.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.opened() }
    val roleName = view.roles.associate { it.id to it.name }
    val active = view.staff.filter { it.status != "left" }
    val left = view.staff.filter { it.status == "left" }

    Page("Staff", "${active.size} on the team", back = back, actions = { if (view.mayManage) IconAction(Icons.Outlined.Add, "Add a person", { openMember(null) }) }) {
        SearchField(search, vm::setSearch, "Name or staff code")
        Section("Roles", trailing = { if (view.mayManage) QuietButton("New role", { openRole(null) }) })
        if (view.roles.isEmpty()) Text("Roles are made at the counter or here.", style = Mb.type.caption, color = Mb.colors.inkMuted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Gap.inline)) {
            view.roles.forEach { r -> Chip(r.name + " · " + active.count { it.roleId == r.id }, false, { openRole(r.id) }) }
        }
        Section("People")
        if (active.isEmpty()) Empty(if (search.isBlank()) "Nobody on the team yet." else "Nobody by that name.")
        active.forEach { s -> MemberRow(s, roleName[s.roleId], onClick = { openMember(s.id) }) }
        if (left.isNotEmpty()) {
            Section("Left")
            left.forEach { s -> MemberRow(s, roleName[s.roleId], onClick = { openMember(s.id) }) }
        }
    }
}

@Composable
private fun MemberRow(s: StaffRow, role: String?, onClick: () -> Unit) {
    ListRow(
        s.name,
        listOfNotNull(role, s.code, s.designation?.takeIf { it.isNotBlank() }).joinToString(" · "),
        trailing = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (s.status == "suspended") Badge("Suspended", Tone.Warn)
                if (s.canLoginOnPhone && s.status == "active") Badge("Phone", Tone.Info)
                if (s.isRider) Badge("Rider")
            }
        },
        onClick = onClick,
        titleColor = if (s.status == "left") Mb.colors.inkMuted else null,
    )
    RowLine()
}
