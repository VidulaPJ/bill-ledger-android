package com.vidula.billledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vidula.billledger.data.Bill
import com.vidula.billledger.data.BillWithItems
import com.vidula.billledger.data.CATEGORIES
import com.vidula.billledger.data.Item
import com.vidula.billledger.data.UNITS
import com.vidula.billledger.data.newBillId
import com.vidula.billledger.reading.cleanItem
import com.vidula.billledger.reading.round2
import java.time.LocalDate

private class ItemForm(item: String, qty: String, unit: String, amount: String, val name: String) {
    var item by mutableStateOf(item)
    var qty by mutableStateOf(qty)
    var unit by mutableStateOf(unit)
    var amount by mutableStateOf(amount)
}

private fun num(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
private fun isDate(s: String) = runCatching { LocalDate.parse(s) }.isSuccess

/** Full-screen form to add a bill by hand or fix one that was read. */
@Composable
fun EditBillDialog(existing: BillWithItems?, currency: String, onDismiss: () -> Unit, onSave: (Bill, List<Item>) -> Unit, onDelete: (String) -> Unit) {
    val l = LocalLedger.current
    val b = existing?.bill
    var vendor by remember { mutableStateOf(b?.vendor ?: "") }
    var date by remember { mutableStateOf(b?.date ?: LocalDate.now().toString()) }
    var due by remember { mutableStateOf(b?.due ?: "") }
    var total by remember { mutableStateOf(b?.total?.let { num(it) } ?: "") }
    var cur by remember { mutableStateOf(b?.currency ?: currency) }
    var category by remember { mutableStateOf(b?.category ?: "Other") }
    var notes by remember { mutableStateOf(b?.notes ?: "") }
    val items = remember { mutableStateListOf<ItemForm>().apply { existing?.items?.forEach { add(ItemForm(it.item, num(it.qty), it.unit, num(it.amount), it.name)) } } }
    var catOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val itemSum = items.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val valid = vendor.isNotBlank() && isDate(date) && (due.isBlank() || isDate(due)) && (total.toDoubleOrNull() != null || itemSum > 0) && cur.length == 3

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row {
                    Text(if (b == null) "Add a bill" else "Edit bill", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                OutlinedTextField(vendor, { vendor = it }, label = { Text("Payee") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("Bill date") }, placeholder = { Text("YYYY-MM-DD") }, isError = !isDate(date), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(due, { due = it }, label = { Text("Due date") }, placeholder = { Text("optional") }, isError = due.isNotBlank() && !isDate(due), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(total, { total = it }, label = { Text("Total") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { if (items.isNotEmpty()) Text("Items add up to ${"%.2f".format(itemSum)}") }, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(cur, { cur = it.uppercase().take(3) }, label = { Text("Currency") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Box {
                    OutlinedButton(onClick = { catOpen = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Category: $category") }
                    DropdownMenu(expanded = catOpen, onDismissRequest = { catOpen = false }) {
                        CATEGORIES.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { category = c; catOpen = false }) }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

                SectionTitle("Items on this bill")
                Text("Name each line as the item you want to track, e.g. Milk.", color = l.mute, style = MaterialTheme.typography.labelMedium)
                items.forEachIndexed { i, f ->
                    var unitOpen by remember(f) { mutableStateOf(false) }
                    Column(Modifier.padding(top = 10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(f.item, { f.item = it }, label = { Text("Item") }, singleLine = true, modifier = Modifier.weight(1f))
                            IconButton(onClick = { items.removeAt(i) }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Filled.Close, contentDescription = "Remove item", tint = l.red) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(f.qty, { f.qty = it }, label = { Text("Qty") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                            Box(Modifier.weight(1f).padding(top = 8.dp)) {
                                OutlinedButton(onClick = { unitOpen = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(f.unit) }
                                DropdownMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                                    UNITS.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { f.unit = u; unitOpen = false }) }
                                }
                            }
                            OutlinedTextField(f.amount, { f.amount = it }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1.2f))
                        }
                        if (f.name.isNotBlank() && !f.name.equals(f.item, true)) Text(f.name, color = l.mute, style = MaterialTheme.typography.labelMedium)
                        HorizontalDivider(color = l.line, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                TextButton(onClick = { items.add(ItemForm("", "1", "pcs", "", "")) }) { Text("Add item") }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (b != null) OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.height(48.dp)) { Text("Delete", color = l.red) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("Cancel") }
                    Button(enabled = valid, modifier = Modifier.height(48.dp), onClick = {
                        val its = items.mapNotNull { f ->
                            cleanItem(Item(item = f.item, name = f.name.ifBlank { f.item }, qty = f.qty.toDoubleOrNull() ?: 1.0, unit = f.unit, amount = f.amount.toDoubleOrNull() ?: 0.0))
                        }
                        val t = total.toDoubleOrNull() ?: its.sumOf { it.amount }
                        onSave(Bill(b?.id ?: newBillId(), date, vendor.trim(), category, round2(t), cur, due, notes.trim(), b?.createdAt ?: System.currentTimeMillis()), its)
                    }) { Text("Save") }
                }
                Spacer(Modifier.size(40.dp))
            }
        }
    }
    if (confirmDelete && b != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this bill?") },
        text = { Text("${b.vendor}, ${b.date}. Its items go too.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(b.id) }) { Text("Delete", color = l.red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
    )
}
