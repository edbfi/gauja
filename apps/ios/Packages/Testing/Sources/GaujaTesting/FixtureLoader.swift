// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation

public enum FixtureLoader {
    public static func read(_ relative: String) throws -> Data {
        guard !relative.contains(".."), !relative.hasPrefix("/") else { throw AppError.validation }
        var directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while !FileManager.default.fileExists(atPath: directory.appendingPathComponent("api/UPSTREAM_COMMIT").path) {
            guard directory.path != "/" else { throw AppError.notFound }
            directory.deleteLastPathComponent()
        }
        return try Data(contentsOf: directory.appendingPathComponent("api/fixtures/3.4.1/" + relative))
    }
}
