package com.personal.jizhang

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.jizhang.di.AppContainer
import com.personal.jizhang.model.BackupBundle
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.Expense
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.YearMonth

data class AppNotice(val message: String, val month: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val notices = Channel<AppNotice>(Channel.UNLIMITED)
    private val reload = MutableStateFlow(0)
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()
    val data = reload.flatMapLatest {
        repository.observeLedger().catch { error ->
            if (error is CancellationException) throw error
            _loadError.value = "暂时无法读取本地账本，请检查手机存储空间后重试。应用不会自动清空账本。"
            emit(LedgerData())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LedgerData())
    val preferences = repository.preferences.catch { error ->
        if (error is CancellationException) throw error
        notices.send(AppNotice("外观偏好暂时无法读取，已使用默认主题"))
        emit(UserPreferences())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val events = notices.receiveAsFlow()
    private val _pendingRestore = MutableStateFlow<BackupBundle?>(null)
    val pendingRestore = _pendingRestore.asStateFlow()
    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()
    private val _jumpMonth = MutableStateFlow<String?>(null)
    val jumpMonth = _jumpMonth.asStateFlow()

    init {
        task {
            withContext(Dispatchers.IO) {
                repository.initialize()
                _canUndo.value = container.backup.hasUndo()
            }
        }
    }

    private fun task(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val text = when (e) {
                    is IllegalArgumentException, is IllegalStateException -> e.message ?: "操作未完成，请重试"
                    is IOException -> "文件读写失败，请检查保存位置与剩余空间后重试"
                    else -> "操作未完成，当前账本已保留，请重试"
                }
                notices.send(AppNotice(text))
            } finally { _busy.value = false }
        }
    }

    fun saveExpense(record: Expense, after: () -> Unit) = task {
        withContext(Dispatchers.IO) { repository.saveExpense(record) }
        after()
        notices.send(AppNotice("已记入 ${record.expenseDate.take(7).replace("-", "年")}月", record.expenseDate.take(7)))
    }

    fun deleteExpense(id: String, after: () -> Unit) = task {
        withContext(Dispatchers.IO) { repository.deleteExpense(id) }
        after()
        notices.send(AppNotice("已删除这笔消费"))
    }

    fun saveCategory(category: Category, after: () -> Unit) = task {
        withContext(Dispatchers.IO) { repository.saveCategory(category) }
        after()
        notices.send(AppNotice("分类已保存"))
    }

    fun archiveCategory(category: Category) = task {
        withContext(Dispatchers.IO) { repository.archiveCategory(category.id, !category.isArchived) }
        notices.send(AppNotice(if (category.isArchived) "分类已恢复" else "分类已归档，历史账单保留"))
    }

    fun deleteCategory(id: String) = task {
        withContext(Dispatchers.IO) { repository.deleteCategory(id) }
        notices.send(AppNotice("分类已删除"))
    }

    fun moveCategory(id: String, direction: Int) = task {
        withContext(Dispatchers.IO) { repository.moveCategory(id, direction) }
    }

    fun setTheme(mode: String) = task { repository.setTheme(mode) }
    fun exportBackup(uri: Uri) = task {
        withContext(Dispatchers.IO) { container.backup.exportBackup(uri) }
        notices.send(AppNotice("完整备份已导出"))
    }
    fun previewRestore(uri: Uri) = task {
        _pendingRestore.value = withContext(Dispatchers.IO) { container.backup.readBackup(uri) }
    }
    fun cancelRestore() { _pendingRestore.value = null }
    fun applyRestore() {
        val backup = _pendingRestore.value ?: return
        task {
            withContext(Dispatchers.IO) { container.backup.restore(backup) }
            _pendingRestore.value = null
            _canUndo.value = withContext(Dispatchers.IO) { container.backup.hasUndo() }
            notices.send(AppNotice("账本已恢复，可以在设置中撤销本次恢复"))
        }
    }
    fun undoRestore() = task {
        withContext(Dispatchers.IO) { container.backup.undoRestore() }
        _canUndo.value = withContext(Dispatchers.IO) { container.backup.hasUndo() }
        notices.send(AppNotice("已撤销上次恢复"))
    }
    fun exportCsv(uri: Uri, month: YearMonth) = task {
        withContext(Dispatchers.IO) { container.backup.exportCsv(uri, month) }
        notices.send(AppNotice("${month.monthValue}月 CSV 已导出"))
    }
    fun jumpToMonth(month: String) { _jumpMonth.value = month }
    fun consumeJump() { _jumpMonth.value = null }
    fun retryLoad() { _loadError.value = null; reload.value += 1 }
}
