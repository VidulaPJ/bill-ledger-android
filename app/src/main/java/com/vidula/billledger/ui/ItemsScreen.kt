package com.vidula.billledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidula.billledger.Labels
import com.vidula.billledger.Money
import com.vidula.billledger.analytics.Analytics
import com.vidula.billledger.analytics.Line
import com.vidula.billledger.data.titleCase

@Composable
fun ItemsScreen(lines: List<Line>, cur: String) {
    val l = LocalLedger.current
    val ranking = remember(lines) { Analytics.itemRanking(lines) }
    var item by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf<String?>(null) }
    var byYear by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = ranking.firstOrNull { it.equals(item, true) } ?: ranking.firstOrNull { it == "Milk" } ?: ranking.firstOrNull()
    val years = remember(lines) { lines.map { it.year }.filter { it.isNotBlank() }.distinct().sortedDescending() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text(if (selected != null) "Track an item: $selected" else "Track an item", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))
        Text("How often you bought it, how much, and what it cost.", color = l.mute, modifier = Modifier.padding(bottom = 12.dp))
        if (ranking.isEmpty() || selected == null) {
            Empty("Item lines appear once you add itemised receipts — groceries, fuel, pharmacy and so on. Each line on a receipt becomes an item you can track here.")
        } else {

        OutlinedTextField(
            value = query, onValueChange = { query = it }, label = { Text("Find an item") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { ranking.firstOrNull { it.contains(query.trim(), true) }?.let { item = it; query = "" } }),
            modifier = Modifier.fillMaxWidth()
        )
        val shown = if (query.isBlank()) ranking.take(12) else ranking.filter { it.contains(query.trim(), true) }.take(12)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { name ->
                FilterChip(selected = name == selected, onClick = { item = name; query = "" }, label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = l.red, selectedLabelColor = Color.White))
            }
            if (shown.isEmpty()) Text("No item called “${titleCase(query)}” yet.", color = l.mute)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = year == null, onClick = { year = null }, label = { Text("All years") })
            years.forEach { y -> FilterChip(selected = year == y, onClick = { year = y }, label = { Text(y) }) }
            Spacer(Modifier.padding(horizontal = 4.dp))
            FilterChip(selected = !byYear, onClick = { byYear = false }, label = { Text("By month") })
            FilterChip(selected = byYear, onClick = { byYear = true }, label = { Text("By year") })
        }

        val r = remember(lines, selected, year, byYear) { Analytics.itemReport(lines, selected, year, byYear) }

        Spacer(Modifier.height(14.dp))
        LedgerCard(dark = true) {
            Text("Spent on ${selected.lowercase()} · ${year ?: "all years"}", color = l.onInk.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
            Text(Money.format(r.cost, cur), color = l.onInk, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Row {
                Stat(r.times.toString(), "Times bought", Modifier.weight(1f))
                Stat(r.qtyText, "Quantity bought", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Stat(Money.format(r.average, cur), if (byYear) "Average a year" else "Average a month", Modifier.weight(1f))
                Stat(r.unitPrice?.let { "${Money.format(it, cur)}/${r.unit}" } ?: "—", "Price per unit", Modifier.weight(1f))
            }
        }

        SectionTitle(if (byYear) "Cost by year" else "Cost by month")
        val chrono = r.rows.asReversed().takeLast(12)
        BarChart(chrono.map { if (byYear) it.key else Labels.monthShort(it.key) }, chrono.map { it.cost }, { Money.short(it, cur) })

        SectionTitle(if (byYear) "Each year" else "Each month")
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            listOf("Period" to 1.4f, "Times" to 0.7f, "Quantity" to 1f, "Cost" to 1.1f).forEach { (h, w) ->
                Text(h, Modifier.weight(w), color = l.mute, style = MaterialTheme.typography.labelMedium, textAlign = if (h == "Period") TextAlign.Start else TextAlign.End)
            }
        }
        HorizontalDivider(color = l.ink, thickness = 2.dp)
        r.rows.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Column(Modifier.weight(1.4f)) {
                    Text(if (byYear) p.key else Labels.month(p.key))
                    p.unitPrice?.let { Text("${Money.format(it, cur)}/${p.unit}", color = l.mute, style = MaterialTheme.typography.labelMedium) }
                }
                Text(p.times.toString(), Modifier.weight(0.7f), textAlign = TextAlign.End)
                Text(p.qtyText, Modifier.weight(1f), textAlign = TextAlign.End)
                Text(Money.format(p.cost, cur), Modifier.weight(1.1f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = l.line)
        }
        }
        Spacer(Modifier.height(32.dp))
    }
}
