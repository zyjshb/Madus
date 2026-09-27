import Foundation
import Combine

@MainActor
final class AppModel: ObservableObject {
    let api = BilibiliClient()
    let library: LibraryStore
    let player: PlayerModel
    @Published var query = ""
    @Published var results: [Track] = []
    @Published var searching = false
    @Published var hasMore = false
    @Published var searched = false
    @Published var error: String?
    @Published var profile: BiliProfile?
    @Published var folders: [FavoriteFolder] = []
    @Published var showLogin = false
    @Published var accountLoading = false
    private var searchTask: Task<Void, Never>?
    private var searchGeneration = UUID()
    private var lastQuery = ""
    private var page = 0
    private var started = false

    init() {
        let library = LibraryStore()
        self.library = library
        player = PlayerModel(api: api, library: library)
    }

    func bootstrap() async {
        guard !started else { return }
        started = true
        let cookie = AccountStore.read()
        await api.setCookie(cookie)
        if !cookie.isEmpty { await refreshAccount() }
    }

    func search(more: Bool = false) {
        let keyword = (more ? lastQuery : query).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyword.isEmpty, !more || (hasMore && !searching) else { return }
        searchTask?.cancel()
        let generation = UUID()
        searchGeneration = generation
        if !more { results = []; page = 0; hasMore = false; lastQuery = keyword }
        searching = true
        searched = true
        error = nil
        let next = page + 1
        searchTask = Task { [weak self] in
            guard let self else { return }
            do {
                let response = try await api.search(keyword, page: next)
                try Task.checkCancellation()
                guard searchGeneration == generation else { return }
                var known = Set(results.map(\.id))
                results += response.tracks.filter { known.insert($0.id).inserted }
                hasMore = response.hasMore
                page = next
            } catch {
                guard !Task.isCancelled, searchGeneration == generation else { return }
                self.error = error.localizedDescription
            }
            if searchGeneration == generation { searching = false }
        }
    }

    func login(cookie: String) async {
        guard !accountLoading else { return }
        accountLoading = true
        defer { accountLoading = false }
        let previous = AccountStore.read()
        await api.setCookie(cookie)
        do {
            let user = try await api.profile()
            try AccountStore.save(cookie)
            profile = user
            showLogin = false
        } catch {
            await api.setCookie(previous)
            self.error = error.localizedDescription
            return
        }
        do { if let profile { folders = try await api.folders(mid: profile.mid) } }
        catch { self.error = error.localizedDescription }
    }

    func refreshAccount() async {
        guard !accountLoading else { return }
        accountLoading = true
        defer { accountLoading = false }
        do {
            let user = try await api.profile()
            profile = user
            folders = try await api.folders(mid: user.mid)
        } catch { self.error = error.localizedDescription }
    }

    func logout() async {
        do {
            try AccountStore.clear()
            await api.setCookie("")
            profile = nil; folders = []
        } catch { self.error = error.localizedDescription }
    }
}
