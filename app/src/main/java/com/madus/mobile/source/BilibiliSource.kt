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
    private val qualityProvider: () -> Int = { 80 },
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
        return runCatching {
            val nav = api.nav(cookie)
            if (!nav.isLogin) {
                // 残留 SESSDATA / 过期 Cookie 不算登录
                store.clearBiliCookie()
                cached = AuthSession(source = type, isLoggedIn = false, displayName = "未登录 · 点此登录")
                return cached
            }
            cached = AuthSession(
                source = type,
                isLoggedIn = true,
                displayName = nav.uname.ifBlank { "B站用户" },
                credentialBlob = cookie,
                updatedAtMs = System.currentTimeMillis(),
                avatarUrl = nav.face.ifBlank { null },
            )
            cached
        }.getOrElse {
            // 网络失败：只有上次已经用接口确认过登录才沿用
            if (cached.isLoggedIn && cached.displayName.isNotBlank() &&
                cached.displayName != "B站用户" && cached.displayName != "未登录 · 点此登录"
            ) {
                cached
            } else {
                AuthSession(source = type, isLoggedIn = false, displayName = "未登录 · 点此登录")
            }
        }
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
            // favFolders 已在封面为空时用夹内第一首补图
            api.favFolders().map { f ->
                Playlist(
                    id = f.id,
                    title = f.title,
                    coverUrl = f.cover.takeIf { it.isNotBlank() },
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
            .filter { !api.isInvalidTrack(it) }
        return if (limit <= 0) all else all.take(limit)
    }

    override suspend fun recommendFeed(limit: Int): List<Track> =
        api.musicRegionFeed(limit).filter { com.madus.mobile.domain.TrackFilters.isLikelyMusic(it) }.take(limit)
}