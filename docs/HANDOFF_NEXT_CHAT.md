# Madus 手机版 · 核心记忆（下机交接）

**日期：** 2026-08-12  
**目录：** `Mineradio-main/and/`（包名 `com.madus.mobile`）  
**当前版本：** `1.14.36` / versionCode `256`  
**正式包：** `and/apk/Madus-1.14.36.apk`  
**GitHub：** https://github.com/zyjshb/Madus  
**Gitee：** https://gitee.com/dikoklhf/madus  
**最新 commit（示意）：** brand logo h_logo + splash refresh  
 

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
| 合并脚本 | `scripts\publish-release.ps1`（有 token 时顺带 Gitee；脚本若编码炸了就手动 gh + publish-gitee） |
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
| **改完即发** | 修完 bug 自动升版打正式包推双仓（用户说「先别传」时暂停） |

---

## 4. 长期产品约定（勿回退）

| 项 | 结论 |
|----|------|
| 音源 | 仅 B 站 + 演示 |
| UI | 线稿 / 主题可切换 |
| 普通搜索 | 全站视频；不做过严阉割 |
| AI 搜 | 文字/图/上传；无「B站识曲」入口（推荐页悬浮球） |
| 哼唱 | ACR + 讯飞；无硬编码歌名 |
| 外站导入 | 高召回；分批 500 |
| 队列 | 整表 + 拖拽 |
| 协议 | `LegalPrefs.CURRENT_VERSION` |

---

## 5. 关键路径

```
app/src/main/java/com/madus/mobile/
  data/PlayerPrefs.kt          # NetworkIntensity 四档 + gameMix/Lite
  data/ExternalPlaylistImporter.kt  # BATCH_SIZE=500, parseForImport + matchSongsBatch
  player/PlayerEngine.kt       # DefaultLoadControl 12–20s；写盘看网络档
  ui/AppViewModel.kt           # 分批导入、预取/续刷按 networkIntensity
  ui/screens/PlaybackPrefsScreen.kt  # 网络使用 UI
  ui/screens/PlaylistDetailScreen.kt # 进页播放门闩
  ui/components/ImportPlaylistSheet.kt
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
- 根目录乱码文件名：`别人的提示词.md` 等可能显示为 ``  

---

## 8. 本会话已完成（2026-08-10～11）

| 版本/项 | 内容 | 是否上传 |
|---------|------|----------|
| **1.14.32** | 导入曾提到 2000；退出再进误触播放（后被 33 策略替代） | 已传 |
| **1.14.33** | 导入改回 **每批 500 + 继续添加** | 已传 |
| **1.14.34** | 后台省网；**修启动崩**（拆 AdaptiveLoadControl） | 本地→并入 35 |
| **1.14.35** | **网络四档默认均衡** + 分批导入等 | **已传** 双仓 |
| 歌单误触 | 指针落定 + 0.45s 门闩 | 在 32/后续里 |
| 功能阉割 | 用户确认要求：**只调强度，不关点播/歌单/搜索/导入** | 遵守中 |

---

## 9. 关机前状态（2026-08-11 续）

- 线上最新：**1.14.35**（网络均衡默认；分批导入 500；歌单误触修复链）  
- 包：`apk/Madus-1.14.35.apk`  


## 10. 动漫宣传片《早班车的一只耳机》（进行中）

- 目录：`and/动漫宣传片-早班车的一只耳机/`（与 Codex outputs 同步）
- 故事：纯爱校园，早班车借耳机，歌单 43 首，纸条“第44首，你来选”，多年后重逢
- 音乐：全部由 Suno 原创生成，避免版权
- 素材：角色/物品/场景/产品界面四类提示词已完成；角色只出三视图，不生成表情差分
- 已废弃：最后频率、明天见、冬天的那首歌
- 未完成：16 段镜头提示词、剪辑配乐时间轴、参考图实际生成

**下次可做（未点名别擅自大改）：**  
1. 用户一句话小改 App / 发版
2. 继续动漫宣传片：从 `and/动漫宣传片-早班车的一只耳机/12-项目记忆.md` 开始
