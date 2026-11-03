package com.personal.jizhang

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import java.io.File

/** Exercises the real Activity, navigation, ViewModel and on-disk Room database together. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun createsEditsAndDeletesARealExpense() {
        waitForText("这个月还没有记录")
        compose.onNodeWithTag("add-expense").performClick()
        compose.onNodeWithText("使用系统键盘 / 粘贴金额").performScrollTo().performClick()
        compose.onNodeWithTag("expense-amount").performTextInput("23.45")
        compose.onNodeWithContentDescription("分类 餐饮").performScrollTo().performClick()
        if (System.getenv("JIZHANG_SCREENSHOTS") == "1") compose.runOnIdle {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val target = File("build/screenshots/editor.png")
            target.parentFile?.mkdirs()
            target.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("保存并继续", substring = false).performClick()
        val month = YearMonth.now()
        waitForText("已记入 ${month.year}年${month.monthValue.toString().padStart(2, '0')}月")
        compose.onNodeWithTag("expense-amount").performTextInput("1.00")
        compose.onAllNodesWithText("查看该月").assertCountEquals(0)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("放弃本次修改？").assertIsDisplayed()
        compose.onNodeWithText("放弃", substring = false).performClick()
        waitForText("−23.45")
        compose.onNodeWithTag("ledger-list").performScrollToNode(hasText("−23.45"))
        compose.onNodeWithText("−23.45").performClick()
        compose.onNodeWithText("− ¥23.45").assertIsDisplayed()
        compose.onNodeWithText("编辑消费").performScrollTo().performClick()
        compose.onNodeWithText("使用系统键盘 / 粘贴金额").performScrollTo().performClick()
        compose.onNodeWithTag("expense-amount").performTextReplacement("25.00")
        compose.onNodeWithText("保存", substring = false).performClick()
        waitForText("− ¥25.00")
        compose.onNodeWithText("删除这笔消费").performScrollTo().performClick()
        compose.onNodeWithText("删除", substring = false).performClick()
        waitForText("这个月还没有记录")
        compose.onNodeWithText("统计").performClick()
        compose.onNodeWithText("¥ 0.00").assertIsDisplayed()
    }
}
