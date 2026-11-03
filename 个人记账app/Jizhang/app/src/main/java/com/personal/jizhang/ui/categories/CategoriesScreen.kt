package com.personal.jizhang.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.ui.components.CategoryGlyph
import com.personal.jizhang.ui.components.CategoryIcon
import com.personal.jizhang.ui.components.categoryColor
import java.util.UUID

private val iconOptions = listOf("food" to "餐饮", "transport" to "交通", "shopping" to "购物", "home" to "居住", "daily" to "日用", "fun" to "娱乐", "health" to "医疗", "study" to "学习", "phone" to "通信", "gift" to "人情", "travel" to "旅行", "other" to "其他")
private val colorOptions = listOf("mint" to "薄荷", "blue" to "蓝色", "orange" to "橙色", "purple" to "紫色", "pink" to "粉色", "yellow" to "金色")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    data: LedgerData, onBack: () -> Unit, onSave: (Category, () -> Unit) -> Unit,
    onArchive: (Category) -> Unit, onDelete: (String) -> Unit, onMove: (String, Int) -> Unit,
    snackbarHost: @Composable () -> Unit = {}
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> data.categories.find { it.id == id } ?: Category(id, "", sortOrder = (data.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1) }
    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(title = { Text("分类管理") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }, actions = {
                IconButton(onClick = {
                    editingId = UUID.randomUUID().toString()
                }) { Icon(Icons.Outlined.Add, "新增分类") }
            })
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("按自己的习惯分类", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("调整顺序，让常用分类更靠前。归档后，历史账单与统计仍会保留。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            listOf(false, true).forEach { archived ->
                val categories = data.categories.filter { it.isArchived == archived }.sortedBy { it.sortOrder }
                if (categories.isNotEmpty()) {
                    item { Text(if (archived) "已归档" else "正在使用", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(categories, key = { it.id }) { category ->
                        val count = data.records.count { it.categoryId == category.id }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryGlyph(category, Modifier.size(40.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(category.name, style = MaterialTheme.typography.titleMedium)
                                        Text("$count 笔消费 · ${if (category.isBuiltIn) "内置" else "自定义"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { editingId = category.id }) { Icon(Icons.Outlined.Edit, "编辑${category.name}") }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                    if (!archived) {
                                        IconButton(onClick = { onMove(category.id, -1) }, enabled = category.id != categories.first().id) { Icon(Icons.Outlined.ArrowUpward, "${category.name}上移") }
                                        IconButton(onClick = { onMove(category.id, 1) }, enabled = category.id != categories.last().id) { Icon(Icons.Outlined.ArrowDownward, "${category.name}下移") }
                                    }
                                    TextButton(onClick = { onArchive(category) }, enabled = category.id != "builtin_other") { Text(if (archived) "恢复使用" else "归档") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    editingId = UUID.randomUUID().toString()
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("新增分类")
                }
            }
        }
    }
    editing?.let { category ->
        CategoryEditor(
            category = category, all = data.categories,
            canDelete = !category.isBuiltIn && data.categories.any { it.id == category.id } && data.records.none { it.categoryId == category.id },
            onDismiss = { editingId = null },
            onSave = { updated -> onSave(updated) { editingId = null } },
            onDelete = { editingId = null; onDelete(category.id) }
        )
    }
}

@Composable
private fun CategoryEditor(
    category: Category, all: List<Category>, canDelete: Boolean,
    onDismiss: () -> Unit, onSave: (Category) -> Unit, onDelete: () -> Unit
) {
    var name by rememberSaveable(category.id) { mutableStateOf(category.name) }
    var icon by rememberSaveable(category.id) { mutableStateOf(category.iconKey) }
    var color by rememberSaveable(category.id) { mutableStateOf(category.colorKey) }
    var deleting by remember { mutableStateOf(false) }
    val trimmed = name.trim()
    val duplicate = all.any { it.id != category.id && it.name.equals(trimmed, ignoreCase = true) }
    val valid = trimmed.isNotEmpty() && trimmed.codePointCount(0, trimmed.length) <= 8 && !duplicate
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (category.name.isEmpty()) "新增分类" else "编辑分类") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { if (it.codePointCount(0, it.length) <= 8) name = it },
                label = { Text("分类名称") }, singleLine = true,
                isError = duplicate, supportingText = { Text(if (duplicate) "已有同名分类（含已归档）" else "1～8 个字符") },
                modifier = Modifier.fillMaxWidth().testTag("category-name")
            )
            Text("选择图标", style = MaterialTheme.typography.labelLarge)
            iconOptions.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (key, label) ->
                        Surface(onClick = { icon = key }, modifier = Modifier.weight(1f).semantics { contentDescription = "$label 图标${if (icon == key) "，已选择" else ""}" },
                            color = if (icon == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp), border = if (icon == key) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) { Icon(CategoryIcon(key), null, Modifier.size(24.dp)) }
                        }
                    }
                }
            }
            Text("选择颜色", style = MaterialTheme.typography.labelLarge)
            colorOptions.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (key, label) ->
                        Surface(onClick = { color = key }, modifier = Modifier.weight(1f).semantics { contentDescription = "$label${if (color == key) "，已选择" else ""}" },
                            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                            border = if (color == key) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(30.dp).background(categoryColor(key), CircleShape), contentAlignment = Alignment.Center) {
                                    if (color == key) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                        }
                    }
                }
            }
            if (canDelete) TextButton(onClick = { deleting = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除未使用的分类") }
        }
    }, confirmButton = { TextButton(onClick = { onSave(category.copy(name = trimmed, iconKey = icon, colorKey = color)) }, enabled = valid) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除${category.name}？") }, text = { Text("该分类没有消费记录，可以删除。") },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } }
    )
}
