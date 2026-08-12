# Madus 短视频推荐算法实施方案

> 版本：v1.0  
> 日期：2026-08-12  
> 目标：在当前「B 站候选内容 + 本地播放队列」条件下，实现接近主流短视频产品的推荐体验：刚表达喜欢时能较快响应，持续刷时不单调，停止喜欢后自然回归稳定推荐。

---

## 1. 方案结论

不要做成“点赞一次，后面全是同类”，也不要让所有兴趣等到第二天才生效。

推荐由四层兴趣共同决定：

| 层 | 作用 | 建议比例 | 生效 | 衰减 |
|---|---|---:|---|---|
| 实时兴趣层 | 刚点赞/收藏后快速响应 | 最多 15% | 数秒～数分钟 | 30 分钟 |
| 小时兴趣层 | 保持本次使用期间的连贯偏好 | 40%～50% | 分钟级 | 24 小时 |
| 每日/长期兴趣层 | 保留长期偏好，避免新兴趣覆盖旧兴趣 | 20%～25% | 天级 | 14～30 天 |
| 探索与多样性层 | 防疲劳、发现新内容 | 至少 15% | 持续 | 不依赖单次行为 |

核心约束：**实时兴趣只能软插入后续少量内容，不能替换正在播放或直接覆盖已有队列。**

这个方案的结构对应主流平台的“多路召回 → 多目标打分 → 去重/多样性重排 → 实时重排”。第一版使用可解释的规则实现；以后有足够数据时，可以将打分函数替换为机器学习模型。

## 2. 产品目标与边界

### 2.1 用户体验目标

1. 点赞或收藏后，后续 2～4 条中可以出现 1～2 条合理的相关内容。
2. 不连续刷到同一个 UP；一般候选池中不连续刷到同一主题超过两条。
3. 偶然点赞一次陌生内容，30 分钟后不会持续被强推。
4. 连续喜欢同一主题，推荐比例才逐步增加。
5. 连续快速跳过同一主题，系统会暂时降低其权重。
6. 主动播放歌单、收藏夹、搜索结果时，推荐机制不改写这些有限队列。

### 2.2 项目边界

- Madus 是客户端，不拥有全站用户行为数据，不能复刻抖音/快手的协同过滤与在线模型。
- 候选来自 B 站首页推荐、相关推荐、搜索、热门、喜欢/收藏/历史。
- 目标是复刻主流短视频的**机制和体验**，而不是宣称达到平台级模型精度。
- 已登录时，B 站官方首页推荐仍是重要候选源，因为它包含账号本身的兴趣信号。

## 3. 总体流程

```text
用户行为（点赞 / 收藏 / 播放 / 跳过）
        ↓
统一事件记录
        ↓
兴趣状态（实时 / 小时 / 长期 / 负反馈）
        ↓
多路召回 → 多目标打分 → 去重与防疲劳重排 → 推荐队列
        ↑                                      ↓
实时兴趣池 ── 小剂量软插入后续第 2～4 条 ───────┘
```

推荐有两个入口：

1. **常规刷新**：首次打开推荐页、队列不足、后台续刷。走完整候选生成、打分和重排。
2. **实时反馈**：点赞/收藏成功后。只生成一个很小的实时候选池，并软插入当前推荐流。

## 4. 统一行为事件

所有影响推荐的操作都必须写入统一事件；不要让点赞、收藏、播放逻辑各自直接改队列。

| 事件 | 初始权重 | 说明 |
|---|---:|---|
| `LIKE` | +1.00 | 点赞/喜欢；强正反馈 |
| `COLLECT_LOCAL` | +1.15 | 收藏到本地歌单 |
| `COLLECT_BILIBILI` | +1.25 | B 站收藏 API 成功后记录 |
| `PLAY_START` | +0.05 | 只记录，不直接强推 |
| `WATCH_50` | +0.30 | 播放达到 50% 或至少 30 秒 |
| `WATCH_90` | +0.60 | 接近完整播放 |
| `REPLAY` | +0.80 | 回看/重播；强兴趣 |
| `SKIP_FAST` | -0.70 | 很快主动切走 |
| `NOT_INTERESTED` | -1.50 | 未来提供“不感兴趣”按钮后使用 |

### 4.1 快速跳过的严格判定

只有同时满足以下条件，才记录 `SKIP_FAST`：

```text
1. 当前来源为 sourceId == "recommend"；
2. 用户主动切到下一条，不是自然播放结束、网络失败或后台恢复；
3. 已播放少于 max(15 秒, 视频时长的 15%)；
4. 本次会话中该视频没有被重复播放过。
```

时长未知时使用 15 秒。拖动进度、切换播放模式、播放器报错都不能记为负反馈。

### 4.2 领域模型

新增 `app/src/main/java/com/madus/mobile/domain/RecommendationModels.kt`：

```kotlin
enum class RecommendationEventType {
    LIKE, COLLECT_LOCAL, COLLECT_BILIBILI,
    PLAY_START, WATCH_50, WATCH_90, REPLAY,
    SKIP_FAST, NOT_INTERESTED,
}

data class RecommendationEvent(
    val trackId: String,
    val bvid: String,
    val type: RecommendationEventType,
    val occurredAtMs: Long,
    val sourceId: String,
    val topicKeys: Set<String>,
    val authorKey: String?,
)
```

事件保存“发生时解析到的主题和作者”，避免以后内容元数据改变时无法解释旧行为。

### 4.3 本地存储与隐私

新增 `data/RecommendationEventStore.kt`，用 DataStore 保存最近 500～1,000 条事件；超过上限删除最旧数据。行为只用于本机推荐，不上传用户历史。

时间窗口：

```text
实时：最近 30 分钟
小时：最近 24 小时
长期：最近 30 天（最少保留最近 1,000 条）
负反馈：最近 30 分钟，最多延长至 2 小时
```

现有 `LikedStore.likedAtMs` 可作为旧数据兼容来源；新代码应在点赞成功后同步写入正式事件表。

## 5. 内容画像：定义“同一类型”

只按标题关键词区分主题不够稳定。新增独立内容画像缓存：

```kotlin
data class ContentProfile(
    val trackId: String,
    val bvid: String,
    val authorId: String?,
    val authorName: String?,
    val categoryId: Int?,
    val categoryName: String?,
    val tags: Set<String>,
    val topicKeys: Set<String>,
    val fetchedAtMs: Long,
)
```

建议路径：`data/ContentProfileStore.kt`。

主题的解析优先级：

1. B 站接口返回的分区 ID/分区名；
2. 视频标签；
3. UP 主；
4. 标题、简介关键词兜底。

将主题归一化为有限集合：

```text
music, anime, gaming, dance, knowledge, life, food,
movie, sport, digital, comedy, news, unknown
```

一个视频可拥有多个主题，例如“游戏音乐”同时有 `gaming` 和 `music`。解析失败使用 `unknown`，但不能因 `unknown` 过度限制候选。

缓存策略：收到首页/搜索/相关推荐数据时即时解析；缺标签时按需补详情；同一 BVID 本会话只请求一次；缓存有效期 7 天；网络失败时标题关键词兜底且不阻塞推荐。

## 6. 四层兴趣状态

不要合成一个“喜欢分数”。至少维护：

```kotlin
data class InterestState(
    val realtimeTopics: Map<String, Double>,
    val hourlyTopics: Map<String, Double>,
    val longTermTopics: Map<String, Double>,
    val mutedTopics: Map<String, Long>, // topic -> 冷却结束时间
    val realtimeAuthors: Map<String, Double>,
    val hourlyAuthors: Map<String, Double>,
)
```

统一采用指数衰减：

```text
有效权重 = 行为基础权重 × exp(-经过时间 / 半衰期)
```

| 层 | 主要事件 | 半衰期 | 用途 |
|---|---|---:|---|
| 实时 | 点赞、收藏、重播 | 10 分钟 | 立即响应 |
| 小时 | 所有正反馈、有效观看 | 6 小时 | 本次/当天偏好 |
| 长期 | 收藏、喜欢、完整播放 | 7 天 | 稳定偏好 |
| 负反馈 | 快速跳过、不感兴趣 | 30 分钟 | 临时降权 |

主题升级规则：

| 条件 | 系统行为 |
|---|---|
| 1 次实时正反馈 | 最多插 1 条相关内容 |
| 10 分钟内 2 次同主题正反馈 | 最多插 2 条，提高实时主题分 |
| 30 分钟内 3 次同主题正反馈 | 提高小时兴趣分；常规下一批也增加 |
| 30 分钟内 2 次同主题快速跳过 | 主题冷却 30 分钟；实时池停止插入 |
| 明确“不感兴趣” | 当前会话过滤；主题/UP 至少冷却 7 天 |

这条“升级而非一次锁死”的规则，是防止信息茧房的关键。

## 7. 多路召回：先找到足够多候选

不要从单一相关推荐直接取下一条。候选源应统一汇集、去重后再打分。

| 来源 | 作用 | 初始目标数量 |
|---|---|---:|
| 实时相关推荐 | 刚点赞/收藏种子的 `relatedTracks` | 12～18 / 种子 |
| 小时相关推荐 | 近 24 小时正反馈种子的相关推荐 | 12～18 / 种子 |
| 长期兴趣 | 喜欢、收藏、常看 UP、关键词搜索 | 30～50 |
| B 站首页推荐 | 使用账号官方兴趣 | 24～48 |
| 历史关联 | 最近有效观看内容的相关推荐 | 20～40 |
| 热门/分区探索 | 打破兴趣茧房 | 20～40 |

最终队列 30 条时，候选池目标为 150～300 条。网络受限时允许降级，不应卡住播放。

### 7.1 打分前硬过滤

```text
- 当前播放、当前队列、sessionSeenIds 中已出现的内容；
- 同一 Track ID 或 BVID 的重复项；
- 不符合当前音乐/视频模式的内容；
- 明确“不感兴趣”的视频、主题、UP；
- 无法播放、失效、无 CID 的内容；
- 最近 8 条已看过内容（用户主动搜索/点播除外）。
```

## 8. 多目标打分

工业系统同时预测点击、完播、点赞、收藏、停留和长期留存。v1 用可解释的多目标分数替代：

```text
score(item) =
    2.8 × realtimeAffinity
  + 2.0 × hourlyAffinity
  + 1.2 × longTermAffinity
  + 1.0 × sourceQuality
  + 0.8 × freshness
  + 0.6 × novelty
  - 2.5 × negativeFeedbackPenalty
  - 1.5 × fatiguePenalty
  - 1.2 × repeatPenalty
```

| 子分数 | 计算含义 |
|---|---|
| `realtimeAffinity` | 与 30 分钟点赞/收藏内容的主题、UP、标签和相关推荐关系 |
| `hourlyAffinity` | 与 24 小时有效观看、喜欢、收藏的相似度 |
| `longTermAffinity` | 与长期喜欢、常看 UP、常收藏主题的相似度 |
| `sourceQuality` | 实时相关推荐 > 小时相关推荐 > 首页 > 搜索 > 热门 |
| `freshness` | 未在本会话出现、与当前队列不同的内容适度加分 |
| `novelty` | 用户很少看、但与长期兴趣弱相关的探索内容 |
| `negativeFeedbackPenalty` | 命中近期快速跳过/不感兴趣主题或 UP 时扣分 |
| `fatiguePenalty` | 与最近队列主题/作者太相似时扣分 |
| `repeatPenalty` | 已看、已排队、标题高度相似、同系列过密时扣分 |

所有权重、窗口和上限必须集中在 `RecommendationTuning`，禁止继续散落在 `AppViewModel`：

```kotlin
object RecommendationTuning {
    const val REALTIME_TTL_MS = 30 * 60 * 1000L
    const val REALTIME_STRONG_TTL_MS = 10 * 60 * 1000L
    const val TOPIC_COOLDOWN_MS = 30 * 60 * 1000L
    const val MAX_REALTIME_IN_FIRST_20 = 3
    const val MAX_SAME_TOPIC_IN_WINDOW_4 = 2
    const val MAX_SAME_AUTHOR_IN_WINDOW_4 = 1
    const val MIN_EXPLORE_RATIO = 0.15
    const val MIN_DAILY_BASELINE_RATIO = 0.20
}
```

## 9. 重排：防疲劳的最后一道关

打分排序后的前十条常常仍然很像。必须用独立重排器执行列表级约束。

| 约束 | 初始规则 | 候选不足时 |
|---|---|---|
| 同一视频 | 永不重复 | 永不放宽 |
| 同一 UP | 最近 4 条最多 1 条 | 最多放宽到 2 条 |
| 同一主题 | 最近 4 条最多 2 条 | 最多放宽到 3 条 |
| 实时同主题 | 最近 6 条最多 2 条 | 不足时才允许第 3 条 |
| 探索 | 每 6 条至少 1 条 | 候选不足可跳过 |
| 每日/长期基线 | 每 5 条至少 1 条 | 候选不足可跳过 |

实现原则：每次取“分数最高且满足约束”的候选；只有找不到时才逐级放宽软约束。绝不能直接随机打乱。

建议新增 `domain/RecommendationReRanker.kt`：

```kotlin
fun rerank(candidates: List<ScoredTrack>, context: FeedContext): List<Track> {
    val waiting = candidates.sortedByDescending { it.score }.toMutableList()
    val result = mutableListOf<Track>()
    while (waiting.isNotEmpty() && result.size < context.limit) {
        val pick = waiting.firstOrNull { candidate ->
            !violatesHardRules(candidate, context) &&
                respectsAuthorWindow(candidate, result, 4) &&
                respectsTopicWindow(candidate, result, 4) &&
                respectsQuota(candidate, result, context)
        } ?: waiting.firstOrNull { !violatesHardRules(it, context) } ?: break
        waiting.remove(pick)
        result += pick.track
    }
    return result
}
```

现有的同 UP 打散逻辑应迁移到该重排器；主题、探索配额和降级逻辑不要继续堆进 `AppViewModel`。

## 10. 实时兴趣池：快，但不打乱现有推荐

### 10.1 触发时机

在操作**成功完成**后调用：

```kotlin
recordPositiveFeedback(track, RecommendationEventType.LIKE)
recordPositiveFeedback(track, RecommendationEventType.COLLECT_LOCAL)
recordPositiveFeedback(track, RecommendationEventType.COLLECT_BILIBILI)
```

B 站收藏必须等 API 成功返回后再写入事件；不要用户一点击就假定收藏成功。

### 10.2 异步处理步骤

```text
点赞/收藏成功
  → 写入 RecommendationEventStore
  → 更新实时兴趣状态
  → IO 后台拉取该视频的相关推荐（不阻塞 UI）
  → 过滤已看、已排队、不感兴趣内容
  → 经过同一个重排器，选最多 2 条
  → 只在 recommend 无限流中软插入
```

### 10.3 软插入规则

```text
前提：当前 sourceId == "recommend"，且用户正在推荐无限流中。
位置：当前播放之后第 2～4 个未播放位置。
数量：单次最多 2 条。
限流：同一主题 10 分钟内最多插入 2 条。
上限：前 20 条推荐中的实时内容最多 3 条。
保护：不替换正在播放和已经准备播放的下一条。
禁止：歌单、收藏夹、搜索结果、用户手动创建队列不允许插入。
```

插入位置还要避开同主题和同 UP。第 2 个位置不合适时尝试第 3、4 个；仍无位置则放弃本次插入，等常规下一批推荐消化实时兴趣。

不直接放在“下一条”的原因：系统马上切成同类，会给用户强烈的跟踪感，也会打断原推荐节奏。

## 11. 与当前项目的代码映射

| 组件 | 职责 | 建议路径 |
|---|---|---|
| 行为记录 | 写入/读取推荐事件 | `data/RecommendationEventStore.kt` |
| 内容画像 | BVID → 分区、标签、UP、主题 | `data/ContentProfileStore.kt` |
| 领域模型与调参 | 事件、兴趣、候选、常量 | `domain/RecommendationModels.kt` |
| 召回和打分 | 汇总候选、计算多目标 score | `domain/RecommendationEngine.kt` |
| 重排器 | 去重、作者/主题窗口、配额 | `domain/RecommendationReRanker.kt` |
| B 站元数据 | 解析首页/相关推荐的分类、标签 | `data/BilibiliApi.kt` |
| UI 接入 | 点赞、收藏、播放、跳过后调用引擎 | `ui/AppViewModel.kt` |
| 播放信号 | 区分自然播完、切走、播放时长 | `player/PlayerController.kt` 或当前回调处 |

`AppViewModel` 只负责调用引擎、更新队列、发起协程。推荐打分、主题判断、配额判断不应直接写在它里面。

已有的喜欢/收藏/最近播放种子、首页推荐、相关推荐、热门、同 UP 打散、小时盐/每日盐、无限流续刷全部保留；它们分别迁移为召回源、兴趣特征或重排输入，而不是推倒重来。

## 12. 分阶段实施

### 阶段 A：事件与画像（不改变推荐结果）

1. 建立 `RecommendationEventStore`、事件模型、`RecommendationTuning`。
2. 点赞、本地收藏、B 站收藏成功都写事件。
3. 推荐流记录播放开始、有效观看、快速跳过。
4. 建立 `ContentProfileStore`，优先使用 B 站分区/标签。
5. 仅输出 Debug 日志，不改变队列排序。

验收：行为可正确记录；快速切歌误判率低；重启后事件可恢复。

### 阶段 B：抽离重排器

1. 把现有同 UP 打散抽离到 `RecommendationReRanker`。
2. 加主题窗口、重复过滤、探索/每日配额。
3. 先保留现有召回和分数。
4. 写纯 Kotlin 单元测试。

验收：30 条候选无重复；正常候选池中同 UP 不连刷；最近 4 条同主题不超过 2 条。

### 阶段 C：实时兴趣池

1. 点赞/收藏后异步拉相关推荐。
2. 仅对 `recommend` 无限流执行软插入。
3. 落实第 2～4 位、最多两条、主题限流规则。
4. 网络失败时静默降级，绝不影响点赞/收藏本身。

验收：不切断当前播放；后续几条可出现相关内容；一次喜欢不替换整个队列。

### 阶段 D：四层打分和负反馈

1. 实时、小时、长期、负反馈分开计算。
2. 常规下一批推荐接入多目标分数。
3. 连接快速跳过冷却；后续增加“不感兴趣”。
4. 所有权重集中配置。

验收：连续喜欢逐步增强；连续跳过明显减少同类；30 分钟后短期兴趣自然消退。

### 阶段 E：调优与可观测性

1. Debug 环境显示每条推荐的来源、分数、主题、重排/插入原因。
2. 本地记录匿名指标，不上传私密历史。
3. 根据真实体验调权重、窗口、配额。
4. 规则稳定、有数据后才考虑模型化。

## 13. 测试用例

| 场景 | 期望 |
|---|---|
| 点赞 A 类一次 | 后续 2～4 位最多插 1 条 A 类相关内容 |
| 10 分钟内再点赞 A 类 | 最多插 2 条；最近 6 条 A 类不超过 2 条 |
| 点赞 A 后喜欢 B | 两类都有机会，不连续堆 A |
| 连续快速跳过 A 两次 | A 冷却 30 分钟；实时池不再插 A |
| 候选只有一个 UP | 安全降级输出，并记录约束放宽原因 |
| 相关推荐网络失败 | 点赞/收藏正常成功，推荐不崩溃、不弹失败提示 |
| 当前来自歌单 | 不允许实时插入 |
| 当前来自 recommend | 不替换当前与下一条，只插后续 |
| 重启应用 | 正反馈事件恢复；过期实时事件不再强推 |

人工体验必须连续刷至少 30 条，分别验证：连续喜欢同主题、偶然喜欢后停止互动、连续跳过、未登录与断网降级。

## 14. 指标与调参

| 指标 | 目标 |
|---|---|
| 实时响应率 | 点赞/收藏后 3 条内出现相关内容的比例；不是越高越好 |
| 实时过量率 | 点赞后 6 条内同主题超过 2 条的比例；应接近 0 |
| 同 UP 连刷率 | 任意 4 条中同 UP 超过 1 条的比例 |
| 快速跳过率 | `SKIP_FAST / 展示`；应下降 |
| 有效观看率 | `WATCH_50 / 展示`；应上升 |
| 深度正反馈率 | `LIKE + COLLECT + WATCH_90`；比播放量可靠 |
| 探索接受率 | 探索内容发生有效观看的比例 |

调参顺序：

```text
实时响应慢：提高实时插入成功率，不先增加同主题数量。
内容太单一：先收紧主题窗口/提高探索配额，不先砍掉所有兴趣权重。
不够懂用户：提高连续正反馈后小时层的升级速度。
快速跳过增加：先检查误判、候选质量和实时来源，再调整负反馈。
```

## 15. 与主流短视频系统的关系

| 能力 | 本方案 v1 | 平台级系统 |
|---|---|---|
| 召回 | 多路 API + 本地候选池 | 海量向量召回、协同过滤、关注图谱 |
| 排序 | 可解释的多目标加权 | 训练出的多任务模型 |
| 实时性 | 本地事件 + 队列软插入 | 在线特征、实时/端侧模型重排 |
| 多样性 | 作者/主题窗口与配额 | 学习式列表重排 |
| 负反馈 | 快速跳过/不感兴趣 | 停留、滑走、屏蔽、举报等综合信号 |
| 长期目标 | 每日基线与探索 | 留存、满意度、长期价值模型 |

后续演进顺序必须是：事件质量与内容标签 → 规则调优 → 多目标模型 → 向量召回/复杂长期优化。没有可靠事件和画像时，机器学习只会更快地制造信息茧房。

## 16. 最终交付清单

- [x] 点赞、两类收藏、播放、有效观看、快速跳过都记录为统一事件。
- [x] 内容画像优先使用 B 站分区/标签，标题关键词只做兜底。
- [x] 常规推荐由独立 `RecommendationEngine` 生成。
- [x] `RecommendationReRanker` 限制重复、同 UP、同主题，并保持探索/每日基线配额。
- [x] 点赞/收藏后异步软插入，不替换当前播放和下一条。
- [x] 前 20 条中实时内容不超过 3 条；最近 6 条实时同主题不超过 2 条。
- [x] 连续快速跳过触发主题冷却。
- [x] 权重、时长、配额集中在 `RecommendationTuning`。
- [x] 单元测试覆盖第 13 节场景。
- [x] Debug 环境能查看每条推荐的来源、分数与重排原因。

## 17. 最终行为示例

用户正在刷推荐，点赞了一条“动漫翻唱”内容：

```text
当前：普通推荐（不打断）
下一条：原本队列内容（不替换）
第 2～4 条：最多插入 1 条动漫翻唱相关推荐
后续：仍穿插每日基线和探索内容

若 10 分钟内又收藏一条动漫翻唱：
最多再加入 1 条同类；下一批常规推荐提高该主题的小时兴趣权重。

若随后连续快速跳过两条动漫翻唱：
该主题冷却 30 分钟，实时池停止插入，常规排序也同步降权。
```

最终体验应该是：**系统会跟着用户当下兴趣变化，但会主动换气，不让推荐变成机械的同类循环。**
