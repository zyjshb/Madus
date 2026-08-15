# Madus 手机版 · 核心记忆（下机交接）

**日期：** 2026-08-15  
**目录：** `Mineradio-main/and/`（包名 `com.madus.mobile`）  
**当前版本：** `1.17.1` / versionCode `290`  
**提交：** （发版后填）  
**正式包：** `and/apk/Madus-1.17.1.apk`  
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
- **发版后必须确认** `GET .../releases/latest` 已是新 tag（`publish-gitee-release.ps1` 会轮询）。没跟上就去网页把该 Release 设为最新  
- 只有 `/latest` 仍停在旧版、且 GitHub API 不通时，才需要网页手装  
- 下载：Gitee 优先，失败换 GitHub  
- 校验：ZIP / AndroidManifest / dex；装在 `files/updates/`  
- **启动时** `AppUpdate.cleanupDownloadedApks()` 清掉已下的 `.apk`/`.part`  
- 更新页：进度条；**Gitee / GitHub 下载入口始终显示**（检测失败 / 已是最新也能去网页）  
- 「我的」进页静默探测，有新版写「有新版本 vX.Y.Z」；远端比本机还旧时提示「可到网页确认」  
- 单测：`app/src/test/java/com/madus/mobile/data/AppUpdateTest.kt`  

---

## 3. 画境 Canvas（1.16.x 定稿，勿回退）

用户原话口径：**功能、排版基于简约；只加整页壁纸 + iOS 26.6 那种玻璃底栏。**  
不是另一套液态页面，不是设置页克隆，不是重新设计电台/私人 FM。

### 主题枚举

| 项 | 结论 |
|----|------|
| `VisualTheme.Classic` | 默认简约 |
| `VisualTheme.Canvas` | 画境。旧 `liquid_glass` **迁移到 Canvas**，不要再加回独立 Liquid 布局 |
| 入口 | 我的 → 主题 / 外观设置 |
| 清屏短视频 / 开屏 / 协议 | **不跟画境重排** |

### 画面怎么叠

1. `MadusTheme` Canvas：`liquidColorScheme` + `LocalContentColor = CanvasPaper` + 状态栏浅色图标  
2. `Scaffold`：`containerColor = Transparent`，`contentColor = onBackground`  
3. `hazeSource` 包住 **壁纸 + NavHost**（漏掉壁纸则玻璃糊不到图）  
4. Classic 各页照常画；底栏换成 `LiquidFloatingChrome`  

### 壁纸

- 源：`https://t.alcy.cc/json?mp` → `{code,data:{link}}`（`AlcyWallpaper.kt`，带 UA / 跟跳转）  
- 模式：`WallpaperMode` = Daily / Pinned / Custom  
- 设置页：每日随机、固定、换一张、下载、从相册选择、跟随壁纸/香槟金、压暗/模糊/玻璃滑条  
- Coil 缓存键必须带 `path + stamp + mtime`，否则「换一张」看起来没换  
- **固定**：先用已存路径；拷到 `wallpaper.jpg` 时若源和目标是同一文件 **禁止 `File.copyTo`**（1.16.5 卡退：随机后再固定自己拷自己）  
- 配色：默认 `followWallpaperColor=true`，`extractCanvasPalette` 抽 accent；关了用香槟金。**字色永远 `CanvasPaper`**  
- 玻璃 `glassTint`：通透=近白薄霜，着色=强调色厚釉，两端要一眼能看出来  
- 壁纸模糊：`wallpaperBlur` 0–1，对应 0–28dp  
- 亮图自动抬 `minDim`（1.16.6 地板约 0.50–0.78，再和用户滑条取 max，且不低于 0.52）  
- **检查更新 / 更新日志**：画境下用 `LiquidPageHeader` + 透明底，禁止自己 Scaffold 实心底把壁纸盖住

### 字为什么曾经是黑的（1.16.6 根因）

Canvas 的 Scaffold 是透明底，Material3 **不会**自动给 `LocalContentColor`。  
很多 `Text()` 没写 `color`，落到默认 **Color.Black**，亮壁纸上直接看不见。

**必须同时有：**

- `CompositionLocalProvider(LocalContentColor provides CanvasPaper)`  
- `Scaffold(contentColor = onBackground)`  
- `liquidColorScheme` 的 `onBackground / onSurface` = 浅色  
- `WindowInsetsControllerCompat`：`isAppearanceLightStatusBars = false`（深底浅图标）

不要再靠「appearance=Light」出深色正文。壁纸当深底。

### 迷你条 / 电台（1.16.6）

| 项 | 结论 |
|----|------|
| 简约迷你条点击 | `openRecommendPlayer()` → 推荐电台台面 |
| **画境迷你条点击** | **同样** `openRecommendPlayer()`。禁止再 `navigate(NOW_PLAYING)` |
| 电台页本身 | **不叠迷你条**（`showMiniNow` 排除 `onRecommend` / `NOW_PLAYING` / 清屏） |
| 迷你条控件 | 上一首 / 播放暂停 / 下一首 |
| `playAndOpen` / 搜索点播 / UP 空间 | 画境仍留在当前页（`if (!liquid) openRecommendPlayer()`）。用户只改了迷你条，别擅自改点播跳转 |

### 明确翻过的车（不要再做）

- 整套 `ui/liquid/` 独立首页/搜索/曲库/我的/电台当主 UI（1.16.0–1.16.3 做过，用户嫌丑，1.16.4 改回简约排版）  
- 电台 StageCard / 私人 FM 大海报卡（用户：功能别乱改）  
- 电台页再叠一条 mini  
- 迷你条进独立播放页  
- 设置页克隆当主题  
- 壁纸 `hazeSource` 只包 NavHost、不包背景图  

### 画境关键文件

```
ui/theme/Theme.kt            # LocalContentColor + 强制浅字 + 状态栏
ui/theme/Liquid.kt           # CanvasPaper / liquidColorScheme / Chrome 尺寸
ui/theme/CanvasPalette.kt    # 壁纸抽色；onBg 恒浅；minDim
ui/theme/VisualTheme.kt      # Classic / Canvas；isLiquidTheme()==Canvas
data/ThemePrefs.kt           # 壁纸模式、pin、换一张、stamp
data/AlcyWallpaper.kt        # t.alcy.cc
ui/liquid/Glass.kt           # LiquidBackground + haze + GlassSurface
ui/liquid/LiquidChrome.kt    # 悬浮玻璃底栏 + 迷你条
ui/liquid/LiquidSettings.kt  # 壁纸/浓度（设置仍走这页）
ui/MadusRoot.kt              # hazeSource、showMiniNow、onOpenMini
```

`docs/DESIGN_LIQUID_GLASS.md` 是早期 HIG 方案，**不要按它重做整套页面**。未入库，别提交。

---

## 4. 近期功能定稿（1.14.x–1.16.x 仍有效）

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
| **迷你条点击** | **简约和画境都回推荐电台**。不要再把画境改去 `NOW_PLAYING` |
| **视频上下滑** | 滑走再滑回从 `sessionPositions` 续播；听歌切歌仍从头 |
| **评论图** | 可点全屏放大，多图左右翻（`CommentsSheet`） |
| **改完即发** | 修完 bug 自动升版打正式包推双仓（用户说「先别传」时暂停） |

---

## 5. 长期产品约定（勿回退）

| 项 | 结论 |
|----|------|
| 音源 | 仅 B 站 + 演示 |
| UI | 简约 / 画境可切换；画境 = 简约功能 + 壁纸玻璃 |
| 普通搜索 | 全站视频；分页对齐 B 站；不做过严阉割 |
| AI 搜 | 文字/图/上传；无「B站识曲」入口（推荐页悬浮球） |
| 哼唱 | ACR + 讯飞；无硬编码歌名 |
| 外站导入 | 高召回；分批 500 |
| 队列 | 整表 + 拖拽 |
| 协议 | `LegalPrefs.CURRENT_VERSION` |

---

## 6. 关键路径

```
app/src/main/java/com/madus/mobile/
  domain/RecommendationModels.kt
  domain/RecommendationEngine.kt
  domain/RecommendationReRanker.kt
  data/RecommendationEventStore.kt
  data/ContentProfileStore.kt
  data/BilibiliApi.kt
  data/PlayerPrefs.kt
  data/AppUpdate.kt
  data/ThemePrefs.kt              # 画境壁纸 Daily/Pinned/Custom
  data/AlcyWallpaper.kt           # t.alcy.cc
  data/ExternalPlaylistImporter.kt
  player/PlayerEngine.kt
  player/AudioFxController.kt
  ui/AppViewModel.kt
  ui/MadusRoot.kt                 # haze、迷你条、openRecommendPlayer
  ui/theme/Theme.kt / Liquid.kt / CanvasPalette.kt / VisualTheme.kt
  ui/liquid/Glass.kt / LiquidChrome.kt / LiquidSettings.kt
  ui/components/CommentsSheet.kt
  ui/splash/BrandSplash.kt
  ui/screens/HomeScreen.kt / SearchScreen.kt / LibraryScreen.kt
  ui/screens/MeScreen.kt / UpdateScreen.kt / PlaybackPrefsScreen.kt
  ui/screens/PlaylistDetailScreen.kt
  MainActivity.kt
app/src/main/res/
  drawable-nodpi/logo_madus.png
  drawable/splash_logo.xml
  mipmap-*/ic_launcher*.png
  values/colors.xml
logo/h_logo.webp
scripts/publish-gitee-release.ps1
```

---

## 7. 发版检查清单

1. 改 `versionCode` / `versionName`  
2. 更新 `AppChangelog` + `CHANGELOG.md` + `scripts/release-notes-x.y.z.md`  
3. `assembleRelease` → `apk/Madus-x.y.z.apk`  
4. `git push origin main` + `git push gitee main:main`  
5. `gh release create` + `publish-gitee-release.ps1`  
6. 更新**本文档**  

---

## 8. 已知注意 / 坑

- **禁止自封装 LoadControl**（Media3 漏接口 → 启动即崩）；1.14.34 已改回官方  
- `publish-release.ps1` 偶发中文编码解析失败 → 手动 push + gh + gitee 脚本  
- Gitee 网页默认分支可能仍是 `master`，代码主推 `main`  
- 勿提交 `GITEE_TOKEN`、勿传 `*-debug.apk`  
- 根目录未入库杂项（别提交）：`别人的提示词.md`、`Kazumi_windows_2.2.7/`、`docs/DESIGN_LIQUID_GLASS.md`、`f6592ff9e1783904468c059a725ab43b.png`、`docs/DESIGN.md` 的未提交改动  
- **搜索翻页**无 WBI / 无 cookie 时 B 站易 **412**；必须走 `signWbiQuery` + `mergedCookieWithSystem`  
- **桌面图标**原图本身有大留白，再缩比例时别裁掉边再放大（会显大）；当前是**整图 70%** 贴画布  
- 换图标后部分启动器会缓存旧图 → 用户侧卸载重装或清启动器缓存  
- **Gitee `/releases/latest` 偶发滞后**，PATCH `make_latest` 会 406；新包必须扫列表取最高版本  
- **1.14.40 旧包**：只信 Gitee `/latest`。发版脚本会校验；`/latest` 跟上就能应用内升，滞后才手装  
- **听歌切歌** `startPos=0`；**视频上下滑** `startPos=-1` 读记忆。别再给视频写死从头  
- **sessionSeenIds** 只能在 `playIndex` 里加。禁止进 feed / 续刷 / 软插入时整表标记已看（1.14.48 修过，回退推荐又会废）  
- 画境字色：透明底必须自己提供 `LocalContentColor`，只改 `colorScheme.onBackground` 不够  
- 画境换图：Coil 缓存键要带 stamp/mtime  
- 画境固定壁纸：同源同路径不要 `copyTo`  
- 画境玻璃：`hazeSource` 必须包壁纸  

---

## 9. 本会话已完成（2026-08-15）

| 版本 | 内容 | 是否上传 |
|------|------|----------|
| **1.17.1** | B 站字幕当歌词（智能字幕也能用） | 待传 |  
| **1.17.0** `3bb9b2d` | 不喜欢 + 通知栏点赞 | **已传** |  
| **1.16.9** `c53e875` | iOS 手感：进页侧滑、Tab 淡入、按压缩放 | **已传** |  
| **1.16.8** `5cfd3fc` | 连点换壁纸不再叠 snackbar 挡底栏 | **已传** |  
| **1.16.7** `83c1597` | 跟随壁纸配色开关；玻璃通透/着色；背景模糊；去重相册入口；更新页跟主题 | **已传** |  
| **1.16.6** `9f7053f` | 画境强制浅字；迷你条进电台（不再进播放页） | **已传** 双仓；Gitee `/latest` = v1.16.6 |
| **1.16.5** `87b7393` | 电台不叠 mini；mini 可切歌；玻璃糊到壁纸；配色跟图；换图无反应；随机后再固定卡退 | **已传** |
| **1.16.4** `43cbd49` | 画境改回简约排版；每日随机壁纸（t.alcy.cc），可固定/下载/相册 | **已传** |
| **1.16.3** `c97d0bf` | 电台舞台 + 首页 FM 大海报（**用户随后否决，功能改回简约**） | 已传，但设计已弃 |
| **1.16.2** | 主题改名画境，整页壁纸 | **已传** |
| **1.16.1–1.16.0 / 1.15.9** | 独立液态页（**已弃作主 UI**，只留玻璃底栏/设置壁纸） | 历史包 |

---

## 10. 关机前状态（2026-08-15）

- 双仓最新：**1.17.1**（发版后填）  
- 包：`apk/Madus-1.17.1.apk`  
- 下载：https://gitee.com/dikoklhf/madus/releases/tag/v1.17.1  
  备用：https://github.com/zyjshb/Madus/releases/tag/v1.17.1  
- 用户要求：**改完直接传**，不要再问；说「先别传」才停  
- 默认主题仍是简约；画境在 我的 → 主题  
- 未入库：见 §8 杂项列表  

**下次可做（未点名别擅自大改）：**

1. 真机看 1.16.7：跟随壁纸配色、玻璃通透/着色、模糊、检查更新是否透壁纸  
2. 用户一句话小改 / 发版  
3. 推荐若仍不跟手：先问「哪里不对」，再改 §12  
4. 动漫宣传片：`and/动漫宣传片-早班车的一只耳机/12-项目记忆.md`（目录可能不在本工作区）  

不要再：重做液态整套页面、重设计电台/FM、把迷你条改回播放页。

---

## 11. 听歌音质（1.14.46 已落地，勿回退）

**口径：** 默认「较高」+「精听」。不是无损/母带。视频取流没动。

**已实现：**
- `pickPlayableUrl`：dash.audio + flac + dolby，按 bandwidth；省流才捡最低；progressive 兜底  
- 听歌 attempts 先 `fnval=16` dash  
- `preferHigher = qn==0 || qn>=64`（标准及以上走高码）  
- 默认 `AudioQuality.High` / `SoundFx.Studio`；`PlayerPrefs.migrateListenDefaults()` 一次性把旧「标准+原声」升上去  
- `AudioFxController`：精听 EQ + 轻量 `LoudnessEnhancer`；Bass 增益已收；无自封装 AudioProcessor  

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

**已知还可能嫌差：** 主题只有十来个粗类；本机规则不是抖音全站模型。再优化先问体感再改。

**不喜欢（1.17.0）：** `markNotInterested` 写 `NOT_INTERESTED`，当前会话过滤这首，主题/UP 冷却 7 天，正在播就切下一首。入口：电台爱心旁 / 上滑菜单 / 短视频长按。通知栏爱心走 `PlaybackService.ACTION_LIKE`，折叠三键是播放、下一首、喜欢。
