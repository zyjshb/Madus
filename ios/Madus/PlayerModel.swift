import AVFoundation
import MediaPlayer
import UIKit
import Combine

@MainActor
final class PlayerModel: ObservableObject {
    @Published private(set) var queue: [Track] = []
    @Published private(set) var index = 0
    @Published private(set) var current: Track?
    @Published private(set) var isPlaying = false
    @Published private(set) var isLoading = false
    @Published private(set) var wantsToPlay = false
    @Published private(set) var position: Double = 0
    @Published private(set) var duration: Double = 0
    @Published var error: String?
    @Published var repeatMode = RepeatMode.all

    let api: BilibiliClient
    private let library: LibraryStore
    private let player = AVPlayer()
    private var loader: AudioResourceLoader?
    private var resolveTask: Task<Void, Never>?
    private var artworkTask: Task<Void, Never>?
    private var itemObservation: NSKeyValueObservation?
    private var playbackObservation: NSKeyValueObservation?
    private var timeToken: Any?
    private var notifications: [NSObjectProtocol] = []
    private var artwork: MPMediaItemArtwork?
    private var resumeAfterInterruption = false
    private var needsReload = false
    private var generation = UUID()

    init(api: BilibiliClient, library: LibraryStore) {
        self.api = api; self.library = library
        playbackObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] _, _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.isPlaying = self.player.timeControlStatus == .playing
                self.isLoading = self.resolveTask != nil || self.player.timeControlStatus == .waitingToPlayAtSpecifiedRate
                self.updateNowPlaying()
            }
        }
        timeToken = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 1, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.position = time.seconds.isFinite ? max(0, time.seconds) : 0
                if let value = self.player.currentItem?.duration.seconds, value.isFinite, value > 0 { self.duration = value }
                self.updateNowPlaying()
            }
        }
        let center = NotificationCenter.default
        notifications.append(center.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor [weak self] in
                guard let self, let item = notification.object as? AVPlayerItem, item === self.player.currentItem else { return }
                self.next(automatic: true)
            }
        })
        notifications.append(center.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor [weak self] in
                guard let self, let item = notification.object as? AVPlayerItem, item === self.player.currentItem else { return }
                self.pause()
                self.needsReload = true
                self.error = "播放连接中断，点播放可重新获取音频。"
            }
        })
        notifications.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] notification in
            let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            let options = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            Task { @MainActor [weak self] in
                guard let self else { return }
                if type == AVAudioSession.InterruptionType.began.rawValue {
                    self.resumeAfterInterruption = self.wantsToPlay
                    self.pause()
                } else if self.resumeAfterInterruption && AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume) {
                    self.resume()
                }
            }
        })
        notifications.append(center.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            if reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {
                Task { @MainActor [weak self] in self?.pause() }
            }
        })
        configureRemoteCommands()
    }

    func play(_ track: Track, in tracks: [Track]) {
        queue = tracks.isEmpty ? [track] : tracks
        index = queue.firstIndex(where: { $0.id == track.id }) ?? 0
        loadCurrent()
    }

    func select(_ selected: Int) {
        guard queue.indices.contains(selected) else { return }
        index = selected
        loadCurrent()
    }

    func enqueue(_ track: Track) {
        guard !queue.contains(where: { $0.id == track.id }) else { return }
        queue.append(track)
        updateNowPlaying()
    }

    func shuffleQueue() {
        guard let current, queue.count > 1 else { return }
        queue = [current] + queue.enumerated().filter { $0.offset != index }.map(\.element).shuffled()
        index = 0
        updateNowPlaying()
    }

    func next(automatic: Bool = false) {
        guard let nextIndex = QueuePolicy.next(count: queue.count, index: index, mode: repeatMode, automatic: automatic) else {
            if automatic { pause() }
            return
        }
        index = nextIndex
        loadCurrent()
    }

    func previous() {
        if position > 3 { seek(0); return }
        guard !queue.isEmpty else { return }
        index = index > 0 ? index - 1 : (repeatMode == .all ? queue.count - 1 : 0)
        loadCurrent()
    }

    func toggle() { wantsToPlay ? pause() : resume() }

    func pause() {
        wantsToPlay = false
        player.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func resume() {
        guard current != nil else { return }
        if player.currentItem == nil || player.currentItem?.status == .failed || needsReload {
            loadCurrent(); return
        }
        do {
            try activateSession()
            wantsToPlay = true
            player.play()
        } catch { self.error = error.localizedDescription }
    }

    func seek(_ value: Double) {
        guard value.isFinite, duration > 0 else { return }
        position = min(duration, max(0, value))
        player.seek(to: CMTime(seconds: position, preferredTimescale: 600))
        updateNowPlaying()
    }

    private func activateSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default)
        try session.setActive(true)
    }

    private func loadCurrent() {
        guard queue.indices.contains(index) else { return }
        resolveTask?.cancel()
        artworkTask?.cancel()
        let token = UUID()
        generation = token
        player.pause()
        itemObservation = nil
        player.replaceCurrentItem(with: nil)
        loader?.stop()
        loader = nil
        current = queue[index]
        position = 0; duration = current?.duration ?? 0
        artwork = nil; error = nil; needsReload = false; isLoading = true; isPlaying = false; wantsToPlay = true
        updateNowPlaying()
        let requested = queue[index]
        resolveTask = Task { [weak self] in
            guard let self else { return }
            do {
                let (track, stream) = try await api.resolve(requested)
                try Task.checkCancellation()
                guard generation == token else { return }
                if wantsToPlay { try activateSession() }
                current = track
                queue[index] = track
                duration = track.duration
                let resourceLoader = AudioResourceLoader(source: stream)
                loader = resourceLoader
                let item = AVPlayerItem(asset: resourceLoader.makeAsset())
                itemObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
                    Task { @MainActor [weak self] in
                        guard let self, self.player.currentItem === item else { return }
                        if item.status == .failed {
                            self.pause()
                            self.needsReload = true
                            self.isLoading = false
                            self.error = item.error?.localizedDescription ?? "音频加载失败，请重试。"
                        }
                    }
                }
                player.replaceCurrentItem(with: item)
                if wantsToPlay { player.play() }
                library.remember(track)
                loadArtwork(track, token: token)
            } catch {
                guard generation == token, !Task.isCancelled else { return }
                self.error = error.localizedDescription
                needsReload = true
                wantsToPlay = false
            }
            guard generation == token else { return }
            resolveTask = nil
            isLoading = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
            updateNowPlaying()
        }
    }

    private func loadArtwork(_ track: Track, token: UUID) {
        guard let url = track.cover else { return }
        artworkTask = Task { [weak self] in
            var request = URLRequest(url: url)
            request.setValue("https://www.bilibili.com/", forHTTPHeaderField: "Referer")
            guard let (data, _) = try? await URLSession.shared.data(for: request), !Task.isCancelled,
                  let image = UIImage(data: data), let self, self.generation == token else { return }
            self.artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            self.updateNowPlaying()
        }
    }

    private func updateNowPlaying() {
        guard let current else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: current.title, MPMediaItemPropertyArtist: current.artist,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue
        ]
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        let commands = MPRemoteCommandCenter.shared()
        commands.nextTrackCommand.isEnabled = queue.count > 1
        commands.previousTrackCommand.isEnabled = !queue.isEmpty
    }

    private func configureRemoteCommands() {
        let commands = MPRemoteCommandCenter.shared()
        commands.playCommand.addTarget { [weak self] _ in Task { @MainActor in self?.resume() }; return .success }
        commands.pauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.pause() }; return .success }
        commands.togglePlayPauseCommand.addTarget { [weak self] _ in Task { @MainActor in self?.toggle() }; return .success }
        commands.nextTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.next() }; return .success }
        commands.previousTrackCommand.addTarget { [weak self] _ in Task { @MainActor in self?.previous() }; return .success }
        commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(event.positionTime) }
            return .success
        }
    }
}
