package com.personal.jizhang.data.backup

import com.personal.jizhang.model.BackupBundle
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.DefaultCategories
import com.personal.jizhang.model.Expense
import com.personal.jizhang.util.LedgerDates
import com.personal.jizhang.util.Money
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

object BackupCodec {
    const val MAX_BYTES = 50 * 1024 * 1024
    private val validId = Regex("[A-Za-z0-9_.:-]{1,100}")
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = false
    }
    private val requiredKeys = setOf("version", "appVersion", "exportedAt", "categories", "records", "categoryCount", "recordCount")

    fun encode(bundle: BackupBundle): String {
        validate(bundle)
        return json.encodeToString(bundle).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 50MB，请先减少账本数据" }
        }
    }

    fun decode(text: String): BackupBundle {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件不能超过 50MB" }
        try {
            val normalized = text.removePrefix("\uFEFF")
            val root = json.parseToJsonElement(normalized) as? JsonObject
                ?: throw IllegalArgumentException("这不是有效的记账备份文件")
            require(root.keys.containsAll(requiredKeys)) { "备份文件缺少格式版本或账本字段" }
            return json.decodeFromString<BackupBundle>(normalized).also(::validate)
        } catch (_: SerializationException) {
            throw IllegalArgumentException("备份文件格式错误或字段类型不正确")
        }
    }

    fun validateCategory(category: Category) {
        require(validId.matches(category.id)) { "分类标识无效" }
        require(category.name == category.name.trim() && category.name.codePointCount(0, category.name.length) in 1..8) { "分类名称需为 1–8 个字符" }
        require(category.name.none { it.isISOControl() }) { "分类名称不能含控制字符" }
        require(category.iconKey in DefaultCategories.iconKeys) { "分类图标无效" }
        require(category.colorKey in DefaultCategories.colorKeys) { "分类颜色无效" }
        require(category.sortOrder >= 0) { "分类排序无效" }
        require(category.id != DefaultCategories.OTHER_ID || (!category.isArchived && category.isBuiltIn)) { "其他分类必须保留且可用" }
    }

    fun validateExpense(record: Expense, today: LocalDate = LocalDate.now()) {
        require(validId.matches(record.id)) { "消费标识无效" }
        require(record.amountFen in 1..Money.MAX_FEN) { "金额需大于 0，且不超过 9,999,999.99 元" }
        val date = requireNotNull(LedgerDates.parseDate(record.expenseDate)) { "消费日期无效" }
        require(!date.isAfter(today)) { "消费日期不能晚于今天" }
        require(record.note.codePointCount(0, record.note.length) <= 100) { "备注不能超过 100 个字符" }
        require(record.createdAt > 0 && record.updatedAt >= record.createdAt) { "消费时间信息无效" }
    }

    fun validate(bundle: BackupBundle) {
        require(bundle.version == 1) { "不支持此备份版本，请使用兼容的记账应用" }
        require(bundle.appVersion.isNotBlank() && bundle.appVersion.length <= 50) { "应用版本信息无效" }
        require(runCatching { Instant.parse(bundle.exportedAt) }.isSuccess) { "备份时间无效" }
        require(bundle.categoryCount == bundle.categories.size && bundle.recordCount == bundle.records.size) { "备份记录数量与文件内容不一致" }
        val ids = bundle.categories.map { it.id }.toSet()
        require(ids.size == bundle.categories.size) { "备份包含重复分类标识" }
        require(bundle.categories.map { it.name.lowercase(Locale.ROOT) }.toSet().size == bundle.categories.size) { "备份包含同名分类" }
        bundle.categories.forEach(::validateCategory)
        val builtInIds = DefaultCategories.items.map { it.id }.toSet()
        require(bundle.categories.filter { it.isBuiltIn }.map { it.id }.toSet() == builtInIds) { "备份缺少内置分类或包含无效内置分类" }
        require(bundle.records.map { it.id }.toSet().size == bundle.records.size) { "备份包含重复消费标识" }
        val today = LocalDate.now()
        bundle.records.forEach {
            validateExpense(it, today)
            require(it.categoryId in ids) { "备份消费引用了不存在的分类" }
        }
    }
}
