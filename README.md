# XlsxWriter for Swift and Kotlin

Swift and Kotlin/Android wrappers for [rust_xlsxwriter](https://github.com/jmcnamara/rust_xlsxwriter), a high-performance Excel XLSX file writer written in Rust. [UniFFI](https://github.com/mozilla/uniffi-rs) generates both language bindings from the same Rust core.

## Features

- Create Excel 2007+ (.xlsx) files from iOS, macOS, and Android apps
- Write strings, integers, numbers, and dates to named worksheets on both platforms
- Set column widths and save to a file or in-memory buffer
- Swift `actor` isolation and an Android `AutoCloseable` API
- Native Foundation `Date` support on Swift and `LocalDate` support on Android
- Additional Swift APIs for booleans, datetimes, row heights, and custom date formats

## Installation

### Swift Package Manager

Add the package to your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/botisan-ai/XlsxWriter.swift.git", from: "0.1.3")
]
```

Or add it via Xcode: File → Add Package Dependencies → Enter the repository URL.

### Android

Android releases are distributed as a zipped folder-based Maven repository. Download the `xlsxwriter-android-<version>-maven.zip` asset and its `.sha256` sidecar from the matching GitHub Release, verify the checksum, then extract it into your project—for example, `third_party/xlsxwriter`.

```sh
cd <download-directory>
shasum -a 256 -c xlsxwriter-android-0.2.0-maven.zip.sha256
```

Point Gradle at the extracted repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri(rootDir.resolve("third_party/xlsxwriter/xlsxwriter-android-0.2.0"))
        }
        google()
        mavenCentral()
    }
}
```

Add the normal Maven coordinate. JNA is resolved transitively as an Android AAR:

```kotlin
// app/build.gradle.kts
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("ai.botisan:xlsxwriter-android:0.2.0")
}
```

Enable core-library desugaring in the app's `android.compileOptions` so `LocalDate` works on API 24 and 25:

```kotlin
compileOptions {
    isCoreLibraryDesugaringEnabled = true
}
```

## Swift usage

### Basic Example

```swift
import XlsxWriterSwift

// Create a new workbook
let workbook = XlsxWorkbook()

// Add a worksheet
let sheet = try await workbook.addWorksheet(name: "Data")

// Write different data types
try await workbook.writeString(sheet: sheet, row: 0, col: 0, value: "Hello, Excel!")
try await workbook.writeNumber(sheet: sheet, row: 1, col: 0, value: 3.14159)
try await workbook.writeInteger(sheet: sheet, row: 2, col: 0, value: 42)
try await workbook.writeBoolean(sheet: sheet, row: 3, col: 0, value: true)

// Save to file
try await workbook.save(to: "/path/to/output.xlsx")
```

### Working with Dates

```swift
import XlsxWriterSwift

let workbook = XlsxWorkbook()
let sheet = try await workbook.addWorksheet()

// Using Foundation Date
try await workbook.writeDate(sheet: sheet, row: 0, col: 0, date: Date())
try await workbook.writeDateTime(sheet: sheet, row: 1, col: 0, datetime: Date())

// Using explicit date values
let christmas = ExcelDateValue(year: 2024, month: 12, day: 25)
try await workbook.writeDate(sheet: sheet, row: 2, col: 0, date: christmas)

// With custom format
try await workbook.writeDateWithFormat(
    sheet: sheet,
    row: 3,
    col: 0,
    date: christmas,
    format: "dd/mm/yyyy"
)

try await workbook.save(to: "dates.xlsx")
```

### Multiple Worksheets

```swift
import XlsxWriterSwift

let workbook = XlsxWorkbook()

// Add multiple worksheets
let dataSheet = try await workbook.addWorksheet(name: "Data")
let summarySheet = try await workbook.addWorksheet(name: "Summary")

// Write to different sheets
try await workbook.writeString(sheet: dataSheet, row: 0, col: 0, value: "Raw Data")
try await workbook.writeNumber(sheet: dataSheet, row: 1, col: 0, value: 100)
try await workbook.writeNumber(sheet: dataSheet, row: 2, col: 0, value: 200)

try await workbook.writeString(sheet: summarySheet, row: 0, col: 0, value: "Total")
try await workbook.writeNumber(sheet: summarySheet, row: 0, col: 1, value: 300)

// Check worksheet count
let count = await workbook.worksheetCount()
print("Workbook has \(count) worksheets")

try await workbook.save(to: "multi-sheet.xlsx")
```

### Column and Row Sizing

```swift
import XlsxWriterSwift

let workbook = XlsxWorkbook()
let sheet = try await workbook.addWorksheet()

// Set column width (in Excel character units)
try await workbook.setColumnWidth(sheet: sheet, col: 0, width: 20.0)
try await workbook.setColumnWidth(sheet: sheet, col: 1, width: 15.0)

// Set row height (in points)
try await workbook.setRowHeight(sheet: sheet, row: 0, height: 30.0)

try await workbook.writeString(sheet: sheet, row: 0, col: 0, value: "Wide column, tall row")

try await workbook.save(to: "sized.xlsx")
```

### Save to Buffer

```swift
import XlsxWriterSwift

let workbook = XlsxWorkbook()
let sheet = try await workbook.addWorksheet()
try await workbook.writeString(sheet: sheet, row: 0, col: 0, value: "In-memory")

// Get the workbook as Data (useful for network upload, etc.)
let data: Data = try await workbook.saveToBuffer()

// The data is a valid XLSX file (ZIP format)
print("Generated \(data.count) bytes")
```

## Android usage

The Kotlin façade uses ordinary `Int`, `Long`, `Double`, and `File` types. It hides the generated UniFFI API, unsigned indices, JNA, and native handles.

```kotlin
import ai.botisan.xlsxwriter.XlsxWorkbook
import java.time.LocalDate

val workbookBytes = XlsxWorkbook().use { workbook ->
    val receipts = workbook.addWorksheet("Receipts")
    workbook.writeString(receipts, row = 0, column = 0, value = "Merchant")
    workbook.writeInteger(receipts, row = 1, column = 0, value = 1)
    workbook.writeNumber(receipts, row = 1, column = 1, value = 12.34)
    workbook.writeDate(receipts, row = 1, column = 2, value = LocalDate.of(2026, 8, 3))
    workbook.setColumnWidth(receipts, column = 0, width = 18.0)
    workbook.saveToByteArray()
}
requireNotNull(contentResolver.openOutputStream(outputUri)).use { output ->
    output.write(workbookBytes)
}
```

Calls are synchronous. Write larger workbooks on an IO dispatcher or another background thread. `saveToByteArray()` is the direct path for a `content://` destination; `save(File)` is also available for a writable filesystem path. The package ships `arm64-v8a` and `x86_64` native libraries.

## Swift API Reference

### XlsxWorkbook

| Method | Description |
|--------|-------------|
| `init()` | Create a new empty workbook |
| `addWorksheet() -> UInt32` | Add a worksheet, returns sheet index |
| `addWorksheet(name:) -> UInt32` | Add a named worksheet |
| `writeString(sheet:row:col:value:)` | Write a string to a cell |
| `writeNumber(sheet:row:col:value:)` | Write a Double to a cell |
| `writeInteger(sheet:row:col:value:)` | Write an Int64 to a cell |
| `writeBoolean(sheet:row:col:value:)` | Write a Bool to a cell |
| `writeDate(sheet:row:col:date:)` | Write a date (ExcelDateValue or Date) |
| `writeDateTime(sheet:row:col:datetime:)` | Write a datetime |
| `writeDateWithFormat(sheet:row:col:date:format:)` | Write a date with custom format |
| `setColumnWidth(sheet:col:width:)` | Set column width |
| `setRowHeight(sheet:row:height:)` | Set row height |
| `worksheetCount() -> UInt32` | Get number of worksheets |
| `save(to:)` | Save to file path or URL |
| `saveToBuffer() -> Data` | Save to in-memory buffer |

### Cell Addressing

- Rows and columns are **zero-indexed**
- Row 0, Col 0 = Cell A1
- Row 0, Col 1 = Cell B1
- Row 1, Col 0 = Cell A2

## Development

### Prerequisites

- Rust stable with the targets listed in `rust-toolchain.toml`
- Xcode with Swift 6.0+
- JDK 17, Android SDK 36, and Android NDK 28.2+
- `cargo-ndk` 4.1.2: `cargo install cargo-ndk --version 4.1.2 --locked`

### Building

```bash
# Install Rust targets (if not already done via rust-toolchain.toml)
rustup target add aarch64-apple-ios aarch64-apple-ios-sim aarch64-apple-darwin

# Build everything (Rust lib + Swift bindings + XCFramework)
./build-ios.sh

# Run tests
swift test

# Build, publish, and verify the Android release through a coordinate-only consumer
./android/gradlew -p android verifyReleaseArtifacts ktlintCheck \
  :xlsxwriter-android:lintRelease :consumer:lintDebug

# Run the Kotlin/native round-trip on a connected emulator or device
./android/gradlew -p android :xlsxwriter-android:connectedDebugAndroidTest

# Produce the Maven repository ZIP, standalone AAR, and SHA-256 sidecars
./android/gradlew -p android :xlsxwriter-android:generateReleaseChecksums
```

### Project Structure

```
XlsxWriter.swift/
├── src/
│   ├── lib.rs              # Rust FFI implementation
│   └── uniffi-bindgen.rs   # UniFFI code generator
├── Sources/
│   ├── XlsxWriterFFI/      # Auto-generated Swift bindings
│   └── XlsxWriterSwift/    # Hand-written Swift wrapper
├── Tests/
│   └── XlsxWriterSwiftTests/
├── android/
│   ├── xlsxwriter-android/ # Published Kotlin/AAR library
│   └── consumer/           # Maven-coordinate consumer smoke build
├── build-ios.sh            # Build script
├── Cargo.toml              # Rust dependencies
└── Package.swift           # Swift package manifest
```

## Limitations

This initial version focuses on basic functionality. Not yet supported:

- Cell formatting (bold, colors, borders)
- Formulas
- Charts
- Images
- Merged cells
- Hyperlinks
- Exact integers above 2^53; the shared Rust core currently writes integers through an XLSX number

These features are available in the underlying [rust_xlsxwriter](https://docs.rs/rust_xlsxwriter) crate and may be exposed in future versions.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [rust_xlsxwriter](https://github.com/jmcnamara/rust_xlsxwriter) - The underlying Rust implementation
- [XlsxWriter](https://xlsxwriter.readthedocs.io/) - Original Python implementation by the same author
- [UniFFI](https://github.com/mozilla/uniffi-rs) - Mozilla's FFI bindings generator
