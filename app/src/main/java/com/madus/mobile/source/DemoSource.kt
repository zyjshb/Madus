package com.madus.mobile.source

import com.madus.mobile.domain.AuthSession
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track

/**
 * Demo catalog with public sample MP3s (SoundHelix) so ExoPlayer can actually play.
 * Replace with real sources later.
 */
class DemoSource : MusicSource {
    override val type: MusicSourceType = MusicSourceType.LOCAL_DEMO

    private val catalog = listOf(
        track("demo-1", "纸上的早晨", "线稿电台", "手稿 Vol.1", 1),
        track("demo-2", "黑白分镜", "简约乐团", "手稿 Vol.1", 2),
        track("demo-3", "留白", "Madus Demo", "手稿 Vol.1", 3),
        track("demo-4", "装订线", "线稿电台", "手稿 Vol.2", 4),
        track("demo-5", "细描边", "简约乐团", "手稿 Vol.2", 5),
        track("demo-6", "夜车时刻表", "线稿电台", "手稿 Vol.3", 6),
        track("demo-7", "未署名的副歌", "简约乐团", "手稿 Vol.3", 7),
        track("demo-8", "铅笔灰", "Madus Demo", "手稿 Vol.3", 8),
        track("demo-9", "对页", "线稿电台", "手稿 Vol.4", 9),
        track("demo-10", "折叠线", "简约乐团", "手稿 Vol.4", 10),
    )

    private fun track(
        id: String,
        title: String,
        artist: String,
        album: String,
        helixIndex: Int,
    ): Track {
        val n = ((helixIndex - 1) % 16) + 1
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = 0L,
            source = type,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$n.mp3",
        )
    }

    override suspend fun getAuthSession(): AuthSession =
        AuthSession(source = type, displayName = "演示模式", isLoggedIn = true)

    override suspend fun login(): AuthSession = getAuthSession()

    override suspend fun logout(): AuthSession =
        AuthSession(source = type, displayName = "演示模式", isLoggedIn = true)

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.trim()
        if (q.isEmpty()) return catalog.take(limit)
        return catalog.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
        }.take(limit)
    }

    override suspend fun resolveStream(track: Track): Track = track

    override suspend fun featuredPlaylists(): List<Playlist> = listOf(
        Playlist(id = "pl-1", title = "今日线稿", trackCount = 5, source = type),
        Playlist(id = "pl-2", title = "深夜留白", trackCount = 3, source = type),
        Playlist(id = "pl-3", title = "通勤细线", trackCount = 4, source = type),
    )

    override suspend fun recommendFeed(limit: Int): List<Track> =
        catalog.shuffled().take(limit.coerceAtMost(catalog.size))
}
