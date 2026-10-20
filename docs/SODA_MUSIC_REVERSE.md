# 汽水音乐（Soda Music PC 3.8.0）推荐算法逆向报告

> 目标文件：`C:\Users\djnio\AppData\Local\Programs\Soda Music\SodaMusicLauncher.exe`
> 实际研究对象：`Packages\3.8.0\main.asar`（渲染进程，Vue 3 + Vite）与 `app.asar`（Electron 主进程）
> 方法：asar 解包 + `.js.map` 的 `sourcesContent` 还原出 **900 + 646 + 362 + 17 个原始 TS/Vue 源文件**
> 结论先行：**推荐排序 100% 在服务端（`/luna/flow/*`、`/luna/feed/*`）。客户端不含任何本地打分/召回代码。**
> 因此"逆向推荐算法机制"能拿到的、且真正有价值的资产是：**特征契约（客户端往服务端送什么）、
> 反馈设计（哪些行为被当成正/负样本、强度如何）、会话与探索控制（模式、曝光去重、刷新节律）、
> 以及冷启动链路**。本报告把这四件事逐条钉死，并给出可移植到本地的等价实现。

---

## 0. 取证与证据链

| 步骤 | 命令/产物 | 结果 |
|---|---|---|
| 解包 | `npx @electron/asar extract main.asar` | `main.html` + `assets/`（145 个文件，34.3 MB） |
| 解包 | `npx @electron/asar extract app.asar` | `entry.js` / `main.js`（各 98 B，真逻辑在 `.node` 与 `main.js.map`） |
| 还原源码 | 解析 `main-DlDxdmOi.js.map`（9.79 MB，含 `sourcesContent`） | **900** 个原始 TS/Vue 文件 |
| 还原源码 | 解析 `app/main.js.map`（8.67 MB） | **646** 个原始文件（主进程 + 全部 IDL 契约） |
| 还原源码 | 解析 `renderer-bLP9ux3C.js.map` / `player--gLSDaJm.js.map` | 362 / 17 个文件 |

关键结论的代码出处（下文所有引用均指还原出的原始路径）：

- `src/services/request/methods.ts` —— 客户端能调的 **74 个**接口白名单
- `src/idl/goapi/contract/{feed,discover,media,track,dislike,flow_contract}.ts` —— 请求/响应字段契约
- `src/services/queue/items/{feed,feedmode}.ts` —— 推荐流的两条主通道
- `src/services/queue/queueItemUnlimited.ts` —— 上下文窗口（played_media）如何组装
- `src/services/queue/queueLoader.ts` —— 预取与去重
- `src/services/player/reporter.ts` —— 播放行为埋点的完整分类学
- `src/renderer/compositions/sceneMode.ts` —— 听歌模式 / 发现页的刷新节律
- `src/services/applog/applog.ts` —— 埋点上报机制（MCS/TEA）

---

## 1. 系统总览：三条召回通道 + 一条上下文环

```
                     ┌────────────────────────── 客户端 ──────────────────────────┐
  用户行为 ──► 播放记录(100条) ─┐                                              │
             埋点(audio_over)  ├─► getMediaPlayed() 30 条窗口 ─┐                 │
             显式反馈(dislike)  ┘                              │                 │
                                                            ▼                 │
                    ┌──────────────── 服务端（黑盒） ─────────────────┐          │
                    │  /luna/feed/song-tab     ← 日推/推荐流          │          │
                    │  /luna/feed/radio/tracks ← 电台（按 radio_id）  │          │
                    │  /luna/flow/recommend    ← 意图流（可返回score）│          │
                    │  /luna/media/related     ← 相似（种子=曲目/歌手）│          │
                    │  /luna/discover + /mix   ← 发现页卡片流          │          │
                    └───────────────┬───────────────────────────────┘          │
                                    ▼                                          │
              items[] + predict_info + status_info.log_id ──► 队列(≤N) ─────────┘
                                    │
                                    ▼
                       播放 → 记录 playTimeMs → 下一次请求的 played_media
```

**核心闭环**：客户端不做排序，只做三件事——
1. **喂上下文**：`played_media`（最近 30 条，含待播与实听毫秒）
2. **喂反馈**：`Dislike`（曲目 + 歌手）、`CollectTracks`、`boost`、`preference_mode`
3. **喂状态**：`feed_counts.mix_session_count`（会话深度）、`did_first_use_time`（首用时间）、
   `exposure_radio_list`（已曝光的卡片）、`first_request`（当日首次）

服务端 `status_info.log_id` 回传为下一次请求的 `source_request_id`，形成**曝光-反馈归因链**。

---

## 2. 特征契约：客户端到底送了什么（这是逆向最值钱的部分）

### 2.1 `played_media` —— 30 条滑动上下文窗口

`src/services/queue/queueItemUnlimited.ts:23-70`

```ts
async getMediaPlayed(): Promise<MediaPlayed[]> {
  const MAX_COUNT = 30
  ...
  // 1) 待播歌曲（逆序）：played_mills = -1  ← 关键：未播曲目用 -1 标记
  const unplayed = currentPlayableKeys.slice(currentTrackIndex + 1).reverse()
      .map(key => ({ type, duration, media_id, played_mills: -1 }))

  // 2) 当前 + 历史播放记录（逆序，最近在前）：played_mills = 实听毫秒
  const played = records.slice().reverse().map(r => ({
      type: r.mediaType, duration: ..., media_id: r.mediaId, played_mills: r.playTimeMs }))

  const remain = Math.max(0, MAX_COUNT - unplayed.length)
  return [...unplayed, ...played.slice(0, remain)]
}
```

协议（`src/idl/goapi/entity/media.ts:171-185`）：

| 字段 | 类型 | 语义 |
|---|---|---|
| `media_id` | string | 曲目 ID |
| `type` | string | `track` / `video_track_mix` / `mv` … |
| `played_mills` | int | **实听毫秒**；`-1` = 队列中尚未播放 |
| `duration` | int | 总时长（秒）；`MediaAttr.duration` 可选 |
| `sim_id_v3` | int | 播放相似度/同源标记（客户端可选填） |
| `play_timestamp` | int | 播放时间戳 |

**设计要点**：`-1` 让服务端能区分"用户即将听到"与"用户已经听过"，
从而把"下一首"候选与"已消费"历史放进同一个 30 条窗口里做**会话级去重与续推**。
`duration + played_mills` 让服务端直接算出**完播率**，无需额外字段。

### 2.2 `audio_over` 埋点 —— 行为样本的完整分类学

`src/services/player/reporter.ts`。这是把"播放"变成训练样本的地方：

**事件**：`audio_play` / `audio_over` / `audio_finish`（视频为 `video_*`）+ `progress_bar_move` / `volume_change` / `play_mode`

**`audio_over_status`（离场原因，13 种）**：

| status | 触发 | 推荐语义 |
|---|---|---|
| `finished` | 自然播完 | 强正样本 |
| `next` | 用户点下一首 | 弱负（"不是现在") |
| `previous` | 用户回退 | 强负（比 next 更差） |
| `hotkey_next` / `hotkey_previous` | 键盘 | 同上，意图更明确 |
| `change_track_reco` | 切到推荐流（换算法通道） | **强负**：直接否定当前推荐分布 |
| `change_queue` | 切到别的队列/歌单 | 中负：否定场景 |
| `shift` | 从队列面板点选 | 中性（主动选择） |
| `pause` | 暂停 | 中性 |
| `foreground_focus` / `foreground_minimize` | 切回前台 | 中性，用于唤醒 |
| `background_lose_focus` / `background_minimize` | 转后台 | 中性，但用于**注意力折扣** |

**每条样本附带的特征**（同一 `audio_finish` 事件）：

```ts
duration_pct: timeCalc.getPlayingTimeMs() / (durationSeconds * 1000)  // 完播率
duration:     timeCalc.getPlayingTimeMs()                             // 实听毫秒
foreground_dur: timeCalc.getForegroundMs()                            // 前台时长
background_dur: timeCalc.getBackgroundMs()                            // 后台时长
is_lyrics_move:       isMediaSeeked ? 1 : 0                            // 是否手动拖过进度(歌词)
is_progress_bar_move: isMediaSeekedByLyrics ? 1 : 0                    // 是否拖过进度条
```

以及 `createMediaCommonEtParams()` 里的：

- `play_action_type`：`auto_play` / `next` / `previous` / `click_list_item` / `click_button_all` /
  `reco_manual_play` / `reco_auto_play` / `hotkey_next` / `hotkey_previous`
  —— **区分"算法自动播"与"用户主动点"**，是把隐式反馈转成显式意图的关键
- `media_start_play_mode`：`loop` / `single` / `shuffle`（单曲循环 = 强正）
- `queue_play_index`、`queue_type`、`page`、`scene_name`、`request_id`（=上一跳的 `log_id`）
- `is_red_collected`、`is_preview`、`risk_result`、`track_quality`

**设计要点**：
1. **完播率 + 绝对秒数 + 前后台时长** 三件套，区分"听完但没在看"与"有在听"。
2. **离场原因**编码了负反馈强度，`change_track_reco` 比 `next` 强得多。
3. **主动/被动播放**由 `play_action_type` 分离——同一首歌"用户搜来听"和"算法推来听"权重不同。

### 2.3 播放记录存储（本地）

`src/services/player/player.ts:727-808`

```ts
PlayRecord = { queueId, mediaId, mediaType, startTime, endTime,
               durationMs, playTimeMs, lastProgressSeconds }
records: PlayRecord[]        // 环形保留最近 100 条 (line 761)
getTrackPlayCountMap(trackId): { [600]: n, [3600]: n, [86400]: n }   // 10min / 1h / 1d 重复计数
```

**设计要点**：三档重复计数窗口正是"防单曲轰炸"的直接依据——
`600s` 内重播 = 主动喜欢；`3600s` 内 ≥2 次 = 上升；`86400s` 内 >N 次 = 疲劳。

### 2.4 其它请求侧画像

| 字段 | 出处 | 语义 |
|---|---|---|
| `did_first_use_time` | `queue/items/feed.ts:27-33` | 设备首次使用时间（秒），冷启动锚点 |
| `is_first_request` | 同上 | 本次会话首次请求 |
| `is_did_first_request` | 同上 | **设备史上首次请求** → 走纯冷启动链路 |
| `feed_counts.mix_session_count` | 同上 | 本次会话第几次拉流——会话越深越该降相似度 |
| `feed_preference.preference_mode` | `queue/items/feedmode.ts:23` | `familiar` / `fresh` / 或 `scene_mode_id` |
| `total_request_count` / `preference_mode_request_count` | `contract/feed.ts:302-303` | 全量/模式内的累计请求数（探索退火用） |
| `exposure_radio_list` | `compositions/sceneMode.ts:189` | 已曝光卡片 `inner_block_id` 列表 → 防重复卡片 |
| `first_req_time` | `contract/feed.ts:384` | 相似推荐会话内保持一致（同一批种子不漂移） |
| `first_request` / `pc_new_discover_request` | `compositions/sceneMode.ts:152-156` | 当日首次发现页刷新 |
| `selected_boost` | `contract/feed.ts:296` | 显式加权：`{artists, genres, langs, source_artists, new_showed_artists, all_selected_artists}` |
| `device_profile` | `contract/feed.ts:313` | 设备画像（机型/音质/网络/音量） |

---

## 3. 反馈设计：正/负样本怎么产生的

### 3.1 "少推荐"（Dislike）—— 唯一的显式负反馈

`src/renderer/utils/dislike.ts` + `collect` 组合：

```ts
POST /luna/dislike
{ item_id, author_ids: artistIds[], action_type: 0|1, dislike_item_type }
// action_type: 0=dislike, 1=cancel_dislike
// dislike_item_type: Track | MV | UgcVideo | UgcClip
```

调用点 `src/renderer/layouts/BottomPlayerNew.vue:266-329`：

1. 先登录校验
2. 成功后 **toast「操作成功，将减少此类歌曲推荐」+ 3 秒撤销**
3. **立即 `playNext(PlaySwitchType.userNext)` 跳过**
4. 撤销 = `playPrevious` + `cancel_dislike(action_type=1)`

另有 `POST /luna/track/dislike`（`contract/media.ts:323`），body 只有 `{ track_id, artist_ids }`，
是"曲目维度"的负反馈，两者并存说明**曲目负反馈与歌手负反馈分开建模**。

**设计要点**：
- 负反馈的作用域是**"这一类"（歌手 + 曲目）而不是"永不出现"**（toast 文案即证据）
- **即时撤销**是数据质量的关键：用户误触的样本必须能撤回，否则标签噪声爆炸
- **dislike 后立刻跳过**——负反馈要立刻在体验上兑现，否则用户不点

### 3.2 正反馈

| 通道 | 接口 | 强度 |
|---|---|---|
| 收藏（红心） | `CollectTracks` | 强 |
| 收藏歌单/专辑/歌手 | `CollectPlaylists/Albums/Artists` | 中 |
| 单曲循环 | `media_start_play_mode = single` | 强 |
| 重复播放（10min/1h 内） | `getTrackPlayCountMap` | 强 |
| 自然播完 | `audio_over_status = finished` | 中 |
| 拖回进度 | `progress_bar_move` 反向 | 强 |
| 主动搜索后点播 | `play_action_type = click_list_item` + 搜索上下文 | 强（意图） |

### 3.3 Boost —— 显式正向加权（含冷启动口味选择器）

- `POST /luna/boost/artists`
  body：`{ req_no, new_showed_artists[], all_selected_artists[], source_artists[], selected_genres[], selected_langs[] }`
  响应：`artists[]` —— **"选歌手/选曲风/选语种"的引导式冷启动**
- `POST /luna/me/boost` —— 会话中的持续加权
- `LaunchDeviceResponse.selected_taste_builder` —— 服务端下发"是否展示口味构建器"开关

---

## 4. 会话 / 探索控制（这是"配方"里最像算法策略的部分）

### 4.1 听歌模式（场景模式）：熟悉 / 新鲜

`src/renderer/compositions/sceneMode.ts:19-38`

```ts
feedModeTop2 = [
  { type:'scene_mode', text:'熟悉模式', entity:{ feed_scene_mode:{ sub_queue_type:'familiar' } } },
  { type:'scene_mode', text:'新鲜模式', entity:{ feed_scene_mode:{ sub_queue_type:'fresh'   } } },
]
```

- 服务端 `GET|POST /luna/feed/mode` 再下发最多 **6** 个模式（`slice(0, 6)`），前端拼成 8 个入口
- 每个模式 = 一条独立队列（`FeedModeQueueItem`，key = `feedMode_${sub_queue_type}`），
  **独立队列缓存、独立播放历史、独立 feed 请求**
- 请求时只传 `feed_preference: { preference_mode }` 或 `{ scene_mode_id }`

**设计要点**：把"探索/利用"这个超参**外包给用户**（熟悉=利用，新鲜=探索），
同时保留服务端下发新模式的扩展位。这比内部调 ε-greedy 更可控、可解释。

### 4.2 刷新节律

| 位置 | 规则 | 出处 |
|---|---|---|
| 发现页（听歌模式页） | **每天 5:00 后首次访问才刷新**，其余走缓存 | `isFirstVisitAfterFive()` line 66-76 |
| 发现页缓存 | `memoryCacheSeconds = 7 天`，`diskCache = true` | line 156-160 |
| 发现页停留刷新 | `stayRefreshIntervalSeconds = 300` | `pages/Discover.vue:40` |
| 发现页 tabs | `refreshOnFocus: 'only-no-cache'` | 同上 |
| 每日推荐队列 | 队列缓存 `expireType = day`（缓存在 24h 后过期） | `queueLoader.ts:288-290` |
| 每日推荐队列 | `onEachDay(refresh, { randomSeconds: 3600 })` | `queue/items/feed.ts:19-23` |
| 队列缓存内容 | 只存**最后 50 首** + 游标 + `lastPlayedKey` | `queueLoader.ts:262-279` |

**设计要点**：
1. **日更 + 5 点切换**（中国音乐 App 的行业惯例：跨夜听歌算前一天）
2. **随机抖动 0~1 小时**，避免全量用户在 5:00 同时打服务端 —— 一个很实用的工程手法
3. 缓存**带播放位置**（`lastPlayedKey`），恢复时可续播

### 4.3 曝光去重（卡片流）

`compositions/sceneMode.ts`：每次响应把 `inner_block_id` 累积进 `curExposureDiscoverDataList`，
下一次请求作为 `exposure_radio_list` 回传；loadmore 时也带上（`SceneMode.vue:79`）。

**设计要点**：卡片流（歌单/电台）比单曲更怕重复；**客户端累积曝光集**是最省事、最可靠的做法。

### 4.4 预取与去重

`queueLoader.ts`：

```ts
const DEFAULT_LOADMORE_THRESHOLD = 6          // 距队尾不足 6 首则预取
// 旧机制：currentIndex >= len - 3 时预取
playables = raw.filter(t => !checkPlayableUnplayable(t))
                 .filter(t => !state.playables.byKey[t.key])   // 与已有队列去重
```
- 首屏失败不阻塞（`firstError` / `moreError` 分离）
- `loadNextImmediately` 允许服务端"立刻再拉一页"

### 4.5 意图流（FlowRecommend）—— 服务端会返回原始分数

`src/idl/goapi/flow/flow_contract.ts`

```ts
FlowRecommendRequest  = { channel, played_media[], count, flow_preference? }
FlowPreference        = { user_query, mcc_intention, scene_id, related_info?, search_result?,
                          req_mode, only_want?: FilterInfo, not_want?: FilterInfo, mcc_mode }
FilterInfo            = { track_ids[], artist_ids[], album_ids[], languages[] }   // 硬过滤
FlowRecommendNotPackResponse.items = [{ id: number, score: number }]              // ← 原始打分
```

**这是全客户端**唯一能看到"分数"的地方（`TrackItem {id, score}`）。
`only_want` / `not_want` 是**硬约束过滤器**（"只想听" / "不想听"），
与软性的 boost/dislike 构成三级干预：软加权 → 硬过滤 → 出局。

---

## 5. 冷启动链路（完整还原）

```
LaunchDevice
  └─ selected_taste_builder: bool        ← 服务端决定是否弹口味选择器
     ├─ true → POST /luna/boost/artists { selected_genres[], selected_langs[], selected_artists[] }
     │           → 逐轮返回 artists[] 供继续勾选（req_no 递增）
     └─ false → 直接进 feed

FeedSongTab 首次请求
  ├─ is_did_first_request = true         ← 设备史上第一次
  ├─ did_first_use_time = now            ← 落库（StoragePartitionId.status）
  ├─ is_first_request = true
  └─ played_media = []                   ← 空上下文

新设备判定
  └─ GET|POST /luna/launch → device_status: NewDevice(3) | Invited(1) | Normal(0)
```

**设计要点**：冷启动不是"随机推"，而是
**先用 3 个维度（曲风/语种/歌手）做一次显式选择，再用 `did_first_use_time` 做时间锚，
之后立刻转入隐式学习**。首屏没有历史也要给出可解释的理由。

---

## 6. 与"客户端算法"的边界（避免误判）

我检索了全部还原源码，**未发现任何本地打分、向量、协同过滤、Embedding、RNN/Transformer 推理**：

- 无 `score` 计算（除读服务端返回）
- 无 `cosine` / `embedding` / `vector` / `dot` 之类计算
- 无本地 ML 模型文件（`assets` 内只有图片/emoji/wasm 音频调音器 `byte_audio_tuner_wasm`）
- 唯一的本地"排序"是 `shuffleTracks()`（`queueItem.ts:127-137`，纯 Fisher-Yates）与
  `reorderTracks()`（仅 shuffle 模式生效）

> 结论：汽水音乐的推荐是**服务端重排 + 客户端特征上报**的经典工业架构。
> 我们能"逆向"到的是**它的特征协议与反馈工程**，而不是它的排序公式。
> 好消息是：对 Mineradio 这种本地/轻服务端 App，
> **照着它的特征协议 + 反馈设计 + 会话策略重写一套本地可解释打分**，
> 效果与可维护性都远好于猜它的 LTR 权重。

---

## 7. 接口全景（推荐相关，共 22 个）

| 路径 | 方法 | 作用 | 关键入参 |
|---|---|---|---|
| `/luna/feed/song-tab` | POST | **日推主通道** | `played_media[30]`, `feed_counts`, `feed_preference`, `selected_boost`, `did_first_use_time` |
| `/luna/pc/feed/song-tab` | POST | PC 变体 | 同上 |
| `/luna/feed/mode` | GET/POST | 听歌模式列表 | `device_profile`, `ab_param` |
| `/luna/feed/mode/guidance` | POST | 模式引导答疑 | `scene`, `disliked_media[]` |
| `/luna/feed/radio/tracks` | POST | 电台流 | `radio_id`, `cursor`, `flow_type`, `played_media` |
| `/luna/feed/playlist-media` | POST | 歌单内推荐填充 | `type`, `count`, `played_media`, `feed_session_id` |
| `/luna/feed/playlist-square` | POST | 歌单广场 | `category_id`, `includes[]` |
| `/luna/feed/video-tab` | POST | 视频流 | `played_media`, `feed_video_type` |
| `/luna/feed/tlb-fallback` | POST | **降级兜底流**（TLB=fallback 静态内容） | 无 |
| `/luna/feed/report-info` | POST | 上报推荐信息（排查/反馈用） | `info_type`, `content` |
| `/luna/flow/recommend` | POST | 意图推荐流 | `channel`, `flow_preference` |
| `/luna/flow/recommend/not_pack` | POST | **返回裸 `{id,score}`** | 同 `FeedSongTabRequest` |
| `/luna/media/related` | POST | 相似曲目 | `media_list`, `artist_ids`, `req_count`, `first_req_time`, `search_query` |
| `/luna/track/related` | POST | 相似曲目（track 维度） | `track_ids`, `artist_ids` |
| `/luna/playlist/related_media` | POST | 歌单相似 | `playlist_id`, `req_count` |
| `/luna/discover` | POST | 发现页区块 | `selected_boost`, `first_request`, `pc_new_discover_request` |
| `/luna/discover/mix` | POST | 发现页卡片流 | `block_type`, `exposure_radio_list[]`, `cursor` |
| `/luna/boost/artists` | POST | 口味选择器/加权 | `selected_genres/langs/artists` |
| `/luna/me/boost` | POST | 持续加权 | — |
| `/luna/dislike` | POST | 少推荐（可撤销） | `item_id`, `author_ids[]`, `action_type`, `dislike_item_type` |
| `/luna/track/dislike` | POST | 曲目负反馈 | `track_id`, `artist_ids[]` |
| `/luna/reco_sample` | POST | 推荐采样回传（PC 端未启用） | `samples[]` |

---

## 8. 可直接借鉴的 7 个工程决策

1. **上下文窗口 = 30，且未播用 `played_mills = -1`** —— 极省字段地表达"已听/将听"，服务端一次拿到会话全貌。
2. **负反馈 = "少推荐"而非"不喜欢"** —— 作用域限于歌手/曲目簇，3 秒可撤销，立即跳过兑现。
3. **熟悉/新鲜二档外包给用户** —— 比内部 ε-greedy 更可解释，且能做 A/B。
4. **日更 + 5:00 + 0~1h 随机抖动** —— 行业惯例 + 削峰，两行代码。
5. **曝光集累积回传** —— 卡片流防重的标准解。
6. **播放记录 100 条 + 三档重复计数（10min/1h/1d）** —— 疲劳与真爱的统一度量。
7. **三级干预梯度**：软加权（boost）→ 硬过滤（`only_want`/`not_want`）→ 出局（`dislike`）。
