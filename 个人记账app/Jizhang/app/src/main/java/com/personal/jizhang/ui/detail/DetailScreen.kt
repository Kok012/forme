package com.personal.jizhang.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.ui.components.CategoryGlyph
import com.personal.jizhang.util.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: String, data: LedgerData, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, snackbarHost: @Composable () -> Unit = {}) {
    var confirmDelete by remember { mutableStateOf(false) }
    val record = data.records.firstOrNull { it.id == id }
    val category = data.categories.firstOrNull { it.id == record?.categoryId }
    Scaffold(snackbarHost = snackbarHost, topBar = {
        TopAppBar(title = { Text("消费详情") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (record == null) {
                Text(if (data.loaded) "这笔消费已不存在" else "正在读取消费…")
                TextButton(onClick = onBack) { Text("返回") }
            } else {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        category?.let { CategoryGlyph(it, Modifier.size(52.dp)) }
                        Text(category?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("− ¥${Money.format(record.amountFen)}", style = MaterialTheme.typography.headlineLarge)
                        Text("生活支出", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DetailLine("消费日期", record.expenseDate)
                DetailLine("消费分类", category?.let { it.name + if (it.isArchived) "（已归档）" else "" }.orEmpty())
                DetailLine("备注", record.note.ifBlank { "未填写" })
                val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
                DetailLine("创建时间", formatter.format(Instant.ofEpochMilli(record.createdAt)))
                if (record.updatedAt != record.createdAt) DetailLine("修改时间", formatter.format(Instant.ofEpochMilli(record.updatedAt)))
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("编辑消费")
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("删除这笔消费")
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这笔消费？") },
        text = { Text("删除后，月度账单和统计会同步更新。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
