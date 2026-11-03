package com.personal.jizhang.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val iconKey: String = "other",
    val colorKey: String = "mint",
    val sortOrder: Int = 0,
    val isBuiltIn: Boolean = false,
    val isArchived: Boolean = false,
)

@Serializable
@Entity(
    tableName = "expense_records",
    foreignKeys = [ForeignKey(
        entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.RESTRICT,
    )],
    indices = [Index("expenseDate"), Index(value = ["categoryId", "expenseDate"])],
)
data class Expense(
    @PrimaryKey val id: String,
    val amountFen: Long,
    val categoryId: String,
    val expenseDate: String,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

data class LedgerData(
    val categories: List<Category> = emptyList(),
    val records: List<Expense> = emptyList(),
    val loaded: Boolean = false,
)

data class UserPreferences(
    val theme: String = "system",
    val lastCategoryId: String = "",
    val lastBackupAt: Long = 0,
)

@Serializable
data class BackupBundle(
    val version: Int = 1,
    val appVersion: String,
    val exportedAt: String,
    val categories: List<Category>,
    val records: List<Expense>,
    val categoryCount: Int = categories.size,
    val recordCount: Int = records.size,
)

object DefaultCategories {
    const val OTHER_ID = "builtin_other"
    val iconKeys = listOf("food", "transport", "shopping", "home", "daily", "fun", "health", "study", "phone", "gift", "travel", "other")
    val colorKeys = listOf("mint", "blue", "orange", "purple", "pink", "yellow")
    val items: List<Category> = listOf(
        "餐饮" to "food", "交通" to "transport", "购物" to "shopping", "居住" to "home",
        "日用" to "daily", "娱乐" to "fun", "医疗" to "health", "学习" to "study",
        "通信" to "phone", "人情" to "gift", "旅行" to "travel", "其他" to "other",
    ).mapIndexed { index, (name, icon) ->
        Category("builtin_$icon", name, icon, colorKeys[index % colorKeys.size], index, isBuiltIn = true)
    }
}
