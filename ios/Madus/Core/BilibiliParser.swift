import Foundation
import CryptoKit

enum BilibiliParser {
    static func text(_ value: Any?) -> String {
        let raw = value as? String ?? (value as? NSNumber)?.stringValue ?? ""
        return raw.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
    }

    static func httpsURL(_ value: Any?) -> URL? {
        guard var raw = value as? String, !raw.isEmpty else { return nil }
        if raw.hasPrefix("//") { raw = "https:" + raw }
        guard var components = URLComponents(string: raw),
              ["https", "http"].contains(components.scheme ?? ""), components.host != nil else { return nil }
        components.scheme = "https"
        return components.url
    }

    static func bvid(in text: String) -> String? {
        guard let range = text.range(of: "(?<![A-Za-z0-9])BV[A-Za-z0-9]{10}(?![A-Za-z0-9])", options: .regularExpression) else { return nil }
        return String(text[range])
    }

    static func duration(_ value: Any?) -> Double {
        if let number = value as? NSNumber { return max(0, number.doubleValue) }
        let parts = text(value).split(separator: ":")
        guard !parts.isEmpty, parts.count <= 3 else { return 0 }
        var seconds: Double = 0
        for part in parts {
            guard let number = Double(part), number >= 0, number.isFinite else { return 0 }
            seconds = seconds * 60 + number
        }
        return seconds
    }

    static func searchTracks(_ rows: [[String: Any]]) -> [Track] {
        var seen = Set<String>()
        return rows.compactMap { row in
            guard let bv = bvid(in: text(row["bvid"])), seen.insert(bv).inserted else { return nil }
            return Track(bvid: bv, title: text(row["title"]), artist: text(row["author"]),
                         cover: httpsURL(row["pic"]), duration: duration(row["duration"]))
        }
    }

    static func viewTracks(_ data: [String: Any]) -> [Track] {
        guard let bv = bvid(in: text(data["bvid"])) else { return [] }
        let owner = data["owner"] as? [String: Any] ?? [:]
        let pages = data["pages"] as? [[String: Any]] ?? []
        let rows = pages.isEmpty ? [data] : pages
        return rows.compactMap { page in
            let cid = text(page["cid"])
            guard !cid.isEmpty else { return nil }
            let title = rows.count > 1 ? text(page["part"]) : text(data["title"])
            return Track(bvid: bv, cid: cid, title: title, artist: text(owner["name"]),
                         cover: httpsURL(data["pic"]), duration: duration(page["duration"]))
        }
    }

    static func stream(_ data: [String: Any], bvid: String) -> StreamSource? {
        let referer = URL(string: "https://www.bilibili.com/video/\(bvid)")!
        let dash = data["dash"] as? [String: Any] ?? [:]
        // AAC/M4A works on supported iPhones. Do not select FLAC/Dolby by bitrate alone.
        let audio = (dash["audio"] as? [[String: Any]] ?? []).filter {
            text($0["codecs"]).lowercased().hasPrefix("mp4a")
        }.sorted { duration($0["bandwidth"]) > duration($1["bandwidth"]) }
        for row in audio {
            let candidates = [text(row["baseUrl"]), text(row["base_url"])]
                + (row["backupUrl"] as? [String] ?? row["backup_url"] as? [String] ?? [])
            for candidate in candidates {
                if let url = httpsURL(candidate) {
                    return StreamSource(url: url, contentType: "audio/mp4", referer: referer)
                }
            }
        }
        // Never return only the first fragment of a segmented video.
        let durl = data["durl"] as? [[String: Any]] ?? []
        if durl.count == 1, let url = httpsURL(durl[0]["url"]),
           url.pathExtension.lowercased() == "mp4" {
            return StreamSource(url: url, contentType: "video/mp4", referer: referer)
        }
        return nil
    }

    static func signedQuery(_ values: [String: String], image: String, sub: String, timestamp: Int) -> String {
        let table = [46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,
                     37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52]
        let key = Array(image + sub)
        let mixin = String(table.filter { $0 < key.count }.map { key[$0] }.prefix(32))
        var parameters = values
        parameters["wts"] = String(timestamp)
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~")
        func encode(_ value: String) -> String { value.addingPercentEncoding(withAllowedCharacters: allowed)! }
        let query = parameters.keys.sorted().map { key in
            let value = parameters[key]!.filter { !"!'()*".contains($0) }
            return "\(encode(key))=\(encode(value))"
        }.joined(separator: "&")
        let digest = Insecure.MD5.hash(data: Data((query + mixin).utf8)).map { String(format: "%02x", $0) }.joined()
        return query + "&w_rid=" + digest
    }
}
