package com.personal.jizhang.ui.editor

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.jizhang.model.Expense
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.ui.components.CategoryGlyph
import com.personal.jizhang.util.Money
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    expenseId: String?, data: LedgerData, lastCategoryId: String, busy: Boolean,
    onBack: () -> Unit, onSave: (Expense, Boolean, () -> Unit) -> Unit,
    snackbarHost: @Composable () -> Unit = {}
) {
    val original = data.records.firstOrNull { it.id == expenseId }
    if (!data.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (expenseId != null && original == null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("这笔消费已不存在")
            TextButton(onClick = onBack) { Text("返回账本") }
        }
        return
    }
    val initialCategory = original?.categoryId ?: lastCategoryId.takeIf { id -> data.categories.any { it.id == id && !it.isArchived } }.orEmpty()
    var draftId by rememberSaveable(expenseId) { mutableStateOf(original?.id ?: UUID.randomUUID().toString()) }
    var amount by rememberSaveable(expenseId) { mutableStateOf(original?.let { Money.format(it.amountFen) }.orEmpty()) }
    var categoryId by rememberSaveable(expenseId) { mutableStateOf(initialCategory) }
    var date by rememberSaveable(expenseId) { mutableStateOf(original?.expenseDate ?: LocalDate.now().toString()) }
    var note by rememberSaveable(expenseId) { mutableStateOf(original?.note.orEmpty()) }
    var createdAt by rememberSaveable(expenseId) { mutableLongStateOf(original?.createdAt ?: System.currentTimeMillis()) }
    var baselineCategory by rememberSaveable(expenseId) { mutableStateOf(initialCategory) }
    var baselineDate by rememberSaveable(expenseId) { mutableStateOf(original?.expenseDate ?: LocalDate.now().toString()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var systemInput by rememberSaveable { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val fen = Money.parse(amount)
    val category = data.categories.firstOrNull { it.id == categoryId }
    val categoryAllowed = category != null && (!category.isArchived || original?.categoryId == category.id)
    val dateValid = runCatching { !LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(false)
    val valid = fen != null && categoryAllowed && dateValid && note.codePointCount(0, note.length) <= 100
    val dirty = if (original != null) {
        amount != Money.format(original.amountFen) || categoryId != original.categoryId || date != original.expenseDate || note != original.note
    } else amount.isNotEmpty() || note.isNotEmpty() || categoryId != baselineCategory || date != baselineDate
    fun back() { if (!busy) { if (dirty) confirmDiscard = true else onBack() } }
    BackHandler { back() }
    fun save(continueAdding: Boolean) {
        if (!valid || busy) return
        keyboard?.hide()
        focus.clearFocus()
        val record = Expense(
            id = draftId, amountFen = fen!!, categoryId = categoryId, expenseDate = date,
            note = note.trim(), createdAt = createdAt, updatedAt = System.currentTimeMillis()
        )
        onSave(record, continueAdding) {
            draftId = UUID.randomUUID().toString()
            createdAt = System.currentTimeMillis()
            amount = ""
            note = ""
            baselineDate = date
            baselineCategory = categoryId
            noteFocused = false
        }
    }
    Scaffold(
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(title = { Text(if (expenseId == null) "记一笔" else "修改消费") }, navigationIcon = {
                IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            })
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!systemInput && !noteFocused) NumericPad { key ->
                        when (key) {
                            "⌫" -> amount = amount.dropLast(1)
                            "." -> if (!amount.contains('.')) amount = if (amount.isEmpty()) "0." else "$amount."
                            else -> if (amount.length < 10 && (!amount.contains('.') || amount.substringAfter('.').length < 2)) {
                                amount = if (amount == "0") key else amount + key
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (expenseId == null) OutlinedButton(onClick = { save(true) }, enabled = valid && !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("保存并继续") }
                        Button(onClick = { save(false) }, enabled = valid && !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("保存")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text("支出金额", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.length <= 20) amount = it },
                    modifier = Modifier.fillMaxWidth().testTag("expense-amount").onFocusChanged {
                        if (it.isFocused) { noteFocused = false; if (!systemInput) keyboard?.hide() }
                    },
                    prefix = { Text("¥", fontSize = 28.sp) },
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.Medium),
                    placeholder = { Text("0.00", fontSize = 40.sp) },
                    singleLine = true, readOnly = !systemInput,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount.isNotEmpty() && fen == null,
                    supportingText = {
                        Text(if (amount.isNotEmpty() && fen == null) "请输入 0.01～9,999,999.99，最多两位小数" else "人民币 · 精确到分")
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                TextButton(onClick = { systemInput = !systemInput; noteFocused = false; focus.clearFocus(); keyboard?.hide() }) {
                    Text(if (systemInput) "使用大号数字键盘" else "使用系统键盘 / 粘贴金额")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("消费分类", style = MaterialTheme.typography.titleSmall)
                val selectable = data.categories.filter { !it.isArchived || it.id == original?.categoryId }.sortedBy { it.sortOrder }
                selectable.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { item ->
                            val selected = categoryId == item.id
                            Surface(
                                onClick = { categoryId = item.id; noteFocused = false; keyboard?.hide(); focus.clearFocus() },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "分类 ${item.name}${if (selected) "，已选择" else ""}" },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(Modifier.padding(vertical = 10.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CategoryGlyph(item, Modifier.size(26.dp))
                                    Text(item.name + if (item.isArchived) "·归档" else "", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            OutlinedButton(onClick = {
                keyboard?.hide(); focus.clearFocus(); noteFocused = false
                val selected = LocalDate.parse(date)
                DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                    datePicker.maxDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    show()
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("消费日期  $date")
            }
            OutlinedTextField(
                value = note, onValueChange = { if (it.codePointCount(0, it.length) <= 100) note = it },
                label = { Text("备注（选填）") }, placeholder = { Text("例如：午餐、超市采购") },
                supportingText = { Text("${note.codePointCount(0, note.length)}/100") },
                modifier = Modifier.fillMaxWidth().testTag("expense-note").onFocusChanged { if (it.isFocused) noteFocused = true },
                minLines = 1, maxLines = 3, shape = RoundedCornerShape(16.dp)
            )
            if (category == null) Text("选择分类后即可保存", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false }, title = { Text("放弃本次修改？") },
        text = { Text("这笔消费还没有保存。") },
        confirmButton = { TextButton(onClick = onBack) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } }
    )
}

@Composable
private fun NumericPad(onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    FilledTonalButton(onClick = { onKey(key) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(6.dp)) {
                        if (key == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, "退格") else Text(key, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}
