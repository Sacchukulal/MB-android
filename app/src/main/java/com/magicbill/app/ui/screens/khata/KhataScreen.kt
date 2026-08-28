package com.magicbill.app.ui.screens.khata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.core.Money
import com.magicbill.app.db.CustomerRow
import com.magicbill.app.db.LedgerRow
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.nav.CustomerDetail
import com.magicbill.app.ui.kit.Badge
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.KeyValue
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.Panel
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.SearchField
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Tone
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
import com.magicbill.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class KhataViewModel @Inject constructor(private val account: Account, private val sync: Sync, private val db: MbDatabase) : ViewModel() {
    private val query = MutableStateFlow("")
    val search: StateFlow<String> get() = query

    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerRow>> = query.flatMapLatest { q ->
        account.perShop(emptyList<CustomerRow>()) { r ->
            kotlinx.coroutines.flow.flow {
                db.khata().customers(r).collect { list ->
                    emit(list.filter { q.isBlank() || it.name.contains(q, true) || it.phone?.contains(q) == true }.sortedByDescending { it.balancePaise })
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearch(q: String) { query.value = q }
    fun opened() = sync.pullIfStale()
}

@Composable
fun KhataScreen(open: (String) -> Unit, vm: KhataViewModel = hiltViewModel()) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.opened() }
    val owed = customers.filter { it.balancePaise > 0 }.sumOf { it.balancePaise }
    val owing = customers.count { it.balancePaise > 0 }

    Page("Khata", "$owing owe you " + Money.rupees(owed), scroll = false, bottomPadding = 0.dp) {
        SearchField(search, vm::setSearch, "Name or phone")
        VGap(Gap.field)
        if (customers.isEmpty()) {
            Empty(if (search.isBlank()) "No credit customers yet. They are added at the counter." else "Nobody by that name.")
        } else {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.s7)) {
                items(customers, key = { it.id }) { c ->
                    ListRow(
                        c.name, c.phone,
                        trailing = {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(Money.rupees(kotlin.math.abs(c.balancePaise)), style = Mb.type.cell, color = if (c.balancePaise > 0) Mb.colors.ink else Mb.colors.inkMuted)
                                if (c.balancePaise > 0) Badge("owes", Tone.Warn) else if (c.balancePaise < 0) Badge("in credit", Tone.Ok) else Badge("settled")
                            }
                        },
                        onClick = { open(c.id) },
                    )
                    RowLine()
                }
            }
        }
    }
}

@HiltViewModel
class CustomerViewModel @Inject constructor(saved: SavedStateHandle, private val account: Account, private val db: MbDatabase, val clock: Clock) : ViewModel() {
    private val id: String = saved.toRoute<CustomerDetail>().id

    data class View(val customer: CustomerRow? = null, val ledger: List<LedgerRow> = emptyList())

    val view: StateFlow<View> = account.perShop(View()) { r ->
        combine(kotlinx.coroutines.flow.flow { emit(db.khata().customer(r, id)) }, db.khata().ledger(r, id)) { c, l -> View(c, l) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())
}

@Composable
fun CustomerScreen(back: () -> Unit, openBill: (String) -> Unit, vm: CustomerViewModel = hiltViewModel()) {
    val view by vm.view.collectAsStateWithLifecycle()
    val c = view.customer
    val today = Ist.today(vm.clock.now())
    Page(c?.name ?: "Customer", c?.phone, back = back) {
        if (c == null) { Empty("This customer is not on the phone yet."); return@Page }
        Panel {
            KeyValue(if (c.balancePaise >= 0) "Owes you" else "In credit", Money.rupees(kotlin.math.abs(c.balancePaise)), bold = true, valueColor = if (c.balancePaise > 0) Mb.colors.warn else null)
            c.creditLimitPaise?.let { KeyValue("Credit limit", Money.rupees(it)) }
            c.address?.takeIf { it.isNotBlank() }?.let { KeyValue("Address", it) }
            if (!c.isActive) KeyValue("Status", "Not active")
        }
        Section("Ledger")
        if (view.ledger.isEmpty()) Text("Nothing on the book yet.", style = Mb.type.body, color = Mb.colors.inkMuted)
        view.ledger.forEach { l ->
            val owed = l.amountPaise > 0
            ListRow(
                title = when (l.kind) { "bill", "credit" -> "Bill on credit"; "payment" -> "Payment received"; "adjustment" -> "Adjustment"; else -> l.kind.replaceFirstChar { it.uppercase() } },
                subtitle = listOf(Ist.moment(l.atMs, today), l.note).filter { it.isNotBlank() }.joinToString(" · "),
                trailing = { Text((if (owed) "+ " else "− ") + Money.rupees(kotlin.math.abs(l.amountPaise)), style = Mb.type.cell, color = if (owed) Mb.colors.warn else Mb.colors.ok) },
                onClick = l.billId?.let { { openBill(it) } },
            )
            RowLine()
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
