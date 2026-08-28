package com.magicbill.app.ui.screens.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.magicbill.app.cloud.Account
import com.magicbill.app.cloud.ReportMath
import com.magicbill.app.cloud.Sync
import com.magicbill.app.core.Clock
import com.magicbill.app.core.Ist
import com.magicbill.app.core.Money
import com.magicbill.app.db.CashMovementRow
import com.magicbill.app.db.ExpenseRow
import com.magicbill.app.db.MbDatabase
import com.magicbill.app.ui.kit.ChipRow
import com.magicbill.app.ui.kit.Empty
import com.magicbill.app.ui.kit.ListRow
import com.magicbill.app.ui.kit.Page
import com.magicbill.app.ui.kit.RowLine
import com.magicbill.app.ui.kit.Section
import com.magicbill.app.ui.kit.Stat
import com.magicbill.app.ui.kit.VGap
import com.magicbill.app.ui.screens.Ranges
import com.magicbill.app.ui.screens.perShop
import com.magicbill.app.ui.theme.Gap
import com.magicbill.app.ui.theme.Mb
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
class ExpensesViewModel @Inject constructor(private val account: Account, private val sync: Sync, private val db: MbDatabase, private val clock: Clock) : ViewModel() {
    private val chosen = MutableStateFlow("This month")
    val choice: StateFlow<String> get() = chosen
    val today get() = Ist.today(clock.now())

    data class View(val expenses: List<ExpenseRow> = emptyList(), val groups: List<ReportMath.ExpenseGroup> = emptyList(), val cash: List<CashMovementRow> = emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val view: StateFlow<View> = chosen.flatMapLatest { c ->
        val rg = Ranges.of(c, today)
        account.perShop(View()) { r ->
            combine(db.expenses().between(r, Ist.key(rg.from), Ist.key(rg.to)), db.cash().between(r, Ist.key(rg.from), Ist.key(rg.to))) { e, cash ->
                View(e, ReportMath.expensesByCategory(e), cash)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), View())

    fun pick(name: String) { chosen.value = name }
    fun opened() = sync.pullIfStale()
}

@Composable
fun ExpensesScreen(back: () -> Unit, vm: ExpensesViewModel = hiltViewModel()) {
    val view by vm.view.collectAsStateWithLifecycle()
    val choice by vm.choice.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.opened() }
    val total = view.expenses.sumOf { it.amountPaise }
    val cashIn = view.cash.filter { it.amountPaise > 0 }.sumOf { it.amountPaise }
    val cashOut = view.cash.filter { it.amountPaise < 0 }.sumOf { -it.amountPaise }

    Page("Expenses", Ranges.words(Ranges.of(choice, vm.today), vm.today), back = back) {
        ChipRow(Ranges.names, choice) { vm.pick(it) }
        VGap(Gap.group)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.field)) {
            Stat("Spent", Money.whole(total), Modifier.weight(1f), sub = "${view.expenses.size} entries")
            Stat("Cash in", Money.whole(cashIn), Modifier.weight(1f))
            Stat("Cash out", Money.whole(cashOut), Modifier.weight(1f))
        }
        if (view.groups.isNotEmpty()) {
            Section("By category")
            view.groups.forEach { g ->
                ListRow(g.category, "${g.count} entries", trailing = { Text(Money.rupees(g.paise), style = Mb.type.cell, color = Mb.colors.ink) })
                RowLine()
            }
        }
        Section("Entries")
        if (view.expenses.isEmpty()) Text("No expenses recorded in these days.", style = Mb.type.body, color = Mb.colors.inkMuted)
        view.expenses.forEach { e ->
            ListRow(e.note.ifBlank { e.categoryName }, listOf(Ist.parseDay(e.businessDay)?.let { Ist.dateWords(it, vm.today) } ?: e.businessDay, e.categoryName).joinToString(" · "), trailing = { Text(Money.rupees(e.amountPaise), style = Mb.type.cell, color = Mb.colors.ink) })
            RowLine()
        }
        if (view.cash.isNotEmpty()) {
            Section("Drawer movements")
            view.cash.forEach { m ->
                ListRow(m.note.ifBlank { m.kind.replace('_', ' ').replaceFirstChar { it.uppercase() } }, Ist.parseDay(m.businessDay)?.let { Ist.dateWords(it, vm.today) } ?: m.businessDay, trailing = { Text((if (m.amountPaise >= 0) "+ " else "− ") + Money.rupees(kotlin.math.abs(m.amountPaise)), style = Mb.type.cell, color = if (m.amountPaise >= 0) Mb.colors.ok else Mb.colors.ink) })
                RowLine()
            }
        }
        if (view.expenses.isEmpty() && view.cash.isEmpty()) { VGap(Gap.group); Empty("Expenses are entered at the counter and show here.") }
    }
}
