package com.personal.jizhang

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personal.jizhang.data.LedgerRepository
import com.personal.jizhang.data.backup.BackupService
import com.personal.jizhang.data.local.LedgerDatabase
import com.personal.jizhang.model.Category
import com.personal.jizhang.model.DefaultCategories
import com.personal.jizhang.model.Expense
import com.personal.jizhang.model.LedgerData
import com.personal.jizhang.util.LedgerDates
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: LedgerRepository
    private lateinit var database: LedgerDatabase
    private lateinit var backup: BackupService

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        repository = LedgerRepository(context, database)
        backup = BackupService(context, repository)
        AtomicFile(File(context.noBackupFilesDir, "before_restore.jizhang.json")).delete()
        repository.initialize()
    }

    @After fun tearDown() { repository.close() }

    @Test fun initializesAnEmptyLedgerAndRetriesAreIdempotent() = runBlocking {
        assertEquals(12, repository.snapshot().categories.size)
        assertTrue(repository.snapshot().records.isEmpty())
        repository.saveExpense(expense())
        repository.saveExpense(expense())
        assertEquals(1, repository.snapshot().records.size)
        assertEquals(2800L, repository.snapshot().records.sumOf { it.amountFen })
    }

    @Test fun editsMoveAcrossMonthsAndArchiveKeepsHistory() = runBlocking {
        repository.saveExpense(expense())
        repository.archiveCategory("builtin_food", true)
        repository.saveExpense(expense().copy(expenseDate = "2024-02-29", updatedAt = 2))
        val records = repository.snapshot().records
        assertTrue(LedgerDates.inMonth(records, YearMonth.of(2024, 1)).isEmpty())
        assertEquals(1, LedgerDates.inMonth(records, YearMonth.of(2024, 2)).size)
        invalid { repository.saveExpense(expense().copy(id = "new_id")) }
        invalid { repository.archiveCategory(DefaultCategories.OTHER_ID, true) }
    }

    @Test fun categoryNamesAndReferencesArePreserved() = runBlocking {
        val custom = Category("custom", "宠物", sortOrder = 12)
        repository.saveCategory(custom)
        repository.saveExpense(expense().copy(categoryId = custom.id))
        invalid { repository.deleteCategory(custom.id) }
        invalid { repository.saveCategory(custom.copy(id = "another")) }
        repository.saveCategory(custom.copy(name = "猫咪"))
        assertEquals("猫咪", repository.snapshot().categories.first { it.id == custom.id }.name)
        repository.archiveCategory(custom.id, true)
        assertEquals(1, repository.snapshot().records.size)
        repository.deleteExpense("r1")
        repository.deleteCategory(custom.id)
        assertFalse(repository.snapshot().categories.any { it.id == custom.id })
    }

    @Test fun restoresAtomicallyAndCanUndoTheReplacement() = runBlocking {
        repository.saveExpense(expense())
        val imported = repository.snapshot().copy(records = listOf(expense().copy(id = "imported", amountFen = 900)))
        backup.restore(imported)
        assertEquals("imported", repository.snapshot().records.single().id)
        assertTrue(backup.hasUndo())
        backup.undoRestore()
        assertEquals("r1", repository.snapshot().records.single().id)
        assertFalse(backup.hasUndo())
        invalid { backup.restore(imported.copy(recordCount = 999)) }
        assertEquals("r1", repository.snapshot().records.single().id)
    }

    @Test fun jsonExportAndReadPreserveTheActualDatabase() = runBlocking {
        repository.saveExpense(expense())
        val target = File(context.cacheDir, "test-export.jizhang.json")
        val uri = Uri.fromFile(target)
        backup.exportBackup(uri)
        val loaded = backup.readBackup(uri)
        assertEquals(repository.snapshot().records, loaded.records)
        assertEquals(repository.snapshot().categories, loaded.categories)
        target.writeText("{broken}")
        invalid { backup.readBackup(uri) }
        assertEquals(1, repository.snapshot().records.size)
    }

    @Test fun editsRefreshTheObservedLedgerEvenWhenRowCountDoesNotChange() = runBlocking {
        val channel = Channel<LedgerData>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) { repository.observeLedger().collect { channel.send(it) } }
        try {
            withTimeout(10_000) { channel.receive() }
            repository.saveExpense(expense())
            withTimeout(10_000) {
                while (channel.receive().records.size != 1) { }
            }
            repository.saveExpense(expense().copy(amountFen = 3000, note = "编辑后", updatedAt = 2))
            val changed = withTimeout(10_000) {
                var current = channel.receive()
                while (current.records.singleOrNull()?.note != "编辑后") current = channel.receive()
                current
            }
            assertEquals(3000L, changed.records.single().amountFen)
            assertEquals(1, changed.records.size)
        } finally { collector.cancelAndJoin(); channel.close() }
    }

    @Test fun outputWriteAndCloseFailuresDoNotClaimBackupSuccess() = runBlocking {
        repository.saveExpense(expense())
        repository.markBackupSuccess(1234)
        val before = repository.snapshot().records
        val uri = Uri.parse("content://test/failed-backup")
        shadowOf(context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(value: Int) { throw IOException("模拟空间不足") }
        })
        failure { backup.exportBackup(uri) }
        assertEquals(1234L, repository.preferences.first().lastBackupAt)
        assertEquals(before, repository.snapshot().records)
        shadowOf(context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(value: Int) { }
            override fun close() { throw IOException("模拟关闭写入失败") }
        })
        failure { backup.exportBackup(uri) }
        assertEquals(1234L, repository.preferences.first().lastBackupAt)
        assertEquals(before, repository.snapshot().records)
    }

    @Test fun cancelledOrFailedReadsDoNotChangeLedgerOrBackupTime() = runBlocking {
        repository.saveExpense(expense())
        repository.markBackupSuccess(1234)
        val before = repository.snapshot().records
        val uri = Uri.parse("content://test/cancelled-read")
        shadowOf(context.contentResolver).registerInputStream(uri, object : InputStream() {
            override fun read(): Int { throw CancellationException("模拟取消读取") }
        })
        failure { backup.readBackup(uri) }
        assertEquals(before, repository.snapshot().records)
        shadowOf(context.contentResolver).registerInputStream(uri, object : InputStream() {
            override fun read(): Int { throw IOException("模拟读取错误") }
        })
        failure { backup.readBackup(uri) }
        assertEquals(before, repository.snapshot().records)
        assertEquals(1234L, repository.preferences.first().lastBackupAt)
    }

    @Test fun cancelledOutputNeverChangesRecentBackupTime() = runBlocking {
        repository.markBackupSuccess(1234)
        val uri = Uri.parse("content://test/cancelled-write")
        shadowOf(context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(value: Int) { throw CancellationException("模拟取消写入") }
        })
        failure { backup.exportBackup(uri) }
        assertEquals(1234L, repository.preferences.first().lastBackupAt)
        assertTrue(repository.snapshot().records.isEmpty())
    }

    @Test fun sqlFailureDuringReplacementRollsBackBothTables() = runBlocking {
        repository.saveExpense(expense())
        val before = repository.snapshot()
        val imported = before.copy(records = listOf(expense().copy(id = "imported")))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_restore BEFORE INSERT ON expense_records WHEN NEW.id = 'imported' BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END",
        )
        failure { backup.restore(imported) }
        assertEquals(before.records, repository.snapshot().records)
        assertEquals(before.categories, repository.snapshot().categories)
        assertTrue(backup.hasUndo())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_restore")
        backup.undoRestore()
        assertEquals(before.records, repository.snapshot().records)
    }

    @Test fun snapshotWriteFailurePreventsDatabaseReplacement() = runBlocking {
        repository.saveExpense(expense())
        val before = repository.snapshot()
        val blocked = File(context.noBackupFilesDir, "before_restore.jizhang.json.new")
        check(blocked.mkdirs())
        try {
            failure { backup.restore(before.copy(records = emptyList(), recordCount = 0)) }
            assertEquals(before.records, repository.snapshot().records)
            assertEquals(before.categories, repository.snapshot().categories)
        } finally { blocked.delete() }
    }

    @Test fun preservesCreationTimestampWithoutAllowingInvalidUpdateMetadata() = runBlocking {
        repository.saveExpense(expense().copy(createdAt = 100, updatedAt = 100))
        invalid { repository.saveExpense(expense().copy(createdAt = 1, updatedAt = 1)) }
        assertEquals(100L, repository.snapshot().records.single().createdAt)
        assertEquals(100L, repository.snapshot().records.single().updatedAt)
    }

    private fun expense() = Expense("r1", 2800, "builtin_food", "2024-01-31", "午餐", 1, 1)

    private suspend fun invalid(block: suspend () -> Unit) {
        try { block(); fail("Expected validation error") } catch (_: IllegalArgumentException) { }
    }

    private suspend fun failure(block: suspend () -> Unit) {
        try { block(); fail("Expected operation to fail") } catch (_: Exception) { }
    }
}
