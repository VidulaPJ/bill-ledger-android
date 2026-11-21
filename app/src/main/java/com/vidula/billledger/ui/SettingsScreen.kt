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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vidula.billledger.data.AppSettings
import com.vidula.billledger.data.ReadMode
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit,
    onLinkExcel: () -> Unit, onUnlink: () -> Unit, onSyncNow: () -> Unit, onExportCopy: () -> Unit, onImport: () -> Unit,
) {
    val l = LocalLedger.current
    var showKey by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp))

        SectionTitle("Excel workbook")
        LedgerCard {
            if (s.excelUri.isNotBlank()) {
                Text("Linked: ${s.excelName.ifBlank { "workbook" }}", fontWeight = FontWeight.SemiBold)
                Text(
                    "Rewritten after every bill you add, edit or delete" + if (s.lastExcelSync > 0) " · last updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastExcelSync))}" else "",
                    color = l.mute, style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Button(onClick = onSyncNow) { Text("Update now") }
                    OutlinedButton(onClick = onUnlink) { Text("Unlink") }
                }
            } else {
                Text("Create a workbook in Downloads, Google Drive or anywhere on your phone. The app keeps it up to date after every change.", color = l.mute)
                Button(onClick = onLinkExcel, modifier = Modifier.padding(top = 10.dp)) { Text("Create & link workbook") }
            }
            Text("Sheets: Bills, Items, Item by month, Item by year, Category by month. The summary sheets use formulas, so rows you add in Excel are counted too.",
                color = l.mute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onExportCopy) { Text("Save a copy") }
                TextButton(onClick = onImport) { Text("Load from Excel") }
            }
        }

        SectionTitle("Reading bills")
        listOf(
            ReadMode.ON_DEVICE to ("On the phone" to "Free and works offline. Good for totals and dates; item names may need a quick fix."),
            ReadMode.CLAUDE to ("Claude" to "Most accurate item names, quantities and categories. Uses your Anthropic API key and needs internet. If Claude can't be reached, the phone reads the bill instead."),
        ).forEach { (mode, text) ->
            Row(Modifier.fillMaxWidth().selectable(selected = s.readMode == mode, onClick = { onChange { it.copy(readMode = mode) } }).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                RadioButton(selected = s.readMode == mode, onClick = { onChange { it.copy(readMode = mode) } })
                Column(Modifier.padding(start = 6.dp, top = 10.dp)) {
                    Text(text.first, fontWeight = FontWeight.SemiBold)
                    Text(text.second, color = l.mute, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (s.readMode == ReadMode.CLAUDE) {
            OutlinedTextField(
                s.apiKey, { v -> onChange { it.copy(apiKey = v.trim()) } }, label = { Text("Anthropic API key") }, singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                supportingText = { Text(if (s.apiKey.isBlank()) "Get one at console.anthropic.com. It stays on this phone." else "Stored only on this phone.") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(s.model, { v -> onChange { it.copy(model = v.trim()) } }, label = { Text("Model") }, singleLine = true,
                supportingText = { Text("claude-sonnet-5-5 is a good balance; claude-haiku-5-5 is cheaper.") }, modifier = Modifier.fillMaxWidth())
        }

        SectionTitle("Main currency")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("AED", "USD", "EUR", "GBP", "INR", "SAR").forEach { c ->
                FilterChip(selected = s.currency == c, onClick = { onChange { it.copy(currency = c) } }, label = { Text(c) })
            }
        }
        Text("Totals and charts count bills in this currency.", color = l.mute, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(40.dp))
    }
}
