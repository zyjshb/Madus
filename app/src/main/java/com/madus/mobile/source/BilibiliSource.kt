package com.madus.mobile.source

import com.madus.mobile.data.BilibiliApi
import com.madus.mobile.data.SessionStore
import com.madus.mobile.domain.AuthSession
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track

class BilibiliSource(
    private val store: SessionStore,
    private val api: BilibiliApi,
    private val loginUi: suspend () -> String?,
    /** 当前音质 qn，默认 64 标准 */
    private val qualityProvider: () -> Int = { 64 },
    /** 视频模式：取可播画面流 */
    private val videoModeProvider: () -> Boolean = { false },
) : MusicSource {
    override val type: MusicSourceType = MusicSourceType.BILIBILI

    @Volatile
    private var cached = AuthSession(source = type, isLoggedIn = false)

    override suspend fun getAuthSession(): AuthSession {
        val cookie = store.getBiliCookie()
        if (!cookie.contains("SESSDATA")) {
            cached = AuthSession(source = type, isLoggedIn = false, displayName = "未登录 · 点此登录")
            return cached
        }
        // Cookie present = treat as logged in even if nav is slow/fails
        val fallback = AuthSession(
            source = type,
            isLoggedIn = true,
            displayName = "B站用户",
            credentialBlob = cookie,
            updatedAtMs = System.currentTimeMillis(),
            avatarUrl = cached.avatarUrl,
        )
        return runCatching {
            val nav = api.nav(cookie)
            val logged = nav.isLogin || cookie.contains("SESSDATA")
            cached = AuthSession(
                source = type,
                isLoggedIn = logged,
                displayName = when {
                    nav.uname.isNotBlank() -> nav.uname
                    logged -> "B站用户"
                    else -> "未登录 · 点此登录"
                },
                credentialBlob = cookie,
                updatedAtMs = System.currentTimeMillis(),
                avatarUrl = nav.face.ifBlank { null },
            )
            cached
        }.getOrDefault(fallback)
    }

    override suspend fun login(): AuthSession {
        val cookie = loginUi() ?: return getAuthSession()
        if (!cookie.contains("SESSDATA")) {
            return AuthSession(
                source = type,
                isLoggedIn = false,
                displayName = "未拿到 SESSDATA",
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        store.setBiliCookie(cookie)
        // Immediate local session so UI updates without waiting nav
        cached = AuthSession(
            source = type,
            isLoggedIn = true,
            displayName = "B站用户",
            credentialBlob = cookie,
            updatedAtMs = System.currentTimeMillis(),
        )
        return runCatching { getAuthSession() }.getOrDefault(cached)
    }

    override suspend fun logout(): AuthSession {
        store.clearBiliCookie()
        cached = AuthSession(source = type, isLoggedIn = false, displayName = "未登录 · 点此登录")
        return cached
    }

    override suspend fun search(query: String, limit: Int): List<Track> {
        runCatching { api.ensureGuestCookies() }
        return api.search(query, limit)
    }

    override suspend fun resolveStream(track: Track): Track {
        runCatching { api.ensureGuestCookies() }
        val qn = qualityProvider()
        return api.resolvePlayUrl(
            track,
            preferredQn = qn,
            videoMode = videoModeProvider(),
        )
    }

    override suspend fun featuredPlaylists(): List<Playlist> {
        val cookie = store.getBiliCookie()
        if (!cookie.contains("SESSDATA")) return emptyList()
        return runCatching {
            api.favFolders().map { f ->
                Playlist(
                    id = f.id,
                    title = f.title,
                    coverUrl = f.cover.ifBlank { null },
                    trackCount = f.count,
                    source = type,
                )
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun playlistTracks(playlistId: String, limit: Int): List<Track> {
        // limit<=0：尽量拉全（上限 500 页 ≈ 1 万首）；否则按条数算页数
        val pages = if (limit <= 0) {
            500
        } else {
            ((limit + 19) / 20).coerceIn(1, 500)
        }
        val all = api.favTracks(playlistId, maxPages = pages)
        return if (limit <= 0) all else all.take(limit)
    }

    override suspend fun recommendFeed(limit: Int): List<Track> {
        // 优先 B 站首页 rcmd（登录 Cookie 个性化），再 related / 热门；不再硬塞音乐区
        val pool = linkedMapOf<String, Track>()
        runCatching { api.homepageRcmd(limit = limit, freshIdx = 1) }.getOrDefault(emptyList())
            .forEach { pool.putIfAbsent(it.id, it) }
        if (pool.size < limit / 2) {
            runCatching { api.popularTracks(limit) }.getOrDefault(emptyList())
                .forEach { pool.putIfAbsent(it.id, it) }
        }
        val cookie = store.getBiliCookie()
        if (cookie.contains("SESSDATA")) {
            runCatching { api.watchHistory(limit = 16) }.getOrDefault(emptyList())
                .forEach { pool.putIfAbsent(it.id, it) }
            val folders = runCatching { api.favFolders() }.getOrDefault(emptyList())
            val seeds = mutableListOf<Track>()
            for (f in folders.take(3)) {
                seeds += runCatching { api.favTracks(f.id, maxPages = 1) }.getOrDefault(emptyList()).take(3)
            }
            for (seed in seeds.shuffled().take(8)) {
                val bv = seed.bvid.ifBlank { com.madus.mobile.data.BilibiliApi.parseBvid(seed.id).orEmpty() }
                if (bv.isBlank()) continue
                runCatching { api.relatedTracks(bv, 10) }.getOrDefault(emptyList())
                    .forEach { pool.putIfAbsent(it.id, it) }
            }
        }
        return pool.values.shuffled().take(limit)
    }
}
