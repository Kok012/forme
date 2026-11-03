package com.personal.jizhang.data.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.personal.jizhang.data.LedgerRepository
import com.personal.jizhang.model.BackupBundle
import com.personal.jizhang.util.CsvExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CharacterCodingException
import java.time.YearMonth

class BackupService(context: Context, private val repository: LedgerRepository) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val undoFile = File(appContext.noBackupFilesDir, "before_restore.jizhang.json")

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val text = BackupCodec.encode(repository.snapshot())
        currentCoroutineContext().ensureActive()
        val output = requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开导出位置" }
        output.use { writeInChunks(it, text.toByteArray(Charsets.UTF_8)); it.flush() }
        currentCoroutineContext().ensureActive()
        repository.markBackupSuccess(System.currentTimeMillis())
    }

    suspend fun readBackup(uri: Uri): BackupBundle = withContext(Dispatchers.IO) {
        val input = requireNotNull(resolver.openInputStream(uri)) { "无法读取备份文件" }
        input.use { BackupCodec.decode(readBounded(it)) }
    }

    suspend fun restore(bundle: BackupBundle) = withContext(Dispatchers.IO) {
        repository.replaceLedger(bundle) { previous -> writeUndoSnapshot(previous) }
    }

    suspend fun undoRestore() = withContext(Dispatchers.IO) {
        require(hasUndo()) { "没有可以撤销的恢复" }
        val snapshot = AtomicFile(undoFile).openRead().use { BackupCodec.decode(readBounded(it)) }
        repository.replaceLedger(snapshot)
        AtomicFile(undoFile).delete()
    }

    fun hasUndo(): Boolean = undoFile.isFile || File(undoFile.path + ".bak").isFile

    suspend fun exportCsv(uri: Uri, month: YearMonth) = withContext(Dispatchers.IO) {
        val csv = CsvExport.encode(repository.snapshot(), month)
        val output = requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开导出位置" }
        output.use { writeInChunks(it, csv.toByteArray(Charsets.UTF_8)); it.flush() }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun writeUndoSnapshot(bundle: BackupBundle) {
        val bytes = BackupCodec.encode(bundle).toByteArray(Charsets.UTF_8)
        val atomic = AtomicFile(undoFile)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            writeInChunks(stream, bytes)
            currentCoroutineContext().ensureActive()
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private suspend fun readBounded(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer)
            if (size < 0) break
            total += size
            require(total <= BackupCodec.MAX_BYTES) { "备份文件不能超过 50MB" }
            output.write(buffer, 0, size)
        }
        return try { output.toByteArray().decodeToString(throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { throw IllegalArgumentException("备份文件编码损坏，需要 UTF-8 文件") }
    }

    private suspend fun writeInChunks(output: OutputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            currentCoroutineContext().ensureActive()
            val count = minOf(8192, bytes.size - offset)
            output.write(bytes, offset, count)
            offset += count
        }
    }
}
