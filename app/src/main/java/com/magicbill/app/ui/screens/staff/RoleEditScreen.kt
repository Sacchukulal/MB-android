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
import com.magicbill.app.cloud.PermissionCode
import com.magicbill.app.core.Answer
import com.magicbill.app.core.parseJsonOrNull
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.nav.RoleEdit
import com.magicbill.app.ui.kit.DangerButton
import com.magicbill.app.ui.kit.Field
import com.magicbill.app.ui.kit.LocalReporter
import com.magicbill.app.ui.kit.Notice
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.PrimaryButton
import com.magicbill.app.ui.kit.SecondaryButton
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Sheet
import com.magicbill.app.ui.kit.SwitchRow
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * A role: a name and the permissions, in the counter's own words. The list of what a
 * permission is comes from the cloud's `permissions` table, so the phone never invents one.
 */
@HiltViewModel
class RoleEditViewModel @Inject constructor(saved: SavedStateHandle, private val account: Account, private val people: People, private val db: MbDatabase) : ViewModel() {
    val id: String? = saved.toRoute<RoleEdit>().id

    data class Form(
        val name: String = "", val permissions: Set<String> = emptySet(), val maxDiscountPercent: String = "", val maxDiscountRupees: String = "",
        val isBuiltin: Boolean = false, val loaded: Boolean = false,
    )

    private val formFlow = MutableStateFlow(Form(loaded = id == null))
    val form: StateFlow<Form> get() = formFlow
    private val codesFlow = MutableStateFlow<List<PermissionCode>>(emptyList())
    val codes: StateFlow<List<PermissionCode>> get() = codesFlow
    private val stateFlow = MutableStateFlow<Pair<Boolean, String?>>(false to null)
    val state: StateFlow<Pair<Boolean, String?>> get() = stateFlow

    init {
        viewModelScope.launch {
            when (val a = people.permissionCodes()) {
                is Answer.Ok -> codesFlow.value = a.value
                else -> stateFlow.value = false to a.sentenceOrNull
            }
            if (id != null) {
                val r = account.current.value?.id ?: return@launch
                db.people().roles(r).first().firstOrNull { it.id == id }?.let { row ->
                    val perms = (parseJsonOrNull(row.permissions) as? JsonArray)?.mapNotNull { p -> (p as? JsonPrimitive)?.content }?.toSet() ?: emptySet()
                    formFlow.value = Form(
                        row.name, perms,
                        row.maxDiscountBp?.let { (it / 100).toString() } ?: "", row.maxDiscountPaise?.let { (it / 100).toString() } ?: "", row.isBuiltin, loaded = true,
                    )
                }
            }
        }
    }

    fun update(f: (Form) -> Form) { formFlow.value = f(formFlow.value) }
    fun toggle(code: String) = update { it.copy(permissions = if (code in it.permissions) it.permissions - code else it.permissions + code) }

    fun save(done: () -> Unit) {
        val r = account.current.value?.id ?: return
        val f = formFlow.value
        if (f.name.isBlank()) { stateFlow.value = false to "A role needs a name."; return }
        stateFlow.value = true to null
        viewModelScope.launch {
            val body = buildJsonObject {
                if (id != null) put("id", id)
                put("name", f.name.trim())
                put("max_discount_bp", f.maxDiscountPercent.toIntOrNull()?.let { JsonPrimitive(it * 100) } ?: JsonNull)
                put("max_discount_paise", f.maxDiscountRupees.toLongOrNull()?.let { JsonPrimitive(it * 100) } ?: JsonNull)
                put("permissions", JsonArray(f.permissions.sorted().map { JsonPrimitive(it) }))
            }
            when (val a = people.saveRole(r, body)) {
                is Answer.Ok -> { stateFlow.value = false to null; done() }
                else -> stateFlow.value = false to a.sentenceOrNull
            }
        }
    }

    fun remove(done: () -> Unit) {
        val r = account.current.value?.id ?: return
        val who = id ?: return
        viewModelScope.launch {
            when (val a = people.saveRole(r, buildJsonObject { put("id", who); put("name", formFlow.value.name); put("permissions", JsonArray(emptyList())); put("deleted", true) })) {
                is Answer.Ok -> done()
                else -> stateFlow.value = false to a.sentenceOrNull
            }
        }
    }
}

@Composable
fun RoleEditScreen(back: () -> Unit, vm: RoleEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val codes by vm.codes.collectAsStateWithLifecycle()
    val (busy, sentence) = vm.state.collectAsStateWithLifecycle().value
    val reporter = LocalReporter.current
    var removing by remember { mutableStateOf(false) }

    Page(if (vm.id == null) "New role" else f.name.ifBlank { "Role" }, back = back) {
        if (!f.loaded) { Text("Loading…", style = Mb.type.caption, color = Mb.colors.inkMuted); return@Page }
        VGap(Gap.field)
        Field(f.name, { v -> vm.update { it.copy(name = v) } }, "Role name", placeholder = "Waiter, Cashier, Manager", enabled = !f.isBuiltin)
        Section("Discount they may give")
        Field(f.maxDiscountPercent, { v -> vm.update { it.copy(maxDiscountPercent = v.filter { c -> c.isDigit() }.take(3)) } }, "Up to, percent", keyboard = KeyboardType.Number, placeholder = "10")
        VGap(Gap.field)
        Field(f.maxDiscountRupees, { v -> vm.update { it.copy(maxDiscountRupees = v.filter { c -> c.isDigit() }.take(7)) } }, "Up to, rupees on one bill", keyboard = KeyboardType.Number, placeholder = "200", trailing = { Text("₹", style = Mb.type.body, color = Mb.colors.inkMuted) })
        val counter = codes.filter { it.scope == "counter" || it.scope == "both" }
        val phone = codes.filter { it.scope == "phone" }
        if (codes.isEmpty()) { Section("Permissions"); Text("Could not fetch the list of permissions. Pull down on Staff and try again.", style = Mb.type.caption, color = Mb.colors.inkMuted) }
        if (phone.isNotEmpty()) {
            Section("On the phone")
            phone.forEach { p -> SwitchRow(p.name, p.code, p.code in f.permissions) { vm.toggle(p.code) } }
        }
        if (counter.isNotEmpty()) {
            Section("At the counter")
            counter.forEach { p -> SwitchRow(p.name, p.code, p.code in f.permissions) { vm.toggle(p.code) } }
        }
        VGap(Gap.group)
        if (sentence != null) { Notice(Tone.Danger, sentence); VGap(Gap.field) }
        PrimaryButton("Save", { vm.save { reporter.say("Saved. The counter has it on its next check."); back() } }, Modifier.fillMaxWidth(), busy = busy)
        if (vm.id != null && !f.isBuiltin) {
            VGap(Gap.field)
            DangerButton("Delete this role", { removing = true }, Modifier.fillMaxWidth())
        }
    }

    if (removing) {
        Sheet("Delete ${f.name}?", onDismiss = { removing = false }) {
            Text("People in this role keep working but have no role until you give them one.", style = Mb.type.body, color = Mb.colors.inkMuted)
            VGap(Gap.group)
            DangerButton("Delete", { removing = false; vm.remove { reporter.say("Deleted."); back() } }, Modifier.fillMaxWidth())
            VGap(Gap.field)
            SecondaryButton("Keep", { removing = false }, Modifier.fillMaxWidth())
        }
    }
}
