package com.personal.jizhang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.personal.jizhang.BuildConfig
import com.personal.jizhang.model.UserPreferences
import com.personal.jizhang.ui.components.MonthPickerDialog
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun SettingsScreen(
    preferences: UserPreferences,
    canUndo: Boolean,
    onCategories: () -> Unit,
    onTheme: (String) -> Unit,
    onExportBackup: () -> Unit,
    onRestore: () -> Unit,
    onUndo: () -> Unit,
    onExportCsv: (YearMonth) -> Unit,
    scrollState: ScrollState,
) {
    var themeDialog by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var csvDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    var undoDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Spa, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("自己的日常，自己记", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("账本保存在手机，无需登录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        SettingsGroup("账本") {
            SettingRow(Icons.Outlined.Category, "分类管理", "让分类符合自己的消费习惯", onCategories)
            SettingRow(Icons.Outlined.Palette, "外观主题", themeName(preferences.theme), { themeDialog = true })
        }
        SettingsGroup("备份与导出") {
            SettingRow(Icons.Outlined.SaveAlt, "导出完整备份", if (preferences.lastBackupAt > 0) "最近备份 ${formatTime(preferences.lastBackupAt)}" else "保存全部账单和分类", { exportDialog = true })
            SettingRow(Icons.Outlined.Restore, "从备份恢复", "从 .jizhang.json 文件恢复账本", onRestore)
            if (canUndo) SettingRow(Icons.AutoMirrored.Outlined.Undo, "撤销上次恢复", "恢复到上次导入前的账本", { undoDialog = true })
            SettingRow(Icons.Outlined.FileDownload, "导出月度 CSV", "用 Excel 查看和整理消费", { csvDialog = true })
        }
        Text("卸载应用或清除应用数据会删除手机中的账本。请定期把完整备份保存到应用之外的本地文件夹。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsGroup("关于") { SettingRow(Icons.Outlined.Info, "关于记账", "版本 ${BuildConfig.VERSION_NAME}", { aboutDialog = true }) }
        Spacer(Modifier.height(8.dp))
    }
    if (themeDialog) AlertDialog(
        onDismissRequest = { themeDialog = false }, title = { Text("外观主题") },
        text = {
            Column {
                listOf("system", "light", "dark").forEach { mode ->
                    Row(Modifier.fillMaxWidth().clickable { onTheme(mode); themeDialog = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(preferences.theme == mode, onClick = null)
                        Text(themeName(mode), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("取消") } },
    )
    if (exportDialog) AlertDialog(
        onDismissRequest = { exportDialog = false }, title = { Text("导出完整备份") },
        text = { Text("备份包含全部消费明细和分类，文件为明文 JSON。请选择手机本地文件夹或移动存储保存。") },
        confirmButton = { TextButton(onClick = { exportDialog = false; onExportBackup() }) { Text("选择保存位置") } },
        dismissButton = { TextButton(onClick = { exportDialog = false }) { Text("取消") } },
    )
    if (csvDialog) MonthPickerDialog(YearMonth.now(), { csvDialog = false }, { csvDialog = false; onExportCsv(it) })
    if (undoDialog) AlertDialog(
        onDismissRequest = { undoDialog = false }, title = { Text("撤销上次恢复？") },
        text = { Text("当前账单和分类将被替换为上次导入前的快照。此后的记账内容不会保留，请先导出当前备份。") },
        confirmButton = { TextButton(onClick = { undoDialog = false; onUndo() }) { Text("确认撤销") } },
        dismissButton = { TextButton(onClick = { undoDialog = false }) { Text("取消") } },
    )
    if (aboutDialog) AlertDialog(
        onDismissRequest = { aboutDialog = false }, title = { Text("记账 ${BuildConfig.VERSION_NAME}") },
        text = { Text("一个专注个人消费的离线账本。\n\n人民币 · 支出记账 · 本地保存\n无广告，无需账号，不主动上传数据。\n\n完整备份可恢复账本；CSV 用于查看，不用于恢复。") },
        confirmButton = { TextButton(onClick = { aboutDialog = false }) { Text("知道了") } },
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(content = content) }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 3.dp),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

private fun themeName(mode: String): String = when (mode) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" }
private fun formatTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
