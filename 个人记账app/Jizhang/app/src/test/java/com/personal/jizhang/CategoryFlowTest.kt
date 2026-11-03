package com.personal.jizhang

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.DefaultCategories
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.ui.categories.CategoriesScreen
import com.personal.jizhang.ui.theme.JizhangTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newCategoryDraftSurvivesScreenRecreation() {
        var saved: Category? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            JizhangTheme("light") {
                CategoriesScreen(LedgerData(DefaultCategories.items, loaded = true), {}, { item, done -> saved = item; done() }, {}, {}, { _, _ -> })
            }
        }
        compose.onNodeWithContentDescription("新增分类").performClick()
        compose.onNodeWithTag("category-name").performTextInput("宠物")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("宠物", saved?.name) }
    }
}
