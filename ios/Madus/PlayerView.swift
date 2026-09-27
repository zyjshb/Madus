import SwiftUI
import AVKit

struct PlayerView: View {
    @EnvironmentObject private var player: PlayerModel
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss
    @State private var seek: Double = 0
    @State private var editing = false
    @State private var showQueue = false

    var body: some View {
        NavigationStack {
            ScrollView {
                if let track = player.current {
                    VStack(spacing: 28) {
                        CoverView(url: track.cover)
                            .aspectRatio(1, contentMode: .fit)
                            .clipShape(RoundedRectangle(cornerRadius: 24))
                            .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(.primary.opacity(0.08)))
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(track.title).font(.title2.bold())
                                Text(track.artist).font(.subheadline).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button { library.toggle(track) } label: {
                                Image(systemName: library.isFavorite(track) ? "heart.fill" : "heart").font(.title2).frame(width: 44, height: 44)
                            }.accessibilityLabel(library.isFavorite(track) ? "取消喜欢" : "喜欢")
                        }
                        VStack(spacing: 5) {
                            Slider(value: $seek, in: 0...max(1, player.duration)) { editing in
                                self.editing = editing
                                if !editing { player.seek(seek) }
                            }.disabled(player.duration <= 0).accessibilityLabel("播放进度")
                            HStack {
                                Text(formatTime(editing ? seek : player.position))
                                Spacer()
                                if player.isLoading { Text("缓冲中…") }
                                Text(formatTime(player.duration))
                            }.font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        }
                        HStack(spacing: 38) {
                            Button { player.previous() } label: { Image(systemName: "backward.end.fill").font(.title).frame(width: 48, height: 56) }
                                .accessibilityLabel("上一首")
                            Button { player.toggle() } label: {
                                Image(systemName: player.wantsToPlay ? "pause.fill" : "play.fill")
                                    .font(.system(size: 32)).frame(width: 80, height: 80)
                                    .background(.primary.opacity(0.08), in: Circle())
                            }.accessibilityLabel(player.wantsToPlay ? "暂停" : "播放")
                            Button { player.next() } label: { Image(systemName: "forward.end.fill").font(.title).frame(width: 48, height: 56) }
                                .accessibilityLabel("下一首")
                        }
                        HStack {
                            Menu {
                                Picker("播放方式", selection: $player.repeatMode) {
                                    ForEach(RepeatMode.allCases, id: \.self) { mode in Text(mode.label).tag(mode) }
                                }
                                Button("打乱当前队列") { player.shuffleQueue() }
                            } label: { Label(player.repeatMode.label, systemImage: player.repeatMode.symbol).font(.caption) }
                            Spacer()
                            AirPlayButton().frame(width: 44, height: 44).accessibilityLabel("选择音频设备")
                            Spacer()
                            Button { showQueue = true } label: { Label("\(player.queue.count) 首", systemImage: "list.bullet").font(.caption) }
                        }
                        Link("在 B 站查看原视频", destination: track.webpage).font(.footnote).foregroundStyle(.secondary)
                    }.padding(28).frame(maxWidth: 460)
                        .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("正在播放").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { Image(systemName: "chevron.down") }.accessibilityLabel("收起播放器") } }
        }
        .onAppear { seek = player.position }
        .onChange(of: player.position) { if !editing { seek = $0 } }
        .alert("播放提示", isPresented: Binding(get: { player.error != nil }, set: { if !$0 { player.error = nil } })) {
            Button("重试") { player.resume() }
            Button("取消", role: .cancel) { player.error = nil }
        } message: { Text(player.error ?? "") }
        .sheet(isPresented: $showQueue) {
            NavigationStack {
                List(Array(player.queue.enumerated()), id: \.offset) { index, track in
                    Button { player.select(index); showQueue = false } label: {
                        HStack {
                            Image(systemName: index == player.index ? "speaker.wave.2.fill" : "music.note").frame(width: 24)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(track.title).lineLimit(2)
                                Text(track.artist).font(.caption).foregroundStyle(.secondary)
                            }
                        }.padding(.vertical, 4)
                    }
                }.navigationTitle("播放队列")
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { showQueue = false } } }
            }
        }
    }
}

private struct AirPlayButton: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let view = AVRoutePickerView()
        view.tintColor = .label
        return view
    }
    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}
