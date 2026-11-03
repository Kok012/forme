package com.personal.jizhang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.Expense
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.ui.components.CategoryGlyph
import com.personal.jizhang.ui.components.MonthPickerDialog
import com.personal.jizhang.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal fun monthRecords(records: List<Expense>, month: YearMonth): List<Expense> {
    val start = month.atDay(1).toString()
    val end = month.plusMonths(1).atDay(1).toString()
    return records.filter { it.expenseDate >= start && it.expenseDate < end }
}

@Composable
internal fun MonthBar(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, enabled = month.year > 1900 || month.monthValue > 1) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月")
            }
            TextButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) {
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month.year < 2100 || month.monthValue < 12) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月")
            }
        }
        if (month != YearMonth.now()) TextButton(onClick = { onMonth(YearMonth.now()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("回到本月") }
    }
    if (picker) MonthPickerDialog(month, { picker = false }, { onMonth(it); picker = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryCard(total: Long, count: Int, month: YearMonth, todayTotal: Long? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (month == YearMonth.now()) "本月支出" else "${month.monthValue}月支出", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("¥ ${Money.format(total)}", style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.horizontalScroll(rememberScrollState()), maxLines = 1)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("共 $count 笔", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (todayTotal != null) Text("今日 ¥${Money.format(todayTotal)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
internal fun EmptyLedger(title: String, subtitle: String, onAdd: () -> Unit, hasFilter: Boolean = false, onClear: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, Modifier.padding(24.dp).size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hasFilter) OutlinedButton(onClick = onClear) { Text("清除筛选") }
        else FilledTonalButton(onClick = onAdd) { Text("记下第一笔") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LedgerScreen(
    data: LedgerData,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    selectedCategoryId: String?,
    onCategory: (String?) -> Unit,
    selectedDay: String?,
    onDay: (String?) -> Unit,
    onRecord: (String) -> Unit,
    onAdd: () -> Unit,
    state: LazyListState,
    fromStatistics: Boolean,
    onReturnStatistics: () -> Unit,
) {
    val records = remember(data.records, month) { monthRecords(data.records, month) }
    val categories = remember(data.categories) { data.categories.associateBy { it.id } }
    val filtered = remember(records, selectedCategoryId, selectedDay) {
        records.filter { (selectedCategoryId == null || it.categoryId == selectedCategoryId) && (selectedDay == null || it.expenseDate == selectedDay) }
            .sortedWith(compareByDescending<Expense> { it.expenseDate }.thenByDescending { it.createdAt }.thenByDescending { it.id })
    }
    val groups = remember(filtered) { filtered.groupBy { it.expenseDate } }
    val selectedCategory = categories[selectedCategoryId]
    var filterSheet by remember { mutableStateOf(false) }
    val hasFilter = selectedCategoryId != null || selectedDay != null
    LazyColumn(modifier = Modifier.testTag("ledger-list"), state = state, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "month") { MonthBar(month, onMonth) }
        if (!data.loaded) item { Box(Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else {
            item(key = "summary") {
                SummaryCard(records.sumOf { it.amountFen }, records.size, month,
                    if (month == YearMonth.now()) records.filter { it.expenseDate == LocalDate.now().toString() }.sumOf { it.amountFen } else null)
            }
            if (fromStatistics) item { TextButton(onClick = onReturnStatistics, contentPadding = PaddingValues(horizontal = 0.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("返回月度统计") } }
            item(key = "filters") {
                Column {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedCategoryId != null,
                            onClick = { filterSheet = true },
                            label = { Text(selectedCategory?.name ?: "全部分类") },
                            trailingIcon = { Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) },
                        )
                        if (selectedDay != null) InputChip(
                            selected = true, onClick = { onDay(null) },
                            label = { Text(LocalDate.parse(selectedDay).format(DateTimeFormatter.ofPattern("M月d日"))) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "清除日期筛选", Modifier.size(16.dp)) },
                        )
                        if (selectedCategoryId != null && selectedDay == null) TextButton(onClick = { onCategory(null) }) { Text("清除") }
                    }
                    if (hasFilter) Text(
                        "筛选合计 ¥${Money.format(filtered.sumOf { it.amountFen })} · ${filtered.size}笔",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (filtered.isEmpty()) item {
                EmptyLedger(
                    if (hasFilter) "没有符合条件的消费" else "这个月还没有记录",
                    if (hasFilter) "可以换个分类或日期看看" else "从第一笔消费开始，慢慢了解日常开销",
                    onAdd, hasFilter, { onCategory(null); onDay(null) },
                )
            }
            groups.forEach { (date, expenses) ->
                item(key = "date:$date") {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 1.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¥${Money.format(expenses.sumOf { it.amountFen })}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(expense, categories[expense.categoryId], onRecord)
                }
            }
        }
    }
    if (filterSheet) ModalBottomSheet(onDismissRequest = { filterSheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.heightIn(max = 520.dp)) {
            item { Text("选择消费分类", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
            item {
                ListItem(
                    headlineContent = { Text("全部分类") },
                    leadingContent = { Icon(Icons.Outlined.Apps, null) },
                    trailingContent = { RadioButton(selectedCategoryId == null, onClick = null) },
                    modifier = Modifier.clickable { onCategory(null); filterSheet = false },
                )
            }
            items(data.categories.filter { !it.isArchived || records.any { record -> record.categoryId == it.id } }.sortedBy { it.sortOrder }, key = { it.id }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = if (category.isArchived) ({ Text("已归档") }) else null,
                    leadingContent = { CategoryGlyph(category) },
                    trailingContent = { RadioButton(selectedCategoryId == category.id, onClick = null) },
                    modifier = Modifier.clickable { onCategory(category.id); filterSheet = false },
                )
            }
        }
    }
}

private fun dayLabel(value: String): String {
    val date = LocalDate.parse(value)
    val label = date.format(DateTimeFormatter.ofPattern("M月d日"))
    return when (date) { LocalDate.now() -> "今天 · $label"; LocalDate.now().minusDays(1) -> "昨天 · $label"; else -> label }
}

@Composable
private fun ExpenseRow(expense: Expense, category: Category?, onRecord: (String) -> Unit) {
    Surface(onClick = { onRecord(expense.id) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (category != null) CategoryGlyph(category)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(expense.note.ifBlank { category?.name ?: "消费" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (expense.note.isNotBlank()) Text(category?.name ?: "消费", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("−${Money.format(expense.amountFen)}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium)
        }
    }
}
