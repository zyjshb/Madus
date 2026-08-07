# 任务：去掉 AI 搜 B站识曲 + 重做通用哼唱链路

**项目：** Madus Android · `and/` · 包名 `com.madus.mobile`  
**先读：** `docs/HANDOFF_NEXT_CHAT.md` 硬约束；本文件全文  
**派工：** Codex / DeepSeek 写代码；Grok 只验收、定稿版本、打包  

---

## A. 删入口（小）

**目标：** AI 搜歌页右上角「B站识曲」删掉。推荐页左侧悬浮球**保留**（用户说推荐页已有）。

| 做 | 不做 |
|----|------|
| 去掉 `AiChatScreen` 的「B站识曲」按钮与 `onRecognizeBgm` 参数 | 不要删推荐页 `BiliRecognizeFab` |
| 去掉 `MadusRoot` 里 AI 页对 `aiVm::recognizeCurrentBgm` 的绑定 | 不要恢复旧 LLM「本片 BGM」半屏面板 |
| `AiChatViewModel.recognizeCurrentBgm` 可删或留作 dead code 清理 | 不要动普通搜索链路 |

---

## B. 哼唱重做（主任务 · 通用 · 禁止单曲 if-else）

### 用户痛点

1. 唱什么都搜不出来  
2. 以前有人只针对「反映的那几首歌」修，其它歌仍烂 → **禁止任何单曲硬编码**  
3. 目标：任意有分类的流行/华语/外语歌，哼唱或清唱都能尽量识别并落到 B 站可播  

### 根因（实现时按代码验证，不必全信）

1. **ACRCloud `data_type` 可能错：** 现实现固定 `"audio"`（指纹听原曲）。用户麦克风哼唱/清唱应优先 `"humming"`，原声片段再用 `"audio"`。建议：**humming 与 audio 都试（或并行），合并候选**。  
2. **引擎错误被吞：** `getOrDefault(emptyList())` 把 key 错、网络错、权限错都变成「未命中」。应把**引擎真实错误**展示给用户（至少 status/reply 里写清 ACR/讯飞失败原因）。  
3. **阈值过狠：** ACR 低分全丢、merge 后再砍、B 站 `minScore` 再砍 → 空结果。阈值要**通用可调**，宁多候选让用户点，不要全空。  
4. **B 站落地：** 哼唱结果应用音乐区优先 + 歌名相关度（`searchMusic` / `SongRanker`），但**不要**因相关度门槛把唯一正确歌名全滤掉；放宽兜底。  
5. **录音：** 最短时长、静音裁剪过狠会导致送引擎空/极短；检查 `HumRecorder` / 讯飞 `preparePcm` 是否误杀正常哼唱。  

### 必须实现的通用策略（不是歌名表）

1. **双引擎（有配置就跑）**  
   - ACR：`data_type=humming` 优先；无结果再 `audio`；解析 metadata 的 humming + music + custom_files  
   - 讯飞：保持 afs 哼唱；PCM 预处理失败时回退原始 WAV  
2. **合并：** 按「归一化歌名」去重；双引擎同名加分；**禁止** `if (title == "xxx")`  
3. **失败可见：** 两边都失败 → reply 写引擎错误；一边失败一边有结果 → 用有结果的，status 可提另一边失败  
4. **B 站：** 对 top 候选多 query（歌名、歌名+歌手、官方音频）；音乐区 + 全站；相关度排序；空结果时用更宽 query 再试一轮  
5. **文案：** 引导「多唱副歌 8–12 秒、环境安静」；不要提单曲  

### 禁止

- 任何单曲 if-else / 歌名白名单特判  
- 接第三方播放源  
- 改推荐算法、普通搜索、PlaybackService release/surface  
- 改密钥存储格式（可改 UI 提示）  
- 改 `docs/HANDOFF_NEXT_CHAT.md`（总管写）  
- 打包 APK  

### 建议改动范围（可增减，勿大爆炸）

```
ai/AcrCloudRecognizer.kt       # data_type humming+audio；阈值；错误信息
ai/XunfeiHummingRecognizer.kt  # 预处理别误杀；错误透出
ai/AiChatViewModel.kt          # beginHummingTurn / merge / 搜 B 站 / 删 recognizeCurrentBgm
ai/HumRecorder.kt              # 仅当确认录音过短/静音问题才动
ai/SongRanker.kt               # 仅通用打分，禁止单曲分支
ui/screens/AiChatScreen.kt     # 删 B站识曲按钮
ui/MadusRoot.kt                # 解绑 onRecognizeBgm
```

`ChineseFamousLyrics`：**不要**为哼唱加单曲；它只服务文字歌词。哼唱路径不要依赖它。

### 完成标准

1. AI 搜页看不到「B站识曲」；推荐页悬浮球仍在  
2. 哼唱：配置正确时，引擎错误能看到；有识别结果时 B 站尽量出可点条目  
3. 无任何「某首歌专用」分支  
4. 能编译：  
   ```
   $env:JAVA_HOME='C:\Users\djnio\Desktop\audio\tools\jdk17'
   $env:GRADLE_USER_HOME='C:\Users\djnio\.gradle'
   .\gradlew.bat :app:compileDebugKotlin --offline
   ```  
5. 中文 ≤10 行变更摘要；列出改了的文件  

### 版本

不要改 `versionName` / changelog / HANDOFF（总管验收后统一 1.13.6 打包）。
