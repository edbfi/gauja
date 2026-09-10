// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "Network",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "Network", targets: ["Network"])],
    dependencies: [
        .package(path: "../Model"),
        .package(path: "../Testing"),
        .package(path: "../Persistence"),
        .package(path: "../Common"),
        .package(url: "https://github.com/apple/swift-openapi-runtime", exact: "1.12.1"),
        .package(url: "https://github.com/apple/swift-openapi-urlsession", exact: "1.3.1"),
        .package(url: "https://github.com/apple/swift-http-types", exact: "1.8.0"),
    ],
    targets: [
        .target(
            name: "Network",
            dependencies: [
                "Model", "Common", "Persistence", .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
                .product(name: "OpenAPIURLSession", package: "swift-openapi-urlsession"),
                .product(name: "HTTPTypes", package: "swift-http-types"),
            ],
            swiftSettings: [
                .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]),
        .testTarget(
            name: "NetworkTests", dependencies: ["Network", .product(name: "GaujaTesting", package: "Testing")]),
    ],
    swiftLanguageModes: [.v6]
)
