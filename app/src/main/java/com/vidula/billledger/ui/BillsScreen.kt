package com.vidula.billledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidula.billledger.Money
import com.vidula.billledger.data.BillWithItems
import java.time.LocalDate

@Composable
fun BillsScreen(bills: List<BillWithItems>, cur: String, category: String?, onCategory: (String?) -> Unit, onOpen: (BillWithItems) -> Unit) {
    val l = LocalLedger.current
    var q by rememberSaveable { mutableStateOf("") }
    val cats = bills.map { it.bill.category }.distinct().sorted()
    val shown = bills.filter { b ->
        (category == null || b.bill.category == category) &&
            (q.isBlank() || "${b.bill.vendor} ${b.bill.notes} ${b.items.joinToString(" ") { it.item }}".contains(q.trim(), true))
    }
    val today = LocalDate.now().toString()
    val soon = LocalDate.now().plusDays(14).toString()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("All bills", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        OutlinedTextField(q, { q = it }, label = { Text("Search payee, item or note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == null, onClick = { onCategory(null) }, label = { Text("All") })
            cats.forEach { c -> FilterChip(selected = category == c, onClick = { onCategory(if (category == c) null else c) }, label = { Text(c) }) }
        }
        val inCur = shown.filter { it.bill.currency == cur }
        Text("${shown.size} bill${if (shown.size == 1) "" else "s"} · ${Money.format(inCur.sumOf { it.bill.total }, cur)}", color = l.mute, style = MaterialTheme.typography.labelMedium)
        if (shown.isEmpty()) Empty(if (bills.isEmpty()) "No bills yet. Add one from the Overview tab." else "No bills match.")
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.bill.id }) { b ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(b) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.bill.vendor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val due = if (b.bill.due.isNotBlank()) " · due ${b.bill.due}" else ""
                        Text("${b.bill.date} · ${b.bill.category}${if (b.items.isNotEmpty()) " · ${b.items.size} items" else ""}$due",
                            color = if (b.bill.due in today..soon) l.red else l.mute, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    Text(Money.format(b.bill.total, b.bill.currency), fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = l.line)
            }
        }
    }
}
