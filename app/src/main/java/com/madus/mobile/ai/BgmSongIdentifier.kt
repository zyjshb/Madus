package com.madus.mobile.ai

import com.madus.mobile.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class BgmIdentifyResult(
    val reply: String,
    val candidates: List<SongCandidate>,
    val tracks: List<Track>,
    val rawModel: String = "",
)

/**
 * 无聊天 UI 的 BGM 识别：音频 → 模型猜歌 → 规范化 → B 站搜可播曲。
 * [preferForeign]：外语 BGM 模式，不强制华语歌名。
 */
object BgmSongIdentifier {

    suspend fun identifyFromWavBase64(
        profile: LlmProfile,
        apiKey: String,
        audioBase64: String,
        audioFormat: String = "wav",
        preferForeign: Boolean = false,
        searchTracks: suspend (String) -> List<Track>,
        client: LlmClient = LlmClient(),
    ): BgmIdentifyResult = withContext(Dispatchers.IO) {
        var foreignMode = preferForeign
        val clue = if (preferForeign) {
            "这是视频里的背景音乐 BGM（非华语/外语优先），请识别歌名。"
        } else {
            "这是视频里的背景音乐 BGM，请识别歌名。"
        }
        val envelope = SongIdPrompt.audioEnvelope(clue, preferForeign = preferForeign)
        val multimodal = MultimodalPayload(
            text = envelope,
            audioBase64 = audioBase64,
            audioFormat = audioFormat,
        )
        val chat = client.chat(
            profile = profile,
            apiKey = apiKey,
            system = SongIdPrompt.SYSTEM,
            userText = envelope,
            forceJson = false,
            multimodal = multimodal,
        )
        if (chat.isFailure) {
            val err = chat.exceptionOrNull()?.message ?: "识别失败"
            return@withContext BgmIdentifyResult(
                reply = err,
                candidates = emptyList(),
                tracks = emptyList(),
            )
        }
        var raw = chat.getOrThrow().text
        val parseClue = if (preferForeign) "foreign bgm english japanese korean" else ""
        var guess = SongGuessParser.parse(raw, userClue = parseClue)
        var detected = SongLanguage.detectLanguage(guess.lyricsHeard, guess.candidates)
        when (detected) {
            SongLangKind.CHINESE -> foreignMode = false
            SongLangKind.JAPANESE, SongLangKind.KOREAN, SongLangKind.LATIN -> foreignMode = true
            else -> {}
        }
        var cands = clean(guess.candidates, preferForeign = foreignMode)
        cands = mergeRecover(cands, raw, preferForeign = foreignMode)

        if (cands.isEmpty()) {
            val retryEnv = SongIdPrompt.audioRetryEnvelope(
                extraText = if (foreignMode) "视频 BGM · 外语" else "视频 BGM",
                previousBad = raw,
                preferForeign = foreignMode,
            )
            val retry = client.chat(
                profile = profile,
                apiKey = apiKey,
                system = SongIdPrompt.AUDIO_RETRY_SYSTEM,
                userText = retryEnv,
                forceJson = false,
                multimodal = multimodal.copy(text = retryEnv),
            )
            if (retry.isSuccess) {
                raw = retry.getOrThrow().text
                guess = SongGuessParser.parse(raw, userClue = parseClue)
                val retryDetected = SongLanguage.detectLanguage(
                    guess.lyricsHeard,
                    guess.candidates,
                )
                when (retryDetected) {
                    SongLangKind.CHINESE -> foreignMode = false
                    SongLangKind.JAPANESE, SongLangKind.KOREAN, SongLangKind.LATIN -> foreignMode = true
                    else -> {}
                }
                if (detected == null) detected = retryDetected
                cands = mergeRecover(
                    clean(guess.candidates, preferForeign = foreignMode),
                    raw,
                    preferForeign = foreignMode,
                )
            }
        }

        if (cands.isEmpty()) {
            val snippet = raw.replace(Regex("""\s+"""), " ").take(120)
            return@withContext BgmIdentifyResult(
                reply = if (snippet.isBlank()) {
                    "没有识别出歌名。可换 MiMo 普通档，或打开「外语 BGM」再试。"
                } else {
                    "没有识别出可用歌名。模型片段：$snippet"
                },
                candidates = emptyList(),
                tracks = emptyList(),
                rawModel = raw,
            )
        }

        val forRank = if (foreignMode) {
            cands.filterNot {
                SongNameNormalizer.isJunkFragment(it.title) ||
                    SongGuessParser.isGarbageTitle(it.title)
            }.let {
                SongNameNormalizer.preferChineseWhenPresent(
                    it,
                    strictAudio = false,
                    globalMode = true,
                )
            }
        } else {
            SongNameNormalizer.normalizeAll(cands)
                .let { SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = true) }
        }

        val rankClue = when {
            detected == SongLangKind.JAPANESE -> "japanese bgm"
            detected == SongLangKind.KOREAN -> "korean bgm"
            detected == SongLangKind.LATIN -> "english bgm"
            foreignMode -> "foreign song bgm english japanese korean"
            else -> forRank.firstOrNull()?.title.orEmpty()
        }
        val ranked = SongRanker.rankCandidates(
            rankClue,
            forRank,
            forceLanguage = detected,
            preferForeign = foreignMode,
        ).ifEmpty { forRank }

        val tracks = searchPlayable(ranked, searchTracks)
        val top = ranked.first()
        val who = listOfNotNull(top.title, top.artist).joinToString(" · ")
        BgmIdentifyResult(
            reply = if (foreignMode) "外语 BGM：$who" else "识别到：$who",
            candidates = ranked.take(5),
            tracks = tracks,
            rawModel = raw,
        )
    }

    private fun clean(
        list: List<SongCandidate>,
        preferForeign: Boolean,
    ): List<SongCandidate> {
        val filtered = list.filterNot {
            SongGuessParser.isGarbageTitle(it.title) ||
                SongGuessParser.isPlaceholderValue(it.title) ||
                SongNameNormalizer.isJunkFragment(it.title)
        }
        return if (preferForeign) {
            SongNameNormalizer.preferChineseWhenPresent(
                filtered,
                strictAudio = false,
                globalMode = true,
            )
        } else {
            SongNameNormalizer.normalizeAll(filtered)
                .let { SongNameNormalizer.preferChineseWhenPresent(it, strictAudio = true) }
        }
    }

    private fun mergeRecover(
        cands: List<SongCandidate>,
        raw: String,
        preferForeign: Boolean,
    ): List<SongCandidate> {
        if (preferForeign) {
            // 外语：不硬挖中文金曲表，避免把外文 BGM 锁成华语
            val filtered = cands.filterNot {
                SongNameNormalizer.isJunkFragment(it.title) ||
                    SongGuessParser.isGarbageTitle(it.title)
            }
            return clean(filtered, preferForeign = true)
        }
        val dug = SongNameNormalizer.extractChineseFromText(raw)
        val hallu = SongNameNormalizer.recoverAudioHallucinations(raw)
        val lyrics = SongGuessParser.extractLyricsHeard(raw)
        val fromLy = if (lyrics.length >= 4) ChineseFamousLyrics.suggest(lyrics) else emptyList()
        return clean(cands + dug + hallu + fromLy, preferForeign = false)
    }

    private suspend fun searchPlayable(
        cands: List<SongCandidate>,
        searchTracks: suspend (String) -> List<Track>,
    ): List<Track> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Track>()
        val top = cands
            .filter {
                SongLanguage.isPlausibleTitleLength(it.title) &&
                    (SongGuessParser.hasCjk(it.title) || it.title.length >= 3)
            }
            .filterNot {
                SongNameNormalizer.isJunkFragment(it.title) ||
                    SongGuessParser.isGarbageTitle(it.title)
            }
            .take(3)
        if (top.isEmpty()) return emptyList()

        for (c in top) {
            // 通用查询策略：短名不裸搜、优先 歌名+歌手
            val queries = SongRanker.buildSearchQueries(c, max = 4)
            for (q in queries) {
                val list = withTimeoutOrNull(8_000L) {
                    runCatching { searchTracks(q) }.getOrDefault(emptyList())
                }.orEmpty()
                for (t in list) {
                    if (SongRanker.isHardGarbageTitle(t.title)) continue
                    // 初筛：至少对任一候选有基本相关度
                    if (SongRanker.bestTrackScore(t, top) < 12) continue
                    if (seen.add(t.id)) {
                        out.add(t)
                        if (out.size >= 28) {
                            return SongRanker.rankTracks(out, top, minScore = 18)
                        }
                    }
                }
            }
        }
        // 严格排序；若全被滤空则放宽一档再取（仍不要硬垃圾）
        val strict = SongRanker.rankTracks(out, top, minScore = 18)
        if (strict.isNotEmpty()) return strict
        return SongRanker.rankTracks(out, top, minScore = 12)
    }
}
