// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
public enum AppError: String, Error, Sendable {
    case network, tls, auth, permission, notFound, validation, serverVersion, offline, unknown
    public var messageKey: String { "error.\(rawValue)" }
}

public typealias AppResult<Value> = Result<Value, AppError>
