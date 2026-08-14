# Madus 手机版 · 核心记忆（下机交接）

**日期：** 2026-08-15  
**目录：** `Mineradio-main/and/`（包名 `com.madus.mobile`）  
**当前版本：** `1.16.6` / versionCode `285`  
**正式包：** `and/apk/Madus-1.16.6.apk`  
**GitHub：** https://github.com/zyjshb/Madus  
**Gitee：** https://gitee.com/dikoklhf/madus

---

## 0. 新对话开场

1. **先读本文**  
2. 中文、少废话、真机优先  
3. 接近 **450–500k token** 再开新对话，**必须带本文档**  
4. 关机前更新本文档  
5. **GitHub / Gitee Releases 只上传正式 APK，禁止 `*-debug.apk`**  
6. **文案忌 AI 味**：不要「像安卓旧版…」「玩笑」「端侧直连」这类说明书腔；界面短句即可  
7. **改完即发双仓**（用户 2026-08-15 再确认：打包发布这些以后都自动）：打正式包 → push GitHub/Gitee → 双仓 Release。只有用户说「先别传」才停  

---

## 1. 双仓发版（固定流程）

| 项 | 说明 |
|----|------|
| GitHub | `origin` → `zyjshb/Madus`，默认 `main` |
| Gitee | `gitee` → `dikoklhf/madus`，应用内更新 **优先 Gitee**，失败再 GitHub |
| 打包 | `JAVA_HOME=...\audio\tools\jdk17`；`.\gradlew.bat :app:assembleRelease` |
| 拷贝 | `apk\Madus-x.y.z.apk` |
| GitHub 上传 | `gh release create vX.Y.Z apk\Madus-X.Y.Z.apk --repo zyjshb/Madus --notes-file scripts\release-notes-X.Y.Z.md` |
| Gitee 上传 | 本机用户环境变量 `GITEE_TOKEN`；`.\scripts\publish-gitee-release.ps1 -Version X.Y.Z` |
| 合并脚本 | `scripts\publish-release.ps1`（有 token 时顺带 Gitee；**中文编码常炸** → 手动 `git push` + `gh release create` + `publish-gitee-release.ps1`） |
| 禁止 | Release 说明里写「正式版 / 无 debug」 |

**JDK：** `C:\Users\djnio\Desktop\audio\tools\jdk17`  
**SDK：** `local.properties` → `sdk.dir=...\audio\tools\android-sdk`

---

## 2. 应用内更新（AppUpdate）

- 探测：Gitee `/latest` + **列表分页扫全量**（最多 4 页 × 100），再与 GitHub `/latest` + list 合并，`pickHighestRelease` 按语义化版本取最高  
- **禁止** `releases?per_page=5` 取 `arr[0]`：Gitee 列表按 tag **字符串**排（或 id 升序），第一条常是 `v1.14.1`，不是最新  
- **Gitee `/releases/latest` 偶发滞后**：不是按版本号取最高。新包（≥1.14.43）已扫全量列表，不靠它当唯一真相  
- **1.14.40 旧包**只信 `/latest`；失败才拿列表第一条（字符串序常是 `v1.14.1`）才会卡死  
- **发版后必须确认** `GET .../releases/latest` 已是新 tag（`publish-gitee-release.ps1` 会轮询）。没跟上就去网页把该 Release 设为最新。1.14.48 发完时 `/latest` 已是 **v1.14.48**  
- 只有 `/latest` 仍停在旧版、且 GitHub API 不通时，才需要网页手装
- 下载：Gitee 优先，失败换 GitHub  
- 校验：ZIP / AndroidManifest / dex；装在 `files/updates/`  
- **启动时** `AppUpdate.cleanupDownloadedApks()` 清掉已下的 `.apk`/`.part`  
- 更新页：进度条；**Gitee / GitHub 下载入口始终显示**（检测失败 / 已是最新也能去网页）  
- 「我的」进页静默探测，有新版写「有新版本 vX.Y.Z」；远端比本机还旧时提示「可到网页确认」  
- 单测：`app/src/test/java/com/madus/mobile/data/AppUpdateTest.kt`  

---

## 3. 近期功能定稿（1.14.x）

| 项 | 结论 |
|----|------|
| **网络使用四档** | 播放设置：**最省 / 均衡 / 流畅 / 充足**，默认 **均衡**；只调预取/推荐续刷/边听写盘，**不删功能** |
| **最省** | 预取 0；后台不主动续推荐；永不写边听缓存；打游戏优先 |
| **均衡** | 前/后台预取 1；后台剩 ≤1 才轻量补；后台不写盘 |
| **流畅** | 前台预取 2；后台预取 1；补歌更勤；可写盘 |
| **充足** | 前台 3 / 后台 2；推荐全量接口；最吃网 |
| **游戏听歌** | 「打游戏时继续播放」默认开；旧「游戏轻量」与 **最省** 同步 |
| **缓冲** | 官方 `DefaultLoadControl` 约 **12–20s**（勿自封装 LoadControl——曾导致启动即崩） |
| **导入** | 每批 **500**，可「继续添加」下一批到同一本地歌单；解析上限 PARSE_CAP≈5000 |
| **二次进歌单误触** | 手指抬起安静 + 约 0.45s 门闩才允许播放全部/点曲 |
| **彩蛋** | 画板定稿；关于连点 7 次；与外观设置拆开 |
| **品牌 logo** | 源图 `logo/h_logo.webp`（白蛇 + 播放键 + 声波，底约 `#1F2121`）；开屏 / 桌面图标同步 |
| **桌面图标大小** | 自适应图标内容约 **70%** 画布（居中留白）；用户嫌大后再调，别回满铺 100% |
| **开屏** | 系统 Splash 透明占位 + `windowBackground=splash_logo`；Compose `BrandSplash`：轻放大渐入 → 定格 → 渐出；底色 `#1F2121` |
| **普通搜索** | **对齐 B 站网页**：`order=totalrank`、每页 **42**、优先 **WBI** `/x/web-interface/wbi/search/type`；滚到底 **loadMore**；全站视频不过度阉割 |
| **推荐刷新** | 见 §12。只有**真正播过**才进 `sessionSeenIds`；点赞重排后续队列 |
| **听歌默认音质** | **较高**（High / qn=80）+ 音效 **精听**。旧「标准+原声」一次性迁过去。省流仍可选 |
| **迷你条点击** | 简约：**回推荐电台台面**。液态：**进 `NOW_PLAYING`**。不要两套都改成同一条 |
| **视频上下滑** | 滑走再滑回从 `sessionPositions` 续播；听歌切歌仍从头 |
| **评论图** | 可点全屏放大，多图左右翻（`CommentsSheet`） |
| **改完即发** | 修完 bug 自动升版打正式包推双仓（用户说「先别传」时暂停） |

---

## 4. 长期产品约定（勿回退）

| 项 | 结论 |
|----|------|
| 音源 | 仅 B 站 + 演示 |
| UI | 线稿 / 主题可切换 |
| 普通搜索 | 全站视频；分页对齐 B 站；不做过严阉割 |
| AI 搜 | 文字/图/上传；无「B站识曲」入口（推荐页悬浮球） |
| 哼唱 | ACR + 讯飞；无硬编码歌名 |
| 外站导入 | 高召回；分批 500 |
| 队列 | 整表 + 拖拽 |
| 协议 | `LegalPrefs.CURRENT_VERSION` |

---

## 5. 关键路径

```
app/src/main/java/com/madus/mobile/
  domain/RecommendationModels.kt   # 事件/画像/兴趣/调参模型
  domain/RecommendationEngine.kt   # 四层兴趣打分
  domain/RecommendationReRanker.kt # 同 UP/主题窗口 + 探索/每日配额
  data/RecommendationEventStore.kt # 本地统一行为事件（上限 1000）
  data/ContentProfileStore.kt      # BVID 分区/标签/主题缓存
  data/BilibiliApi.kt          # searchPage / WBI 搜索分页；ensureGuestCookies
  data/PlayerPrefs.kt          # NetworkIntensity 四档 + 音质 High + 精听迁移
  data/AppUpdate.kt            # probeLatest / pickHighestRelease；禁止 list[0]
  data/ExternalPlaylistImporter.kt  # BATCH_SIZE=500
  player/PlayerEngine.kt       # DefaultLoadControl 12–20s
  player/AudioFxController.kt  # 精听 Equalizer + LoudnessEnhancer
  ui/AppViewModel.kt           # 推荐/续播/取流；sessionSeen 只在 playIndex 写入
  ui/components/CommentsSheet.kt  # 评论图全屏预览
  ui/splash/BrandSplash.kt     # 开屏动画 + logo_madus
  ui/screens/SearchScreen.kt   # 滚到底加载更多；显示已加载条数
  ui/screens/MeScreen.kt       # 进页静默探测更新
  ui/screens/UpdateScreen.kt   # 网页下载入口始终显示
  ui/screens/PlaybackPrefsScreen.kt
  ui/screens/PlaylistDetailScreen.kt # 进页播放门闩
  MainActivity.kt              # 系统 Splash + shellBg #1F2121
app/src/main/res/
  drawable-nodpi/logo_madus.png
  drawable/splash_logo.xml
  mipmap-*/ic_launcher*.png    # 内容约 70% 画布
  values/colors.xml            # splash_bg / ic_launcher_background = #1F2121
logo/h_logo.webp               # 品牌源图
scripts/publish-gitee-release.ps1
```

---

## 6. 发版检查清单

1. 改 `versionCode` / `versionName`  
2. 更新 `AppChangelog` + `CHANGELOG.md` + `scripts/release-notes-x.y.z.md`  
3. `assembleRelease` → `apk/Madus-x.y.z.apk`  
4. `git push origin main` + `git push gitee main:main`  
5. `gh release create` + `publish-gitee-release.ps1`  
6. 更新**本文档**  

---

## 7. 已知注意 / 坑

- **禁止自封装 LoadControl**（Media3 漏接口 → 启动即崩）；1.14.34 已改回官方  
- `publish-release.ps1` 偶发中文编码解析失败 → 手动 push + gh + gitee 脚本  
- Gitee 网页默认分支可能仍是 `master`，代码主推 `main`  
- 勿提交 `GITEE_TOKEN`、勿传 `*-debug.apk`  
- 根目录未入库杂项：`别人的提示词.md`、`动漫宣传片-早班车的一只耳机/`（按需单独管）  
- **搜索翻页**无 WBI / 无 cookie 时 B 站易 **412**；必须走 `signWbiQuery` + `mergedCookieWithSystem`  
- **桌面图标**原图本身有大留白，再缩比例时别裁掉边再放大（会显大）；当前是**整图 70%** 贴画布  
- 换图标后部分启动器会缓存旧图 → 用户侧卸载重装或清启动器缓存  
- **Gitee `/releases/latest` 偶发滞后**，PATCH `make_latest` 会 406；新包必须扫列表取最高版本  
- **1.14.40 旧包**：只信 Gitee `/latest`。发版脚本会校验；`/latest` 跟上就能应用内升，滞后才手装  
- **听歌切歌** `startPos=0`；**视频上下滑** `startPos=-1` 读记忆。别再给视频写死从头  
- **sessionSeenIds** 只能在 `playIndex` 里加。禁止进 feed / 续刷 / 软插入时整表标记已看（1.14.48 修过，回退推荐又会废）

---

## 8. 本会话已完成（2026-08-14）

| 版本/项 | 内容 | 是否上传 |
|---------|------|----------|
| **1.15.9** | 液态主题定稿一版：悬浮底栏+全面屏提示；迷你条只暂停；更多右上角实色窗；登录误判修复；快捷退出 | **已传** 双仓 `1377edc` |

### 主题系统（勿回退）

- `VisualTheme.Classic` 默认；`LiquidGlass` 另套布局，不是换 token
- 简约仍用 `AppearanceMode` + `ColorTheme`
- 液态：`ui/liquid/`（Glass / Chrome / Home / Search / Library / Me / Recommend / Settings）
- 入口：我的 → 主题 / 外观设置
- 推荐清屏短视频、开屏、协议不跟液态重排

---

## 8b. 更早（2026-08-13）

| 版本/项 | 内容 | 是否上传 |
|---------|------|----------|
| **1.14.48** | 评论图放大；推荐未看不算已看 + 点赞重排后续 | **已传** 双仓 |
| **1.14.47** | 推荐：跳过/半播极性、冷却硬挡、点赞搅动后续、续刷加宽 | **已传** 双仓 |
| **1.14.46** | 听歌默认较高码率 + 精听；迷你条点回推荐电台 | **已传** 双仓 |
| **1.14.45** | 听歌体感（音质除外）：加载/错误、循环、喜欢、进度、音效、迷你条、切歌先换封面 | **已传** 双仓 |
| **1.14.44** | 修视频上下滑进度记忆：上滑再滑回从上次进度续播；听歌切歌仍从头 | **已传** 双仓 |
| **1.14.36** | 新品牌蛇标 `h_logo`：桌面图标 + 开屏资源 + `BrandSplash` 轻放大渐入；底色 `#1F2121`；README 预览换新图 | **已传** 双仓 |
| **1.14.37** | 桌面图标嫌大 → 内容缩至约 **70%** 画布，四周留白（只改 mipmap，开屏图不动） | **已传** 双仓 |
| **1.14.38** | 搜索对齐 B 站：WBI + totalrank + 每页 42 + 滚到底 `loadMoreSearch`；列表显示「已显示 x / y」 | **已传** 双仓 |
| **1.14.39** | 推荐：喜欢写入 `likedAtMs`；近 1 小时点赞强化种子/无限流；小时级抖动 + UP/题材打散 | **已传** 双仓 |
| **1.14.40** | 短视频推荐方案：统一事件、内容画像、四层打分、实时软插入、独立重排器、快速跳过冷却、Debug 推荐行 | **已传** 双仓 |
| **1.14.41** | 修复推荐起播按钮二次闪现/卡加载；应用内可更新 | **已传** 双仓 |
| **1.14.42** | 真修「播放 为你推荐」：一次起播、复用 feed、等流就绪再收按钮 | **已传** 双仓 |
| **1.14.43** | 修检查更新：扫全量取最高版本；我的页提示新版本；更新页常驻网页下载。双仓 Release 已传 | **已传** 双仓 |

### 更早（仍有效，勿回退）

| 版本/项 | 内容 |
|---------|------|
| **1.14.35** | 网络四档默认均衡；分批导入 500 |
| **1.14.34** | 修启动崩（拆自封装缓冲）；后台省网 |
| 功能阉割 | 用户要求：**只调强度，不关点播/歌单/搜索/导入** |

---

## 9. 关机前状态（2026-08-14）

- 双仓最新：**1.15.9**（`1377edc`，Release 均已上传）
- 下载：https://gitee.com/dikoklhf/madus/releases/tag/v1.15.9  
  备用：https://github.com/zyjshb/Madus/releases/tag/v1.15.9
- `/latest` 已是 **v1.15.9**
- 用户要求：**以后改完直接传仓库**，不要再问
- 默认主题仍是简约；液态在 我的 → 主题
- 未入库：`别人的提示词.md`、`Kazumi_windows_2.2.7/`

## 9b. 更早关机状态（2026-08-13）

- 双仓最新：**1.14.48**（`8303e39` / 文档 `a337ee1`，Release 均已上传）
- 包：`apk/Madus-1.14.48.apk`（约 16.6 MB）
- 下载：https://gitee.com/dikoklhf/madus/releases/tag/v1.14.48  
  备用：https://github.com/zyjshb/Madus/releases/tag/v1.14.48
- 起播：`startForYouRecommend` 单飞；近 15 分钟有新赞则**不复用**旧 feed，重跑 `buildSmartFeed`（带 `recentLikeIds`）
- 更新：新包扫列表取最高版本。1.14.40 旧包靠 `/latest`；发 1.14.48 时 `/latest` 已跟上
- 听歌音质 **1.14.46 已做**（§11），勿再当待办
- 迷你条点击 **回到推荐电台**，不要再改去清屏页
- 推荐用户说 1.14.47「还是不行」→ 1.14.48 修了「未看当已看」+ 点赞重排后续。真机还要再刷一轮看跟不跟手
- 未入库：`别人的提示词.md`、`动漫宣传片-早班车的一只耳机/`

---

## 10. 动漫宣传片《早班车的一只耳机》（进行中）

- 目录：`and/动漫宣传片-早班车的一只耳机/`  
- 故事：纯爱校园，早班车借耳机，歌单 43 首，纸条“第44首，你来选”，多年后重逢  
- 音乐：全部由 Suno 原创生成，避免版权  
- 素材：角色/物品/场景/产品界面四类提示词已完成；角色只出三视图，不生成表情差分  
- 已废弃：最后频率、明天见、冬天的那首歌  
- 未完成：16 段镜头提示词、剪辑配乐时间轴、参考图实际生成  

**下次可做（未点名别擅自大改）：**  
1. 真机过液态玻璃：排版/字号/浓度不对先改 `ui/liquid/`，别动简约  
2. 用户点头后再打 1.15.0 正式包推双仓  
3. 用户一句话小改 App / 发版  
4. 推荐若 1.14.48 真机仍不跟手：先问「哪里不对」，再改 §12  
5. 动漫宣传片：`and/动漫宣传片-早班车的一只耳机/12-项目记忆.md`  

---

## 11. 听歌音质（1.14.46 已落地，勿回退）

**口径：** 默认「较高」+「精听」。不是无损/母带。视频取流没动。

**已实现：**
- `pickPlayableUrl`：dash.audio + flac + dolby，按 bandwidth；省流才捡最低；progressive 兜底  
- 听歌 attempts 先 `fnval=16` dash  
- `preferHigher = qn==0 || qn>=64`（标准及以上走高码）  
- 默认 `AudioQuality.High` / `SoundFx.Studio`；`PlayerPrefs.migrateListenDefaults()` 一次性把旧「标准+原声」升上去  
- `AudioFxController`：精听 EQ + 轻量 `LoudnessEnhancer`；Bass 增益已收；无自封装 AudioProcessor  
- 迷你条：用户要求保持点进**推荐电台**，1.14.46 已改回  

**别做：** 蝰蛇/ViPER so、承诺无损、动视频取流、默认改回 Standard。

---

## 12. 推荐池（1.14.40 骨架 + 47/48 修补，勿回退骨架）

**是什么：** 本机 `sourceId=="recommend"` 无限队列。歌单/搜索/收藏夹绝不被改写。

**1.14.47：**
- `WATCH_50` = 至少一半时长（长视频不再看 30 秒就算喜欢）  
- `SKIP_FAST` 墙约 15 秒；一次快划就给小时层扣分  
- mute 填进 `FeedContext`，重排**硬挡**（relaxation 也不放行）  
- 续刷不再一条 related 就停；新鲜点赞 related 加分  
- 近 15 分钟有新赞：`startForYouRecommend` 不复用旧 feed  

**1.14.48（用户说 47 还不行之后）：**
- **根因：** `loadRecommendFeed` / 续刷 / 起播把整表 id 写进 `sessionSeenIds`，点赞 related 常被当成已看进不去  
- **现规则：** 只有 `playIndex` 写 `sessionSeenIds`  
- 点赞后 `reshuffleUpcomingAfterLike`：保住当前+下一条，后面用 `related-like` 重排  
- 来源分：`related-like` / `realtime-related` = 1.0，队尾 `related` 降到 0.5  

**别动：** 四层兴趣 + 多路召回 + 重排 + 软插入骨架；网络四档只调强度；不上传行为；不接非 B 站推荐后端。

**已知还可能嫌差：** 主题只有十来个粗类；没「不感兴趣」按钮；本机规则不是抖音全站模型。再优化先问体感再改。
