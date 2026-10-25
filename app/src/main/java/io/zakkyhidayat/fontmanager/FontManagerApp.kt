package io.zakkyhidayat.fontmanager

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FontManagerApp(vm: FontViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Font Manager") },
                actions = {
                    if (vm.hasPermission) {
                        IconButton(onClick = vm::refresh, enabled = !vm.busy) {
                            Icon(Icons.Default.Refresh, contentDescription = "Pindai ulang")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (tab == 0 && vm.hasPermission && !vm.busy && vm.selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = vm::install,
                    icon = { Icon(Icons.Default.Done, contentDescription = null) },
                    text = { Text("Pindahkan ${vm.selected.size} font") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                if (!vm.hasPermission) {
                    PermissionCard()
                    return@Column
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    ToggleButton(
                        checked = tab == 0,
                        onCheckedChange = { tab = 0 },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Ditemukan (${vm.candidates.size})") }
                    ToggleButton(
                        checked = tab == 1,
                        onCheckedChange = { tab = 1 },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Terpasang (${vm.installed.size})") }
                }
                if (vm.busy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else if (tab == 0) {
                    FoundTab(vm)
                } else {
                    InstalledTab(vm)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Izin akses file diperlukan", style = MaterialTheme.typography.titleLarge)
            Text(
                "Font Manager perlu \"Akses semua file\" untuk memindai Download, memindahkan font, " +
                    "dan menulis ke folder .fonts di penyimpanan internal.",
            )
            Button(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            }) { Text("Buka pengaturan izin") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FoundTab(vm: FontViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val dir = treeUriToFile(uri)
            if (dir != null) vm.setScanFolder(dir) else vm.message = "Folder ini tidak bisa dipakai"
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Folder pindai (termasuk subfolder)", style = MaterialTheme.typography.labelLarge)
                    Text(vm.scanDir.path, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { picker.launch(null) }) { Text("Ganti folder") }
                        if (!vm.isDefaultScanDir) {
                            TextButton(onClick = { vm.setScanFolder(null) }) { Text("Kembali ke Download") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Hapus arsip (zip, 7z, tar) setelah fontnya diambil",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = vm.deleteArchives, onCheckedChange = vm::updateDeleteArchives)
                    }
                }
            }
        }
        if (vm.candidates.isEmpty()) {
            item {
                Text(
                    "Tidak ada font (.ttf, .otf, .ttc, .woff, .woff2) atau arsip berisi font di folder ini.",
                    Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            item {
                val all = vm.selected.size == vm.candidates.size
                TextButton(onClick = { vm.selectAll(!all) }) { Text(if (all) "Batalkan semua" else "Pilih semua") }
            }
        }
        itemsIndexed(vm.candidates, key = { _, c -> c.id }) { index, c ->
            SegmentedListItem(
                checked = c.id in vm.selected,
                onCheckedChange = { vm.toggle(c) },
                shapes = ListItemDefaults.segmentedShapes(index, vm.candidates.size),
                leadingContent = { Checkbox(checked = c.id in vm.selected, onCheckedChange = null) },
                supportingContent = {
                    val where = if (c.entry == null) c.source.parent.orEmpty() else "di dalam ${c.source.name}"
                    Text(
                        "${Formatter.formatShortFileSize(context, c.size)} · $where",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            ) { Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun InstalledTab(vm: FontViewModel) {
    var sample by rememberSaveable { mutableStateOf("The quick brown fox jumps over the lazy dog 0123") }
    var pendingDelete by remember { mutableStateOf<InstalledFont?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Lokasi: ${vm.targetDir.path}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = { Text("Teks pratinjau") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (vm.installed.isEmpty()) {
            item {
                Text(
                    "Belum ada font. Pilih font di tab Ditemukan lalu tekan Pindahkan.",
                    Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(vm.installed, key = { it.file.path }) { font ->
            InstalledCard(font, sample) { pendingDelete = font }
        }
    }

    pendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus font?") },
            text = { Text("${font.file.name} akan dihapus permanen dari ${font.file.parent}.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(font)
                    pendingDelete = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun InstalledCard(font: InstalledFont, sample: String, onDelete: () -> Unit) {
    val context = LocalContext.current
    // first = sudah dicoba dimuat, second = keluarga font (null bila format tidak bisa dipratinjau).
    val preview by produceState<Pair<Boolean, FontFamily?>>(false to null, font.file) {
        value = true to withContext(Dispatchers.IO) {
            FontFiles.loadTypeface(context, font.file)?.let { FontFamily(Typeface(it)) }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 16.dp, end = 4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(font.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${font.file.name} · ${Formatter.formatShortFileSize(context, font.file.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val family = preview.second
                when {
                    family != null -> Text(
                        sample,
                        fontFamily = family,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    preview.first -> Text(
                        "Pratinjau tidak tersedia untuk format ini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Hapus ${font.name}") }
        }
    }
}

/** content://…/tree/primary:Download/Fonts → /storage/emulated/0/Download/Fonts (butuh "Akses semua file"). */
private fun treeUriToFile(uri: Uri): File? = runCatching {
    val id = DocumentsContract.getTreeDocumentId(uri)
    val volume = id.substringBefore(':')
    val relative = id.substringAfter(':', "")
    val base = if (volume.equals("primary", ignoreCase = true)) {
        Environment.getExternalStorageDirectory()
    } else {
        File("/storage/$volume")
    }
    (if (relative.isEmpty()) base else File(base, relative)).takeIf { it.isDirectory }
}.getOrNull()
