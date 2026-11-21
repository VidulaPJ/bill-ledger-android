package com.vidula.billledger

import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Money {
    fun format(v: Double, currency: String, decimals: Int = 2): String = try {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            this.currency = Currency.getInstance(currency)
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(v)
    } catch (e: Exception) {
        "$currency ${"%,.${decimals}f".format(v)}"
    }

    fun short(v: Double, currency: String) = format(v, currency, 0)
}

object Labels {
    private val long = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val short = DateTimeFormatter.ofPattern("MMM")
    fun month(key: String) = runCatching { YearMonth.parse(key).format(long) }.getOrDefault(key)
    fun monthShort(key: String) = runCatching { YearMonth.parse(key).format(short) }.getOrDefault(key)
}
