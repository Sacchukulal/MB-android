package com.magicbill.app.ui.screens

import com.magicbill.app.cloud.Account
import com.magicbill.app.core.Ist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A Room flow for whichever shop is on screen; empty when nobody is signed in. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Account.perShop(empty: T, block: (restaurantId: String) -> Flow<T>): Flow<T> =
    current.map { it?.id }.distinctUntilChanged().flatMapLatest { id -> if (id == null) flowOf(empty) else block(id) }

/** The ranges an owner picks from. "Custom" is added by the screen. */
object Ranges {
    const val CUSTOM = "Custom"
    val names = listOf("Today", "Yesterday", "7 days", "This week", "This month", "Last month", "30 days")

    fun of(name: String, today: LocalDate): Ist.Range = when (name) {
        "Today" -> Ist.today(today)
        "Yesterday" -> Ist.yesterday(today)
        "7 days" -> Ist.last7(today)
        "This week" -> Ist.thisWeek(today)
        "This month" -> Ist.thisMonth(today)
        "Last month" -> Ist.lastMonth(today)
        "30 days" -> Ist.last30(today)
        else -> Ist.today(today)
    }

    /** "12 Aug – 27 Aug 2026", or the day's own words. */
    fun words(range: Ist.Range, today: LocalDate): String =
        if (range.from == range.to) Ist.dateWords(range.from, today)
        else Ist.dateWords(range.from, today) + " – " + Ist.dateWords(range.to, today)
}
