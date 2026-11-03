package com.personal.jizhang.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.jizhang.AppViewModel
import com.personal.jizhang.ui.categories.CategoriesScreen
import com.personal.jizhang.ui.detail.DetailScreen
import com.personal.jizhang.ui.editor.EditorScreen
import com.personal.jizhang.ui.main.MainTabs
import com.personal.jizhang.ui.theme.JizhangTheme
import com.personal.jizhang.util.Money
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest

@Composable
fun JizhangApp(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()
    val jumpMonth by vm.jumpMonth.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    var csvMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.previewRestore(uri)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri, YearMonth.parse(csvMonth))
    }
    LaunchedEffect(entry?.destination?.route) {
        if (entry?.destination?.route != "main" && snackbar.currentSnackbarData?.visuals?.actionLabel == "查看该月") {
            snackbar.currentSnackbarData?.dismiss()
        }
    }
    LaunchedEffect(vm) {
        vm.events.collectLatest { event ->
            val canJump = event.month != null && nav.currentDestination?.route == "main"
            val result = snackbar.showSnackbar(event.message, actionLabel = if (canJump) "查看该月" else null,
                withDismissAction = canJump, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && event.month != null && nav.currentDestination?.route == "main") {
                vm.jumpToMonth(event.month)
            }
        }
    }
    JizhangTheme(prefs.theme) {
        val window = LocalActivity.current?.window
        val view = LocalView.current
        val lightSurface = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            if (window != null) WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightSurface
                isAppearanceLightNavigationBars = lightSurface
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                NavHost(navController = nav, startDestination = "main") {
                    composable("main") {
                        MainTabs(
                            data = data, preferences = prefs, canUndo = canUndo, jumpMonth = jumpMonth,
                            onJumpConsumed = vm::consumeJump,
                            onAdd = { nav.navigate("editor") },
                            onRecord = { nav.navigate("detail/$it") },
                            onCategories = { nav.navigate("categories") },
                            onTheme = vm::setTheme,
                            onExportBackup = {
                                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                                exportBackup.launch("记账备份_$stamp.jizhang.json")
                            },
                            onRestore = { importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            onUndo = vm::undoRestore,
                            onExportCsv = { month -> csvMonth = month.toString(); exportCsv.launch("记账_$month.csv") },
                            snackbarHost = { SnackbarHost(snackbar) }
                        )
                    }
                    composable("editor?id={id}", arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                        EditorScreen(
                            expenseId = entry.arguments?.getString("id"), data = data,
                            lastCategoryId = prefs.lastCategoryId, busy = busy,
                            onBack = { nav.popBackStack() },
                            onSave = { record, continueAdding, reset ->
                                vm.saveExpense(record) { if (continueAdding) reset() else nav.popBackStack() }
                            },
                            snackbarHost = { SnackbarHost(snackbar) }
                        )
                    }
                    composable("detail/{id}") { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        DetailScreen(id, data, { nav.popBackStack() }, { nav.navigate("editor?id=$id") }, {
                            vm.deleteExpense(id) { nav.popBackStack() }
                        }, snackbarHost = { SnackbarHost(snackbar) })
                    }
                    composable("categories") {
                        CategoriesScreen(data, { nav.popBackStack() }, vm::saveCategory, vm::archiveCategory, vm::deleteCategory, vm::moveCategory,
                            snackbarHost = { SnackbarHost(snackbar) })
                    }
                }
                loadError?.let { error ->
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.safeDrawingPadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
                            Text("账本暂时无法打开", style = MaterialTheme.typography.headlineSmall)
                            Text(error)
                            Button(onClick = vm::retryLoad) { Text("重新读取") }
                        }
                    }
                }
            }
        }
        if (busy) Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("正在处理，请稍候")
                }
            }
        }
        pendingRestore?.takeIf { !busy }?.let { backup ->
            AlertDialog(
                onDismissRequest = vm::cancelRestore,
                title = { Text("恢复这个账本？") },
                text = {
                    val range = backup.records.map { it.expenseDate }.sorted()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${backup.records.size} 笔消费 · ${backup.categories.size} 个分类")
                        Text("总支出 ¥${Money.format(backup.records.sumOf { it.amountFen })}")
                        if (range.isNotEmpty()) Text("${range.first()} 至 ${range.last()}")
                        Text("备份时间：${backup.exportedAt}")
                        Text("恢复将替换当前全部账单与分类。恢复前会保留快照，可在设置中撤销。")
                    }
                },
                confirmButton = { TextButton(onClick = vm::applyRestore, enabled = !busy) { Text("替换并恢复") } },
                dismissButton = { TextButton(onClick = vm::cancelRestore, enabled = !busy) { Text("取消") } }
            )
        }
    }
}
