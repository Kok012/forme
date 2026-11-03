package com.personal.jizhang

import com.personal.jizhang.data.backup.BackupCodec
import com.personal.jizhang.model.BackupBundle
import com.personal.jizhang.model.DefaultCategories
import com.personal.jizhang.model.Expense
import com.personal.jizhang.util.CsvExport
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class BackupCodecTest {
    private val record = Expense("r1", 100, "builtin_food", "2024-02-29", "午餐,\"特别版\"\n备注", 1, 1)
    private fun bundle(rows: List<Expense> = listOf(record)) = BackupBundle(
        appVersion = "1.0.0", exportedAt = "2024-03-01T12:00:00Z", categories = DefaultCategories.items, records = rows,
    )

    @Test fun completeBackupRoundTripsWithoutLosingIdsOrText() {
        val original = bundle()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
    }

    @Test fun rejectsUnknownVersionsCountsDuplicatesAndDanglingReferences() {
        invalid { BackupCodec.validate(bundle().copy(version = 2)) }
        invalid { BackupCodec.validate(bundle().copy(recordCount = 2)) }
        invalid { BackupCodec.validate(bundle(listOf(record, record))) }
        invalid { BackupCodec.validate(bundle(listOf(record.copy(categoryId = "missing")))) }
        invalid { BackupCodec.validate(bundle().copy(categories = emptyList(), categoryCount = 0)) }
        invalid { BackupCodec.validate(bundle(listOf(record.copy(amountFen = -1)))) }
        invalid { BackupCodec.validate(bundle(listOf(record.copy(expenseDate = "2024-02-30")))) }
        invalid { BackupCodec.validate(bundle(listOf(record.copy(expenseDate = "9999-01-01")))) }
        invalid { BackupCodec.validate(bundle().copy(exportedAt = "not a time")) }
    }

    @Test fun protectsBuiltinFallbackAndClassificationIdentity() {
        val categories = DefaultCategories.items
        invalid {
            BackupCodec.validate(bundle().copy(categories = categories.map {
                if (it.id == DefaultCategories.OTHER_ID) it.copy(isArchived = true) else it
            }))
        }
        invalid {
            BackupCodec.validate(bundle().copy(categories = categories.mapIndexed { index, value ->
                if (index == 0) value.copy(name = categories[1].name) else value
            }))
        }
        invalid {
            BackupCodec.validate(bundle().copy(categories = categories.mapIndexed { index, value ->
                if (index == 0) value.copy(iconKey = "remote-url") else value
            }))
        }
    }

    @Test fun rejectsInvalidTypesAndMissingFormatVersion() {
        invalid { BackupCodec.decode("[]") }
        invalid { BackupCodec.decode("{broken}") }
        val json = BackupCodec.encode(bundle())
        invalid { BackupCodec.decode(json.replace("\"version\":1,", "")) }
        invalid { BackupCodec.decode(json.replace("\"amountFen\":100", "\"amountFen\":1.5")) }
    }

    @Test fun csvQuotesMultilineNotesAndDisarmsSpreadsheetFormulas() {
        val csv = CsvExport.encode(bundle(listOf(record.copy(note = "=SUM(A1:A2)"), record.copy(id = "r2"))), YearMonth.of(2024, 2))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"'=SUM(A1:A2)\""))
        assertTrue(csv.contains("\"午餐,\"\"特别版\"\"\n备注\""))
        assertTrue(csv.contains("\"1.00\""))
        val empty = CsvExport.encode(bundle(), YearMonth.of(2024, 3))
        assertEquals(1, empty.trimEnd().lines().size)
    }

    private fun invalid(block: () -> Unit) {
        try { block(); fail("Expected validation error") } catch (_: IllegalArgumentException) { }
    }
}
