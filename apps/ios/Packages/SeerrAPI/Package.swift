// swift-tools-version: 6.2
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import PackageDescription

let package = Package(
    name: "SeerrAPI",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "SeerrAPI", targets: ["SeerrAPI"])],
    dependencies: [
        .package(url: "https://github.com/apple/swift-openapi-runtime", exact: "1.12.1"),
        .package(url: "https://github.com/apple/swift-http-types", exact: "1.8.0"),
    ],
    targets: [
        .target(
            name: "SeerrAPI",
            dependencies: [
                .product(name: "OpenAPIRuntime", package: "swift-openapi-runtime"),
                .product(name: "HTTPTypes", package: "swift-http-types"),
            ], path: "Generated",
            swiftSettings: [
                .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
                .enableUpcomingFeature("InferIsolatedConformances"),
            ]),
        .testTarget(name: "SeerrAPITests", dependencies: ["SeerrAPI"]),
    ],
    swiftLanguageModes: [.v6]
)
