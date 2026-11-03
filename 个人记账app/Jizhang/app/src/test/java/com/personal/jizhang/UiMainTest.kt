package com.personal.jizhang

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.personal.jizhang.model.DefaultCategories
import com.personal.jizhang.model.Expense
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.model.UserPreferences
import com.personal.jizhang.ui.main.MainTabs
import com.personal.jizhang.ui.theme.JizhangTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiMainTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val emptyLedger = LedgerData(categories = DefaultCategories.items, loaded = true)

    private fun show(data: LedgerData = emptyLedger, onTheme: (String) -> Unit = {}) {
        compose.setContent {
            JizhangTheme("light") {
                MainTabs(
                    data = data, preferences = UserPreferences(), canUndo = false, jumpMonth = null,
                    onJumpConsumed = {}, onAdd = {}, onRecord = {}, onCategories = {}, onTheme = onTheme,
                    onExportBackup = {}, onRestore = {}, onUndo = {}, onExportCsv = {},
                )
            }
        }
    }

    @Test fun newLedgerShowsZeroAndEmptyState() {
        show()
        compose.onNodeWithText("¥ 0.00").assertIsDisplayed()
        compose.onNodeWithText("这个月还没有记录").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("add-expense").assertIsDisplayed()
        captureIfRequested("main-empty")
    }

    @Test fun previousMonthHasIndependentEmptySummary() {
        show()
        compose.onNodeWithContentDescription("上个月").performClick()
        val previous = YearMonth.now().minusMonths(1)
        compose.onNodeWithText("${previous.year}年${previous.monthValue}月").assertIsDisplayed()
        compose.onNodeWithText("回到本月").assertIsDisplayed()
        compose.onNodeWithText("¥ 0.00").assertIsDisplayed()
        compose.onNodeWithText("回到本月").performClick()
        val current = YearMonth.now()
        compose.onNodeWithText("${current.year}年${current.monthValue}月").assertIsDisplayed()
    }

    @Test fun settingsThemeSelectionCallsThePreferenceAction() {
        var selected = ""
        show(onTheme = { selected = it })
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("外观主题").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.runOnIdle { assertEquals("dark", selected) }
    }

    @Test fun categoryDrilldownKeepsStatisticsAvailableOnReturn() {
        val food = DefaultCategories.items.first { it.iconKey == "food" }
        val travel = DefaultCategories.items.first { it.iconKey == "transport" }
        val today = LocalDate.now().toString()
        val records = listOf(
            Expense("test-lunch", 2800L, food.id, today, "工作午餐", 1L, 1L),
            Expense("test-bus", 300L, travel.id, today, "公交", 2L, 2L),
        )
        show(emptyLedger.copy(records = records))
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("¥ 31.00").assertIsDisplayed()
        compose.onNodeWithText("餐饮").performScrollTo().performClick()
        compose.onNodeWithTag("ledger-list").performScrollToNode(hasText("工作午餐"))
        compose.onNodeWithText("工作午餐").assertIsDisplayed()
        compose.onNodeWithText("返回月度统计").performScrollTo().performClick()
        compose.onNodeWithText("月度账单").assertIsDisplayed()
        compose.onNodeWithText("¥ 31.00").performScrollTo().assertIsDisplayed()
        captureIfRequested("main-statistics")
    }

    private fun captureIfRequested(name: String) {
        if (System.getenv("JIZHANG_SCREENSHOTS") != "1") return
        lateinit var image: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
        }
        val file = File("build/screenshots/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
