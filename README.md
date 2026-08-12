<div align="center">

<img src="logo/h_logo.webp" width="128" height="128" alt="Madus logo" />

# Madus

### 简约线稿 · Android 听歌客户端

B 站音源 · 电台心智 · 端侧 AI 搜歌

<br/>

[![GitHub release](https://img.shields.io/github/v/release/zyjshb/Madus?color=black&label=GitHub&logo=github)](https://github.com/zyjshb/Madus/releases/latest)
[![Gitee](https://img.shields.io/badge/Gitee-Releases-C71D23?logo=gitee&logoColor=white)](https://gitee.com/dikoklhf/madus/releases)
[![License](https://img.shields.io/github/license/zyjshb/Madus?color=blue)](LICENSE)
[![Changelog](https://img.shields.io/badge/Changelog-Keep%20a%20Changelog-lightgrey)](CHANGELOG.md)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen?logo=android)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

<br/>

**下载（国内优先 Gitee）**

| 渠道 | 链接 |
|:---:|:---|
| **Gitee（推荐）** | [Releases · 最新版](https://gitee.com/dikoklhf/madus/releases) |
| **GitHub（备用）** | [Releases · Latest](https://github.com/zyjshb/Madus/releases/latest) |

仓库：

- GitHub：https://github.com/zyjshb/Madus  
- Gitee：https://gitee.com/dikoklhf/madus  

</div>

---

## Features

- 🎧 **B 站听歌** — 登录、搜索、收藏夹、UP 主页、评论
- 📻 **推荐电台** — 封面 / 视频双模式，像电台台面一样听
- 📱 **清屏短视频** — 抖音 / B 站 / 快手手势可切换，右侧 UP 头像进主页
- 📋 **完整队列** — 整表可见、长按拖拽排序、上一首校准
- 📥 **外站歌单导入** — 网易云 / QQ / 酷狗 / 酷我链接，或「歌名 - 歌手」多行文本 → 匹配 B 站可播
- ✨ **AI 搜歌** — 文字 / 图片 / 上传音视频（需自备模型 Key）
- 🎤 **哼唱识别** — ACRCloud + 讯飞双引擎（需自备 Key）
- 🎨 **主题** — 线稿黑白、深色墨黑、Catppuccin 配色；圆滑玻璃 / 方角线稿
- 🔄 **应用内更新** — 我的 → 检查更新（优先 Gitee，失败再 GitHub）

## Download

下载 **`Madus-x.y.z.apk`**（不要选 `debug`，除非你在开发）。

1. 国内打开 [Gitee Releases](https://gitee.com/dikoklhf/madus/releases)  
2. 或打开 [GitHub Releases](https://github.com/zyjshb/Madus/releases/latest)  
3. 下载 `Madus-*.apk` → 允许「未知来源」→ 安装  

也可在 App 内：**我的 → 检查更新**（双源自动切换）。

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose · Material 3 |
| Player | Media3 / ExoPlayer |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Backend | **无自建服务器**（端侧直连） |

## Build

需要 **JDK 17** 与 Android SDK。

```powershell
# Windows
$env:JAVA_HOME = 'C:\path\to\jdk17'
.\gradlew.bat :app:assembleRelease
```

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleRelease
```

输出：`app/build/outputs/apk/release/`

发版：

```powershell
# GitHub（需 gh 登录）
.\scripts\publish-release.ps1 -Version x.y.z

# Gitee（需 GITEE_TOKEN）
$env:GITEE_TOKEN = 'your_token'
.\scripts\publish-gitee-release.ps1 -Version x.y.z -SetDefaultMain
```

## FAQ

**Q: 为什么安装会提示风险 / 未备案？**  
A: 通过 GitHub / Gitee 侧载分发，未上架应用商店。确认下载自本仓库即可。

**Q: 支持 iPhone 吗？**  
A: 暂不支持，仅 Android。

**Q: 哼唱 / AI 搜不能用？**  
A: 需在 App 内自行配置对应 API Key（模型 / ACR / 讯飞），仓库不会内置密钥。

**Q: 外站歌单是直接播网易/QQ 吗？**  
A: 不是。只解析歌名并在 B 站搜索可播内容，不接第三方播放源。

**Q: Gitee 和 GitHub 有什么区别？**  
A: 代码与发版内容一致。国内下载用 Gitee 更快；GitHub 作国际备用。

## Disclaimer

本项目仅供学习交流。音源与内容版权归原作者 / 平台所有；请遵守当地法律法规与平台服务条款。使用本软件产生的一切后果由使用者自行承担。

## License

见 [LICENSE](LICENSE)。
