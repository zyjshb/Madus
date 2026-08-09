# Madus 手机版 · 核心记忆（下机交接）

**日期：** 2026-08-10  
**目录：** `Mineradio-main/and/`（包名 `com.madus.mobile`）  
**当前版本：** `1.14.28` / versionCode `248`  
**正式包：** `and/apk/Madus-1.14.28.apk`  
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
| 合并脚本 | `scripts\publish-release.ps1`（有 token 时顺带 Gitee） |
| 禁止 | Release 说明里写「正式版 / 无 debug」 |

**JDK：** `C:\Users\djnio\Desktop\audio\tools\jdk17`  
**SDK：** `local.properties` → `sdk.dir=...\audio\tools\android-sdk`

---

## 2. 应用内更新（AppUpdate）

- 探测：Gitee + GitHub 合并，取更高版本  
- 下载：Gitee 优先，失败换 GitHub  
- 校验：ZIP / AndroidManifest / dex；装在 `files/updates/`  
- **启动时** `AppUpdate.cleanupDownloadedApks()` 清掉已下的 `.apk`/`.part`（只清应用内 updates 目录）  
- 更新页：进度条；「Gitee 下载」「GitHub 下载」浏览器入口  

---

## 3. 近期功能定稿（1.14.x）

| 项 | 结论 |
|----|------|
| **游戏听歌** | 播放设置：「打游戏时继续播放」（默认开，忽略短暂音频焦点）、「游戏轻量」（缓冲/写盘/后台预取更省）— **不删原有功能** |
| **P1 后台降载** | 后台少预取、通知节流、进度落盘变慢、封面内存清 |
| **换歌** | 队列 🔍 / 歌单 ⋯「搜索换歌」；**搜索框不预填旧歌名**；点结果替换；可写回本地歌单 |
| **导入** | 输入框**空白**（无示例字）；多行歌名-歌手 / 网易QQ酷狗酷我 |
| **关于连点** | 与「外观设置」**拆成两块**（防误触）；连点 **7 次** 进彩蛋；提示仅「还差 N 次」「不用再点了」 |
| **彩蛋（当前）** | **画板**（版本号 + 涂鸦 + 清除）。曾试过恐龙/风扇/掉音符，用户嫌卡或不好玩，**定稿画板** |
| **B 站收藏封面** | `favFolders` 空 cover 时用夹内第一首补；进夹后回写首页/曲库列表 |
| **长播提示** | 同一曲 **约 5 分钟** 顶部轻条「已经听了一会儿了，可以换首歌」；「知道了」/点条关闭；不挡操作 |
| **歌单二次进入** | 打开收藏夹**不要**自动 purge（手动 ⋯ 清失效）；`openPlaylist` 必须可取消；playlist 路由 `launchSingleTop` |
| **退出歌单** | 只 pop + `cancelPlaylistLoad`，**不要**立刻 `closePlaylist`（否则退出动画闪 0 首） |
| **二次进歌单** | 打开封面=只浏览；`markExplicitPlaylistPlay` 一次性令牌；`openRecommendPlayer(fromPlaylistPlay=true)` 必须 consume 令牌；打开后 2s 无令牌禁止跳推荐台 |
| **改完即发** | 用户约定：修完 bug **自动**升版、打正式包、推 GitHub+Gitee Release（勿等再喊上传） |
| **上一首** | 队首+循环直接到队尾；非队首才 3s 重头；引擎/通知栏勿再单独 3s 拦截 |

---

## 4. 长期产品约定（勿回退）

| 项 | 结论 |
|----|------|
| 音源 | 仅 B 站 + 演示 |
| UI | 线稿 / 主题可切换 |
| 普通搜索 | 全站视频；不做过严阉割 |
| AI 搜 | 文字/图/上传；**无「B站识曲」入口**（推荐页悬浮球识曲） |
| 哼唱 | ACR humming 优先 + 讯飞；无硬编码歌名 |
| 短视频 | 右侧圆头像进 UP 主页 |
| 外站导入 | 高召回匹配 |
| 队列 | 整表 + 拖拽 |
| 协议 | 首次启动；`LegalPrefs.CURRENT_VERSION` |

---

## 5. 关键路径

```
app/src/main/java/com/madus/mobile/
  data/AppUpdate.kt          # 双源更新 + 清包
  data/BilibiliApi.kt        # favFolders 补封面
  data/PlayerPrefs.kt        # gameMixAudio / gameLiteMode
  player/PlayerEngine.kt     # 音频焦点 / 后台 ticker
  ui/AppViewModel.kt         # 换歌、长播提示、导入
  ui/screens/AboutEasterEggScreen.kt  # 画板彩蛋
  ui/screens/MeScreen.kt     # 关于连点 / 外观拆开
  ui/screens/UpdateScreen.kt
  ui/MadusRoot.kt            # 长播提示条、导航
scripts/publish-release.ps1
scripts/publish-gitee-release.ps1
```

---

## 6. 发版检查清单

1. 改 `versionCode` / `versionName`  
2. 更新 `AppChangelog` + `CHANGELOG.md` + `scripts/release-notes-x.y.z.md`  
3. `assembleRelease` → 拷到 `apk/`  
4. `git push origin main` + `git push gitee main:main`  
5. `gh release create` + `publish-gitee-release.ps1`  
6. 更新**本文档**版本号  

---

## 7. 已知注意

- Gitee 默认分支网页上可能仍是 `master`，代码主推 `main`  
- 不要把 `GITEE_TOKEN` 发到聊天  
- Release 附件名保持 `Madus-x.y.z.apk`，与应用内约定路径一致  
- 问题截图目录已 gitignore，勿再提交 `问题截图/`  

---

## 8. 关机前状态（2026-08-09）

- 已发 **1.14.23**：打开收藏夹自动 purge 失效（clean + batch-del），列表默认 excludeInvalid  
- 正式包：`apk/Madus-1.14.23.apk`  

**下次可做（用户未点名则别擅自大改）：**  
按用户一句话继续小改/发版即可；彩蛋保持画板。  
