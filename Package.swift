// swift-tools-version: 6.0

import PackageDescription

let useLocalFramework = false
let binaryTarget: Target

if useLocalFramework {
    binaryTarget = .binaryTarget(
        name: "XlsxWriterRS",
        path: "./build/libxlsxwriter-rs.xcframework"
    )
} else {
    let releaseTag = "0.2.0"
    let releaseChecksum = "dfce294c17a7dcc221ba4a2ab8f9eba002a0ab7f74491f34971eedebf9d841a6"
    binaryTarget = .binaryTarget(
        name: "XlsxWriterRS",
        url:
        "https://github.com/botisan-ai/xlsxwriter-uniffi/releases/download/\(releaseTag)/libxlsxwriter-rs.xcframework.zip",
        checksum: releaseChecksum
    )
}

let package = Package(
    name: "XlsxWriterSwift",
    platforms: [
        .iOS(.v13),
        .macOS(.v10_15),
    ],
    products: [
        .library(
            name: "XlsxWriterSwift",
            targets: ["XlsxWriterSwift"]
        ),
    ],
    targets: [
        binaryTarget,
        .target(
            name: "XlsxWriterSwift",
            dependencies: ["XlsxWriterFFI"]
        ),
        .target(
            name: "XlsxWriterFFI",
            dependencies: ["XlsxWriterRS"]
        ),
        .testTarget(
            name: "XlsxWriterSwiftTests",
            dependencies: ["XlsxWriterSwift"]
        ),
    ]
)
