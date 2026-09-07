// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import CoreGraphics
import CryptoKit
import Foundation
import ImageIO
import Model

public actor ProfileImages {
    private let directory: URL
    private let clock: WallClock
    private let memory = NSCache<NSString, CGImage>()
    private var costs: [String: Int] = [:]
    private var order: [String] = []
    private struct ImageSession {
        let profile: ServerProfile
        let proxied: Bool
        let session: URLSession
    }
    private var sessions: [ProfileID: ImageSession] = [:]
    private let memoryLimit = 64 * 1024 * 1024
    private let diskLimit = 256 * 1024 * 1024

    public init(directory: URL, clock: WallClock) {
        self.directory = directory
        self.clock = clock
        memory.totalCostLimit = memoryLimit
    }

    // ProfileTransport's gate must surround this operation and profile deletion.
    public func load(_ profile: ServerProfile, source: String, size: PosterSize, offline: Bool) async throws -> CGImage
    {
        guard
            let url = imageURL(
                source: source, address: profile.address,
                cacheImages: profile.publicSettings?.value.cacheImages == true, size: size)
        else { throw AppError.validation }
        let digest = SHA256.hash(data: Data(url.absoluteString.utf8)).map { String(format: "%02x", $0) }.joined()
        let key = profile.id.rawValue.uuidString + ":" + digest
        if let image = memory.object(forKey: key as NSString) {
            touch(key)
            return image
        }
        let file = directory.appendingPathComponent(profile.id.rawValue.uuidString).appendingPathComponent(digest)
        if let bytes = try? Data(contentsOf: file), let image = decode(bytes, size) {
            cache(image, key: key)
            if !offline {
                try FileManager.default.setAttributes([.modificationDate: clock.now()], ofItemAtPath: file.path)
            }
            return image
        }
        guard !offline else { throw AppError.offline }
        let session = session(profile, proxied: url.host == profile.address.url.host)
        var request = URLRequest(url: url)
        request.setValue("image/*", forHTTPHeaderField: "Accept")
        let (stream, response) = try await session.bytes(for: request)
        guard let response = response as? HTTPURLResponse, response.statusCode == 200,
            response.mimeType?.hasPrefix("image/") == true
        else { throw AppError.network }
        var bytes = Data()
        for try await byte in stream {
            guard bytes.count < 8 * 1024 * 1024 else { throw AppError.validation }
            bytes.append(byte)
        }
        guard let image = decode(bytes, size) else { throw AppError.validation }
        try Task.checkCancellation()
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try bytes.write(to: file, options: .atomic)
        try FileManager.default.setAttributes([.modificationDate: clock.now()], ofItemAtPath: file.path)
        try pruneDisk()
        cache(image, key: key)
        return image
    }

    public func close() {
        for stored in sessions.values { stored.session.invalidateAndCancel() }
        sessions.removeAll()
        memory.removeAllObjects()
        costs.removeAll()
        order.removeAll()
    }

    public func clear(_ id: ProfileID) throws {
        let prefix = id.rawValue.uuidString + ":"
        for key in order.filter({ $0.hasPrefix(prefix) }) {
            memory.removeObject(forKey: key as NSString)
            costs[key] = nil
        }
        order.removeAll { $0.hasPrefix(prefix) }
        let folder = directory.appendingPathComponent(id.rawValue.uuidString)
        if FileManager.default.fileExists(atPath: folder.path) { try FileManager.default.removeItem(at: folder) }
        sessions.removeValue(forKey: id)?.session.invalidateAndCancel()
    }

    private func session(_ profile: ServerProfile, proxied: Bool) -> URLSession {
        if let stored = sessions[profile.id], stored.profile == profile, stored.proxied == proxied {
            return stored.session
        }
        sessions.removeValue(forKey: profile.id)?.session.invalidateAndCancel()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 20
        let session = URLSession(
            configuration: configuration,
            delegate: ProfileSessionDelegate(mode: proxied ? profile.tlsMode : .system), delegateQueue: nil)
        sessions[profile.id] = ImageSession(profile: profile, proxied: proxied, session: session)
        return session
    }

    private func decode(_ data: Data, _ size: PosterSize) -> CGImage? {
        let pixels: Int =
            switch size {
            case .small: 278
            case .medium: 513
            case .large: 750
            }
        guard
            let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary)
        else { return nil }
        return CGImageSourceCreateThumbnailAtIndex(
            source, 0,
            [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: pixels,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true,
            ] as CFDictionary)
    }

    private func touch(_ key: String) {
        order.removeAll { $0 == key }
        order.append(key)
    }
    private func cache(_ image: CGImage, key: String) {
        let cost = image.bytesPerRow * image.height
        guard cost <= memoryLimit else { return }
        costs[key] = cost
        touch(key)
        while costs.values.reduce(0, +) > memoryLimit, let oldest = order.first {
            order.removeFirst()
            costs[oldest] = nil
            memory.removeObject(forKey: oldest as NSString)
        }
        memory.setObject(image, forKey: key as NSString, cost: cost)
    }

    private func pruneDisk() throws {
        let keys: Set<URLResourceKey> = [.isRegularFileKey, .fileSizeKey, .contentModificationDateKey]
        guard let enumerator = FileManager.default.enumerator(at: directory, includingPropertiesForKeys: Array(keys))
        else { return }
        var files: [(URL, URLResourceValues)] = []
        for case let url as URL in enumerator {
            let values = try url.resourceValues(forKeys: keys)
            if values.isRegularFile == true { files.append((url, values)) }
        }
        var total = files.reduce(0) { $0 + ($1.1.fileSize ?? 0) }
        for (url, values) in files.sorted(by: {
            ($0.1.contentModificationDate ?? .distantPast) < ($1.1.contentModificationDate ?? .distantPast)
        }) where total > diskLimit {
            try FileManager.default.removeItem(at: url)
            total -= values.fileSize ?? 0
        }
    }
}
