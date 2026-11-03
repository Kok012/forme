package com.personal.jizhang.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.ui.components.CategoryGlyph
import com.personal.jizhang.ui.components.categoryColor
import com.personal.jizhang.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private data class CategoryTotal(val category: Category, val amount: Long)
private data class RingSlice(val label: String, val amount: Long, val color: Color)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StatisticsScreen(
    data: LedgerData,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onCategory: (String) -> Unit,
    onDay: (String) -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
    state: LazyListState,
) {
    val records = remember(data.records, month) { monthRecords(data.records, month) }
    val totals = remember(records, data.categories) {
        val amounts = records.groupBy { it.categoryId }.mapValues { (_, items) -> items.sumOf { it.amountFen } }
        data.categories.mapNotNull { category -> amounts[category.id]?.let { CategoryTotal(category, it) } }
            .sortedWith(compareByDescending<CategoryTotal> { it.amount }.thenBy { it.category.sortOrder })
    }
    val total = records.sumOf { it.amountFen }
    val days = if (month == YearMonth.now()) LocalDate.now().dayOfMonth else month.lengthOfMonth()
    val daily = remember(records, days, month) {
        val byDate = records.groupBy { it.expenseDate }.mapValues { (_, items) -> items.sumOf { it.amountFen } }
        (1..days).map { day -> byDate[month.atDay(day).toString()] ?: 0L }
    }
    var daySheet by remember { mutableStateOf(false) }
    LazyColumn(state = state, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 108.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "month") { MonthBar(month, onMonth) }
        if (!data.loaded) item { Box(Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else {
            item(key = "summary") { SummaryCard(total, records.size, month) }
            if (records.isEmpty()) item { EmptyLedger("这个月还没有记录", "记下消费后，就能看到分类与每日趋势", onAdd) }
            else {
                item(key = "categories") {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("消费分类", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
                            CategoryRing(totals, total, Modifier.align(Alignment.CenterHorizontally))
                            totals.forEach { item ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { onCategory(item.category.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CategoryGlyph(item.category)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(item.category.name, style = MaterialTheme.typography.bodyLarge)
                                            Text("¥${Money.format(item.amount)}", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                                        }
                                        Text("${percentage(item.amount, total)}%${if (item.category.isArchived) " · 已归档" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Outlined.ChevronRight, "查看${item.category.name}明细", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item(key = "daily") {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("每日消费", style = MaterialTheme.typography.titleMedium)
                                Text("单位：元", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DailyBars(daily, month, onDay)
                            TextButton(onClick = { daySheet = true }, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 0.dp)) {
                                Text("按日期查看明细")
                                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            item(key = "export") {
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("导出本月 CSV")
                }
            }
        }
    }
    if (daySheet) ModalBottomSheet(onDismissRequest = { daySheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.heightIn(max = 520.dp)) {
            item { Text("${month.monthValue}月每日消费", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
            items((1..days).toList(), key = { it }) { day ->
                ListItem(
                    headlineContent = { Text("${month.monthValue}月${day}日") },
                    trailingContent = { Text("¥${Money.format(daily[day - 1])}", style = MaterialTheme.typography.titleMedium) },
                    modifier = Modifier.clickable { daySheet = false; onDay(month.atDay(day).toString()) },
                )
            }
        }
    }
}

private fun percentage(amount: Long, total: Long): String = String.format(Locale.CHINA, "%.2f", amount.toDouble() * 100 / total)

@Composable
private fun CategoryRing(totals: List<CategoryTotal>, total: Long, modifier: Modifier = Modifier) {
    val remainderColor = MaterialTheme.colorScheme.outline
    val slices = totals.take(5).map { RingSlice(it.category.name, it.amount, categoryColor(it.category.colorKey)) } +
        if (totals.size > 5) listOf(RingSlice("其余分类", totals.drop(5).sumOf { it.amount }, remainderColor)) else emptyList()
    val description = slices.joinToString("；") { "${it.label} ${percentage(it.amount, total)}%" }
    Box(modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "消费分类占比：$description" }) {
            val stroke = 24.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            var start = -90f
            slices.forEach { slice ->
                val sweep = (slice.amount.toDouble() / total * 360).toFloat()
                val gap = if (slices.size > 1) minOf(2.5f, sweep / 4) else 0f
                drawArc(slice.color, start + gap / 2, sweep - gap, false, topLeft, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${totals.size}", style = MaterialTheme.typography.headlineMedium)
            Text("消费分类", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyBars(values: List<Long>, month: YearMonth, onDay: (String) -> Unit) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.height(160.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Text(Money.format(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)
                .semantics { contentDescription = "${month.monthValue}月每日支出柱状图，最高 ¥${Money.format(max)}，可通过下方按日期查看明细获取每日金额" }
                .pointerInput(values, month) {
                    detectTapGestures { offset ->
                        val day = (offset.x / size.width * values.size).toInt().coerceIn(0, values.lastIndex) + 1
                        onDay(month.atDay(day).toString())
                    }
                }) {
                val plotHeight = size.height - 2.dp.toPx()
                drawLine(gridColor, Offset(0f, plotHeight), Offset(size.width, plotHeight), 1.dp.toPx())
                drawLine(gridColor.copy(alpha = .4f), Offset(0f, plotHeight / 2), Offset(size.width, plotHeight / 2), 1.dp.toPx())
                val slot = size.width / values.size
                val width = (slot * .62f).coerceAtLeast(1f)
                values.forEachIndexed { index, amount ->
                    if (amount > 0) {
                        val height = (amount.toDouble() / max * plotHeight).toFloat().coerceAtLeast(2.dp.toPx())
                        drawRoundRect(barColor, Offset(index * slot + (slot - width) / 2, plotHeight - height), Size(width, height), CornerRadius(minOf(width / 3, 4.dp.toPx())))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (values.size > 3) Text("${(values.size + 1) / 2}日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (values.size > 1) Text("${values.size}日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
