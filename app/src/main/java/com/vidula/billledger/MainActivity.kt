package com.vidula.billledger

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidula.billledger.analytics.Analytics
import com.vidula.billledger.data.BillWithItems
import com.vidula.billledger.ui.BillLedgerTheme
import com.vidula.billledger.ui.BillsScreen
import com.vidula.billledger.ui.EditBillDialog
import com.vidula.billledger.ui.ItemsScreen
import com.vidula.billledger.ui.OverviewScreen
import com.vidula.billledger.ui.SettingsScreen
import java.io.File
import java.time.LocalDate

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent { BillLedgerTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A bill shared from the gallery, WhatsApp, email etc. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.read(listOf(it)) }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Home), ITEMS("Items", Icons.Filled.ShoppingCart),
    BILLS("Bills", Icons.AutoMirrored.Filled.List), SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
private fun App(vm: AppViewModel) {
    val context = LocalContext.current
    val bills by vm.bills.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    var billCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<BillWithItems?>(null) }
    var adding by remember { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    LaunchedEffect(msg) { msg?.let { snack.showSnackbar(it); vm.message.value = null } }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        photoUri?.let { if (ok) vm.read(listOf(Uri.parse(it))) }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.read(it) }
    val createLinked = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        vm.linkExcel(uri)
    }
    val exportCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX)) { uri -> uri?.let(vm::exportCopy) }
    val importXlsx = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importExcel) }

    fun camera() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "bill_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        photoUri = uri.toString()
        runCatching { takePhoto.launch(uri) }.onFailure { vm.message.value = "No camera app found" }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { if (t == Tab.BILLS && tab != Tab.BILLS) billCategory = null; tab = t },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            val cur = settings.currency
            when (tab) {
                Tab.OVERVIEW -> OverviewScreen(
                    Analytics.overview(bills, cur), cur, queue, hasCamera,
                    onCamera = { camera() },
                    onFiles = { pickFiles.launch(arrayOf("image/*", "application/pdf")) },
                    onManual = { adding = true },
                    onClearQueue = vm::clearQueue,
                    onCategory = { billCategory = it; tab = Tab.BILLS },
                )
                Tab.ITEMS -> ItemsScreen(Analytics.lines(bills, cur), cur)
                Tab.BILLS -> BillsScreen(bills, cur, billCategory, { billCategory = it }, { editing = it })
                Tab.SETTINGS -> SettingsScreen(
                    settings, vm::updateSettings,
                    onLinkExcel = { createLinked.launch("Bill Ledger.xlsx") },
                    onUnlink = vm::unlinkExcel,
                    onSyncNow = { vm.syncNow() },
                    onExportCopy = { exportCopy.launch("Bill Ledger ${LocalDate.now()}.xlsx") },
                    onImport = { importXlsx.launch(arrayOf(XLSX, "application/octet-stream")) },
                )
            }
        }
    }

    if (adding || editing != null) {
        EditBillDialog(
            existing = editing, currency = settings.currency,
            onDismiss = { adding = false; editing = null },
            onSave = { b, items -> vm.save(b, items); adding = false; editing = null },
            onDelete = { id -> vm.delete(id); adding = false; editing = null },
        )
    }
}
