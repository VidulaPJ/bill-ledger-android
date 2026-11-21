package com.vidula.billledger.analytics

import com.vidula.billledger.data.BillWithItems
import java.time.LocalDate
import java.time.YearMonth

data class Share(val name: String, val total: Double, val count: Int, val share: Double)
data class Recurring(val vendor: String, val months: Int, val avg: Double)
data class Overview(
    val latest: String?, val latestTotal: Double,
    val previous: String?, val previousTotal: Double,
    val monthlyAverage: Double, val dueSoon: Double, val dueSoonCount: Int,
    val months: List<Pair<String, Double>>,     // last 12 months, oldest first
    val categories: List<Share>,
    val recurring: List<Recurring>,
    val topVendors: List<Share>,
    val billCount: Int, val otherCurrencyCount: Int,
)

data class PeriodRow(val key: String, val times: Int, val qtyText: String, val cost: Double, val unitPrice: Double?, val unit: String?)
data class ItemReport(
    val item: String, val cost: Double, val times: Int, val qtyText: String,
    val average: Double, val unitPrice: Double?, val unit: String?, val rows: List<PeriodRow>,
)

/** One purchased line, flattened with its bill's date. */
data class Line(val billId: String, val date: String, val vendor: String, val item: String, val qty: Double, val unit: String, val amount: Double) {
    val month get() = date.take(7)
    val year get() = date.take(4)
}

object Analytics {

    fun inCurrency(all: List<BillWithItems>, cur: String) = all.filter { it.bill.currency.ifBlank { cur } == cur }

    fun lines(all: List<BillWithItems>, cur: String): List<Line> = inCurrency(all, cur).flatMap { b ->
        b.items.map { Line(b.bill.id, b.bill.date, b.bill.vendor, it.item, it.qty, it.unit, it.amount) }
    }

    /** Every month from the first to the last key, with no gaps. */
    fun monthSpan(keys: Collection<String>, max: Int = Int.MAX_VALUE): List<String> {
        val sorted = keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }.sorted()
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var m = sorted.first()
        while (m <= sorted.last()) { out += m.toString(); m = m.plusMonths(1) }
        return out.takeLast(max)
    }

    fun overview(all: List<BillWithItems>, cur: String): Overview {
        val bills = inCurrency(all, cur).map { it.bill }
        val byMonth = bills.groupBy { it.month }
        val span = monthSpan(byMonth.keys)
        val latest = span.lastOrNull()
        val previous = span.dropLast(1).lastOrNull()
        val total = bills.sumOf { it.total }
        val today = LocalDate.now()
        val due = bills.filter { it.due.isNotBlank() && runCatching { LocalDate.parse(it.due) }.getOrNull()?.let { d -> !d.isBefore(today) && !d.isAfter(today.plusDays(14)) } == true }
        val cats = bills.groupBy { it.category }.map { (k, v) -> Share(k, v.sumOf { it.total }, v.size, if (total > 0) v.sumOf { it.total } / total else 0.0) }
            .sortedByDescending { it.total }
        val byVendor = bills.groupBy { it.vendor.trim().lowercase() }
        val recurring = byVendor.values.map { v -> Recurring(v.first().vendor, v.map { it.month }.toSet().size, v.sumOf { it.total } / v.size) }
            .filter { it.months >= 2 }.sortedByDescending { it.avg }
        val vendors = byVendor.values.map { v -> Share(v.first().vendor, v.sumOf { it.total }, v.size, if (total > 0) v.sumOf { it.total } / total else 0.0) }
            .sortedByDescending { it.total }
        return Overview(
            latest, latest?.let { m -> byMonth[m]?.sumOf { it.total } } ?: 0.0,
            previous, previous?.let { m -> byMonth[m]?.sumOf { it.total } } ?: 0.0,
            if (span.isNotEmpty()) total / span.size else 0.0, due.sumOf { it.total }, due.size,
            span.takeLast(12).map { m -> m to (byMonth[m]?.sumOf { it.total } ?: 0.0) },
            cats, recurring, vendors.take(7), all.size, all.size - bills.size,
        )
    }

    /** Items ordered by how often they are bought, then by spend. */
    fun itemRanking(lines: List<Line>): List<String> =
        lines.groupBy { it.item }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Line>>> { e -> e.value.map { it.billId }.toSet().size }.thenByDescending { e -> e.value.sumOf { it.amount } })
            .map { it.key }

    fun qtyText(lines: List<Line>): String {
        val byUnit = lines.filter { it.unit != "service" }.groupBy { it.unit }.mapValues { (_, v) -> v.sumOf { it.qty } }
        return byUnit.entries.joinToString(" + ") { (u, q) -> "${trim(q)} $u" }.ifBlank { "—" }
    }

    private fun unitPrice(lines: List<Line>): Pair<Double?, String?> {
        val q = lines.filter { it.unit != "service" }
        val units = q.map { it.unit }.toSet()
        val qty = q.sumOf { it.qty }
        return if (units.size == 1 && qty > 0) q.sumOf { it.amount } / qty to units.first() else null to null
    }

    /** How often an item was bought, how much and what it cost, per month or per year. */
    fun itemReport(lines: List<Line>, item: String, year: String?, byYear: Boolean): ItemReport {
        val mine = lines.filter { it.item.equals(item, true) && (year == null || it.year == year) }
        val groups = mine.groupBy { if (byYear) it.year else it.month }
        val keys = if (byYear) groups.keys.sorted() else monthSpan(groups.keys)
        val rows = keys.map { k ->
            val g = groups[k].orEmpty()
            val (up, u) = unitPrice(g)
            PeriodRow(k, g.map { it.billId }.toSet().size, qtyText(g), g.sumOf { it.amount }, up, u)
        }
        val (up, u) = unitPrice(mine)
        val cost = mine.sumOf { it.amount }
        val periods = if (byYear) keys.size else monthSpan(mine.map { it.month }).size
        return ItemReport(item, cost, mine.map { it.billId }.toSet().size, qtyText(mine),
            if (periods > 0) cost / periods else 0.0, up, u, rows.asReversed().toList())
    }

    fun trim(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
