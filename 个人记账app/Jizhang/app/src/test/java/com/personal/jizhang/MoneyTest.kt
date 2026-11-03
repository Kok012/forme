package com.personal.jizhang

import com.personal.jizhang.model.Expense
import com.personal.jizhang.util.LedgerDates
import com.personal.jizhang.util.Money
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MoneyTest {
    @Test fun decimalAmountsAreExactAndBounded() {
        assertEquals(1L, Money.parse("0.01"))
        assertEquals(30L, Money.parse("0.10")!! + Money.parse("0.20")!!)
        assertEquals(2_800L, Money.parse("28"))
        assertEquals(2_850L, Money.parse("28.5"))
        assertEquals(Money.MAX_FEN, Money.parse("9999999.99"))
        assertEquals("0.30", Money.format(30))
        assertEquals("9999999.99", Money.format(Money.MAX_FEN))
        assertEquals("0.00%", Money.percentage(0, 0))
        assertEquals("33.33%", Money.percentage(1, 3))
    }

    @Test fun malformedOrRoundedInputsAreNeverSilentlyAccepted() {
        listOf("", "0", "0.00", "-1", "+1", "1.001", "10000000", "1e2", "NaN", "1,000", "1.", ".1", "00.20", "１２").forEach {
            assertNull("Should reject $it", Money.parse(it))
        }
    }

    @Test fun naturalDatesUseInclusiveStartAndExclusiveEnd() {
        val values = listOf(record("2023-12-31"), record("2024-01-01"), record("2024-01-31"), record("2024-02-01"))
        assertEquals(listOf("2024-01-01", "2024-01-31"), LedgerDates.inMonth(values, YearMonth.of(2024, 1)).map { it.expenseDate })
        assertNotNull(LedgerDates.parseDate("2024-02-29"))
        assertNull(LedgerDates.parseDate("2023-02-29"))
        assertNull(LedgerDates.parseDate("2024-2-9"))
        assertNull(LedgerDates.parseDate("0000-01-01"))
    }

    @Test fun dayChartFillsZerosAndStopsAtToday() {
        val month = YearMonth.of(2024, 2)
        val past = LedgerDates.dayTotals(listOf(record("2024-02-29")), month, LocalDate.of(2024, 3, 1))
        assertEquals(29, past.size)
        assertEquals(0L, past.first().second)
        assertEquals(100L, past.last().second)
        assertEquals(2, LedgerDates.dayTotals(emptyList(), month, LocalDate.of(2024, 2, 2)).size)
        assertTrue(LedgerDates.dayTotals(emptyList(), month, LocalDate.of(2024, 1, 1)).isEmpty())
    }

    private fun record(date: String) = Expense("r_$date", 100, "builtin_food", date, createdAt = 1, updatedAt = 1)
}
