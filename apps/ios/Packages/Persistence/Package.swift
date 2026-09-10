// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "Persistence",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "Persistence", targets: ["Persistence"])],
    dependencies: [.package(path: "../Model"), .package(path: "../Common")],
    targets: [
        .target(
            name: "Persistence", dependencies: ["Model", "Common"],
            swiftSettings: [
                .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]),
        .testTarget(name: "PersistenceTests", dependencies: ["Persistence"]),
    ],
    swiftLanguageModes: [.v6]
)
