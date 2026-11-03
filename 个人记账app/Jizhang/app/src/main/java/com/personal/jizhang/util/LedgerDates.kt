package com.personal.jizhang.util

import com.personal.jizhang.model.Expense
import java.time.LocalDate
import java.time.YearMonth

object LedgerDates {
    private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    fun parseDate(value: String): LocalDate? = if (!datePattern.matches(value)) null else
        runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { it.year in 1..9999 }

    fun inMonth(records: List<Expense>, month: YearMonth): List<Expense> {
        val start = month.atDay(1).toString()
        val end = month.plusMonths(1).atDay(1).toString()
        return records.filter { it.expenseDate >= start && it.expenseDate < end }
    }

    fun dayTotals(records: List<Expense>, month: YearMonth, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Long>> {
        val totals = inMonth(records, month).groupBy { it.expenseDate }.mapValues { (_, rows) -> rows.sumOf { it.amountFen } }
        val endDay = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month > YearMonth.from(today) -> 0
            else -> month.lengthOfMonth()
        }
        return (1..endDay).map { month.atDay(it).let { date -> date to (totals[date.toString()] ?: 0L) } }
    }
}
