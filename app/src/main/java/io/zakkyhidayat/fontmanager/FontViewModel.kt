package io.zakkyhidayat.fontmanager

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FontViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val defaultScanDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** Setara ~/.fonts: "home" pengguna di Android adalah penyimpanan internal bersama. */
    val targetDir = File(Environment.getExternalStorageDirectory(), ".fonts")

    var scanDir by mutableStateOf(prefs.getString(KEY_SCAN_DIR, null)?.let(::File) ?: defaultScanDir)
        private set
    var deleteArchives by mutableStateOf(prefs.getBoolean(KEY_DELETE_ARCHIVES, false))
        private set
    var hasPermission by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var candidates by mutableStateOf(emptyList<Candidate>())
        private set
    var selected by mutableStateOf(emptySet<String>())
        private set
    var installed by mutableStateOf(emptyList<InstalledFont>())
        private set
    var message by mutableStateOf<String?>(null)

    val isDefaultScanDir get() = scanDir == defaultScanDir

    /** Dipanggil tiap layar aktif lagi, mis. setelah kembali dari halaman izin. */
    fun onResume() {
        val granted = Environment.isExternalStorageManager()
        val newlyGranted = granted && !hasPermission
        hasPermission = granted
        if (newlyGranted) refresh()
    }

    fun refresh() {
        if (busy || !hasPermission) return
        viewModelScope.launch {
            busy = true
            val (found, present) = withContext(Dispatchers.IO) {
                FontStore.scan(scanDir, targetDir) to FontStore.installed(targetDir)
            }
            candidates = found
            selected = found.map { it.id }.toSet()
            installed = present
            busy = false
        }
    }

    fun toggle(c: Candidate) {
        selected = if (c.id in selected) selected - c.id else selected + c.id
    }

    fun selectAll(all: Boolean) {
        selected = if (all) candidates.map { it.id }.toSet() else emptySet()
    }

    fun setScanFolder(dir: File?) {
        scanDir = dir ?: defaultScanDir
        prefs.edit { if (dir == null) remove(KEY_SCAN_DIR) else putString(KEY_SCAN_DIR, dir.path) }
        refresh()
    }

    fun updateDeleteArchives(value: Boolean) {
        deleteArchives = value
        prefs.edit { putBoolean(KEY_DELETE_ARCHIVES, value) }
    }

    fun install() {
        if (busy) return
        val items = candidates.filter { it.id in selected }
        if (items.isEmpty()) return
        viewModelScope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { FontStore.install(items, targetDir, deleteArchives) }
            busy = false
            message = buildList {
                add("${r.moved} font dipindahkan")
                if (r.duplicates > 0) add("${r.duplicates} sudah ada")
                if (r.failed > 0) add("${r.failed} gagal")
            }.joinToString(", ")
            refresh()
        }
    }

    fun remove(font: InstalledFont) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { font.file.delete() }
            if (ok) installed = installed - font
            message = if (ok) "${font.name} dihapus" else "Gagal menghapus ${font.file.name}"
        }
    }

    private companion object {
        const val KEY_SCAN_DIR = "scan_dir"
        const val KEY_DELETE_ARCHIVES = "delete_archives"
    }
}
