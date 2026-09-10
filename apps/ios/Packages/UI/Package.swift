// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "UI", platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "UI", targets: ["UI"])],
    dependencies: [.package(path: "../Model"), .package(path: "../Common"), .package(path: "../DesignSystem")],
    targets: [
        .target(
            name: "UI", dependencies: ["Model", "Common", "DesignSystem"],
            swiftSettings: [
                .defaultIsolation(MainActor.self), .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ])
    ], swiftLanguageModes: [.v6])
