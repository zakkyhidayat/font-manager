package io.zakkyhidayat.fontmanager

import android.content.Context
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.zip.Inflater

/** Membaca berkas font secara langsung: nama dari tabel 'name', dan konversi WOFF ke TTF/OTF untuk pratinjau. */
object FontFiles {
    private const val TAG_TTCF = 0x74746366 // 'ttcf'
    private const val TAG_WOFF = 0x774F4646 // 'wOFF'
    private const val TAG_NAME = 0x6E616D65 // 'name'

    val EXTENSIONS = setOf("ttf", "otf", "ttc", "otc", "woff", "woff2")

    fun isFont(name: String) = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** Nama lengkap font (mis. "Inter Bold"), atau null kalau tidak terbaca (WOFF2 atau berkas rusak). */
    fun displayName(file: File): String? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val table = when (raf.readInt()) {
                TAG_WOFF -> woffTable(raf, TAG_NAME)
                TAG_TTCF -> {
                    raf.seek(12)
                    sfntTable(raf, raf.readInt().toLong(), TAG_NAME)
                }
                else -> sfntTable(raf, 0, TAG_NAME)
            }
            table?.let(::parseName)
        }
    }.getOrNull()

    /** Typeface untuk pratinjau. WOFF dikonversi dulu ke cache; WOFF2 tidak didukung Android, jadi null. */
    fun loadTypeface(context: Context, file: File): Typeface? = runCatching {
        val source = when (file.extension.lowercase()) {
            "woff2" -> return null
            "woff" -> File(context.cacheDir, "woff/${file.absolutePath.hashCode()}_${file.length()}.sfnt").also {
                if (!it.exists()) woffToSfnt(file, it)
            }
            else -> file
        }
        Typeface.Builder(source).build()
    }.getOrNull()

    private fun sfntTable(raf: RandomAccessFile, start: Long, tag: Int): ByteArray? {
        raf.seek(start + 4)
        val numTables = raf.readUnsignedShort()
        raf.seek(start + 12)
        repeat(numTables) {
            val t = raf.readInt()
            raf.readInt() // checksum
            val offset = raf.readInt().toLong()
            val length = raf.readInt()
            if (t == tag) {
                raf.seek(offset)
                return ByteArray(length).also(raf::readFully)
            }
        }
        return null
    }

    private class WoffEntry(val tag: Int, val offset: Long, val compLength: Int, val origLength: Int, val checksum: Int)

    private fun woffEntries(raf: RandomAccessFile): List<WoffEntry> {
        raf.seek(12)
        val numTables = raf.readUnsignedShort()
        raf.seek(44)
        return List(numTables) {
            WoffEntry(raf.readInt(), raf.readInt().toLong(), raf.readInt(), raf.readInt(), raf.readInt())
        }
    }

    private fun woffData(raf: RandomAccessFile, e: WoffEntry): ByteArray {
        raf.seek(e.offset)
        val data = ByteArray(e.compLength).also(raf::readFully)
        if (e.compLength >= e.origLength) return data
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArray(e.origLength)
        var n = 0
        while (n < out.size && !inflater.finished()) n += inflater.inflate(out, n, out.size - n)
        inflater.end()
        return out
    }

    private fun woffTable(raf: RandomAccessFile, tag: Int): ByteArray? =
        woffEntries(raf).firstOrNull { it.tag == tag }?.let { woffData(raf, it) }

    /** Menyusun ulang WOFF 1.0 menjadi sfnt biasa (TTF/OTF) di [out]. */
    internal fun woffToSfnt(file: File, out: File) {
        out.parentFile?.mkdirs()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(4)
            val flavor = raf.readInt()
            val entries = woffEntries(raf).sortedBy { Integer.toUnsignedLong(it.tag) }
            val tables = entries.map { woffData(raf, it) }
            val n = entries.size
            var pow = 1
            var log = 0
            while (pow * 2 <= n) { pow *= 2; log++ }
            val header = ByteBuffer.allocate(12 + 16 * n)
            header.putInt(flavor).putShort(n.toShort())
                .putShort((pow * 16).toShort()).putShort(log.toShort()).putShort((n * 16 - pow * 16).toShort())
            var offset = 12 + 16 * n
            entries.forEachIndexed { i, e ->
                header.putInt(e.tag).putInt(e.checksum).putInt(offset).putInt(tables[i].size)
                offset += (tables[i].size + 3) and 3.inv()
            }
            val body = ByteArrayOutputStream(offset)
            body.write(header.array())
            tables.forEach { t ->
                body.write(t)
                repeat(((t.size + 3) and 3.inv()) - t.size) { body.write(0) }
            }
            out.writeBytes(body.toByteArray())
        }
    }

    /** Memilih nama terbaik dari tabel 'name': nama lengkap (ID 4) Windows bahasa Inggris, lalu yang lain. */
    private fun parseName(data: ByteArray): String? {
        val b = ByteBuffer.wrap(data)
        val count = b.getShort(2).toInt() and 0xFFFF
        val stringOffset = b.getShort(4).toInt() and 0xFFFF
        var best: String? = null
        var bestScore = -1
        for (i in 0 until count) {
            val r = 6 + i * 12
            if (r + 12 > data.size) break
            val platform = b.getShort(r).toInt() and 0xFFFF
            val language = b.getShort(r + 4).toInt() and 0xFFFF
            val nameId = b.getShort(r + 6).toInt() and 0xFFFF
            val length = b.getShort(r + 8).toInt() and 0xFFFF
            val offset = stringOffset + (b.getShort(r + 10).toInt() and 0xFFFF)
            if (nameId != 4 && nameId != 1) continue
            if (offset + length > data.size) continue
            val score = (if (nameId == 4) 4 else 0) +
                (if (platform == 3) 2 else if (platform == 0) 1 else 0) +
                (if (platform == 3 && language == 0x409) 1 else 0)
            if (score <= bestScore) continue
            val charset = if (platform == 1) Charsets.ISO_8859_1 else Charsets.UTF_16BE
            val text = String(data, offset, length, charset).trim()
            if (text.isNotEmpty()) {
                best = text
                bestScore = score
            }
        }
        return best
    }
}
