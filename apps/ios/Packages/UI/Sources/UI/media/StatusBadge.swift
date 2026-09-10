// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import DesignSystem
import Model
import SwiftUI

public struct StatusBadge: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.colorScheme) private var scheme
    private let label: LocalizedStringKey
    private let family: Family
    private let is4K: Bool
    private enum Family { case warning, processing, available, danger, neutral }

    public init(status: MediaStatus, is4K: Bool = false) {
        self.is4K = is4K
        (label, family) =
            switch status {
            case .pending: ("Pending", .warning)
            case .processing: ("Processing", .processing)
            case .partiallyAvailable: ("Partially available", .available)
            case .available: ("Available", .available)
            case .blocklisted: ("Blocklisted", .danger)
            case .deleted: ("Deleted", .danger)
            case .unknown, .unrecognized: ("Unknown status", .neutral)
            }
    }

    public init(status: MediaRequestStatus, is4K: Bool = false) {
        self.is4K = is4K
        (label, family) =
            switch status {
            case .pending: ("Pending", .warning)
            case .approved: ("Approved", .processing)
            case .declined: ("Declined", .danger)
            case .failed: ("Failed", .danger)
            case .completed: ("Completed", .available)
            case .unrecognized: ("Unknown status", .neutral)
            }
    }

    public var body: some View {
        HStack(spacing: 4) {
            if is4K { Text("4K") }
            Text(label)
        }
        .font(.gaujaLabelMedium(dynamicTypeSize)).foregroundStyle(colors.1)
        .padding(.horizontal, 8).padding(.vertical, 4)
        .background(colors.0, in: RoundedRectangle(cornerRadius: 4))
        .accessibilityElement(children: .combine)
    }

    private var colors: (Color, Color) {
        switch family {
        case .warning: (.gaujaPendingBackground(scheme), .gaujaPendingForeground(scheme))
        case .processing: (.gaujaProcessingBackground(scheme), .gaujaProcessingForeground(scheme))
        case .available: (.gaujaAvailableBackground(scheme), .gaujaAvailableForeground(scheme))
        case .danger: (.gaujaFailedBackground(scheme), .gaujaFailedForeground(scheme))
        case .neutral: (.gaujaUnknownBackground(scheme), .gaujaUnknownForeground(scheme))
        }
    }
}

#Preview("Unknown status") { GaujaTheme { StatusBadge(status: MediaStatus.unrecognized(999), is4K: true) } }
#Preview("Large text", traits: .fixedLayout(width: 400, height: 200)) {
    GaujaTheme { StatusBadge(status: MediaStatus.partiallyAvailable).environment(\.dynamicTypeSize, .accessibility5) }
}
