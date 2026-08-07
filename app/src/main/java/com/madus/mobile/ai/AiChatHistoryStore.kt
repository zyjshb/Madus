package com.madus.mobile.ai

import android.content.Context
import com.madus.mobile.domain.MusicSourceType
import com.madus.mobile.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AiChatSessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val preview: String,
)

data class AiChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messages: List<AiChatMessage>,
)

/**
 * AI 对话历史：本机 JSON 文件。
 */
class AiChatHistoryStore(context: Context) {
    private val app = context.applicationContext
    private val file get() = app.filesDir.resolve(FILE_NAME)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _sessions = MutableStateFlow(loadAll())
    val sessions: StateFlow<List<AiChatSession>> = _sessions.asStateFlow()

    fun summaries(): List<AiChatSessionSummary> =
        _sessions.value
            .sortedByDescending { it.updatedAt }
            .map {
                AiChatSessionSummary(
                    id = it.id,
                    title = it.title,
                    updatedAt = it.updatedAt,
                    preview = it.messages
                        .filterIsInstance<AiChatMessage.User>()
                        .lastOrNull()
                        ?.text
                        ?.take(40)
                        ?: "空对话",
                )
            }

    fun get(id: String): AiChatSession? = _sessions.value.find { it.id == id }

    fun isGuideDismissed(): Boolean = prefs.getBoolean(KEY_GUIDE, false)

    fun setGuideDismissed(dismissed: Boolean = true) {
        prefs.edit().putBoolean(KEY_GUIDE, dismissed).apply()
    }

    /** 视频 BGM 悬浮球「外语 BGM」开关 */
    fun isBgmPreferForeign(): Boolean = prefs.getBoolean(KEY_BGM_PREFER_FOREIGN, false)

    fun setBgmPreferForeign(on: Boolean) {
        prefs.edit().putBoolean(KEY_BGM_PREFER_FOREIGN, on).apply()
    }

    suspend fun saveSession(
        id: String?,
        messages: List<AiChatMessage>,
    ): String = withContext(Dispatchers.IO) {
        val usable = messages.filter {
            it is AiChatMessage.User ||
                (it is AiChatMessage.Assistant && !it.isStreaming)
        }
        if (usable.isEmpty()) return@withContext id.orEmpty()

        val sid = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val title = usable.filterIsInstance<AiChatMessage.User>()
            .firstOrNull()
            ?.text
            ?.trim()
            ?.take(24)
            ?.ifBlank { null }
            ?: "对话"
        val session = AiChatSession(
            id = sid,
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = usable,
        )
        val others = _sessions.value.filterNot { it.id == sid }
        val next = (listOf(session) + others)
            .sortedByDescending { it.updatedAt }
            .take(MAX_SESSIONS)
        persist(next)
        _sessions.value = next
        sid
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        val next = _sessions.value.filterNot { it.id == id }
        persist(next)
        _sessions.value = next
    }

    private fun loadAll(): List<AiChatSession> {
        return runCatching {
            if (!file.exists()) return emptyList()
            val root = JSONArray(file.readText())
            buildList {
                for (i in 0 until root.length()) {
                    val o = root.optJSONObject(i) ?: continue
                    add(sessionFromJson(o))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(list: List<AiChatSession>) {
        val arr = JSONArray()
        list.forEach { arr.put(sessionToJson(it)) }
        file.writeText(arr.toString())
    }

    private fun sessionToJson(s: AiChatSession): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("title", s.title)
        put("updatedAt", s.updatedAt)
        val msgs = JSONArray()
        s.messages.forEach { msgs.put(messageToJson(it)) }
        put("messages", msgs)
    }

    private fun sessionFromJson(o: JSONObject): AiChatSession {
        val arr = o.optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                messageFromJson(m)?.let { add(it) }
            }
        }
        return AiChatSession(
            id = o.getString("id"),
            title = o.optString("title", "对话"),
            updatedAt = o.optLong("updatedAt"),
            messages = messages,
        )
    }

    private fun messageToJson(m: AiChatMessage): JSONObject = when (m) {
        is AiChatMessage.User -> JSONObject().apply {
            put("type", "user")
            put("id", m.id)
            put("createdAt", m.createdAt)
            put("text", m.text)
        }
        is AiChatMessage.Assistant -> JSONObject().apply {
            put("type", "assistant")
            put("id", m.id)
            put("createdAt", m.createdAt)
            put("text", m.text)
            put("error", m.error)
            if (!m.thinking.isNullOrBlank()) put("thinking", m.thinking)
            if (!m.lyricsHeard.isNullOrBlank()) put("lyricsHeard", m.lyricsHeard)
            if (!m.modelRaw.isNullOrBlank()) put("modelRaw", m.modelRaw)
            val cands = JSONArray()
            m.candidates.forEach { c ->
                cands.put(
                    JSONObject().apply {
                        put("title", c.title)
                        put("artist", c.artist)
                        if (c.confidence != null) put("confidence", c.confidence.toDouble())
                        put("bilibiliQuery", c.bilibiliQuery)
                        put("note", c.note)
                    },
                )
            }
            put("candidates", cands)
            val tracks = JSONArray()
            m.tracks.forEach { tracks.put(trackToJson(it)) }
            put("tracks", tracks)
        }
        is AiChatMessage.SystemNote -> JSONObject().apply {
            put("type", "system")
            put("id", m.id)
            put("createdAt", m.createdAt)
            put("text", m.text)
        }
    }

    private fun messageFromJson(o: JSONObject): AiChatMessage? {
        return when (o.optString("type")) {
            "user" -> AiChatMessage.User(
                id = o.getString("id"),
                createdAt = o.optLong("createdAt"),
                text = o.optString("text"),
            )
            "assistant" -> {
                val cands = o.optJSONArray("candidates") ?: JSONArray()
                val candidates = buildList {
                    for (i in 0 until cands.length()) {
                        val c = cands.optJSONObject(i) ?: continue
                        add(
                            SongCandidate(
                                title = c.optString("title"),
                                artist = c.optString("artist").takeIf { it.isNotBlank() && it != "null" },
                                confidence = if (c.has("confidence") && !c.isNull("confidence")) {
                                    c.optDouble("confidence").toFloat()
                                } else {
                                    null
                                },
                                bilibiliQuery = c.optString("bilibiliQuery"),
                                note = c.optString("note").takeIf { it.isNotBlank() && it != "null" },
                            ),
                        )
                    }
                }
                val tracksArr = o.optJSONArray("tracks") ?: JSONArray()
                val tracks = buildList {
                    for (i in 0 until tracksArr.length()) {
                        val t = tracksArr.optJSONObject(i) ?: continue
                        trackFromJson(t)?.let { add(it) }
                    }
                }
                AiChatMessage.Assistant(
                    id = o.getString("id"),
                    createdAt = o.optLong("createdAt"),
                    text = o.optString("text"),
                    candidates = candidates,
                    tracks = tracks,
                    isStreaming = false,
                    error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
                    thinking = o.optString("thinking").takeIf { it.isNotBlank() },
                    lyricsHeard = o.optString("lyricsHeard").takeIf { it.isNotBlank() },
                    modelRaw = o.optString("modelRaw").takeIf { it.isNotBlank() },
                )
            }
            "system" -> AiChatMessage.SystemNote(
                id = o.getString("id"),
                createdAt = o.optLong("createdAt"),
                text = o.optString("text"),
            )
            else -> null
        }
    }

    private fun trackToJson(t: Track): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("artist", t.artist)
        put("album", t.album)
        put("durationMs", t.durationMs)
        put("coverUrl", t.coverUrl)
        put("streamUrl", t.streamUrl)
        put("source", t.source.name)
        put("isVideoStream", t.isVideoStream)
        put("bvid", t.bvid)
        put("cid", t.cid)
        put("aid", t.aid)
        put("pageCount", t.pageCount)
    }

    private fun trackFromJson(o: JSONObject): Track? {
        return runCatching {
            Track(
                id = o.getString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                album = o.optString("album"),
                durationMs = o.optLong("durationMs"),
                coverUrl = o.optString("coverUrl").takeIf { it.isNotBlank() },
                streamUrl = o.optString("streamUrl").takeIf { it.isNotBlank() },
                source = MusicSourceType.entries
                    .find { it.name == o.optString("source") }
                    ?: MusicSourceType.BILIBILI,
                isVideoStream = o.optBoolean("isVideoStream", false),
                bvid = o.optString("bvid"),
                cid = o.optString("cid"),
                aid = o.optString("aid"),
                pageCount = o.optInt("pageCount", 1),
            )
        }.getOrNull()
    }

    companion object {
        private const val FILE_NAME = "madus_ai_chat_history.json"
        private const val PREFS = "madus_ai_chat_prefs"
        private const val KEY_GUIDE = "guide_dismissed"
        private const val KEY_BGM_PREFER_FOREIGN = "bgm_prefer_foreign_song"
        private const val MAX_SESSIONS = 40
    }
}
