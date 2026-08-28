package com.magicbill.app.ui.screens.staff

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.People
import com.magicbill.app.core.Answer
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.db.RoleRow
import com.magicbill.app.db.StaffRow
import com.magicbill.app.nav.StaffEdit
import com.magicbill.app.ui.kit.ChipRow
import com.magicbill.app.ui.kit.DangerButton
import com.magicbill.app.ui.kit.Field
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PinField
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.SwitchRow
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/** One person: the same row the counter has. Saved through the cloud; back down on the next pull. */
@HiltViewModel
class StaffEditViewModel @Inject constructor(saved: SavedStateHandle, private val account: Account, private val people: People, private val db: MbDatabase) : ViewModel() {
    val id: String? = saved.toRoute<StaffEdit>().id

    data class Form(
        val name: String = "", val code: String = "", val phone: String = "", val roleId: String? = null, val status: String = "active",
        val designation: String = "", val department: String = "", val employmentType: String = "full_time", val joinedOn: String = "",
        val leftOn: String = "", val isRider: Boolean = false, val canLoginOnPhone: Boolean = false, val loaded: Boolean = false,
    )

    private val formFlow = MutableStateFlow(Form(loaded = id == null))
    val form: StateFlow<Form> get() = formFlow
    val roles: StateFlow<List<RoleRow>> = account.perShop(emptyList<RoleRow>()) { r -> db.people().roles(r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val stateFlow = MutableStateFlow<Pair<Boolean, String?>>(false to null) // busy, sentence
    val state: StateFlow<Pair<Boolean, String?>> get() = stateFlow

    init {
        if (id != null) viewModelScope.launch {
            val r = account.current.value?.id ?: return@launch
            db.people().member(r, id)?.let { s -> formFlow.value = fromRow(s) }
        }
    }

    private fun fromRow(s: StaffRow) = Form(
        s.name, s.code ?: "", s.phone ?: "", s.roleId, s.status, s.designation ?: "", s.department ?: "", s.employmentType.ifBlank { "full_time" },
        s.joinedOn ?: "", s.leftOn ?: "", s.isRider, s.canLoginOnPhone, loaded = true,
    )

    fun update(f: (Form) -> Form) { formFlow.value = f(formFlow.value) }

    fun save(done: () -> Unit) {
        val r = account.current.value?.id ?: return
        val f = formFlow.value
        if (f.name.isBlank()) { stateFlow.value = false to "A person needs a name."; return }
        stateFlow.value = true to null
        viewModelScope.launch {
            val body = buildJsonObject {
                if (id != null) put("id", id)
                put("name", f.name.trim()); put("code", f.code.trim().uppercase().ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("phone", f.phone.trim().ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("role_id", f.roleId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("status", f.status); put("designation", f.designation.trim()); put("department", f.department.trim())
                put("is_rider", f.isRider); put("employment_type", f.employmentType)
                put("joined_on", f.joinedOn.trim().ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("left_on", if (f.status == "left") f.leftOn.trim().ifBlank { java.time.LocalDate.now(com.magicbill.app.core.Ist.zone).toString() }.let { JsonPrimitive(it) } else JsonNull)
                put("can_login_on_phone", f.canLoginOnPhone)
            }
            when (val a = people.saveStaff(r, body)) {
                is Answer.Ok -> { stateFlow.value = false to null; done() }
                else -> stateFlow.value = false to a.sentenceOrNull
            }
        }
    }

    fun setPin(pin: String, onDone: (String) -> Unit) {
        val r = account.current.value?.id ?: return
        val who = id ?: return
        viewModelScope.launch {
            when (val a = people.setPin(r, who, pin)) {
                is Answer.Ok -> onDone("PIN set. It works at the counter after its next check.")
                else -> onDone(a.sentenceOrNull ?: "")
            }
        }
    }

    fun remove(done: () -> Unit) {
        val r = account.current.value?.id ?: return
        val who = id ?: return
        viewModelScope.launch {
            val row = db.people().member(r, who) ?: return@launch
            val body = buildJsonObject { put("id", who); put("name", row.name); put("status", "left"); put("deleted", true) }
            when (val a = people.saveStaff(r, body)) {
                is Answer.Ok -> done()
                else -> stateFlow.value = false to a.sentenceOrNull
            }
        }
    }

    suspend fun roleName(id: String?): String? = id?.let { rid -> roles.first().firstOrNull { it.id == rid }?.name }
}

@Composable
fun StaffEditScreen(back: () -> Unit, vm: StaffEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val roles by vm.roles.collectAsStateWithLifecycle()
    val (busy, sentence) = vm.state.collectAsStateWithLifecycle().value
    val reporter = LocalReporter.current
    var pinSheet by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    Page(if (vm.id == null) "New person" else f.name.ifBlank { "Person" }, back = back) {
        if (!f.loaded) { Text("Loading…", style = Mb.type.caption, color = Mb.colors.inkMuted); return@Page }
        VGap(Gap.field)
        Field(f.name, { v -> vm.update { it.copy(name = v) } }, "Name")
        VGap(Gap.field)
        Field(f.code, { v -> vm.update { it.copy(code = v.uppercase().take(12)) } }, "Staff code", placeholder = "RAVI", capitalise = true)
        VGap(Gap.field)
        Field(f.phone, { v -> vm.update { it.copy(phone = v.filter { c -> c.isDigit() }.take(10)) } }, "Mobile", keyboard = KeyboardType.Phone, placeholder = "10 digits")
        Section("Role")
        if (roles.isEmpty()) Text("Make a role first.", style = Mb.type.caption, color = Mb.colors.inkMuted)
        ChipRow(roles.map { it.name }, roles.firstOrNull { it.id == f.roleId }?.name ?: "") { picked -> vm.update { it.copy(roleId = roles.first { r -> r.name == picked }.id) } }
        Section("Employment")
        ChipRow(StaffVocab.statuses.map { it.second }, StaffVocab.statusWord(f.status)) { picked -> vm.update { it.copy(status = StaffVocab.statuses.first { s -> s.second == picked }.first) } }
        VGap(Gap.field)
        ChipRow(StaffVocab.employmentTypes.map { it.second }, StaffVocab.typeWord(f.employmentType)) { picked -> vm.update { it.copy(employmentType = StaffVocab.employmentTypes.first { s -> s.second == picked }.first) } }
        VGap(Gap.field)
        Field(f.designation, { v -> vm.update { it.copy(designation = v) } }, "Designation", placeholder = "Waiter, Cook, Manager")
        VGap(Gap.field)
        Field(f.department, { v -> vm.update { it.copy(department = v) } }, "Department", placeholder = "Kitchen, Floor")
        VGap(Gap.field)
        Field(f.joinedOn, { v -> vm.update { it.copy(joinedOn = v.take(10)) } }, "Joined on", placeholder = "2026-08-01", keyboard = KeyboardType.Number)
        if (f.status == "left") {
            VGap(Gap.field)
            Field(f.leftOn, { v -> vm.update { it.copy(leftOn = v.take(10)) } }, "Left on", placeholder = "today", keyboard = KeyboardType.Number)
        }
        SwitchRow("Delivers orders (rider)", null, f.isRider) { v -> vm.update { it.copy(isRider = v) } }
        Section("The phone")
        SwitchRow("Can sign in on a phone", "With the shop code, their staff code and PIN", f.canLoginOnPhone) { v -> vm.update { it.copy(canLoginOnPhone = v) } }
        if (vm.id != null) {
            VGap(Gap.field)
            SecondaryButton("Set a new PIN", { pinSheet = true }, Modifier.fillMaxWidth())
        }
        VGap(Gap.group)
        if (sentence != null) { Notice(Tone.Danger, sentence); VGap(Gap.field) }
        PrimaryButton("Save", { vm.save { reporter.say("Saved. The counter has it on its next check."); back() } }, Modifier.fillMaxWidth(), busy = busy)
        if (vm.id != null) {
            VGap(Gap.field)
            DangerButton("Remove from the shop", { removing = true }, Modifier.fillMaxWidth())
        }
    }

    if (pinSheet) {
        var pin by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        Sheet("New PIN for ${f.name}", onDismiss = { pinSheet = false }) {
            Text("The same PIN opens the counter and the phone. It is never sent anywhere — only its hash is.", style = Mb.type.caption, color = Mb.colors.inkMuted)
            VGap(Gap.group)
            Text("PIN", style = Mb.type.label, color = Mb.colors.inkMuted); VGap(Gap.inline)
            PinField(pin, { pin = it })
            VGap(Gap.field)
            Text("Again", style = Mb.type.label, color = Mb.colors.inkMuted); VGap(Gap.inline)
            PinField(again, { again = it })
            VGap(Gap.group)
            if (pin.length == 4 && again.length == 4 && pin != again) Notice(Tone.Danger, "The two PINs are not the same.")
            PrimaryButton("Set the PIN", { pinSheet = false; vm.setPin(pin) { reporter.say(it) } }, Modifier.fillMaxWidth(), enabled = pin.length == 4 && pin == again)
        }
    }

    if (removing) {
        Sheet("Remove ${f.name}?", onDismiss = { removing = false }) {
            Text("They are marked as left and can no longer sign in anywhere. Their bills and history stay.", style = Mb.type.body, color = Mb.colors.inkMuted)
            VGap(Gap.group)
            DangerButton("Remove", { removing = false; vm.remove { reporter.say("Removed."); back() } }, Modifier.fillMaxWidth())
            VGap(Gap.field)
            SecondaryButton("Keep", { removing = false }, Modifier.fillMaxWidth())
        }
    }
}
