// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "Compat",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "Compat", targets: ["Compat"])],
    dependencies: [
        .package(path: "../Model")
    ],
    targets: [
        .target(
            name: "Compat", dependencies: ["Model"],
            resources: [.copy("Resources/compat.json")],
            swiftSettings: [
                .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]),
        .testTarget(name: "CompatTests", dependencies: ["Compat"]),
    ],
    swiftLanguageModes: [.v6]
)
