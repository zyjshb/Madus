import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var player: PlayerModel
    @EnvironmentObject private var library: LibraryStore
    @State private var showPlayer = false

    var body: some View {
        TabView {
            NavigationStack { SearchView() }
                .tabItem { Label("发现", systemImage: "magnifyingglass") }
            NavigationStack {
                List {
                    Section {
                        NavigationLink { TrackList(title: "喜欢的音乐", tracks: library.favorites) } label: {
                            Label("喜欢的音乐 · \(library.favorites.count)", systemImage: "heart")
                        }
                        NavigationLink { TrackList(title: "最近播放", tracks: library.recent) } label: {
                            Label("最近播放", systemImage: "clock")
                        }
                    }
                    if let profile = model.profile {
                        Section("\(profile.name) 的 B 站收藏夹") {
                            ForEach(model.folders) { folder in
                                NavigationLink { FolderView(folder: folder) } label: {
                                    Label("\(folder.title) · \(folder.count)", systemImage: "folder")
                                }
                            }
                            if model.folders.isEmpty { Text("暂无收藏夹，可下拉刷新。").foregroundStyle(.secondary) }
                        }
                    } else {
                        Section { Button("登录 B 站，查看收藏夹") { model.showLogin = true } }
                    }
                }
                .navigationTitle("音乐库")
                .refreshable { await model.refreshAccount() }
            }
            .tabItem { Label("音乐库", systemImage: "square.stack") }
            NavigationStack { SettingsView() }
                .tabItem { Label("我的", systemImage: "person.crop.circle") }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if let current = player.current {
                HStack(spacing: 12) {
                    Button { showPlayer = true } label: {
                        HStack(spacing: 12) {
                            CoverView(url: current.cover).frame(width: 42, height: 42).clipShape(RoundedRectangle(cornerRadius: 8))
                            VStack(alignment: .leading, spacing: 4) {
                                Text(current.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                                Text(player.isLoading ? "正在载入…" : current.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }.buttonStyle(.plain)
                    Button { player.toggle() } label: {
                        Image(systemName: player.wantsToPlay ? "pause.fill" : "play.fill").frame(width: 44, height: 44)
                    }.accessibilityLabel(player.wantsToPlay ? "暂停" : "播放")
                    Button { player.next() } label: { Image(systemName: "forward.end.fill").frame(width: 40, height: 44) }
                        .accessibilityLabel("下一首")
                }
                .padding(.horizontal).padding(.vertical, 8)
                .background(.regularMaterial)
                .overlay(alignment: .top) { Divider() }
            }
        }
        .sheet(isPresented: $showPlayer) { PlayerView() }
        .sheet(isPresented: $model.showLogin) { BilibiliLoginView() }
        .alert("提示", isPresented: Binding(get: { !model.showLogin && !showPlayer && (model.error != nil || player.error != nil) }, set: { if !$0 { model.error = nil; player.error = nil } })) {
            Button("知道了", role: .cancel) { model.error = nil; player.error = nil }
        } message: { Text(model.error ?? player.error ?? "") }
    }
}

private struct SearchView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        List {
            if !model.searched {
                Section {
                    VStack(alignment: .leading, spacing: 14) {
                        Text("把喜欢的声音，\n随身带走。").font(.system(size: 32, weight: .bold, design: .rounded))
                        Text("搜索歌名、歌手，或粘贴包含 BV 号的 B 站链接。")
                            .font(.subheadline).foregroundStyle(.secondary)
                    }.padding(.vertical, 24)
                }.listRowBackground(Color.clear)
                Section("从这里开始") {
                    ForEach(["纯音乐", "翻唱", "爵士", "游戏原声"], id: \.self) { keyword in
                        Button { model.query = keyword; model.search() } label: { Label(keyword, systemImage: "music.note") }
                    }
                }
            }
            ForEach(model.results) { track in TrackRow(track: track, tracks: model.results) }
            if model.searching {
                HStack { Spacer(); ProgressView("搜索中…"); Spacer() }.padding()
            } else if model.hasMore {
                Button("加载更多") { model.search(more: true) }.frame(maxWidth: .infinity)
            } else if model.searched && model.results.isEmpty {
                Text("没有找到结果。试试其他关键词，或登录 B 站后重试。")
                    .foregroundStyle(.secondary).padding(.vertical)
            }
        }
        .listStyle(.plain)
        .navigationTitle("Madus")
        .searchable(text: $model.query, prompt: "歌名、歌手或 BV 号")
        .onSubmit(of: .search) { model.search() }
        .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Text("听见喜欢").font(.caption).foregroundStyle(.secondary) } }
    }
}

struct TrackRow: View {
    @EnvironmentObject private var player: PlayerModel
    @EnvironmentObject private var library: LibraryStore
    let track: Track
    let tracks: [Track]
    @State private var showParts = false

    var body: some View {
        HStack(spacing: 12) {
            Button { player.play(track, in: tracks) } label: {
                HStack(spacing: 12) {
                    CoverView(url: track.cover).frame(width: 54, height: 54).clipShape(RoundedRectangle(cornerRadius: 10))
                    VStack(alignment: .leading, spacing: 5) {
                        Text(track.title).font(.subheadline.weight(.medium)).lineLimit(2)
                        Text("\(track.artist) · \(formatTime(track.duration))").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                }.contentShape(Rectangle())
            }.buttonStyle(.plain)
            Button { library.toggle(track) } label: {
                Image(systemName: library.isFavorite(track) ? "heart.fill" : "heart").frame(width: 40, height: 44)
            }.buttonStyle(.plain).accessibilityLabel(library.isFavorite(track) ? "取消喜欢" : "喜欢")
        }
        .padding(.vertical, 4)
        .contextMenu {
            Button { player.enqueue(track) } label: { Label("加入播放队列", systemImage: "text.badge.plus") }
            Button { showParts = true } label: { Label("查看视频分 P", systemImage: "list.bullet") }
            Link(destination: track.webpage) { Label("在 B 站打开", systemImage: "arrow.up.right.square") }
        }
        .sheet(isPresented: $showParts) { NavigationStack { PartsView(track: track) } }
    }
}

struct TrackList: View {
    let title: String
    let tracks: [Track]
    var body: some View {
        List {
            if tracks.isEmpty { Text("这里还没有音乐。").foregroundStyle(.secondary) }
            ForEach(tracks) { track in TrackRow(track: track, tracks: tracks) }
        }.listStyle(.plain).navigationTitle(title)
    }
}

private struct PartsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let track: Track
    @State private var tracks: [Track] = []
    @State private var loading = true
    @State private var error: String?
    var body: some View {
        List {
            if loading { ProgressView("读取分 P…") }
            if let error { Text(error).foregroundStyle(.secondary) }
            ForEach(tracks) { item in TrackRow(track: item, tracks: tracks) }
        }
        .navigationTitle("视频分 P").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        .task {
            do { tracks = try await model.api.parts(bvid: track.bvid) }
            catch { self.error = error.localizedDescription }
            loading = false
        }
    }
}

private struct FolderView: View {
    @EnvironmentObject private var model: AppModel
    let folder: FavoriteFolder
    @State private var tracks: [Track] = []
    @State private var page = 0
    @State private var hasMore = true
    @State private var loading = false
    @State private var error: String?
    var body: some View {
        List {
            ForEach(tracks) { item in TrackRow(track: item, tracks: tracks) }
            if loading { ProgressView("载入收藏夹…") }
            if let error { Text(error).foregroundStyle(.secondary) }
            if hasMore && !loading { Button(error == nil ? "加载更多" : "重试") { Task { await loadMore() } } }
            if !loading && !hasMore && tracks.isEmpty { Text("这个收藏夹没有可用的音乐。").foregroundStyle(.secondary) }
        }
        .listStyle(.plain).navigationTitle(folder.title)
        .task { if page == 0 { await loadMore() } }
    }
    private func loadMore() async {
        guard !loading else { return }
        loading = true; error = nil
        do {
            let result = try await model.api.folderTracks(id: folder.id, page: page + 1)
            var seen = Set(tracks.map(\.id))
            tracks += result.tracks.filter { seen.insert($0.id).inserted }
            page += 1; hasMore = result.hasMore
        } catch { self.error = error.localizedDescription }
        loading = false
    }
}

private struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    var body: some View {
        List {
            Section("B 站账号") {
                if let profile = model.profile {
                    Label(profile.name, systemImage: "person.crop.circle")
                    Button("刷新收藏夹") { Task { await model.refreshAccount() } }.disabled(model.accountLoading)
                    Button("退出登录", role: .destructive) { Task { await model.logout() } }.disabled(model.accountLoading)
                } else { Button("登录 B 站") { model.showLogin = true } }
                Text("登录后可读取你的 B 站收藏夹。心形按钮收藏在本机，不会修改 B 站收藏夹。")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("关于 Madus") {
                LabeledContent("版本", value: "iOS 0.1.0")
                Text("原生播放器 · 搜索与播放 · 本地收藏 · 后台音频")
                Text("音乐和视频来自 B 站，内容归原作者所有。可用内容取决于平台与账号权限。")
                    .font(.footnote).foregroundStyle(.secondary)
                Link("项目主页", destination: URL(string: "https://github.com/zyjshb/Madus")!)
            }
        }.navigationTitle("我的")
    }
}

struct CoverView: View {
    let url: URL?
    var body: some View {
        AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: {
            ZStack { Color(uiColor: .secondarySystemBackground); Image(systemName: "music.note").foregroundStyle(.secondary) }
        }.clipped().accessibilityHidden(true)
    }
}

func formatTime(_ value: Double) -> String {
    guard value.isFinite, value > 0 else { return "0:00" }
    let seconds = Int(value)
    return seconds >= 3600 ? String(format: "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        : String(format: "%d:%02d", seconds / 60, seconds % 60)
}
