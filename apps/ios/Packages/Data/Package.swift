// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "Data",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "Data", targets: ["Data"])],
    dependencies: [
        .package(path: "../SeerrAPI"),
        .package(path: "../Model"),
        .package(path: "../Persistence"),
        .package(path: "../Common"),
        .package(path: "../Compat"),
        .package(path: "../Network"),
        .package(url: "https://github.com/pointfreeco/swift-dependencies", exact: "1.17.1"),
        .package(url: "https://github.com/apple/swift-openapi-runtime", exact: "1.12.1"),
    ],
    targets: [
        .target(
            name: "Data",
            dependencies: [
                "SeerrAPI", "Model", "Common", "Persistence", "Compat", "Network",
                .product(name: "Dependencies", package: "swift-dependencies"),
                .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
            ],
            swiftSettings: [
                .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]),
        .testTarget(
            name: "DataTests", dependencies: ["Data"],
            swiftSettings: [.defaultIsolation(MainActor.self)]),
    ],
    swiftLanguageModes: [.v6]
)
