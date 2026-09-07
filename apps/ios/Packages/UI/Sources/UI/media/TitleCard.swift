// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import DesignSystem
import Model
import SwiftUI

public struct TitleCard: View {
    @Environment(\.colorScheme) private var scheme
    private let title: TitleSummary
    private let poster: Image?
    private let onOpen: (MediaType, TMDBID) -> Void

    public init(title: TitleSummary, poster: Image? = nil, onOpen: @escaping (MediaType, TMDBID) -> Void) {
        self.title = title
        self.poster = poster
        self.onOpen = onOpen
    }

    public var body: some View {
        Button {
            onOpen(title.mediaType, title.id)
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                Color.gaujaSurfaceVariant(scheme).aspectRatio(2 / 3, contentMode: .fit)
                    .overlay {
                        if let poster {
                            poster.resizable().scaledToFill().accessibilityHidden(true)
                        } else {
                            Text("No artwork").padding(12)
                        }
                    }.clipped()
                VStack(alignment: .leading, spacing: 4) {
                    Text(title.title.flatMap { $0.isEmpty ? nil : $0 } ?? String(localized: "Untitled"))
                        .font(.gaujaTitleMedium)
                    Text(mediaLabel).font(.gaujaBodySmall)
                    if let year = title.year { Text(year, format: .number.grouping(.never)).font(.gaujaBodySmall) }
                    if let rating = title.rating, rating.isFinite, (0...10).contains(rating) {
                        Text("Rating: \(rating, format: .number.precision(.fractionLength(1))) out of 10").font(
                            .gaujaBodySmall)
                    }
                    StatusBadge(status: title.status)
                    if let status = title.status4K { StatusBadge(status: status, is4K: true) }
                }.padding(12).frame(maxWidth: .infinity, alignment: .leading)
            }
            .foregroundStyle(Color.gaujaOnSurface(scheme))
            .background(Color.gaujaSurfaceContainer(scheme))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain).disabled(!canOpen)
        .accessibilityElement(children: .combine)
    }

    private var canOpen: Bool {
        switch title.mediaType {
        case .movie, .tv: true
        case .unrecognized: false
        }
    }
    private var mediaLabel: LocalizedStringKey {
        switch title.mediaType {
        case .movie: "Movie"
        case .tv: "TV"
        case .unrecognized: "Unknown media type"
        }
    }
}

#Preview("Title card") {
    if let id = TMDBID(rawValue: 42) {
        GaujaTheme {
            TitleCard(
                title: TitleSummary(
                    id: id, mediaType: .movie, title: "A journey along the river", year: 2026,
                    posterPath: nil, rating: 8.2, status: .available)
            ) { _, _ in }.frame(width: 220)
        }
    }
}
#Preview("Largest text", traits: .fixedLayout(width: 500, height: 1200)) {
    if let id = TMDBID(rawValue: 42) {
        GaujaTheme(scheme: .light) {
            TitleCard(
                title: TitleSummary(
                    id: id, mediaType: .tv, title: nil, year: nil,
                    posterPath: nil, rating: nil, status: .unrecognized(999))
            ) { _, _ in }.environment(\.dynamicTypeSize, .accessibility5)
        }
    }
}
