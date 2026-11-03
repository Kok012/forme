package com.personal.jizhang.util

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    const val MAX_FEN: Long = 999_999_999L
    private val amountPattern = Regex("(?:0|[1-9][0-9]{0,6})(?:\\.[0-9]{1,2})?")

    /** Parse decimal text without ever passing the monetary value through floating point. */
    fun parse(text: String): Long? {
        val value = text.trim()
        if (!amountPattern.matches(value)) return null
        return try {
            BigDecimal(value).movePointRight(2).longValueExact().takeIf { it in 1..MAX_FEN }
        } catch (_: ArithmeticException) { null }
    }

    fun format(fen: Long): String = BigDecimal.valueOf(fen, 2).setScale(2).toPlainString()

    fun formatWithSymbol(fen: Long): String = "¥${format(fen)}"

    fun percentage(partFen: Long, totalFen: Long): String = if (totalFen <= 0L) "0.00%" else
        BigDecimal.valueOf(partFen).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(totalFen), 2, RoundingMode.HALF_UP).toPlainString() + "%"
}
