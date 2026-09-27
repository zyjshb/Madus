import Foundation
import Combine

@MainActor
final class LibraryStore: ObservableObject {
    @Published private(set) var favorites: [Track] = []
    @Published private(set) var recent: [Track] = []

    init() {
        favorites = read("madus.favorites")
        recent = read("madus.recent")
    }

    func isFavorite(_ track: Track) -> Bool { favorites.contains { matches($0, track) } }

    func toggle(_ track: Track) {
        if isFavorite(track) { favorites.removeAll { matches($0, track) } }
        else { favorites.insert(track, at: 0) }
        save(favorites, "madus.favorites")
    }

    func remember(_ track: Track) {
        recent.removeAll { matches($0, track) }
        recent.insert(track, at: 0)
        recent = Array(recent.prefix(100))
        save(recent, "madus.recent")
    }

    private func matches(_ lhs: Track, _ rhs: Track) -> Bool {
        lhs.bvid == rhs.bvid && (lhs.cid == rhs.cid || lhs.cid.isEmpty || rhs.cid.isEmpty)
    }

    private func read(_ key: String) -> [Track] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([Track].self, from: data)) ?? []
    }

    private func save(_ tracks: [Track], _ key: String) {
        if let data = try? JSONEncoder().encode(tracks) { UserDefaults.standard.set(data, forKey: key) }
    }
}
