package com.personal.jizhang.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.personal.jizhang.BuildConfig
import com.personal.jizhang.data.backup.BackupCodec
import com.personal.jizhang.data.local.LedgerDatabase
import com.personal.jizhang.model.BackupBundle
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.DefaultCategories
import com.personal.jizhang.model.Expense
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.model.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant

private val Context.ledgerPreferences by preferencesDataStore(name = "jizhang_settings")

class LedgerRepository(context: Context, private val database: LedgerDatabase = LedgerDatabase.create(context)) {
    private val settings = context.applicationContext.ledgerPreferences
    private val dao = database.ledgerDao()
    private val mutationMutex = Mutex()
    private var initialized = false

    val preferences: Flow<UserPreferences> = settings.data
        .catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
        .map { values -> UserPreferences(values[THEME] ?: "system", values[LAST_CATEGORY] ?: "", values[LAST_BACKUP] ?: 0) }

    suspend fun initialize() = mutationMutex.withLock { initializeLocked() }

    private suspend fun initializeLocked() {
        if (initialized) return
        database.withTransaction {
            if (dao.categoryCount() == 0) dao.insertCategories(DefaultCategories.items)
        }
        initialized = true
    }

    fun observeLedger(): Flow<LedgerData> = flow {
        initialize()
        emitAll(dao.observeChanges().map {
            mutationMutex.withLock { readLedgerLocked() }
        })
    }.flowOn(Dispatchers.IO)

    private suspend fun readLedgerLocked(): LedgerData = database.withTransaction {
        LedgerData(dao.categories(), dao.records(), loaded = true)
    }

    private suspend fun snapshotLocked(): BackupBundle {
        val ledger = readLedgerLocked()
        return BackupBundle(
            appVersion = BuildConfig.VERSION_NAME, exportedAt = Instant.now().toString(),
            categories = ledger.categories, records = ledger.records,
        )
    }

    suspend fun snapshot(): BackupBundle = mutationMutex.withLock {
        initializeLocked()
        snapshotLocked()
    }

    suspend fun saveExpense(record: Expense) = mutationMutex.withLock {
        initializeLocked()
        BackupCodec.validateExpense(record)
        database.withTransaction {
            val category = requireNotNull(dao.category(record.categoryId)) { "分类不存在，请重新选择" }
            val previous = dao.record(record.id)
            require(!category.isArchived || previous?.categoryId == category.id) { "该分类已归档，请重新选择" }
            // A stable UUID makes retries idempotent, while edits retain the original creation time.
            val normalized = record.copy(note = record.note.trim(), createdAt = previous?.createdAt ?: record.createdAt)
            BackupCodec.validateExpense(normalized)
            dao.saveRecord(normalized)
        }
        try { settings.edit { it[LAST_CATEGORY] = record.categoryId } }
        catch (_: IOException) { /* The expense is committed; a preference write must not turn it into a failed save. */ }
    }

    suspend fun deleteExpense(id: String) = mutationMutex.withLock {
        initializeLocked()
        dao.deleteRecord(id)
    }

    suspend fun saveCategory(category: Category) = mutationMutex.withLock {
        initializeLocked()
        val normalized = category.copy(name = category.name.trim())
        BackupCodec.validateCategory(normalized)
        database.withTransaction {
            val all = dao.categories()
            val previous = all.find { it.id == normalized.id }
            require(all.none { it.id != normalized.id && it.name.equals(normalized.name, ignoreCase = true) }) { "已有同名分类，请换一个名称" }
            require(previous?.isBuiltIn == normalized.isBuiltIn || (previous == null && !normalized.isBuiltIn)) { "不能修改内置分类标识" }
            require(normalized.id != DefaultCategories.OTHER_ID || !normalized.isArchived) { "其他分类必须保持可用" }
            dao.saveCategory(normalized)
        }
    }

    suspend fun archiveCategory(id: String, archived: Boolean) = mutationMutex.withLock {
        initializeLocked()
        require(id != DefaultCategories.OTHER_ID || !archived) { "其他分类必须保持可用" }
        database.withTransaction {
            val category = requireNotNull(dao.category(id)) { "分类不存在" }
            dao.saveCategory(category.copy(isArchived = archived))
        }
    }

    suspend fun deleteCategory(id: String) = mutationMutex.withLock {
        initializeLocked()
        database.withTransaction {
            val category = requireNotNull(dao.category(id)) { "分类不存在" }
            require(!category.isBuiltIn) { "内置分类可以归档，不能删除" }
            require(dao.referencesTo(id) == 0) { "该分类已有消费记录，请改为归档" }
            dao.deleteCategory(id)
        }
    }

    suspend fun moveCategory(id: String, direction: Int) = mutationMutex.withLock {
        initializeLocked()
        require(direction == -1 || direction == 1) { "无效排序方向" }
        database.withTransaction {
            val all = dao.categories()
            val active = all.filterNot { it.isArchived }.toMutableList()
            val oldIndex = active.indexOfFirst { it.id == id }
            if (oldIndex < 0) return@withTransaction
            val newIndex = oldIndex + direction
            if (newIndex !in active.indices) return@withTransaction
            val selected = active.removeAt(oldIndex)
            active.add(newIndex, selected)
            (active + all.filter { it.isArchived }).forEachIndexed { index, value ->
                dao.saveCategory(value.copy(sortOrder = index))
            }
        }
    }

    suspend fun setTheme(mode: String) {
        require(mode in setOf("light", "dark", "system")) { "无效主题" }
        settings.edit { it[THEME] = mode }
    }

    suspend fun markBackupSuccess(time: Long) { settings.edit { it[LAST_BACKUP] = time } }

    /** Holds the writer lock across snapshot creation and replacement; an invalid import never touches the database. */
    internal suspend fun replaceLedger(bundle: BackupBundle, beforeReplace: (suspend (BackupBundle) -> Unit)? = null) = mutationMutex.withLock {
        initializeLocked()
        BackupCodec.validate(bundle)
        beforeReplace?.invoke(snapshotLocked())
        database.withTransaction {
            dao.deleteAllRecords()
            dao.deleteAllCategories()
            dao.insertCategories(bundle.categories)
            dao.insertRecords(bundle.records)
        }
    }

    fun close() { database.close() }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LAST_CATEGORY = stringPreferencesKey("last_category")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }
}
