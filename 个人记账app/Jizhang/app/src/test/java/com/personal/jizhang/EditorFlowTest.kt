package com.personal.jizhang

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.personal.jizhang.model.DefaultCategories
import com.personal.jizhang.model.Expense
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.ui.editor.EditorScreen
import com.personal.jizhang.ui.theme.JizhangTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
class EditorFlowTest {
    @get:Rule val compose = createComposeRule()
    private val ledger = LedgerData(categories = DefaultCategories.items, records = emptyList(), loaded = true)

    @Test fun amountAndCategoryRequiredBeforeSaving() {
        compose.setContent {
            JizhangTheme("light") {
                EditorScreen(null, ledger, "", false, {}, { _, _, _ -> error("无效表单不应保存") })
            }
        }
        compose.onNodeWithText("保存", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("使用系统键盘 / 粘贴金额").performScrollTo().performClick()
        compose.onNodeWithTag("expense-amount").performTextInput("0.001")
        compose.onNodeWithContentDescription("分类 餐饮").performScrollTo().performClick()
        compose.onNodeWithText("保存", substring = false).assertIsNotEnabled()
    }

    @Test fun draftSurvivesSavedStateAndCommitsExactFen() {
        var result: Expense? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            JizhangTheme("light") { EditorScreen(null, ledger, "", false, {}, { expense, _, _ -> result = expense }) }
        }
        compose.onNodeWithText("使用系统键盘 / 粘贴金额").performScrollTo().performClick()
        compose.onNodeWithTag("expense-amount").performTextInput("28.10")
        compose.onNodeWithContentDescription("分类 餐饮").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle {
            assertNotNull(result)
            assertEquals(2810L, result!!.amountFen)
            assertEquals(DefaultCategories.items.first { it.iconKey == "food" }.id, result!!.categoryId)
        }
    }
}
