// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import SwiftData

public enum CacheSchemaV1: VersionedSchema {
    public static let versionIdentifier = Schema.Version(1, 0, 0)
    public static var models: [any PersistentModel.Type] { [ProfileRow.self, UserRow.self, TitleRow.self] }

    @Model final class ProfileRow {
        #Index<ProfileRow>([\.profileID])
        @Attribute(.unique) var profileID: UUID
        var position: Int
        var payload: Data
        init(profileID: UUID, position: Int, payload: Data) {
            self.profileID = profileID
            self.position = position
            self.payload = payload
        }
    }

    @Model final class UserRow {
        #Index<UserRow>([\.profileID])
        @Attribute(.unique) var profileID: UUID
        var payload: Data
        var fetchedAt: Date
        init(profileID: UUID, payload: Data, fetchedAt: Date) {
            self.profileID = profileID
            self.payload = payload
            self.fetchedAt = fetchedAt
        }
    }

    @Model final class TitleRow {
        #Unique<TitleRow>([\.profileID, \.mediaType, \.tmdbID])
        #Index<TitleRow>([\.profileID, \.mediaType, \.tmdbID])
        var profileID: UUID
        var mediaType: String
        var tmdbID: Int
        var payload: Data
        var fetchedAt: Date
        init(profileID: UUID, mediaType: String, tmdbID: Int, payload: Data, fetchedAt: Date) {
            self.profileID = profileID
            self.mediaType = mediaType
            self.tmdbID = tmdbID
            self.payload = payload
            self.fetchedAt = fetchedAt
        }
    }
}

public enum CacheMigrationPlan: SchemaMigrationPlan {
    public static var schemas: [any VersionedSchema.Type] { [CacheSchemaV1.self] }
    public static var stages: [MigrationStage] { [] }
}

public enum PersistenceContainer {
    public static func make(url: URL? = nil, inMemory: Bool = false) throws -> ModelContainer {
        let schema = Schema(versionedSchema: CacheSchemaV1.self)
        let configuration =
            if let url {
                ModelConfiguration(schema: schema, url: url, cloudKitDatabase: .none)
            } else {
                ModelConfiguration(schema: schema, isStoredInMemoryOnly: inMemory, cloudKitDatabase: .none)
            }
        return try ModelContainer(for: schema, migrationPlan: CacheMigrationPlan.self, configurations: [configuration])
    }
}
