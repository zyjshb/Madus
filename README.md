<div align="center">

<img src="logo/logo.jpg" width="128" height="128" alt="Madus logo" />

# Madus

### 简约线稿 · Android 听歌客户端

B 站音源 · 电台心智 · 端侧 AI 搜歌

<br/>

[![GitHub release](https://img.shields.io/github/v/release/zyjshb/Madus?color=black&label=Stable&logo=github)](https://github.com/zyjshb/Madus/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/zyjshb/Madus/total?label=Downloads&logo=github)](https://github.com/zyjshb/Madus/releases)
[![License](https://img.shields.io/github/license/zyjshb/Madus?color=blue)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen?logo=android)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

<br/>

<a href="https://github.com/zyjshb/Madus/releases/latest">
  <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub" height="80">
</a>

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
- 🔄 **应用内更新** — 「我的 → 检查更新」打开本仓库 Releases

## Download

推荐下载 **正式包** `Madus-x.y.z.apk`（不要选 debug，除非你在开发）。

1. 打开 [Releases · Latest](https://github.com/zyjshb/Madus/releases/latest)
2. 下载 `Madus-*.apk`
3. 允许「未知来源」后安装  
   （侧载时系统可能提示风险，属常见现象，请确认来源为本仓库）

也可在 App 内：**我的 → 检查更新**。

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

发版可使用 `scripts/publish-release.ps1`（需 [GitHub CLI](https://cli.github.com/) 已登录）。

## FAQ

**Q: 为什么安装会提示风险 / 未备案？**  
A: 目前通过 GitHub 侧载分发，未上架应用商店。确认下载自本仓库即可。

**Q: 支持 iPhone 吗？**  
A: 暂不支持，仅 Android。

**Q: 哼唱 / AI 搜不能用？**  
A: 需在 App 内自行配置对应 API Key（模型 / ACR / 讯飞），仓库不会内置密钥。

**Q: 外站歌单是直接播网易/QQ 吗？**  
A: 不是。只解析歌名并在 B 站搜索可播内容，不接第三方播放源。

## Disclaimer

本项目与 Bilibili、网易云音乐、QQ 音乐等 **无官方关联**。

- 代码以 [MIT](LICENSE) 开源  
- **媒体与版权归原平台及权利人所有**  
- 请遵守相关服务条款与法律法规；使用风险自负  

## Credits

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Media3](https://developer.android.com/guide/topics/media/media3)
- [Coil](https://coil-kt.github.io/coil/)

README 结构参考了 [InnerTune](https://github.com/z-huang/InnerTune)、[Seal](https://github.com/JunkFood02/Seal)、[NewPipe](https://github.com/TeamNewPipe/NewPipe) 等优秀开源项目。

## License

[MIT](LICENSE) © Madus Contributors

---

<div align="right">
<a href="#madus">⬆ 回到顶部</a>
</div>
