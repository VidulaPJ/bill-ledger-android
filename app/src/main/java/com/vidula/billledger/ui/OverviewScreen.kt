package com.vidula.billledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidula.billledger.Labels
import com.vidula.billledger.Money
import com.vidula.billledger.QState
import com.vidula.billledger.QueueItem
import com.vidula.billledger.analytics.Overview

@Composable
fun OverviewScreen(
    o: Overview, cur: String, queue: List<QueueItem>, hasCamera: Boolean,
    onCamera: () -> Unit, onFiles: () -> Unit, onManual: () -> Unit, onClearQueue: () -> Unit, onCategory: (String) -> Unit,
) {
    val l = LocalLedger.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---- steel panel: headline, add buttons, readout
        Column(Modifier.fillMaxWidth().background(l.panel).padding(horizontal = 18.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("■ ", color = l.red, fontSize = 16.sp)
                Text("Bill Ledger", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            val change = if (o.previousTotal > 0) (o.latestTotal - o.previousTotal) / o.previousTotal * 100 else null
            Text(
                if (o.latest == null) "Your bills, sorted and added up"
                else "${Money.short(o.latestTotal, cur)} on bills in ${Labels.monthShort(o.latest)}" +
                    (change?.let { ", ${if (it >= 0) "up" else "down"} ${"%.0f".format(kotlin.math.abs(it))}% on ${Labels.monthShort(o.previous!!)}" } ?: ""),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                o.categories.firstOrNull()?.let { "${it.name} was the largest share at ${Money.short(it.total, cur)}." }
                    ?: "Photograph or upload bills and receipts. Each one is read, sorted into a category and added up.",
                color = l.mute, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasCamera) Button(onClick = onCamera, colors = ButtonDefaults.buttonColors(containerColor = l.red, contentColor = androidx.compose.ui.graphics.Color.White), modifier = Modifier.weight(1f).height(48.dp)) { Text("Take photo") }
                OutlinedButton(onClick = onFiles, modifier = Modifier.weight(1f).height(48.dp)) { Text(if (hasCamera) "Choose files" else "Upload bills") }
            }
            TextButton(onClick = onManual) { Text("Add a bill by hand") }
            if (queue.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(l.card).padding(10.dp)) {
                    queue.forEach { q ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(q.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            Text(q.message, color = if (q.state == QState.FAILED) l.red else l.mute, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                        }
                    }
                    if (queue.none { it.state == QState.READING || it.state == QState.WAITING })
                        TextButton(onClick = onClearQueue) { Text("Clear") }
                }
            }
            Spacer(Modifier.height(14.dp))
            LedgerCard(dark = true) {
                Text(o.latest?.let { Labels.month(it) } ?: "Latest month", color = l.onInk.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                Text(Money.short(o.latestTotal, cur), color = l.onInk, fontSize = 44.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (change != null) Text(
                    "${if (change > 0) "▲" else "▼"} ${"%.0f".format(kotlin.math.abs(change))}% vs ${Labels.monthShort(o.previous!!)} (${Money.short(o.previousTotal, cur)})",
                    color = if (change > 0) androidx.compose.ui.graphics.Color(0xFFFF6A5F) else androidx.compose.ui.graphics.Color(0xFF7BD88F), fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Stat(o.billCount.toString(), "All bills", Modifier.weight(1f))
                    Stat(if (o.monthlyAverage > 0) Money.short(o.monthlyAverage, cur) else "—", "Monthly average", Modifier.weight(1f))
                    Stat(if (o.dueSoonCount > 0) Money.short(o.dueSoon, cur) else "None", "Due in 14 days", Modifier.weight(1f))
                }
            }
        }

        Column(Modifier.padding(horizontal = 18.dp)) {
            SectionTitle("Where the money goes")
            if (o.categories.isEmpty()) Empty("No bills yet. Add your first one above.")
            val max = o.categories.firstOrNull()?.total ?: 1.0
            o.categories.forEachIndexed { i, c ->
                Strip(c.name, (c.total / max).toFloat(), Money.short(c.total, cur), "${"%.0f".format(c.share * 100)}%", i == 0) { onCategory(c.name) }
            }
            if (o.categories.isNotEmpty()) Text("Tap a category to see its bills.", color = l.mute, style = MaterialTheme.typography.labelMedium)

            SectionTitle("Month by month, $cur")
            if (o.months.isEmpty()) Empty("The chart fills in as bills are added.")
            else BarChart(o.months.map { Labels.monthShort(it.first) }, o.months.map { it.second }, { Money.short(it, cur) })

            SectionTitle("Recurring bills")
            if (o.recurring.isEmpty()) Empty("Bills from the same payee in two or more months show up here.")
            o.recurring.take(7).forEach { ListRow(it.vendor, "${Money.short(it.avg, cur)}/mo", "${it.months} months") }
            if (o.recurring.isNotEmpty()) ListRow("Fixed monthly load", Money.short(o.recurring.sumOf { it.avg }, cur), bold = true)

            SectionTitle("Biggest payees")
            if (o.topVendors.isEmpty()) Empty("Nothing yet.")
            o.topVendors.forEach { ListRow(it.name, Money.short(it.total, cur), "${it.count} bill${if (it.count == 1) "" else "s"}") }
            if (o.otherCurrencyCount > 0) Text("${o.otherCurrencyCount} bill(s) in other currencies are left out of these totals.", color = l.mute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}
