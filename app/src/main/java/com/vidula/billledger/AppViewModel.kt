package com.vidula.billledger

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidula.billledger.data.AppDb
import com.vidula.billledger.data.AppSettings
import com.vidula.billledger.data.Bill
import com.vidula.billledger.data.BillWithItems
import com.vidula.billledger.data.Item
import com.vidula.billledger.data.ReadMode
import com.vidula.billledger.data.SettingsStore
import com.vidula.billledger.data.newBillId
import com.vidula.billledger.excel.Workbook
import com.vidula.billledger.excel.XlsxReader
import com.vidula.billledger.reading.ClaudeException
import com.vidula.billledger.reading.ClaudeReader
import com.vidula.billledger.reading.Draft
import com.vidula.billledger.reading.NotABillException
import com.vidula.billledger.reading.OnDeviceReader
import com.vidula.billledger.reading.loadPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class QState { WAITING, READING, DONE, FAILED }
data class QueueItem(val key: Long, val name: String, val state: QState, val message: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).bills()
    private val store = SettingsStore(app)

    val bills: StateFlow<List<BillWithItems>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _settings = MutableStateFlow(store.load())
    val settings = _settings.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue = _queue.asStateFlow()

    /** One-line messages shown as a snackbar. */
    val message = MutableStateFlow<String?>(null)

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        _settings.update(f)
        store.save(_settings.value)
    }

    // ---------- reading bills ----------

    fun read(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val items = uris.map { QueueItem(System.nanoTime(), displayName(it), QState.WAITING, "Waiting") }
        _queue.update { (it + items).takeLast(8) }
        viewModelScope.launch {
            uris.zip(items).forEach { (uri, q) ->
                setQ(q.key, QState.READING, "Reading…")
                try {
                    val d = readOne(uri)
                    val id = newBillId()
                    dao.save(Bill(id, d.date, d.vendor, d.category, d.total, d.currency, d.due, d.notes), d.items)
                    setQ(q.key, QState.DONE, "${d.vendor} · ${Money.format(d.total, d.currency)} · ${d.items.size} items")
                } catch (e: Exception) {
                    setQ(q.key, QState.FAILED, when (e) {
                        is NotABillException, is ClaudeException -> e.message ?: "Couldn't read it"
                        else -> "Couldn't read it — add it by hand"
                    })
                }
            }
            scheduleExcelSync()
        }
    }

    private suspend fun readOne(uri: Uri): Draft {
        val s = _settings.value
        val pages = withContext(Dispatchers.IO) { loadPages(getApplication(), uri) }
        if (s.readMode == ReadMode.CLAUDE && s.apiKey.isNotBlank()) {
            try {
                return ClaudeReader.read(pages, s.apiKey, s.model, s.currency)
            } catch (e: IOException) {
                // offline or unreachable: fall back to the phone's own reader
                message.value = "Couldn't reach Claude — read on the phone instead"
            }
        }
        return withContext(Dispatchers.Default) { OnDeviceReader.read(pages, s.currency) }
    }

    private fun setQ(key: Long, st: QState, msg: String) =
        _queue.update { l -> l.map { if (it.key == key) it.copy(state = st, message = msg) else it } }

    fun clearQueue() = _queue.update { l -> l.filter { it.state == QState.READING || it.state == QState.WAITING } }

    private fun displayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.let { if (it.length > 28) it.take(25) + "…" else it } ?: "Photo"

    // ---------- editing ----------

    fun save(bill: Bill, items: List<Item>) = viewModelScope.launch {
        dao.save(bill, items)
        scheduleExcelSync()
    }

    fun delete(id: String) = viewModelScope.launch {
        dao.deleteBill(id)
        scheduleExcelSync()
    }

    // ---------- Excel ----------

    private var syncJob: Job? = null

    /** Rewrites the linked workbook shortly after changes, so it always matches the app. */
    private fun scheduleExcelSync() {
        if (_settings.value.excelUri.isBlank()) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch { delay(800); writeLinked() }
    }

    private suspend fun writeLinked(): Boolean {
        val s = _settings.value
        if (s.excelUri.isBlank()) return false
        return try {
            writeTo(Uri.parse(s.excelUri))
            updateSettings { it.copy(lastExcelSync = System.currentTimeMillis()) }
            true
        } catch (e: Exception) {
            message.value = "Couldn't update ${s.excelName.ifBlank { "the Excel file" }} — link it again in Settings"
            false
        }
    }

    private suspend fun writeTo(uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = Workbook.build(dao.all(), _settings.value.currency)
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw IOException("Couldn't open the file")
    }

    fun linkExcel(uri: Uri) = viewModelScope.launch {
        updateSettings { it.copy(excelUri = uri.toString(), excelName = displayName(uri)) }
        if (writeLinked()) message.value = "Linked — this file now updates after every change"
    }

    fun unlinkExcel() = updateSettings { it.copy(excelUri = "", excelName = "", lastExcelSync = 0) }

    fun syncNow() = viewModelScope.launch { if (writeLinked()) message.value = "Excel file updated" }

    fun exportCopy(uri: Uri) = viewModelScope.launch {
        try { writeTo(uri); message.value = "Saved a copy of the workbook" } catch (e: Exception) { message.value = "Couldn't save the workbook" }
    }

    fun importExcel(uri: Uri) = viewModelScope.launch {
        try {
            val existing = dao.all().associateBy { it.bill.id }
            val parsed = withContext(Dispatchers.IO) {
                val sheets = getApplication<Application>().contentResolver.openInputStream(uri)?.use { XlsxReader.read(it) }
                    ?: throw IOException("Couldn't open the file")
                Workbook.parse(sheets, existing, _settings.value.currency)
            }
            var added = 0; var changed = 0
            for (b in parsed) {
                val old = existing[b.bill.id]
                val same = old != null && old.bill == b.bill && old.items.map { it.copy(id = 0) } == b.items.map { it.copy(id = 0, billId = b.bill.id) }
                if (same) continue
                if (old == null) added++ else changed++
                dao.save(b.bill, b.items)
            }
            message.value = "Loaded workbook: $added new, $changed updated"
            scheduleExcelSync()
        } catch (e: Exception) {
            message.value = "Couldn't read that workbook — ${e.message ?: "use one saved from Bill Ledger"}"
        }
    }
}
