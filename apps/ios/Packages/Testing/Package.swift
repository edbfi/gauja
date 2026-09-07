// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "Testing", platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "GaujaTesting", targets: ["GaujaTesting"])],
    dependencies: [
        .package(path: "../Common"), .package(path: "../Model"), .package(path: "../Persistence"),
        .package(path: "../UI"), .package(path: "../DesignSystem"),
    ],
    targets: [
        .target(
            name: "GaujaTesting", dependencies: ["Common", "Model", "Persistence", "UI", "DesignSystem"],
            swiftSettings: [
                .defaultIsolation(MainActor.self), .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ])
    ], swiftLanguageModes: [.v6])
