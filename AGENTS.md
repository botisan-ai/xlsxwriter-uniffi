# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Rust-to-Swift wrapper that exposes the `rust_xlsxwriter` crate (Excel XLSX file writer) to iOS/macOS applications using Mozilla's UniFFI for FFI bindings generation.

## Build Commands

```bash
# Full build (generates bindings + builds for all iOS targets + creates XCFramework)
./build-ios.sh

# Run Rust tests
cargo test

# Run Swift tests (requires build-ios.sh to have been run first)
swift test

# Manual bindgen (usually run via build-ios.sh)
cargo run --bin uniffi-bindgen generate --library ./target/debug/libxlsxwriter.dylib --language swift --out-dir ./out
```

## Architecture: Rust + Swift via UniFFI

### Layer Structure

```
┌─────────────────────────────────────────┐
│  Swift App Layer                        │
│  (Uses XlsxWorkbook actor)              │
├─────────────────────────────────────────┤
│  Sources/XlsxWriterSwift/               │
│  XlsxWriterSwift.swift                  │
│  (Swift-native API with actors)         │
├─────────────────────────────────────────┤
│  Sources/XlsxWriterFFI/xlsxwriter.swift │
│  (UniFFI-generated bindings)            │
├─────────────────────────────────────────┤
│  XCFramework (libxlsxwriter-rs.xcframework)│
│  (Compiled Rust static libraries)       │
├─────────────────────────────────────────┤
│  src/lib.rs                             │
│  (Rust implementation with UniFFI attrs)│
└─────────────────────────────────────────┘
```

### Key Files

- **src/lib.rs**: Rust implementation with `#[uniffi::export]` annotations wrapping the `rust_xlsxwriter` crate
- **src/uniffi-bindgen.rs**: Binary that invokes UniFFI's Swift code generator
- **Sources/XlsxWriterFFI/xlsxwriter.swift**: Auto-generated Swift bindings (do not edit manually)
- **Sources/XlsxWriterSwift/XlsxWriterSwift.swift**: Hand-written Swift wrapper providing idiomatic API with actors
- **build-ios.sh**: Build script that orchestrates the entire build pipeline

## API Reference

### Rust API (via UniFFI)

- `Workbook::new()` - Create new workbook
- `add_worksheet()` / `add_worksheet_with_name(name)` - Add worksheets
- `write_string(sheet_index, row, col, value)` - Write string to cell
- `write_number(sheet_index, row, col, value)` - Write float to cell
- `write_integer(sheet_index, row, col, value)` - Write integer to cell
- `write_boolean(sheet_index, row, col, value)` - Write boolean to cell
- `write_date(sheet_index, row, col, date)` - Write date to cell
- `write_datetime(sheet_index, row, col, datetime)` - Write datetime to cell
- `set_column_width(sheet_index, col, width)` - Set column width
- `set_row_height(sheet_index, row, height)` - Set row height
- `save(path)` - Save workbook to file
- `save_to_buffer()` - Save workbook to memory buffer

### Swift API

```swift
// Create a new workbook
let workbook = XlsxWorkbook()

// Add worksheets
let sheet = try await workbook.addWorksheet()
let namedSheet = try await workbook.addWorksheet(name: "Data")

// Write data
try await workbook.writeString(sheet: sheet, row: 0, col: 0, value: "Hello")
try await workbook.writeNumber(sheet: sheet, row: 1, col: 0, value: 3.14159)
try await workbook.writeInteger(sheet: sheet, row: 2, col: 0, value: 42)
try await workbook.writeBoolean(sheet: sheet, row: 3, col: 0, value: true)

// Write dates
let date = ExcelDateValue(year: 2024, month: 12, day: 25)
try await workbook.writeDate(sheet: sheet, row: 4, col: 0, date: date)

// Or use Foundation Date directly
try await workbook.writeDate(sheet: sheet, row: 5, col: 0, date: Date())

// Set column/row sizes
try await workbook.setColumnWidth(sheet: sheet, col: 0, width: 20.0)
try await workbook.setRowHeight(sheet: sheet, row: 0, height: 30.0)

// Save to file
try await workbook.save(to: "/path/to/output.xlsx")

// Or save to buffer
let data = try await workbook.saveToBuffer()
```

## iOS Build Considerations

- **Targets**: Build for `aarch64-apple-ios` (devices), `aarch64-apple-ios-sim` (Apple Silicon simulators), `aarch64-apple-darwin` (macOS)
- **Modulemap naming**: UniFFI generates `xlsxwriterFFI.modulemap` but Swift packages require `module.modulemap`

## Local Development Flag

In `Package.swift`, line 5:
```swift
let useLocalFramework = true  // Change to false before committing
```

**IMPORTANT**:
- Set to `true` when developing locally (uses `./build/libxlsxwriter-rs.xcframework`)
- Set to `false` before committing (uses GitHub release binary)
- Run `./build-ios.sh` first when `useLocalFramework = true`

## Testing

Swift tests are in `Tests/XlsxWriterSwiftTests/`. The test suite demonstrates:
- Creating workbooks and worksheets
- Writing strings, numbers, and dates
- Saving to file and buffer
- Multiple worksheet handling
