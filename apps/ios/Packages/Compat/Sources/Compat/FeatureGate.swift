// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public struct FeatureMetadata: Sendable, Decodable {
    public let min: String
    public let max: String?
    public let endpoint: String
    public let note: String
}

public struct FeatureGate: Sendable {
    private let features: [String: FeatureMetadata]
    public init(data: Data) throws { features = try JSONDecoder().decode([String: FeatureMetadata].self, from: data) }

    public static func bundled() throws -> Self {
        guard let url = Bundle.module.url(forResource: "compat", withExtension: "json") else {
            throw CocoaError(.fileNoSuchFile)
        }
        return try Self(data: Data(contentsOf: url))
    }

    public func metadata(_ featureID: String) -> FeatureMetadata? { features[featureID] }

    public func isSupported(_ featureID: String, version: ServerVersion?) -> Bool {
        guard let feature = features[featureID], let minimum = ServerVersion(feature.min),
            let version, version >= minimum
        else { return false }
        guard let maximumText = feature.max else { return true }
        guard let maximum = ServerVersion(maximumText) else { return false }
        return version <= maximum
    }
}
