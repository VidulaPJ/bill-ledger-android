package com.vidula.billledger.reading

import com.vidula.billledger.data.CATEGORIES
import com.vidula.billledger.data.Item
import com.vidula.billledger.data.UNITS
import com.vidula.billledger.data.titleCase
import java.time.LocalDate

/** What a reader found on one bill, before it is saved. */
data class Draft(
    val vendor: String,
    val date: String,
    val due: String,
    val total: Double,
    val currency: String,
    val category: String,
    val items: List<Item>,
    val notes: String = "",
)

class NotABillException : Exception("That doesn't look like a bill")

private val ISO = Regex("^\\d{4}-\\d{2}-\\d{2}$")

fun cleanDraft(d: Draft, defaultCurrency: String) = d.copy(
    vendor = d.vendor.trim().ifBlank { "Unknown" }.take(80),
    date = if (ISO.matches(d.date)) d.date else LocalDate.now().toString(),
    due = if (ISO.matches(d.due)) d.due else "",
    total = round2(d.total),
    currency = d.currency.uppercase().take(3).ifBlank { defaultCurrency },
    category = if (d.category in CATEGORIES) d.category else "Other",
    items = d.items.mapNotNull { cleanItem(it) }.take(60),
    notes = d.notes.take(120),
)

fun cleanItem(i: Item): Item? {
    val generic = titleCase(i.item.ifBlank { i.name })
    if (generic.isBlank()) return null
    return i.copy(
        item = generic,
        name = i.name.trim().ifBlank { generic }.take(80),
        qty = if (i.qty > 0) Math.round(i.qty * 1000) / 1000.0 else 1.0,
        unit = if (i.unit in UNITS) i.unit else "pcs",
        amount = round2(i.amount),
    )
}

fun round2(v: Double) = Math.round(v * 100) / 100.0
