# AGENTS.md

This file describes the repository layout and the supported development workflows for coding agents.

## Project Overview

This repository exposes [`rust_xlsxwriter`](https://github.com/jmcnamara/rust_xlsxwriter) to Swift and Kotlin through a shared Rust/UniFFI core:

- **Swift/iOS/macOS:** a Swift `actor` façade distributed through Swift Package Manager with a Rust XCFramework.
- **Kotlin/Android:** an `AutoCloseable` Kotlin façade distributed as an AAR and a folder-based Maven repository ZIP.

The language façades intentionally differ. Swift exposes the full current Rust surface, while the first Android API focuses on strings, integers, numbers, dates, column widths, and file/buffer output. Do not expose the generated UniFFI Kotlin API as the public Android API.

## Build Commands

Run commands from the repository root.

```bash
# Compile and test the shared Rust core
cargo test --locked

# Generate Swift bindings, build all Apple native libraries, and create the XCFramework
./build-ios.sh

# Test the Swift package
swift test

# Run JVM tests, then build and verify the Android publication and coordinate-only consumer
./android/gradlew -p android verifyReleaseArtifacts ktlintCheck \
  :xlsxwriter-android:lintRelease :consumer:lintDebug

# Run the Kotlin/native XLSX round-trip test on a connected device or emulator
./android/gradlew -p android :xlsxwriter-android:connectedDebugAndroidTest

# Produce the Android Maven ZIP, standalone AAR, and SHA-256 sidecars
./android/gradlew -p android :xlsxwriter-android:generateReleaseChecksums
```

Useful narrower commands:

```bash
# Build only the Android release AAR (also builds Rust and generates Kotlin bindings)
./android/gradlew -p android :xlsxwriter-android:assembleRelease

# Run fast Kotlin façade tests against the host Rust library
./android/gradlew -p android :xlsxwriter-android:testDebugUnitTest

# Regenerate Swift bindings manually after `cargo build`
cargo run --locked --bin uniffi-bindgen -- generate \
  --library ./target/debug/libxlsxwriter.dylib \
  --language swift \
  --out-dir ./out

# Generate the Android bindings through the Gradle-owned pipeline
./android/gradlew -p android :xlsxwriter-android:generateUniFfiBindings
```

Android Gradle commands need the SDK configured through `ANDROID_HOME` or `android/local.properties`. `connectedDebugAndroidTest` requires an API 24+ device or emulator matching a shipped ABI; CI uses an API 35 x86_64 emulator. The release verifier specifically requires `ANDROID_HOME` and an installed Android NDK so it can inspect ELF and APK alignment.

## Architecture: Rust + Swift/Kotlin via UniFFI

### Layer Structure

```text
src/lib.rs — shared Rust Workbook API
├── Apple build
│   ├── static libraries ────────────────> libxlsxwriter-rs.xcframework
│   └── UniFFI Swift generation ─────────> generated Swift FFI source
│                                          + public Swift actor
│                                          └──> Swift Package Manager
└── Android Gradle build
    ├── host release library ────────────> UniFFI internal Kotlin/JNA bindings
    ├── cargo-ndk ───────────────────────> arm64-v8a + x86_64 `.so` libraries
    └── public Kotlin façade + both above ─> AAR / Maven repository
```

### Shared Rust Core

`src/lib.rs` owns a `rust_xlsxwriter::Workbook` behind a `Mutex` and exports records, errors, and workbook operations with UniFFI attributes. Keep cross-platform behavior here when both public packages need it.

### Swift Pipeline

`build-ios.sh` builds static Rust libraries for Apple platforms, generates `Sources/XlsxWriterFFI/xlsxwriter.swift`, creates an XCFramework, and updates the release version/checksum in `Package.swift`. `Sources/XlsxWriterSwift/XlsxWriterSwift.swift` wraps the generated bindings in an actor-based, Foundation-friendly API.

### Android Pipeline

`android/xlsxwriter-android/build.gradle.kts` owns Android code generation:

1. `buildRustHost` produces `target/release/libxlsxwriter.dylib` on macOS or `libxlsxwriter.so` on Linux, whose unstripped symbol table UniFFI can inspect and whose native symbols host-JVM tests can load.
2. UniFFI generates Kotlin from that host library into `android/xlsxwriter-android/build/generated/source/uniffi/ai/botisan/xlsxwriter/internal/`.
3. Separately, `cargo ndk` builds `libxlsxwriter.so` for `arm64-v8a` and `x86_64` with min SDK 24.
4. The Android library packages the generated bindings, Android native libraries, and hand-written façade into an AAR, with JNA declared as a transitive AAR dependency.
5. Maven publication tasks write a local repository under `android/build/repository`; the consumer app resolves only `ai.botisan:xlsxwriter-android:<version>` from that repository.

The generated package is `ai.botisan.xlsxwriter.internal`. Public Android code belongs in `ai.botisan.xlsxwriter` and must hide UniFFI unsigned types, JNA, and native handles.

## Key Files

### Shared Rust and UniFFI

- **`src/lib.rs`** — Rust implementation and UniFFI-exported records, errors, and methods.
- **`src/uniffi-bindgen.rs`** — UniFFI command-line driver used by both platform builds.
- **`uniffi.toml`** — Kotlin binding configuration, including the internal package name.
- **`Cargo.toml` / `Cargo.lock`** — shared Rust version and dependency lockfile; the Cargo package version is also the Android Maven version.
- **`rust-toolchain.toml`** — Rust toolchain components and Apple/Android targets.

### Swift

- **`Sources/XlsxWriterFFI/xlsxwriter.swift`** — generated Swift bindings; regenerate instead of hand-editing.
- **`Sources/XlsxWriterSwift/XlsxWriterSwift.swift`** — hand-written public Swift actor.
- **`Tests/XlsxWriterSwiftTests/XlsxWriterSwiftTests.swift`** — Swift package behavior tests.
- **`Package.swift`** — Swift package products and local/release XCFramework selection.
- **`build-ios.sh`** — Swift binding and XCFramework build pipeline.

### Android

- **`android/xlsxwriter-android/src/main/kotlin/ai/botisan/xlsxwriter/XlsxWorkbook.kt`** — hand-written public Kotlin API, validation, and error mapping.
- **`android/xlsxwriter-android/build.gradle.kts`** — Rust/UniFFI generation, AAR configuration, Maven publication, and release artifacts.
- **`android/xlsxwriter-android/src/test/kotlin/ai/botisan/xlsxwriter/XlsxWorkbookValidationTest.kt`** — fast host-JVM tests for public façade validation and native-boundary behavior.
- **`android/xlsxwriter-android/src/androidTest/kotlin/ai/botisan/xlsxwriter/XlsxWorkbookRoundTripTest.kt`** — device/emulator test that writes and reopens a real XLSX file.
- **`android/consumer/`** — smoke app that consumes only the published Maven coordinate.
- **`android/verify-release-artifacts.sh`** — validates Maven metadata, dependencies, ABIs, checksums, and 16 KiB native/APK alignment.
- **`.github/workflows/android.yml`** — Linux CI for Rust, Android publication, and emulator round-trip verification.
- **`gh-release.sh`** — uploads the Apple XCFramework and Android release assets after both platform builds agree on the version.

## API Reference

Rows and columns are zero-indexed on both platforms: row `0`, column `0` is Excel cell `A1`.

### Shared Rust API (via UniFFI)

- `Workbook::new()` — create an empty workbook.
- `add_worksheet()` / `add_worksheet_with_name(name)` — add a worksheet and return its index.
- `write_string(...)`, `write_number(...)`, `write_integer(...)`, `write_boolean(...)` — write primitive values.
- `write_date(...)`, `write_datetime(...)`, `write_date_with_format(...)` — write formatted Excel dates/datetimes.
- `set_column_width(...)`, `set_row_height(...)` — size columns and rows.
- `worksheet_count()` — return the current number of worksheets.
- `save(path)` / `save_to_buffer()` — write to a filesystem path or memory.

### Swift API

`XlsxWorkbook` is an actor, so calls from outside its isolation use `await`; fallible calls also use `try`.

```swift
let workbook = XlsxWorkbook()
let sheet = try await workbook.addWorksheet(name: "Data")

try await workbook.writeString(sheet: sheet, row: 0, col: 0, value: "Hello")
try await workbook.writeNumber(sheet: sheet, row: 1, col: 0, value: 3.14159)
try await workbook.writeInteger(sheet: sheet, row: 2, col: 0, value: 42)
try await workbook.writeBoolean(sheet: sheet, row: 3, col: 0, value: true)
try await workbook.writeDate(
    sheet: sheet,
    row: 4,
    col: 0,
    date: ExcelDateValue(year: 2026, month: 8, day: 3)
)
try await workbook.setColumnWidth(sheet: sheet, col: 0, width: 20)
try await workbook.setRowHeight(sheet: sheet, row: 0, height: 30)

try await workbook.save(to: "/path/to/output.xlsx")
let data = try await workbook.saveToBuffer()
```

The Swift façade also accepts Foundation `Date` values, supports explicit `ExcelDateTimeValue`, custom date formats, `URL` output, and `worksheetCount()`.

### Kotlin API

`XlsxWorkbook` is synchronous and `AutoCloseable`. Use Kotlin's `use` block, and run large workbook writes on an IO dispatcher or another background thread.

```kotlin
val bytes = XlsxWorkbook().use { workbook ->
    val sheet = workbook.addWorksheet("Data")
    workbook.writeString(sheet, row = 0, column = 0, value = "Hello")
    workbook.writeInteger(sheet, row = 1, column = 0, value = 42L)
    workbook.writeNumber(sheet, row = 2, column = 0, value = 3.14159)
    workbook.writeDate(sheet, row = 3, column = 0, value = LocalDate.of(2026, 8, 3))
    workbook.setColumnWidth(sheet, column = 0, width = 20.0)
    workbook.saveToByteArray()
}
```

| Public Kotlin API | Purpose |
| --- | --- |
| `XlsxWorkbook()` | Create an empty Rust-backed workbook. |
| `addWorksheet(name: String): Worksheet` | Add a named sheet and return an opaque workbook-owned handle. |
| `writeString(sheet, row, column, value)` | Write a `String`. |
| `writeInteger(sheet, row, column, value)` | Write a `Long` (subject to XLSX number precision limits). |
| `writeNumber(sheet, row, column, value)` | Write a `Double`. |
| `writeDate(sheet, row, column, value)` | Write a `LocalDate` without timezone conversion. |
| `setColumnWidth(sheet, column, width)` | Set width in Excel character units. |
| `save(file: File)` | Save to a writable filesystem path. |
| `saveToByteArray(): ByteArray` | Save in memory; use this for `content://` output streams. |
| `close()` | Release the UniFFI object; normally called by `use`. |

The façade validates Excel's row/column bounds, rejects `LocalDate` years outside UniFFI's unsigned 16-bit range (`0..65535`), and rejects a `Worksheet` created by another workbook with `IllegalArgumentException`. Rust failures map to the stable sealed `XlsxWriterException` hierarchy (`Io`, `RowColumnLimit`, `MaxStringLength`, `WorksheetNameReused`, `InvalidParameter`, `WorksheetNotFound`, `InvalidDate`, and `Unknown`).

## Apple Build Considerations

- `build-ios.sh` builds `aarch64-apple-ios`, `aarch64-apple-ios-sim`, `x86_64-apple-ios`, `aarch64-apple-darwin`, and `x86_64-apple-darwin` before combining simulator and macOS slices.
- UniFFI produces `xlsxwriterFFI.modulemap`; the build script renames it to `module.modulemap` for the XCFramework.
- `Sources/XlsxWriterFFI/xlsxwriter.swift` is tracked generated output. Review its changes after regenerating it.

## Android Build Considerations

- Required tooling: JDK 17, Android SDK 36, Android NDK 28.2, and `cargo-ndk` 4.1.2.
- The library has min SDK 24 and ships only the 64-bit `arm64-v8a` and `x86_64` ABIs.
- Consumers must enable core-library desugaring and depend on `com.android.tools:desugar_jdk_libs:2.1.5` because the API uses `java.time.LocalDate` on API 24 and 25.
- JNA must remain a public Android AAR dependency (`api("net.java.dev.jna:jna:5.19.1@aar")`) so consuming apps receive it on their compile/runtime classpaths and package `libjnidispatch.so` transitively.
- Android generated bindings and native libraries live under `android/xlsxwriter-android/build/`; they are build artifacts and are not committed.

## Local Swift Development Flag

`Package.swift` defines `useLocalFramework` near the top:

```swift
let useLocalFramework = false
```

- Leave it `false` in commits so consumers use the GitHub Release XCFramework.
- To test local Rust changes through Swift, set it to `true`, run `./build-ios.sh`, then run `swift test`.
- Restore it to `false` before committing. `build-ios.sh` also updates the release tag and checksum, so review the `Package.swift` diff deliberately.

## Testing Expectations

- Run `cargo test --locked` for shared Rust changes.
- Run `swift test` for Swift façade or generated Swift binding changes.
- Run `:xlsxwriter-android:testDebugUnitTest` for fast Kotlin façade and validation feedback against the host Rust library; the root `verifyReleaseArtifacts` gate also depends on it.
- Run the full Android publication verification command for Android, Rust, Gradle, or release changes.
- Run `connectedDebugAndroidTest` when the Kotlin API, Rust calls used by Android, native packaging, or XLSX serialization behavior changes.
- The Android instrumentation test is intentionally a real round trip: it generates XLSX bytes with the public Kotlin façade and reopens them with FastExcel Reader.
