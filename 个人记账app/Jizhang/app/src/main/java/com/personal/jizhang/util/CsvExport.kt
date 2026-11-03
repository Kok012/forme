package com.personal.jizhang.util

import com.personal.jizhang.model.BackupBundle
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExport {
    fun encode(bundle: BackupBundle, month: YearMonth): String {
        val names = bundle.categories.associate { it.id to it.name }
        return buildString {
            append('\uFEFF')
            append("消费日期,分类,支出金额（元）,备注,创建时间\r\n")
            LedgerDates.inMonth(bundle.records, month)
                .sortedWith(compareByDescending<com.personal.jizhang.model.Expense> { it.expenseDate }.thenByDescending { it.createdAt }.thenByDescending { it.id })
                .forEach { record ->
                    val time = Instant.ofEpochMilli(record.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    append(listOf(
                        quote(record.expenseDate), quote(names[record.categoryId].orEmpty(), protectFormula = true),
                        quote(Money.format(record.amountFen)), quote(record.note, protectFormula = true), quote(time),
                    ).joinToString(","))
                    append("\r\n")
                }
        }
    }

    private fun quote(value: String, protectFormula: Boolean = false): String {
        val dangerous = value.trimStart().firstOrNull() in setOf('=', '+', '-', '@') || value.firstOrNull() in setOf('\t', '\r', '\n')
        val safe = if (protectFormula && dangerous) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
}
