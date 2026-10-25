package io.zakkyhidayat.fontmanager

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.InputStream

/** Font yang ditemukan saat pemindaian: berkas lepas ([entry] null) atau isi sebuah arsip. */
data class Candidate(val source: File, val entry: String?, val name: String, val size: Long) {
    val id: String get() = if (entry == null) source.path else "${source.path}!$entry"
}

data class InstalledFont(val file: File, val name: String)

data class InstallResult(val moved: Int, val duplicates: Int, val failed: Int)

object FontStore {
    private enum class Archive { ZIP, SEVEN_Z, TAR, TAR_GZ, TAR_XZ, TAR_BZ2 }

    private fun archiveType(name: String): Archive? {
        val n = name.lowercase()
        return when {
            n.endsWith(".zip") -> Archive.ZIP
            n.endsWith(".7z") -> Archive.SEVEN_Z
            n.endsWith(".tar") -> Archive.TAR
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> Archive.TAR_GZ
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> Archive.TAR_XZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") -> Archive.TAR_BZ2
            else -> null
        }
    }

    /** Sampah macOS di arsip (__MACOSX/, ._Nama.ttf) bukan font sungguhan. */
    private fun isJunk(path: String) = path.contains("__MACOSX/") || path.substringAfterLast('/').startsWith("._")

    /** Menelusuri [root] beserta subfoldernya; folder tersembunyi (termasuk [target]) dilewati. */
    fun scan(root: File, target: File): List<Candidate> {
        val skip = target.absoluteFile
        val found = mutableListOf<Candidate>()
        root.walkTopDown()
            .onEnter { it == root || (!it.name.startsWith(".") && it.absoluteFile != skip) }
            .filter { it.isFile && !isJunk(it.path) }
            .forEach { f ->
                when {
                    FontFiles.isFont(f.name) -> found += Candidate(f, null, f.name, f.length())
                    archiveType(f.name) != null -> runCatching {
                        readArchive(f) { path, size, _ ->
                            found += Candidate(f, path, path.substringAfterLast('/'), size)
                        }
                    }
                }
            }
        return found.sortedBy { it.name.lowercase() }
    }

    fun installed(target: File): List<InstalledFont> =
        target.walkTopDown()
            .filter { it.isFile && FontFiles.isFont(it.name) }
            .map { InstalledFont(it, FontFiles.displayName(it) ?: it.nameWithoutExtension) }
            .sortedBy { it.name.lowercase() }
            .toList()

    /**
     * Memindahkan font lepas ("cut") dan mengekstrak font dari arsip ke [target]. Berkas dengan nama dan ukuran
     * yang sama dianggap sudah terpasang; nama sama tapi isi beda diberi akhiran -2, -3, dst.
     */
    fun install(items: List<Candidate>, target: File, deleteArchives: Boolean): InstallResult {
        target.mkdirs()
        var moved = 0
        var duplicates = 0
        var failed = 0
        for ((source, group) in items.groupBy { it.source }) {
            if (group.first().entry == null) {
                val dest = destination(target, source.name, source.length())
                when {
                    dest == null -> { duplicates++; source.delete() }
                    moveFile(source, dest) -> moved++
                    else -> failed++
                }
                continue
            }
            val wanted = group.mapNotNull { it.entry }.toMutableSet()
            runCatching {
                readArchive(source) { path, size, stream ->
                    if (wanted.remove(path)) {
                        val dest = destination(target, path.substringAfterLast('/'), size)
                        if (dest == null) {
                            duplicates++
                        } else {
                            dest.outputStream().use { stream.copyTo(it) }
                            moved++
                        }
                    }
                }
            }.onFailure { wanted.clear(); failed += group.size }
            failed += wanted.size
            if (deleteArchives && wanted.isEmpty()) source.delete()
        }
        return InstallResult(moved, duplicates, failed)
    }

    private fun destination(dir: File, name: String, size: Long): File? {
        val first = File(dir, name)
        if (!first.exists()) return first
        if (first.length() == size) return null
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        return generateSequence(2) { it + 1 }.map { File(dir, "$base-$it.$ext") }.first { !it.exists() }
    }

    private fun moveFile(from: File, to: File): Boolean {
        if (from.renameTo(to)) return true
        // Beda volume (mis. SSD eksternal): salin lalu hapus.
        return runCatching {
            from.inputStream().use { input -> to.outputStream().use { input.copyTo(it) } }
            from.delete()
        }.getOrElse { to.delete(); false }
    }

    /** Memanggil [onFont] untuk tiap font di dalam arsip; [stream] berisi data entri itu (dibaca berurutan). */
    private fun readArchive(file: File, onFont: (path: String, size: Long, stream: InputStream) -> Unit) {
        val type = archiveType(file.name) ?: return
        when (type) {
            Archive.ZIP -> ZipFile.builder().setFile(file).get().use { zip ->
                for (e in zip.entries) {
                    if (!e.isDirectory && FontFiles.isFont(e.name) && !isJunk(e.name)) {
                        zip.getInputStream(e).use { onFont(e.name, e.size, it) }
                    }
                }
            }
            Archive.SEVEN_Z -> SevenZFile.builder().setFile(file).get().use { sz ->
                while (true) {
                    val e = sz.nextEntry ?: break
                    if (!e.isDirectory && FontFiles.isFont(e.name) && !isJunk(e.name)) {
                        onFont(e.name, e.size, object : InputStream() {
                            override fun read() = sz.read()
                            override fun read(b: ByteArray, off: Int, len: Int) = sz.read(b, off, len)
                        })
                    }
                }
            }
            else -> {
                val raw = file.inputStream().buffered()
                val decompressed = when (type) {
                    Archive.TAR_GZ -> GzipCompressorInputStream(raw)
                    Archive.TAR_XZ -> XZCompressorInputStream(raw)
                    Archive.TAR_BZ2 -> BZip2CompressorInputStream(raw)
                    else -> raw
                }
                TarArchiveInputStream(decompressed).use { tar -> forEachTar(tar, onFont) }
            }
        }
    }

    private fun forEachTar(tar: ArchiveInputStream<*>, onFont: (String, Long, InputStream) -> Unit) {
        while (true) {
            val e = tar.nextEntry ?: break
            if (!e.isDirectory && FontFiles.isFont(e.name) && !isJunk(e.name)) onFont(e.name, e.size, tar)
        }
    }
}
