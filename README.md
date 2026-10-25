# Font Manager

> **Work in progress.** No release yet and not yet tested on a device. Build it from source (see below).

A simple Android app (Jetpack Compose, Material 3 Expressive) for Googlebook / gbOS86:

- Scans **Download** and its subfolders (the folder can be changed) for `.ttf`, `.otf`, `.ttc`/`.otc`, `.woff`, `.woff2`.
- Opens **zip, 7z, tar, tar.gz/tgz, tar.xz, tar.bz2** archives and takes the fonts out (`__MACOSX` junk is skipped). RAR is not supported.
- **Moves (cuts)** fonts into a local fonts folder, then lists the installed fonts with a preview and a delete button.

The app's UI is in Indonesian.

## Fonts folder: `/storage/emulated/0/.fonts`

This is Android's counterpart of `~/.fonts`. A user's "home" on Android is internal shared storage (`/storage/emulated/0`, also `/sdcard`). Fonts are stored as-is (ttf/otf/ttc/woff/woff2), the way fontconfig reads them on Linux, so the folder is ready if Android/Googlebook ever reads per-user fonts. Android itself does **not** use this folder today: fonts there are only visible and previewable in this app. No root needed.

Seeing what's in the hidden `.fonts` folder:

- **In the app:** the **Terpasang** (Installed) tab lists every font in the folder (subfolders included) with a preview.
- **Files app:** ⋮ menu → *Settings* → turn on **Show hidden files**, then open *Internal storage* → `.fonts`.
- **Over adb from a computer:** `adb shell ls -la /sdcard/.fonts`

How moving works:

- Loose font files are moved, not copied. If `.fonts` already has a file with the same name and size, the source is deleted and counted as "already there". Same name but different content: the new font is saved with a `-2`, `-3`, … suffix.
- Fonts inside archives are extracted. The archive is kept unless the **Hapus arsip setelah fontnya diambil** (delete archive after taking its fonts) switch is on.
- Hidden folders (names starting with `.`) are not scanned.

Notes:

- **Permission:** on first launch the app asks for **All files access** (Android 11+). Tap *Buka pengaturan izin* (open permission settings), turn it on, and come back to the app.
- **WOFF2** fonts are moved but can't be previewed, because Android can't load WOFF2. WOFF 1.0 is previewed through a temporary conversion in the app's cache.
- **Changing the folder:** Android's folder picker won't let you pick the storage root or the Download folder itself. Pick a subfolder, or use *Kembali ke Download* (back to Download). Folders on an SSD or USB drive work too (`/storage/XXXX-XXXX/...`).

## Build (Fedora)

Needs JDK 17+ (Fedora's `java-25-openjdk` is fine) and the Android SDK with platform `android-37.0` and build-tools. Create `local.properties` with `sdk.dir=/home/<you>/Android/Sdk`. The Gradle wrapper downloads Gradle itself.

```bash
cd ~/Git/font-manager
```

```bash
./gradlew assembleRelease
```

The output is `app/build/outputs/apk/release/app-release.apk` (about 2.6 MB, signed with the debug key so it installs directly). Debug build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

Tests for the scanning/extraction logic (they use Fedora's system fonts in `/usr/share/fonts` as test data):

```bash
./gradlew testDebugUnitTest
```

No SDK yet: download the *command-line tools* from developer.android.com into `~/Android/Sdk/cmdline-tools/latest`, then run `sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"` (no sudo needed).

## Install on the Googlebook

Turn on USB debugging on the Googlebook (adbd listens on TCP 5555), then from the other computer:

```bash
adb connect <googlebook-ip>:5555
```

Over a Thunderbolt cable the address is `10.42.0.2:5555`. Then:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

With more than one device connected, add `-s <ip>:5555` after `adb`.

## Code layout

- `FontStore.kt`: scans folders, reads archives, moves/extracts into `.fonts`.
- `FontFiles.kt`: reads the font name from the `name` table, converts WOFF → TTF/OTF for previews.
- `FontViewModel.kt`: app state and settings (scan folder, delete archives).
- `FontManagerApp.kt`: the UI (ButtonGroup/ToggleButton, SegmentedListItem, LoadingIndicator, ExtendedFAB).
