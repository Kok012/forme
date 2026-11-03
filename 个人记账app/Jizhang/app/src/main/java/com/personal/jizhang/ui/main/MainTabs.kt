package com.personal.jizhang.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.model.UserPreferences
import java.time.YearMonth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabs(
    data: LedgerData,
    preferences: UserPreferences,
    canUndo: Boolean,
    jumpMonth: String?,
    onJumpConsumed: () -> Unit,
    onAdd: () -> Unit,
    onRecord: (String) -> Unit,
    onCategories: () -> Unit,
    onTheme: (String) -> Unit,
    onExportBackup: () -> Unit,
    onRestore: () -> Unit,
    onUndo: () -> Unit,
    onExportCsv: (YearMonth) -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var ledgerMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var statisticsMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    var fromStatistics by rememberSaveable { mutableStateOf(false) }
    val ledgerState = rememberLazyListState()
    val statisticsState = rememberLazyListState()
    val settingsState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(jumpMonth) {
        if (jumpMonth != null) {
            runCatching { YearMonth.parse(jumpMonth) }.getOrNull()?.let {
                ledgerMonth = it.toString()
                categoryId = null
                day = null
                fromStatistics = false
                tab = 0
                ledgerState.scrollToItem(0)
            }
            onJumpConsumed()
        }
    }
    BackHandler(tab == 0 && fromStatistics) { tab = 1; fromStatistics = false }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(listOf("记账", "月度账单", "设置")[tab], style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (tab != 2) Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.padding(end = 20.dp),
                    ) { Text("离线账本", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                listOf("明细" to Icons.AutoMirrored.Outlined.ReceiptLong, "统计" to Icons.Outlined.DonutSmall, "设置" to Icons.Outlined.Tune).forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index; fromStatistics = false },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) ExtendedFloatingActionButton(
                modifier = Modifier.testTag("add-expense"),
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("记一笔") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> LedgerScreen(
                    data = data,
                    month = YearMonth.parse(ledgerMonth),
                    onMonth = { ledgerMonth = it.toString(); day = null; scope.launch { ledgerState.scrollToItem(0) } },
                    selectedCategoryId = categoryId,
                    onCategory = { categoryId = it; scope.launch { ledgerState.scrollToItem(0) } },
                    selectedDay = day,
                    onDay = { day = it; scope.launch { ledgerState.scrollToItem(0) } },
                    onRecord = onRecord,
                    onAdd = onAdd,
                    state = ledgerState,
                    fromStatistics = fromStatistics,
                    onReturnStatistics = { tab = 1; fromStatistics = false },
                )
                1 -> StatisticsScreen(
                    data = data,
                    month = YearMonth.parse(statisticsMonth),
                    onMonth = { statisticsMonth = it.toString(); scope.launch { statisticsState.scrollToItem(0) } },
                    onCategory = { selected ->
                        ledgerMonth = statisticsMonth; categoryId = selected; day = null
                        fromStatistics = true; tab = 0
                        scope.launch { ledgerState.scrollToItem(0) }
                    },
                    onDay = { selected ->
                        ledgerMonth = statisticsMonth; categoryId = null; day = selected
                        fromStatistics = true; tab = 0
                        scope.launch { ledgerState.scrollToItem(0) }
                    },
                    onExport = { onExportCsv(YearMonth.parse(statisticsMonth)) },
                    onAdd = onAdd,
                    state = statisticsState,
                )
                else -> SettingsScreen(preferences, canUndo, onCategories, onTheme, onExportBackup, onRestore, onUndo, onExportCsv, settingsState)
            }
        }
    }
}
