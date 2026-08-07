# Madus 手机版 · 核心记忆（下机交接）

**日期：** 2026-08-07  
**目录：** `Mineradio-main/and/`（包名 `com.madus.mobile`）  
**当前版本：** `1.14.0` / `220`  
**Debug 包：** `and/apk/Madus-1.14.0-debug.apk`  
**正式包：** `and/apk/Madus-1.14.0.apk`

---

## 0. 新对话开场

1. **先读本文**  
2. 中文、少废话、真机优先  
3. 接近 **450–500k token** 再开新对话，**必须带本文档**  
4. 关机前更新本文档  
5. Codex/DeepSeek 工人约定见 `docs/CODEX_WORKER.md`（Grok 总管可按任务分流；工人易卡则总管直接改）  
6. **GitHub Releases 只上传正式 APK，禁止上传 `*-debug.apk`**  

---

## 0.1 ★ 当前定稿（1.13.8）

| 项 | 结论 |
|----|------|
| **版本** | Madus **1.13.8** / versionCode **218** |
| **音源** | 仅 B 站 + 演示 |
| **UI** | 纯线稿 |
| **普通搜索 / 队列搜索** | 全站视频原始结果；错字：多 order + suggest + 去标点 |
| **AI 搜** | 文字 / 图 / 上传听辨；**无「B站识曲」按钮**（推荐页悬浮球负责） |
| **哼唱** | ACR **humming 优先**（audio 仅兜底）+ 讯飞；低把握不强推；**候选可点搜 B 站**；禁止单曲硬编码 |
| **B站识曲（官方标签）** | **仅推荐页左侧悬浮球**；切 tab 记位置/收起态；非 LLM |
| **短视频** | 右侧 **圆头像**（无「主页」字），点进 UP 主页；@昵称也可进 |
| **旧 LLM「本片 BGM」半屏面板** | **已下线，禁止恢复** |
| **外站歌单导入** | 我的/曲库；多行「歌名 - 歌手」；网易/QQ/酷狗/酷我；无汽水；VIP 清洗+打分匹配 |
| **UP 主页** | card + WBI 投稿 + 关注同步 B 站；投稿 **小说式上一页/下一页**（40/页，替换不追加，同收藏夹） |
| **播放队列** | 整表 + 长按拖拽；上一首 id 校准 |
| **推荐上滑菜单** | 左右滑；竖滑不抢菜单 |

---

## 1. 版本摘要

| 版本 | 要点 |
|------|------|
| **1.13.5** | 我的导入；外站多平台；悬浮球记忆；菜单左右滑 |
| **1.13.6** | 短视频 UP 头像；AI 去 B站识曲；哼唱 ACR humming+audio 重做 |
| **1.13.7** | 头像去「主页」；哼唱 humming 优先 + 候选可点 |
| **1.13.8** | UP 投稿小说式翻页（同收藏夹） |

---

## 2. 硬约束

- 清空队列必须 **Stop**  
- 禁止 `clearVideoSurface()`  
- 禁止 `PlaybackService.onDestroy` 里 `player.release()`  
- **普通搜索禁止改回音乐区优先**（识曲过滤只留 AI/哼唱/导入）  
- **哼唱不退大模型听辨**；**禁止单曲 if-else**  
- **不恢复**旧 LLM「本片 BGM」半屏  
- 外站导入只做歌名→B 站，不接第三方播放源  

---

## 3. 构建

```powershell
$env:JAVA_HOME='C:\Users\djnio\Desktop\audio\tools\jdk17'
$env:GRADLE_USER_HOME='C:\Users\djnio\.gradle'
cd C:\Users\djnio\Desktop\audio\Mineradio-main\and
.\gradlew.bat :app:assembleDebug :app:assembleRelease --offline
Copy-Item app\build\outputs\apk\debug\app-debug.apk apk\Madus-1.13.8-debug.apk
Copy-Item app\build\outputs\apk\release\app-release.apk apk\Madus-1.13.8.apk
```

---

## 4. 建议回归

- 短视频右侧头像 → UP 主页  
- AI 搜无「B站识曲」；推荐页悬浮球仍在  
- 哼唱 8–12 秒副歌；报错应显示引擎原因  
- 我的/曲库导入多行歌名  
