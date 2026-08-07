package com.madus.mobile.source

import com.madus.mobile.domain.AuthSession
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Playlist
import com.madus.mobile.domain.Track

interface MusicSource {
    val type: MusicSourceType
    suspend fun getAuthSession(): AuthSession
    suspend fun login(): AuthSession
    suspend fun logout(): AuthSession
    suspend fun search(query: String, limit: Int = 30): List<Track>
    suspend fun resolveStream(track: Track): Track
    suspend fun featuredPlaylists(): List<Playlist> = emptyList()
    /** @param limit 条数上限；<=0 表示尽量拉全（B 站收藏夹等） */
    suspend fun playlistTracks(playlistId: String, limit: Int = 0): List<Track> = emptyList()
    suspend fun recommendFeed(limit: Int = 30): List<Track> = emptyList()
}

class SourceRegistry(
    private val sources: List<MusicSource>,
) {
    fun all(): List<MusicSource> = sources
    fun get(type: MusicSourceType): MusicSource? = sources.find { it.type == type }
}
