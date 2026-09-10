// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.common

enum class AppError(val messageKey: String) {
    NETWORK("error.network"),
    TLS("error.tls"),
    AUTH("error.auth"),
    PERMISSION("error.permission"),
    NOT_FOUND("error.notFound"),
    VALIDATION("error.validation"),
    SERVER_VERSION("error.serverVersion"),
    OFFLINE("error.offline"),
    UNKNOWN("error.unknown"),
}

// Exceptions carry only an allow-listed category, never an underlying HTTP body or credential.
class AppException(val reason: AppError) : Exception(reason.messageKey)

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}
