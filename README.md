<p align="center">
  <img src="logo/logo.jpg" alt="Madus" width="120" />
</p>

<h1 align="center">Madus</h1>

<p align="center">
  <b>极简线稿 · 电台心智 · 端侧智能</b><br/>
  面向现代 Android 的沉浸式听歌客户端
</p>

<p align="center">
  <a href="https://github.com/zyjshb/Madus/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/zyjshb/Madus?style=for-the-badge&color=111111&labelColor=f7f5f2&label=release" /></a>
  <a href="https://github.com/zyjshb/Madus/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/zyjshb/Madus/total?style=for-the-badge&color=111111&labelColor=f7f5f2" /></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-MIT-111111?style=for-the-badge&labelColor=f7f5f2" /></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%2026%2B-111111?style=for-the-badge&labelColor=f7f5f2" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.x-7F52FF?style=for-the-badge&labelColor=f7f5f2&logo=kotlin&logoColor=7F52FF" />
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&labelColor=f7f5f2&logo=jetpackcompose&logoColor=4285F4" />
</p>

<p align="center">
  <a href="https://github.com/zyjshb/Madus/releases/latest"><strong>⬇ 下载最新版 APK</strong></a>
  ·
  <a href="#-功能全景">功能</a>
  ·
  <a href="#-架构概览">架构</a>
  ·
  <a href="#-快速开始">构建</a>
  ·
  <a href="#-声明">声明</a>
</p>

---

## 愿景

Madus 不是又一个「塞满按钮的播放器」。

它把 **听歌** 做成一条连贯体验：线稿界面克制干净，推荐页像电台台面，清屏短视频像沉浸滑动，搜索与 AI 能力把「想听的那首歌」更快落到可播内容。

> **设计关键词**  
> 线稿美学 · 电台心智 · 队列优先 · 端侧可扩展 · 无自建服务器

---

## 功能全景

<table>
<tr>
<td width="50%" valign="top">

### 播放与电台
- 推荐电台台面（封面 / 视频双模式）
- 清屏短视频手势（抖音 / B 站 / 快手风格可切换）
- 完整播放队列：整表可见、长按拖拽排序
- 上一首 / 下一首稳健跳转与下标校准
- 音质偏好、睡眠定时、倍速、边听缓存

</td>
<td width="50%" valign="top">

### 发现与内容
- 全站搜索 + 错字纠错（多排序 / 联想 / 去标点）
- UP 主页：资料卡、投稿分页、合集、关注同步
- B 站收藏夹：小说式上一页 / 下一页
- 评论：分页、楼中楼、表情与配图
- 官方 BGM 标签识曲（推荐页悬浮球）

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 曲库与导入
- 本地歌单创建 / 管理 / 封面
- 喜欢、最近播放
- 外站歌单导入（网易云 / QQ / 酷狗 / 酷我链接，或多行「歌名 - 歌手」）
- 导入结果匹配到 B 站可播内容（**不接第三方播放源**）

</td>
<td width="50%" valign="top">

### 智能能力
- AI 搜歌：文字 / 图片 / 上传音视频听辨
- 哼唱识别：ACRCloud + 讯飞双引擎（需自备 Key）
- 候选可点选再搜，结果可一键播放 / 收藏
- 模型配置与会话历史本地管理

</td>
</tr>
</table>

### 体验细节

| 模块 | 说明 |
|------|------|
| **主题** | 线稿黑白、深色墨黑、Catppuccin 系列配色 |
| **形态** | 圆滑玻璃 / 方角线稿 |
| **更新** | 「我的 → 检查更新」直达 [GitHub Releases](https://github.com/zyjshb/Madus/releases/latest) |
| **日志** | 检查更新连点 3 次打开内置更新日志 |

---

## 架构概览

```text
┌─────────────────────────────────────────────────────────────┐
│                        Madus App                            │
│  Jetpack Compose UI  ·  Navigation  ·  Material 3 线稿主题   │
├───────────────┬─────────────────────┬───────────────────────┤
│  AppViewModel │  AiChatViewModel    │  Theme / Prefs / Store│
│  播放·队列·推荐 │  搜歌·哼唱·会话      │  DataStore 本地状态    │
├───────────────┴─────────────────────┴───────────────────────┤
│              Player Layer (Media3 / ExoPlayer)              │
│         PlaybackService · Queue · Cache · Audio FX          │
├─────────────────────────────────────────────────────────────┤
│  MusicSource  │  BilibiliApi  │  ExternalPlaylistImporter   │
│  抽象音源接口  │  登录/搜索/流  │  外站歌单解析 → B 站匹配     │
└─────────────────────────────────────────────────────────────┘
          │ 直连公开/用户授权接口，无需自建后端 │
          └──────────────────────────────────┘
```

### 技术栈

| 层级 | 选型 |
|------|------|
| 语言 | **Kotlin** |
| UI | **Jetpack Compose** + Material 3 |
| 最低系统 | **Android 8.0 (API 26)** |
| 目标 SDK | **35** |
| 播放 | **Media3 / ExoPlayer** + 前台服务 |
| 异步 | Kotlin Coroutines · Flow |
| 图片 | Coil |
| 本地配置 | DataStore / EncryptedSharedPreferences |
| 构建 | Gradle · JDK 17 |

### 仓库结构（精简）

```text
and/
├── app/src/main/java/com/madus/mobile/
│   ├── ai/           # AI 搜歌、哼唱、模型配置
│   ├── data/         # B 站 API、歌单导入、偏好存储
│   ├── domain/       # 领域模型与过滤
│   ├── player/       # 播放引擎与前台服务
│   ├── source/       # 音源抽象（B 站 / 演示）
│   └── ui/           # Compose 界面、组件、主题
├── docs/             # 架构与交接文档
├── apk/              # 本地发版产物（默认 gitignore）
└── scripts/          # 发布辅助脚本
```

---

## 快速开始

### 安装（普通用户）

1. 打开 **[Releases · Latest](https://github.com/zyjshb/Madus/releases/latest)**
2. 下载 **`Madus-x.y.z.apk`**（正式包，非 debug）
3. 系统设置中允许安装未知来源应用
4. 安装并打开；侧载时系统风险提示属常见现象，请确认来源为本仓库

> App 内路径：**我的 → 检查更新** → 自动打开本页下载。

### 构建（开发者）

**环境要求**

- JDK **17**
- Android SDK（compileSdk 35）
- Windows / macOS / Linux 均可

```powershell
# Windows 示例
$env:JAVA_HOME = 'C:\path\to\jdk17'
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"

cd Madus
.\gradlew.bat :app:assembleRelease
```

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleRelease
```

产物位置：

```text
app/build/outputs/apk/release/app-release.apk
```

可选：使用仓库内 `scripts/publish-release.ps1` 推送代码并创建 GitHub Release（需已 `gh auth login`）。

---

## 截图

> 可将界面截图放入 `docs/screenshots/` 并在此引用，例如：

```markdown
| 推荐电台 | 清屏短视频 | 曲库 |
|:---:|:---:|:---:|
| ![](docs/screenshots/recommend.png) | ![](docs/screenshots/immersive.png) | ![](docs/screenshots/library.png) |
```

---

## 路线图（Roadmap）

- [x] B 站登录 / 搜索 / 收藏 / 推荐电台  
- [x] 清屏短视频与手势模式  
- [x] 外站歌单导入  
- [x] AI 搜歌与哼唱双引擎  
- [x] GitHub Releases 应用内更新入口  
- [ ] 更完善的安装引导与版本检测提示  
- [ ] 性能与耗电持续打磨  
- [ ] 文档与贡献指南完善  

欢迎通过 [Issues](https://github.com/zyjshb/Madus/issues) 提出想法与缺陷。

---

## 贡献

1. Fork 本仓库  
2. 创建特性分支：`git checkout -b feature/your-idea`  
3. 提交清晰的 commit  
4. 发起 Pull Request  

开发时请尽量遵守现有 Compose 线稿风格与硬约束（例如：清空队列需 Stop、勿在 `onDestroy` 中错误释放播放器等——详见 `docs/`）。

---

## 声明

**Madus 是个人/社区向的开源客户端。**

- 代码以 [MIT License](LICENSE) 开源，你可以学习、修改与分发代码本身。  
- **音源与媒体内容版权归 B 站、创作者及其他权利人所有。**  
- 本项目 **不提供** 任何未授权的音乐版权；外站歌单导入仅解析歌名并在 B 站检索可播内容，**不接入第三方播放源**。  
- 请遵守相关平台服务条款与当地法律法规；因使用本软件产生的风险由使用者自行承担。  

本软件与 Bilibili、网易云音乐、QQ 音乐等品牌 **无官方从属关系**。

---

## 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose) · [Media3](https://developer.android.com/guide/topics/media/media3) · [Coil](https://coil-kt.github.io/coil/)  
- 社区与内测同学的反馈  
- 开源生态中所有可复用的优秀实践  

---

<p align="center">
  <sub>Made with restraint · Madus</sub><br/>
  <a href="https://github.com/zyjshb/Madus/releases/latest">Download</a>
  ·
  <a href="https://github.com/zyjshb/Madus/issues">Issues</a>
  ·
  <a href="LICENSE">MIT</a>
</p>
