import Foundation

struct Track: Identifiable, Codable, Equatable, Sendable {
    let bvid: String
    var cid: String = ""
    var title: String
    var artist: String
    var cover: URL?
    var duration: Double = 0
    var id: String { cid.isEmpty ? bvid : "\(bvid)_\(cid)" }
    var webpage: URL { URL(string: "https://www.bilibili.com/video/\(bvid)")! }
}

struct SearchPage: Sendable {
    let tracks: [Track]
    let hasMore: Bool
}

struct BiliProfile: Sendable {
    let name: String
    let mid: String
}

struct FavoriteFolder: Identifiable, Sendable {
    let id: String
    let title: String
    let count: Int
}

struct StreamSource: Sendable {
    let url: URL
    let contentType: String
    let referer: URL
}

enum MadusError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let message): return message }
    }
}

enum RepeatMode: String, CaseIterable, Codable {
    case all, one, off
    var label: String {
        switch self { case .all: return "列表循环"; case .one: return "单曲循环"; case .off: return "顺序播放" }
    }
    var symbol: String { self == .one ? "repeat.1" : "repeat" }
}

enum QueuePolicy {
    static func next(count: Int, index: Int, mode: RepeatMode, automatic: Bool) -> Int? {
        guard count > 0, (0..<count).contains(index) else { return nil }
        if automatic && mode == .one { return index }
        if index + 1 < count { return index + 1 }
        return mode == .all ? 0 : nil
    }
}

// Shared with the AVAssetResourceLoader. Offsets are absolute file offsets.
struct HTTPByteRange: Equatable {
    let start: Int64
    let end: Int64
    let total: Int64

    init?(_ header: String) {
        let pieces = header.split(separator: " ")
        guard pieces.count == 2, pieces[0].lowercased() == "bytes" else { return nil }
        let length = pieces[1].split(separator: "/")
        guard length.count == 2, let total = Int64(length[1]), total > 0 else { return nil }
        let bounds = length[0].split(separator: "-")
        guard bounds.count == 2, let start = Int64(bounds[0]), let end = Int64(bounds[1]),
              start >= 0, end >= start, end < total else { return nil }
        self.start = start; self.end = end; self.total = total
    }
}
