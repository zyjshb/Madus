# Madus 手机版 · 核心记忆（下机交接）

**日期：** 2026-08-12  
**目录：** `Mineradio-main/and/`（包名 `com.madus.mobile`）  
**当前版本：** `1.14.41` / versionCode `261`  
**正式包：** `and/apk/Madus-1.14.41.apk`  
**GitHub：** https://github.com/zyjshb/Madus  
**Gitee：** https://gitee.com/dikoklhf/madus  
**最新 commit（示意）：** recommend hourly like affinity + diversify

---

## 0. 新对话开场

1. **先读本文**  
2. 中文、少废话、真机优先  
3. 接近 **450–500k token** 再开新对话，**必须带本文档**  
4. 关机前更新本文档  
5. **GitHub / Gitee Releases 只上传正式 APK，禁止 `*-debug.apk`**  
6. **文案忌 AI 味**：不要「像安卓旧版…」「玩笑」「端侧直连」这类说明书腔；界面短句即可  

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

- 探测：Gitee + GitHub 合并，取更高版本  
- 下载：Gitee 优先，失败换 GitHub  
- 校验：ZIP / AndroidManifest / dex；装在 `files/updates/`  
- **启动时** `AppUpdate.cleanupDownloadedApks()` 清掉已下的 `.apk`/`.part`  
- 更新页：进度条；「Gitee 下载」「GitHub 下载」浏览器入口  

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
| **推荐刷新** | 喜欢存 `likedAtMs`；**近 1 小时**点赞作强种子，影响首页推荐与无限续刷；小时盐抖动 + 日更切片穿插；UP/题材（标题关键词）打散 |
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
  data/PlayerPrefs.kt          # NetworkIntensity 四档 + gameMix/Lite
  data/ExternalPlaylistImporter.kt  # BATCH_SIZE=500
  player/PlayerEngine.kt       # DefaultLoadControl 12–20s
  ui/AppViewModel.kt           # submitSearch / loadMoreSearch；预取按 networkIntensity
  ui/splash/BrandSplash.kt     # 开屏动画 + logo_madus
  ui/screens/SearchScreen.kt   # 滚到底加载更多；显示已加载条数
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

---

## 8. 本会话已完成（2026-08-12）

| 版本/项 | 内容 | 是否上传 |
|---------|------|----------|
| **1.14.36** | 新品牌蛇标 `h_logo`：桌面图标 + 开屏资源 + `BrandSplash` 轻放大渐入；底色 `#1F2121`；README 预览换新图 | **已传** 双仓 |
| **1.14.37** | 桌面图标嫌大 → 内容缩至约 **70%** 画布，四周留白（只改 mipmap，开屏图不动） | **已传** 双仓 |
| **1.14.38** | 搜索对齐 B 站：WBI + totalrank + 每页 42 + 滚到底 `loadMoreSearch`；列表显示「已显示 x / y」 | **已传** 双仓 |
| **1.14.39** | 推荐：喜欢写入 `likedAtMs`；近 1 小时点赞强化种子/无限流；小时级抖动 + UP/题材打散 | **已传** 双仓 |
| **1.14.40** | 短视频推荐方案：统一事件、内容画像、四层打分、实时软插入、独立重排器、快速跳过冷却、Debug 推荐行 | **已传** 双仓 |
| **1.14.41** | 修复推荐起播按钮二次闪现/卡加载；应用内可更新 | **已传** 双仓 |

### 更早（仍有效，勿回退）

| 版本/项 | 内容 |
|---------|------|
| **1.14.35** | 网络四档默认均衡；分批导入 500 |
| **1.14.34** | 修启动崩（拆自封装缓冲）；后台省网 |
| 功能阉割 | 用户要求：**只调强度，不关点播/歌单/搜索/导入** |

---

## 9. 关机前状态（2026-08-12）

- 双仓最新：**1.14.41**（Release 均已上传）
- 包：`apk/Madus-1.14.41.apk`

### 推荐机制（1.14.40，勿回退）

- 统一事件：点赞/两类收藏/播放/50%·90%观看/快速跳过，存 `RecommendationEventStore`（上限 1000，仅本机）
- 内容画像：`ContentProfileStore` 缓存 B 站分区/标签/主题 7 天；`BilibiliApi.videoMeta` 补详情
- 四层兴趣：实时 30 分钟 / 小时 24 小时 / 长期 30 天 / 负反馈冷却，集中在 `RecommendationTuning`
- 打分重排：`RecommendationEngine.scoreCandidate` + `RecommendationReRanker`（同 UP/主题窗口、探索每 6 条、每日基线每 5 条）
- 实时软插入：点赞/收藏成功后异步拉 related，只插当前播放后第 2~4 位，最多 2 条；播放器报错跳歌不记 SKIP_FAST
- 网络：新增请求只有点赞/收藏后一次 related（失败静默）；续刷仍走网络四档与后台省流
- 起播按钮：`RecommendUiState.isStartingPlayback` 短暂隐藏“播放”按钮，5 秒兜底恢复，避免二次闪现/卡加载
- Codex 改推荐（点赞时间戳 + 小时亲和 + 打散）；本侧修编译（`UInt % 80u`）并打包上传  
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
1. 用户一句话小改 App / 发版  
2. 若搜索结果仍与 B 站网页对不齐：对同一关键词对比 `searchPage` 与网页（WBI 参数 / 登录态）  
3. 桌面图标再微调比例（当前 70%）  
4. 继续动漫宣传片：从 `and/动漫宣传片-早班车的一只耳机/12-项目记忆.md` 开始  
