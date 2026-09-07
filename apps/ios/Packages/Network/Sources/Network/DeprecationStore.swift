// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import HTTPTypes
import Model

public struct EndpointDeprecation: Sendable, Equatable {
    public let endpoint: String
    public let deprecation: String?
    public let sunset: String?
    public let successor: String?
}

public actor DeprecationStore {
    private var entries: [ProfileID: [String: EndpointDeprecation]] = [:]
    public init() {}
    func record(_ id: ProfileID, endpoint: String, fields: HTTPFields) {
        let deprecation = fields.first { $0.name.canonicalName == "deprecation" }?.value
        let sunset = fields.first { $0.name.canonicalName == "sunset" }?.value
        let successor = fields.filter { $0.name.canonicalName == "link" }
            .flatMap { $0.value.split(separator: ",").map(String.init) }
            .first { $0.contains("rel=\"successor-version\"") || $0.contains("rel=successor-version") }
        guard deprecation != nil || sunset != nil || successor != nil else { return }
        if entries[id, default: [:]].count >= 512 { entries[id] = [:] }
        entries[id, default: [:]][endpoint] = EndpointDeprecation(
            endpoint: endpoint, deprecation: deprecation, sunset: sunset, successor: successor)
    }
    public func read(_ id: ProfileID) -> [EndpointDeprecation] { Array(entries[id, default: [:]].values) }
    public func clear(_ id: ProfileID) { entries[id] = nil }
}
