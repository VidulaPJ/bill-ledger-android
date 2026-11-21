package com.vidula.billledger.excel

import com.vidula.billledger.data.Bill
import com.vidula.billledger.data.BillWithItems
import com.vidula.billledger.data.CATEGORIES
import com.vidula.billledger.data.Item
import com.vidula.billledger.data.newBillId
import com.vidula.billledger.reading.cleanItem
import com.vidula.billledger.reading.round2
import java.time.LocalDate

/**
 * The Bill Ledger workbook: Bills, Items, and formula sheets that total each item
 * per month and per year (times bought, quantity, cost, price per unit) and each category per month.
 */
object Workbook {
    private val EPOCH = LocalDate.of(1899, 12, 30).toEpochDay()
    private fun serial(d: String) = runCatching { (LocalDate.parse(d).toEpochDay() - EPOCH).toDouble() }.getOrNull()
    private fun dateCell(d: String) = serial(d)?.let { Cell.Num(it, Style.DATE) } ?: Cell.Text(d)
    private fun money(v: Double) = Cell.Num(v, Style.MONEY)
    private fun t(s: String) = Cell.Text(s)

    fun build(all: List<BillWithItems>, defaultCurrency: String): ByteArray {
        val bills = all.sortedBy { it.bill.date }
        val billRows = bills.map { (b, _) ->
            listOf(t(b.id), dateCell(b.date), t(b.month), t(b.vendor), t(b.category), money(b.total), t(b.currency.ifBlank { defaultCurrency }), if (b.due.isBlank()) Cell.Empty else dateCell(b.due), t(b.notes))
        }
        data class L(val bill: Bill, val i: Item)
        val lines = bills.flatMap { bw -> bw.items.map { L(bw.bill, it) } }
        val itemRows = lines.map { (b, i) ->
            listOf(t(b.id), dateCell(b.date), t(b.month), Cell.Num(b.year.toDoubleOrNull() ?: 0.0), t(b.vendor), t(i.item), t(i.name), Cell.Num(i.qty), t(i.unit), money(i.amount), t(b.currency.ifBlank { defaultCurrency }))
        }

        // Item by month: one row per item and month, every figure a formula over the Items sheet
        val im = lines.groupBy { it.i.item to it.bill.month }.toSortedMap(compareBy<Pair<String, String>> { it.first.lowercase() }.thenBy { it.second })
        val itemMonth = im.entries.mapIndexed { idx, (k, g) ->
            val n = idx + 2
            val q = g.filter { it.i.unit != "service" }.sumOf { it.i.qty }
            val c = g.sumOf { it.i.amount }
            val crit = "Items!\$F:\$F,\$A$n,Items!\$C:\$C,\$B$n"
            listOf(t(k.first), t(k.second),
                Cell.Formula("COUNTIFS($crit)", g.size.toDouble()),
                Cell.Formula("SUMIFS(Items!\$H:\$H,$crit)", q),
                Cell.Formula("SUMIFS(Items!\$J:\$J,$crit)", c, Style.MONEY),
                Cell.Formula("IFERROR(E$n/D$n,\"\")", if (q > 0) c / q else 0.0, Style.MONEY))
        }

        // Item by year, with the average per month the item was bought in
        val iy = lines.groupBy { it.i.item to it.bill.year }.toSortedMap(compareBy<Pair<String, String>> { it.first.lowercase() }.thenBy { it.second })
        val itemYear = iy.entries.mapIndexed { idx, (k, g) ->
            val n = idx + 2
            val q = g.filter { it.i.unit != "service" }.sumOf { it.i.qty }
            val c = g.sumOf { it.i.amount }
            val months = g.map { it.bill.month }.toSet().size
            val crit = "Items!\$F:\$F,\$A$n,Items!\$D:\$D,\$B$n"
            listOf(t(k.first), Cell.Num(k.second.toDoubleOrNull() ?: 0.0),
                Cell.Formula("COUNTIFS($crit)", g.size.toDouble()),
                Cell.Formula("SUMIFS(Items!\$H:\$H,$crit)", q),
                Cell.Formula("SUMIFS(Items!\$J:\$J,$crit)", c, Style.MONEY),
                Cell.Formula("COUNTIFS('Item by month'!\$A:\$A,\$A$n,'Item by month'!\$B:\$B,\$B$n&\"-*\")", months.toDouble()),
                Cell.Formula("IFERROR(E$n/F$n,\"\")", if (months > 0) c / months else 0.0, Style.MONEY))
        }

        val cm = bills.map { it.bill }.groupBy { it.category to it.month }.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
        val catMonth = cm.entries.mapIndexed { idx, (k, g) ->
            val n = idx + 2
            val crit = "Bills!\$E:\$E,\$A$n,Bills!\$C:\$C,\$B$n"
            listOf(t(k.first), t(k.second), Cell.Formula("COUNTIFS($crit)", g.size.toDouble()), Cell.Formula("SUMIFS(Bills!\$F:\$F,$crit)", g.sumOf { it.total }, Style.MONEY))
        }

        return XlsxWriter.write(listOf(
            Sheet("Bills", listOf("Bill ID", "Date", "Month", "Payee", "Category", "Total", "Currency", "Due", "Note"), listOf(18, 12, 9, 26, 14, 12, 9, 12, 30), billRows),
            Sheet("Items", listOf("Bill ID", "Date", "Month", "Year", "Payee", "Item", "Product", "Quantity", "Unit", "Amount", "Currency"), listOf(18, 12, 9, 7, 22, 18, 34, 10, 8, 12, 9), itemRows),
            Sheet("Item by month", listOf("Item", "Month", "Times bought", "Quantity", "Cost", "Price per unit"), listOf(20, 9, 13, 10, 12, 14), itemMonth),
            Sheet("Item by year", listOf("Item", "Year", "Times bought", "Quantity", "Cost", "Months bought in", "Average a month"), listOf(20, 7, 13, 10, 12, 16, 16), itemYear),
            Sheet("Category by month", listOf("Category", "Month", "Bills", "Cost"), listOf(16, 9, 8, 12), catMonth),
        ))
    }

    private fun date(v: String): String? {
        val s = v.trim()
        s.toDoubleOrNull()?.let { return LocalDate.ofEpochDay(EPOCH + it.toLong()).toString() }
        return runCatching { LocalDate.parse(s.take(10)).toString() }.getOrNull()
    }
    private val ID = Regex("^[A-Za-z0-9_-]{3,60}$")

    /** Turns the Bills and Items sheets back into bills. Rows on Items with an unknown or blank Bill ID become their own bill. */
    fun parse(sheets: Map<String, List<Map<String, String>>>, existing: Map<String, BillWithItems>, defaultCurrency: String): List<BillWithItems> {
        val billSheet = sheets["Bills"]
        val itemSheet = sheets["Items"]
        require(billSheet != null || itemSheet != null) { "No Bills or Items sheet in that workbook" }
        val bills = linkedMapOf<String, Bill>()
        val items = linkedMapOf<String, MutableList<Item>>()
        billSheet?.forEach { r ->
            val d = date(r["Date"].orEmpty()) ?: return@forEach
            val payee = r["Payee"].orEmpty().trim().ifBlank { return@forEach }
            val id = r["Bill ID"].orEmpty().trim().takeIf { ID.matches(it) } ?: newBillId()
            val old = existing[id]?.bill
            bills[id] = Bill(id, d, payee.take(80), r["Category"].orEmpty().takeIf { it in CATEGORIES } ?: "Other",
                round2(r["Total"]?.toDoubleOrNull() ?: 0.0), r["Currency"].orEmpty().uppercase().take(3).ifBlank { defaultCurrency },
                r["Due"]?.let { date(it) }.orEmpty(), r["Note"].orEmpty().take(120), old?.createdAt ?: System.currentTimeMillis())
            if (itemSheet == null) items[id] = existing[id]?.items.orEmpty().toMutableList()
        }
        itemSheet?.forEach { r ->
            val line = cleanItem(Item(item = r["Item"].orEmpty(), name = r["Product"].orEmpty(), qty = r["Quantity"]?.toDoubleOrNull() ?: 1.0,
                unit = r["Unit"].orEmpty(), amount = r["Amount"]?.toDoubleOrNull() ?: 0.0)) ?: return@forEach
            var id = r["Bill ID"].orEmpty().trim()
            if (id !in bills) {
                val old = existing[id]?.bill
                if (old != null) bills[id] = old
                else {
                    val d = date(r["Date"].orEmpty()) ?: return@forEach
                    id = id.takeIf { ID.matches(it) } ?: newBillId()
                    bills[id] = Bill(id, d, r["Payee"].orEmpty().ifBlank { "Added in Excel" }.take(80), "Groceries", 0.0,
                        r["Currency"].orEmpty().uppercase().take(3).ifBlank { defaultCurrency }, notes = "Added in Excel")
                }
            }
            items.getOrPut(id) { mutableListOf() } += line
        }
        return bills.values.map { b ->
            val its = items[b.id].orEmpty()
            // a bill made only from Item rows takes its total from them
            val fixed = if (b.total == 0.0 && its.isNotEmpty()) b.copy(total = round2(its.sumOf { it.amount })) else b
            BillWithItems(fixed, its)
        }
    }
}
