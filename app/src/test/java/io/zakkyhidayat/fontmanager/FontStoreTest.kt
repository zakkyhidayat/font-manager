package io.zakkyhidayat.fontmanager

import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Memakai font sistem Fedora (/usr/share/fonts) sebagai bahan; dilewati bila tidak ada. */
class FontStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun systemFont(path: String) = File(path).also { assumeTrue("$path tidak ada", it.isFile) }

    private val ttf get() = systemFont("/usr/share/fonts/adwaita-mono-fonts/AdwaitaMono-Bold.ttf")
    private val otf get() = systemFont("/usr/share/fonts/aajohan-comfortaa-fonts/Comfortaa-Bold.otf")
    private val ttc get() = systemFont("/usr/share/fonts/google-noto-sans-cjk-vf-fonts/NotoSansCJK-VF.ttc")

    @Test
    fun readsFontNames() {
        assertEquals("Adwaita Mono Bold", FontFiles.displayName(ttf))
        assertEquals("Comfortaa Bold", FontFiles.displayName(otf))
        assertTrue(FontFiles.displayName(ttc)!!.startsWith("Noto Sans CJK"))
    }

    @Test
    fun convertsWoff() {
        val woff = File("/opt/GenOffice/resources/modules/markdown/renderer/assets")
            .listFiles { f -> f.extension == "woff" }?.firstOrNull()
        assumeTrue(woff != null)
        val name = FontFiles.displayName(woff!!)
        val out = tmp.newFile("out.ttf")
        FontFiles.woffToSfnt(woff, out)
        assertTrue(name!!.startsWith("KaTeX"))
        assertEquals(name, FontFiles.displayName(out))
    }

    @Test
    fun scansAndInstallsFromLooseFilesAndArchives() {
        val downloads = tmp.newFolder("Download")
        val target = tmp.newFolder(".fonts")
        File(downloads, "sub").mkdirs()
        ttf.copyTo(File(downloads, "sub/Loose.ttf"))
        File(downloads, "notes.txt").writeText("bukan font")

        ZipOutputStream(File(downloads, "pack.zip").outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("pack/Comfortaa-Bold.otf")); otf.inputStream().use { it.copyTo(zip) }
            zip.putNextEntry(ZipEntry("__MACOSX/pack/._Comfortaa-Bold.otf")); zip.write(ByteArray(10))
            zip.putNextEntry(ZipEntry("pack/README.txt")); zip.write("hi".toByteArray())
        }
        TarArchiveOutputStream(GzipCompressorOutputStream(File(downloads, "pack.tar.gz").outputStream())).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry(ttf, "fonts/FromTar.ttf")); ttf.inputStream().use { it.copyTo(tar) }
            tar.closeArchiveEntry()
        }
        SevenZOutputFile(File(downloads, "pack.7z")).use { sz ->
            sz.putArchiveEntry(sz.createArchiveEntry(otf, "From7z.otf")); sz.write(otf.readBytes())
            sz.closeArchiveEntry()
        }

        val found = FontStore.scan(downloads, target)
        assertEquals(listOf("Comfortaa-Bold.otf", "From7z.otf", "FromTar.ttf", "Loose.ttf"), found.map { it.name })

        val result = FontStore.install(found, target, deleteArchives = true)
        assertEquals(InstallResult(moved = 4, duplicates = 0, failed = 0), result)
        assertFalse("font lepas dipindah (cut)", File(downloads, "sub/Loose.ttf").exists())
        assertFalse("arsip dihapus", File(downloads, "pack.zip").exists())
        assertEquals(ttf.length(), File(target, "FromTar.ttf").length())
        assertEquals(otf.readBytes().toList(), File(target, "From7z.otf").readBytes().toList())
        assertEquals(4, FontStore.installed(target).size)
        assertTrue(FontStore.scan(downloads, target).isEmpty())

        // Font yang sama persis dianggap duplikat; nama sama tapi isi beda diberi akhiran.
        ttf.copyTo(File(downloads, "Loose.ttf"))
        otf.copyTo(File(downloads, "FromTar.ttf"))
        val again = FontStore.install(FontStore.scan(downloads, target), target, deleteArchives = false)
        assertEquals(InstallResult(moved = 1, duplicates = 1, failed = 0), again)
        assertTrue(File(target, "FromTar-2.ttf").exists())
    }
}
