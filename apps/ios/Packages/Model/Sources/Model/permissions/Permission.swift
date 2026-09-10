// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/lib/permissions.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum Permission: UInt32, Sendable, CaseIterable {
    case none = 0
    case admin = 2
    case manageSettings = 4
    case manageUsers = 8
    case manageRequests = 16
    case request = 32
    case vote = 64
    case autoApprove = 128
    case autoApproveMovie = 256
    case autoApproveTv = 512
    case request4k = 1024
    case request4kMovie = 2048
    case request4kTv = 4096
    case requestAdvanced = 8192
    case requestView = 16384
    case autoApprove4k = 32768
    case autoApprove4kMovie = 65536
    case autoApprove4kTv = 131072
    case requestMovie = 262144
    case requestTv = 524288
    case manageIssues = 1_048_576
    case viewIssues = 2_097_152
    case createIssues = 4_194_304
    case autoRequest = 8_388_608
    case autoRequestMovie = 16_777_216
    case autoRequestTv = 33_554_432
    case recentView = 67_108_864
    case watchlistView = 134_217_728
    case manageBlocklist = 268_435_456
    case viewBlocklist = 1_073_741_824
}

public enum PermissionMode: Sendable { case and, or }

public func hasPermission(_ required: UInt32, user: UInt32) -> Bool {
    required == 0 || user & Permission.admin.rawValue != 0 || user & required != 0
}

public func hasPermission(_ required: [UInt32], user: UInt32, mode: PermissionMode = .and) -> Bool {
    if user & Permission.admin.rawValue != 0 { return true }
    switch mode {
    case .and: return required.allSatisfy { user & $0 != 0 }
    case .or: return required.contains { user & $0 != 0 }
    }
}
