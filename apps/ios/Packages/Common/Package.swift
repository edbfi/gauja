// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "Common",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "Common", targets: ["Common"])],
    dependencies: [
        .package(path: "../Model"),
        .package(url: "https://github.com/pointfreeco/swift-dependencies", exact: "1.17.1"),
    ],
    targets: [
        .target(
            name: "Common", dependencies: ["Model", .product(name: "Dependencies", package: "swift-dependencies")],
            swiftSettings: [
                .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]),
        .testTarget(name: "CommonTests", dependencies: ["Common"]),
    ],
    swiftLanguageModes: [.v6]
)
