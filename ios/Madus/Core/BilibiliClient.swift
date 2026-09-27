import Foundation

actor BilibiliClient {
    static let userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private let session: URLSession
    private var userCookies: [String: String] = [:]
    private var guestCookies: [String: String] = [:]
    private var guestAttempt = Date.distantPast
    private var keys: (image: String, sub: String, date: Date)?

    init() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 40
        config.httpShouldSetCookies = false
        session = URLSession(configuration: config)
    }

    func setCookie(_ value: String) {
        userCookies = [:]
        for pair in value.split(separator: ";") {
            let parts = pair.split(separator: "=", maxSplits: 1)
            if parts.count == 2 {
                userCookies[String(parts[0]).trimmingCharacters(in: .whitespaces)] = String(parts[1])
            }
        }
        keys = nil
    }

    private func cookieHeader() -> String {
        guestCookies.merging(userCookies, uniquingKeysWith: { _, user in user })
            .sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }.joined(separator: "; ")
    }

    private func prepareGuest() async {
        guard guestCookies.isEmpty, Date().timeIntervalSince(guestAttempt) > 60 else { return }
        guestAttempt = Date()
        var request = URLRequest(url: URL(string: "https://www.bilibili.com/")!)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        if let (_, response) = try? await session.data(for: request), let response = response as? HTTPURLResponse {
            let headers = response.allHeaderFields.reduce(into: [String: String]()) { result, item in
                result[String(describing: item.key)] = String(describing: item.value)
            }
            for cookie in HTTPCookie.cookies(withResponseHeaderFields: headers, for: request.url!) {
                guestCookies[cookie.name] = cookie.value
            }
        }
    }

    private func json(_ path: String, params: [String: String] = [:], query: String? = nil) async throws -> [String: Any] {
        try Task.checkCancellation()
        await prepareGuest()
        var components = URLComponents(string: "https://api.bilibili.com" + path)!
        if let query { components.percentEncodedQuery = query }
        else { components.queryItems = params.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) } }
        var request = URLRequest(url: components.url!)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("https://www.bilibili.com/", forHTTPHeaderField: "Referer")
        request.setValue(cookieHeader(), forHTTPHeaderField: "Cookie")
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse, response.statusCode == 200 else {
            throw MadusError.message("B 站请求失败，请稍后重试。")
        }
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw MadusError.message("B 站返回了无法识别的数据。")
        }
        let code = (root["code"] as? NSNumber)?.intValue ?? -1
        // Anonymous nav still returns WBI keys with code -101.
        let guestNav = path == "/x/web-interface/nav" && code == -101
        guard code == 0 || guestNav else {
            let message = BilibiliParser.text(root["message"])
            throw MadusError.message("B 站：\(message.isEmpty ? "请求失败" : message)（\(code)）")
        }
        return root["data"] as? [String: Any] ?? [:]
    }

    func profile() async throws -> BiliProfile {
        let data = try await json("/x/web-interface/nav")
        guard data["isLogin"] as? Bool == true else { throw MadusError.message("请先登录 B 站。") }
        return BiliProfile(name: BilibiliParser.text(data["uname"]), mid: BilibiliParser.text(data["mid"]))
    }

    func search(_ input: String, page: Int = 1) async throws -> SearchPage {
        let keyword = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty else { return SearchPage(tracks: [], hasMore: false) }
        if let bv = BilibiliParser.bvid(in: keyword) {
            return SearchPage(tracks: try await parts(bvid: bv), hasMore: false)
        }
        let parameters = ["search_type": "video", "keyword": keyword, "page": String(page), "page_size": "30", "order": "totalrank"]
        var data: [String: Any]
        do {
            data = try await json("/x/web-interface/search/type", params: parameters)
        } catch {
            try Task.checkCancellation()
            // Same WBI strategy as Android, with the percent-encoded query included in the digest.
            if keys == nil || Date().timeIntervalSince(keys!.date) > 3600 {
                let nav = try await json("/x/web-interface/nav")
                let images = nav["wbi_img"] as? [String: Any] ?? [:]
                func key(_ name: String) -> String {
                    BilibiliParser.httpsURL(images[name])?.deletingPathExtension().lastPathComponent ?? ""
                }
                guard !key("img_url").isEmpty, !key("sub_url").isEmpty else { throw error }
                keys = (key("img_url"), key("sub_url"), Date())
            }
            let signed = BilibiliParser.signedQuery(parameters, image: keys!.image, sub: keys!.sub, timestamp: Int(Date().timeIntervalSince1970))
            data = try await json("/x/web-interface/wbi/search/type", query: signed)
        }
        let tracks = BilibiliParser.searchTracks(data["result"] as? [[String: Any]] ?? [])
        let pages = (data["numPages"] as? NSNumber)?.intValue ?? page
        return SearchPage(tracks: tracks, hasMore: !tracks.isEmpty && page < pages)
    }

    func parts(bvid: String) async throws -> [Track] {
        BilibiliParser.viewTracks(try await json("/x/web-interface/view", params: ["bvid": bvid]))
    }

    func resolve(_ track: Track) async throws -> (Track, StreamSource) {
        var resolved = track
        if resolved.cid.isEmpty {
            guard let first = try await parts(bvid: track.bvid).first else { throw MadusError.message("这个视频暂时不可用。") }
            resolved = first
        }
        var lastError: Error = MadusError.message("没有找到 iPhone 支持的音频。可尝试登录 B 站后重试。")
        let attempts = [["fnval": "16"], ["fnval": "1", "platform": "html5"]]
        for attempt in attempts {
            do {
                var params = ["bvid": resolved.bvid, "cid": resolved.cid, "qn": "64", "fourk": "0"]
                params.merge(attempt, uniquingKeysWith: { _, new in new })
                let data = try await json("/x/player/playurl", params: params)
                if let stream = BilibiliParser.stream(data, bvid: resolved.bvid) { return (resolved, stream) }
            } catch { lastError = error }
            try Task.checkCancellation()
        }
        throw lastError
    }

    func folders(mid: String) async throws -> [FavoriteFolder] {
        let data = try await json("/x/v3/fav/folder/created/list-all", params: ["up_mid": mid])
        return (data["list"] as? [[String: Any]] ?? []).map {
            FavoriteFolder(id: BilibiliParser.text($0["id"]), title: BilibiliParser.text($0["title"]), count: ($0["media_count"] as? Int) ?? 0)
        }
    }

    func folderTracks(id: String, page: Int) async throws -> SearchPage {
        let data = try await json("/x/v3/fav/resource/list", params: ["media_id": id, "pn": String(page), "ps": "20", "platform": "web"])
        let tracks: [Track] = (data["medias"] as? [[String: Any]] ?? []).compactMap { row in
            guard let bv = BilibiliParser.bvid(in: BilibiliParser.text(row["bvid"])),
                  (row["attr"] as? Int ?? 0) & 1 == 0 else { return nil }
            let upper = row["upper"] as? [String: Any] ?? [:]
            return Track(bvid: bv, title: BilibiliParser.text(row["title"]), artist: BilibiliParser.text(upper["name"]),
                         cover: BilibiliParser.httpsURL(row["cover"]), duration: BilibiliParser.duration(row["duration"]))
        }
        return SearchPage(tracks: tracks, hasMore: (data["has_more"] as? Bool ?? false))
    }
}
