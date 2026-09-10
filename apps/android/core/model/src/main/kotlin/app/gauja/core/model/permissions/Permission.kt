// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.permissions

// Seerr server/lib/permissions.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
enum class Permission(val mask: Long) {
    NONE(0),
    ADMIN(2L),
    MANAGE_SETTINGS(4L),
    MANAGE_USERS(8L),
    MANAGE_REQUESTS(16L),
    REQUEST(32L),
    VOTE(64L),
    AUTO_APPROVE(128L),
    AUTO_APPROVE_MOVIE(256L),
    AUTO_APPROVE_TV(512L),
    REQUEST_4K(1024L),
    REQUEST_4K_MOVIE(2048L),
    REQUEST_4K_TV(4096L),
    REQUEST_ADVANCED(8192L),
    REQUEST_VIEW(16384L),
    AUTO_APPROVE_4K(32768L),
    AUTO_APPROVE_4K_MOVIE(65536L),
    AUTO_APPROVE_4K_TV(131072L),
    REQUEST_MOVIE(262144L),
    REQUEST_TV(524288L),
    MANAGE_ISSUES(1048576L),
    VIEW_ISSUES(2097152L),
    CREATE_ISSUES(4194304L),
    AUTO_REQUEST(8388608L),
    AUTO_REQUEST_MOVIE(16777216L),
    AUTO_REQUEST_TV(33554432L),
    RECENT_VIEW(67108864L),
    WATCHLIST_VIEW(134217728L),
    MANAGE_BLOCKLIST(268435456L),
    VIEW_BLOCKLIST(1073741824L),
}

enum class PermissionMode {
    AND,
    OR,
}

// Scalar masks match any contained bit, including when callers combine flags.
fun hasPermission(required: Long, user: Long): Boolean =
    required == 0L || user and Permission.ADMIN.mask != 0L || user and required != 0L

fun hasPermission(
    required: List<Long>,
    user: Long,
    mode: PermissionMode = PermissionMode.AND,
): Boolean {
    if (user and Permission.ADMIN.mask != 0L) return true
    return when (mode) {
        PermissionMode.AND -> required.all { user and it != 0L }
        PermissionMode.OR -> required.any { user and it != 0L }
    }
}
