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
    let releaseTag = "0.1.3"
    let releaseChecksum = "637e47e49db2b3959cd753612c0516af7dfc3541695f8cc12b12fb122f14b3c3"
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
