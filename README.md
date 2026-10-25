# Font Manager

> **Work in progress.** Belum ada rilis; belum diuji di perangkat. Build sendiri dari sumber (lihat di bawah).

Aplikasi Android sederhana (Jetpack Compose, Material 3 Expressive) untuk Googlebook / gbOS86:

- Memindai **Download** beserta subfoldernya (folder bisa diganti) untuk mencari `.ttf`, `.otf`, `.ttc`/`.otc`, `.woff`, `.woff2`.
- Membuka arsip **zip, 7z, tar, tar.gz/tgz, tar.xz, tar.bz2** dan mengambil font di dalamnya (sampah `__MACOSX` dilewati). RAR tidak didukung.
- **Memindahkan (cut)** font ke folder font lokal, lalu menampilkan font terpasang dengan pratinjau dan tombol hapus.

## Folder font: `/storage/emulated/0/.fonts`

Ini padanan `~/.fonts` di Android. "Home" pengguna di Android adalah penyimpanan internal (`/storage/emulated/0`, alias `/sdcard`). Font disimpan apa adanya (ttf/otf/ttc/woff/woff2) seperti yang dibaca fontconfig di Linux, jadi folder ini siap dipakai kalau suatu saat Android/Googlebook membaca font per pengguna. Saat ini Android sendiri **belum** memakai folder ini. Font di sini hanya terlihat dan bisa dipratinjau di aplikasi ini. Tidak perlu root.

Cara melihat isi folder tersembunyi `.fonts`:

- **Di aplikasi:** tab **Terpasang** menampilkan semua font di folder itu (termasuk subfolder) dengan pratinjau.
- **Aplikasi Files:** menu ⋮ → *Settings* → nyalakan **Show hidden files**, lalu buka *Internal storage* → `.fonts`.
- **Lewat adb dari Fedora:** `adb shell ls -la /sdcard/.fonts`

Aturan pemindahan:

- Font lepas dipindah (bukan disalin). Kalau di `.fonts` sudah ada berkas dengan nama dan ukuran sama, berkas sumber dihapus dan dihitung "sudah ada". Kalau namanya sama tapi isinya beda, font baru disimpan dengan akhiran `-2`, `-3`, dst.
- Font dari arsip diekstrak. Arsipnya dibiarkan, kecuali sakelar **Hapus arsip setelah fontnya diambil** dinyalakan.
- Folder tersembunyi (yang namanya diawali `.`) tidak ikut dipindai.

Catatan:

- **Izin:** saat pertama dibuka, aplikasi meminta **Akses semua file** (Android 11+). Ketuk *Buka pengaturan izin*, nyalakan, lalu kembali ke aplikasi.
- **WOFF2** ikut dipindah, tapi tidak bisa dipratinjau karena Android tidak memuat WOFF2. WOFF 1.0 dipratinjau lewat konversi sementara di cache.
- **Ganti folder:** pemilih folder Android tidak mengizinkan memilih akar penyimpanan atau folder Download itu sendiri. Pilih subfolder, atau pakai *Kembali ke Download*. Folder di SSD/flashdisk juga bisa (`/storage/XXXX-XXXX/...`).

## Build (Fedora)

Butuh JDK 17+ (Fedora: `java-25-openjdk` sudah cukup) dan Android SDK dengan platform `android-37.0` dan build-tools. `local.properties` menunjuk ke `~/Android/Sdk`. Gradle diunduh otomatis oleh wrapper.

```bash
cd ~/Git/font-manager
```

```bash
./gradlew assembleRelease
```

Hasilnya `app/build/outputs/apk/release/app-release.apk` (sekitar 2,6 MB, ditandatangani kunci debug supaya langsung bisa dipasang). Versi debug: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

Tes logika pemindaian/ekstraksi (memakai font sistem Fedora di `/usr/share/fonts`):

```bash
./gradlew testDebugUnitTest
```

Kalau SDK belum ada di mesin lain: unduh *command-line tools* dari developer.android.com ke `~/Android/Sdk/cmdline-tools/latest`, lalu jalankan `sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"` (tanpa sudo).

## Pasang ke Googlebook

Nyalakan USB debugging di Googlebook (adbd mendengarkan di TCP 5555), lalu dari Fedora:

```bash
adb connect <ip-googlebook>:5555
```

Atau lewat kabel Thunderbolt: `adb connect 10.42.0.2:5555`. Setelah itu:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Kalau ada lebih dari satu perangkat terhubung, tambahkan `-s <ip>:5555` setelah `adb`.

## Struktur kode

- `FontStore.kt`: memindai folder, membaca arsip, memindahkan/mengekstrak ke `.fonts`.
- `FontFiles.kt`: membaca nama font dari tabel `name`, konversi WOFF → TTF/OTF untuk pratinjau.
- `FontViewModel.kt`: state aplikasi dan pengaturan (folder pindai, hapus arsip).
- `FontManagerApp.kt`: UI (ButtonGroup/ToggleButton, SegmentedListItem, LoadingIndicator, ExtendedFAB).
