// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Model

public struct ServerVersion: Sendable, Comparable {
    public let major: Int
    public let minor: Int
    public let patch: Int
    public let suffix: String

    public init?(_ text: String?) {
        guard let text,
            let match = text.wholeMatch(
                of:
                    // One anchored semver grammar.
                    // swiftlint:disable:next line_length
                    /^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)((?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?)$/
            ),
            let major = Int(match.1), let minor = Int(match.2), let patch = Int(match.3)
        else { return nil }
        let suffix = String(match.4)
        if suffix.hasPrefix("-") {
            let identifiers = suffix.dropFirst().split(separator: "+")[0].split(separator: ".")
            guard !identifiers.contains(where: { $0.count > 1 && $0.hasPrefix("0") && $0.allSatisfy(\.isNumber) })
            else { return nil }
        }
        self.major = major
        self.minor = minor
        self.patch = patch
        self.suffix = suffix
    }

    public static func < (lhs: Self, rhs: Self) -> Bool {
        let leftCore = (lhs.major, lhs.minor, lhs.patch)
        let rightCore = (rhs.major, rhs.minor, rhs.patch)
        if leftCore != rightCore { return leftCore < rightCore }
        guard let left = lhs.prerelease else { return false }
        guard let right = rhs.prerelease else { return true }
        for (leftPart, rightPart) in zip(left, right) where leftPart != rightPart {
            let numericA = leftPart.allSatisfy(\.isNumber)
            let numericB = rightPart.allSatisfy(\.isNumber)
            if numericA && numericB {
                return leftPart.count == rightPart.count ? leftPart < rightPart : leftPart.count < rightPart.count
            }
            if numericA != numericB { return numericA }
            return leftPart < rightPart
        }
        return left.count < right.count
    }

    private var prerelease: [Substring]? {
        suffix.hasPrefix("-") ? suffix.dropFirst().split(separator: "+")[0].split(separator: ".") : nil
    }

    public static func == (lhs: Self, rhs: Self) -> Bool {
        (lhs.major, lhs.minor, lhs.patch) == (rhs.major, rhs.minor, rhs.patch) && lhs.prerelease == rhs.prerelease
    }

    public var compatibility: Compatibility {
        let numbers = (major, minor, patch)
        if numbers < (3, 4, 1) { return .tooOld }
        if numbers > (3, 4, 1) || !suffix.isEmpty { return .untested }
        return .tested
    }
}
