package com.madus.mobile.ai

/**
 * 三步链路：① 模型猜歌 → ② 全网检索 → ③ B 站搜。
 * 支持全球语言歌名（中/英/日/韩/西等）；Schema 禁止中文占位词。
 */
object SongIdPrompt {

    val SYSTEM: String =
        "You help find REAL song titles for a music app that plays via Bilibili.\n" +
            "Pipeline: your guess → web confirm → Bilibili search.\n" +
            "Output ONE JSON object only. No markdown. No thinking tags.\n" +
            "\n" +
            "GLOBAL LANGUAGE RULES:\n" +
            "1) Support songs in ANY language: Chinese, English, Japanese, Korean, Spanish, French, etc.\n" +
            "2) title/artist must be REAL names in the song's natural language " +
            "(e.g. 青花瓷/周杰伦, Shape of You/Ed Sheeran, 夜に駆ける/YOASOBI, Dynamite/BTS).\n" +
            "NEVER use 歌名/歌手/null as values. NEVER invent placeholders.\n" +
            "3) Do NOT force-translate foreign songs into Chinese. Keep official/common titles " +
            "users would type when searching (English title for Western pop; " +
            "Japanese for J-pop; Hangul or official English for K-pop).\n" +
            "4) Chinese songs: use Chinese characters (歌声与微笑/谷建芬), NOT pinyin, " +
            "NOT English fragments (Something/Possible/In Chinese).\n" +
            "5) Typos / wrong characters in Chinese lyrics: still guess the real Chinese song; " +
            "bilibili_query = \"title artist\".\n" +
            "6) Audio humming: listen carefully. Match language of what you hear. " +
            "Chinese lyrics → Chinese title; English lyrics → English title. " +
            "Never swap the melody to another famous song because one phrase sounds similar.\n" +
            "7) Image: read text on screenshot for song/artist in whatever language shown.\n" +
            "8) Chinese sound-alike of English (欧baby): map to real English title+artist in Latin letters.\n" +
            "9) Give 2-5 candidates, best first. If unsure, still give best real guesses.\n" +
            "10) App UI is Chinese: write reply, note and explanation in Chinese " +
            "unless quoting a song title/artist.\n" +
            "11) When user writes or sings Chinese, reason in Chinese too; " +
            "keep foreign song titles/artists in their original language.\n" +
            "\n" +
            "JSON shape example (replace with real guesses):\n" +
            "{\"reply\":\"short note\",\"candidates\":[" +
            "{\"title\":\"Shape of You\",\"artist\":\"Ed Sheeran\",\"confidence\":0.85," +
            "\"bilibili_query\":\"Shape of You Ed Sheeran\",\"note\":\"chorus match\"}]}"

    val REFINE_SYSTEM: String =
        "Refine song guesses using web snippets. ONE JSON only.\n" +
            "Use REAL song titles/artists in the song's own language " +
            "(Chinese / English / Japanese / Korean / etc.).\n" +
            "Never use 歌名/歌手 as values. Never force Chinese translation of foreign titles.\n" +
            "Write reply/note/explanation in Chinese unless quoting a title/artist.\n" +
            "bilibili_query like \"七里香 周杰伦\" or \"Blinding Lights The Weeknd\". " +
            "Ignore novel/audiobook/recognition-tool noise."

    val REPAIR_SYSTEM: String =
        "Convert the draft into ONE JSON with REAL song titles only.\n" +
            "Any language OK: 青花瓷/周杰伦, Hello/Adele, 夜に駆ける/YOASOBI.\n" +
            "NEVER placeholders: 歌名, 歌手, 真实歌名, null, Something, Possible, In Chinese.\n" +
            "Write reply/note/explanation in Chinese unless quoting a title/artist.\n" +
            "Example: {\"reply\":\"likely English pop\",\"candidates\":[{\"title\":\"Hello\"," +
            "\"artist\":\"Adele\",\"confidence\":0.8,\"bilibili_query\":\"Hello Adele\",\"note\":\"\"}]}"

    /** 哼唱二次听辨：先写听到的歌词，再按语言写歌名 */
    val AUDIO_RETRY_SYSTEM: String =
        "You are identifying a song from user singing/humming audio.\n" +
            "Output ONE JSON only. No markdown.\n" +
            "Step1: lyrics_heard = words you catch (same language as sung).\n" +
            "Step2: candidates = real song title+artist in that song's language.\n" +
            "If the audio language is clear, the title MUST be in that same language. " +
            "Never replace it with another famous song.\n" +
            "Write reply/note/explanation in Chinese unless quoting a title/artist.\n" +
            "When user writes or sings Chinese, reason in Chinese too.\n" +
            "Chinese song → Chinese title (歌声与微笑/谷建芬). " +
            "English song → English title (Hello/Adele). " +
            "Japanese/Korean keep original.\n" +
            "Forbidden as title: 歌名, 歌手, Something, Possible, In Chinese, Little Star, Art Troupe, null.\n" +
            "If lyrics sound like 请把我的歌带回你的家 → title=歌声与微笑 artist=谷建芬.\n" +
            "JSON example:\n" +
            "{\"reply\":\"short\",\"lyrics_heard\":\"...\",\"candidates\":" +
            "[{\"title\":\"...\",\"artist\":\"...\",\"confidence\":0.85," +
            "\"bilibili_query\":\"...\",\"note\":\"lyrics\"}]}"

    fun userTextEnvelope(userText: String): String = buildString {
        appendLine("User clue (lyrics / typos / homophones / any language):")
        appendLine(userText.trim().ifBlank { "(empty text, use attachment only)" })
        appendLine()
        appendLine("Return JSON with real song titles in the song's natural language.")
    }

    fun audioEnvelope(
        extraText: String?,
        preferForeign: Boolean = false,
    ): String = buildString {
        appendLine("The user attached audio: humming or singing a song.")
        if (preferForeign) {
            appendLine("IMPORTANT: User toggled NON-CHINESE / foreign song mode.")
            appendLine("Prefer English, Japanese, Korean, Spanish, etc. Keep original-language titles.")
            appendLine("Do NOT force-translate into Chinese. Do NOT invent Chinese titles for Western melodies.")
        }
        appendLine("Do TWO things in one JSON:")
        appendLine("1) lyrics_heard: transcribe words you hear (keep original language).")
        appendLine("2) candidates: real song title + artist in that language.")
        appendLine(
            "If the audio language is clear, keep the title in that same language; " +
                "do not swap in another famous song.",
        )
        if (preferForeign) {
            appendLine("Foreign melody → Latin/JP/KR title first (e.g. Hello/Adele, Shape of You/Ed Sheeran).")
        } else {
            appendLine("Chinese → Chinese chars. English → Latin. JP/KR → original script when possible.")
            appendLine("If hear 请把我的歌带回你的家 / 请把你的微笑留下 → 歌声与微笑 / 谷建芬.")
        }
        appendLine("NEVER title=Something / In Chinese / Possible / Little Star / Art Troupe / 歌名 / 歌手.")
        appendLine("Never Bilibili as title. Never pinyin-only for Chinese songs.")
        if (!extraText.isNullOrBlank()) {
            appendLine("Extra user text:")
            appendLine(extraText.trim())
        }
        appendLine(
            "JSON: {\"reply\":\"...\",\"lyrics_heard\":\"...\",\"candidates\":" +
                "[{\"title\":\"...\",\"artist\":\"...\",\"confidence\":0.8," +
                "\"bilibili_query\":\"...\",\"note\":\"\"}]}",
        )
    }

    fun audioRetryEnvelope(
        extraText: String?,
        previousBad: String,
        preferForeign: Boolean = false,
    ): String = buildString {
        appendLine("Listen to the audio AGAIN. Previous answer was invalid (junk placeholders).")
        appendLine("Previous bad output (do NOT copy junk fragments):")
        appendLine(previousBad.take(600))
        appendLine()
        if (preferForeign) {
            appendLine("User wants NON-CHINESE song. lyrics_heard + title in original language.")
            appendLine("English example: Hello / Adele. Japanese: 夜に駆ける / YOASOBI.")
        } else {
            appendLine("Now: lyrics_heard in the sung language, then real title+artist.")
            appendLine("Chinese example: 歌声与微笑 / 谷建芬. English example: Hello / Adele.")
        }
        if (!extraText.isNullOrBlank()) {
            appendLine("Extra user text: ${extraText.trim()}")
        }
        appendLine("ONE JSON only.")
    }

    fun imageEnvelope(extraText: String?): String = buildString {
        appendLine("The user attached an image (lyrics screenshot / music app / playlist).")
        appendLine("Read song title and artist from the image in whatever language is shown.")
        if (!extraText.isNullOrBlank()) {
            appendLine("Extra user text:")
            appendLine(extraText.trim())
        }
        appendLine("JSON only.")
    }

    fun refineEnvelope(userText: String, llmJsonOrText: String, webSnippets: String): String =
        buildString {
            appendLine("User clue:")
            appendLine(userText.trim().ifBlank { "(humming audio)" })
            appendLine()
            appendLine("Model draft:")
            appendLine(llmJsonOrText.take(800))
            appendLine()
            appendLine("Web snippets (noisy):")
            appendLine(webSnippets.take(1500))
            appendLine()
            appendLine("Final JSON with real song titles (any language). Do not force Chinese.")
        }

    fun repairEnvelope(userText: String, badOutput: String): String = buildString {
        appendLine("User clue:")
        appendLine(userText.trim().ifBlank { "(humming)" })
        appendLine()
        appendLine("Bad output (extract real songs if any):")
        appendLine(badOutput.take(1200))
        appendLine()
        appendLine("JSON only; real titles any language; never 歌名/歌手 as values.")
    }
}
