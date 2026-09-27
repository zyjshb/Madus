# Madus iOS 原生版

这是独立的 SwiftUI + AVPlayer 客户端，最低 iOS / iPadOS 16。手机直接访问 B 站；无需部署服务器。Android 工程继续使用原来的 Gradle 构建。

**当前为 0.1.0 移植初版。没有上架 App Store，也没有包含签名证书。源码检查不能代替 Xcode 编译和 iPhone 实测。**

## 已实现的范围

- 歌名、歌手、BV 号 / 包含 BV 号的完整链接搜索，分页与分 P 展开。
- AAC 音频播放、暂停、进度拖动、上下首、列表 / 单曲循环、打乱队列。
- 后台音频模式、锁屏媒体信息与控制、音频设备选择、耳机拔出暂停、音频中断处理。
- 本地喜欢与最近播放；通过 B 站官方登录网页登录，在钥匙串保存会话，读取账号收藏夹。
- 播放使用公开的 `AVAssetResourceLoaderDelegate` 接口附加来源请求头，流式读取 HTTP Range，不预下载整首歌曲。
- 没有内置任何人的 B 站 Cookie 或 Apple 凭据。

尚未移植 Android 的推荐学习、AI / 哼唱识曲、评论、外站歌单导入、短视频、下载与音效。短链接 `b23.tv` 需先在浏览器展开为包含 BV 号的完整链接。心形按钮仅收藏到本机，不修改 B 站收藏夹。列表顺序在当前运行期间保留，重启后从音乐库重新选择播放。

## 只有 Windows、没有付费开发者账号

可用路径是：**GitHub 的 macOS 机器编译 → 下载未签名 IPA → 朋友用自己的 Apple 账号侧载签名安装**。不需要购买 Mac，也不需要把 Apple 密码或证书放入 GitHub。

### 1. 编译 IPA

仓库根目录已经包含 `.github/workflows/ios-ipa.yml`：

1. 将本次 iOS 文件和工作流提交到仓库的 `codex/ios-native` 分支，push 自动触发构建。工作流也会在涉及 iOS 的 PR 中运行。
2. 在 GitHub 仓库的 **Actions → iOS IPA** 打开本次运行。
3. 等待核心测试和 iOS Release 编译都通过，再下载底部 **Artifacts → Madus-iOS-unsigned**。
4. 解压得到 `Madus-iOS-unsigned.ipa` 及 SHA-256 文件。

工作流合入默认分支后，也可在 Actions 使用 **Run workflow** 手动构建。未签名 IPA 不能直接点开安装，也不是网页安装链接。构建文件只保留 14 天；下载后可以自己保存。

如果推送工作流提示 OAuth token 缺少 `workflow` 权限，需要在 Windows 终端自行运行 `gh auth refresh -h github.com -s workflow`，完成 GitHub 登录授权，然后重试推送。这只用于允许更新构建工作流。

### 2. 朋友签名安装

按 [AltStore Classic 官方 Windows 安装指南](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) 安装 AltServer 和所需苹果组件，再用数据线连接 iPhone、信任电脑，通过 AltServer 安装 AltStore。官方指南还说明了 iOS 16 及以后版本的开发者模式设置。

将 IPA 保存到朋友 iPhone 的“文件”中，在 AltStore Classic 的 **My Apps → +** 选择该文件，按提示用**朋友自己的 Apple 账号**签名安装。在苹果要求时信任相应开发者证书。不要把 Apple 账号密码发送给本项目作者。

免费个人账号的描述文件有效期是 **7 天**，需要续签；同一设备同时可用的免费侧载应用数量也有限。AltStore 的刷新条件和系统兼容性以其官方文档为准。免费方案不等于“安装一次永久使用”。

- [苹果个人开发者账号与 7 天期限](https://developer.apple.com/help/account/basics/about-your-developer-account)
- [AltStore 应用刷新与安装限制](https://faq.altstore.io/altstore-classic/your-altstore)

如果以后使用付费 Apple Developer Program，可以按 [Ad Hoc 注册设备分发](https://developer.apple.com/help/account/provisioning-profiles/create-an-ad-hoc-provisioning-profile) 流程给朋友设备签名；这仍需要对应的设备注册和描述文件。改变分发方式本身不提供 B 站内容授权。

## 有 Mac 时本地构建

需要 Xcode 16 或更新版本，安装 iOS SDK、Xcode 命令行工具，以及 Node.js 20 或更新版本。

```bash
# 在仓库根目录执行：测试核心逻辑、编译并打包未签名 IPA
bash scripts/build-ios.sh
```

产物：`ios/build/Madus-iOS-unsigned.ipa`。脚本不使用第三方 Swift 依赖，也不读取签名凭据。

直接在 Mac 打开 `ios/Madus.xcodeproj`，选中 Madus target，在 **Signing & Capabilities** 选择自己的 Personal Team，并将 Bundle Identifier 改为自己的唯一值。连接并信任 iPhone，选择真机，点击 Run，可使用 Xcode 签名安装。个人账号仍有期限限制。

修改 Swift 文件列表后，从仓库根目录运行：

```bash
node scripts/generate-ios-project.mjs
node scripts/generate-ios-project.mjs --check
swift test --package-path ios
```

可选的匿名接口连通性检查（Windows 也可执行）：`node scripts/check-bilibili-ios.mjs`。它验证搜索、AAC 地址及 `bytes=0-1` 分段响应，不代表已在 iPhone 上播放成功。

## 首次真机验收

构建成功只证明可编译；给朋友长期使用前，在真实 iPhone 验证以下流程：

1. 搜索普通歌曲与 BV 链接，连续快速切换搜索，确认旧结果不会覆盖新结果。
2. 播放一首、拖到中间、暂停恢复；打开分 P 列表并选择后续分 P。
3. 连续快速切歌，确认最终播放的是最后选中的音乐；测试队尾停止、列表循环、单曲循环。
4. 锁屏持续播放，操作锁屏上的暂停 / 上下首 / 进度；使用耳机并拔出，确认暂停。
5. 接听电话后恢复；切换 Wi-Fi / 蜂窝网络，验证中断后的重新播放。
6. 登录自己的 B 站账号，查看分页收藏夹；退出后再次打开应用，确认登录状态已清除。
7. 喜欢几首歌，重启后确认本地收藏仍在。验证暗色模式、iPad、较大的系统字体。

Apple 模拟器不能完整验证真机后台、锁屏、系统音频路由和签名安装。B 站接口行为可能变化；出现平台错误时会显示原因，不承诺所有内容均可播放。

## 文件结构

- `Madus/Core/`：模型、B 站数据解析、接口、签名与播放队列规则；`Tests/` 为 Swift Package 测试。
- `Madus/AudioResourceLoader.swift`：媒体分段传输；`PlayerModel.swift`：原生播放与锁屏。
- `Madus/AccountStore.swift`：钥匙串会话；`BilibiliLoginView.swift`：官方网页登录。
- `Madus/ContentView.swift`、`PlayerView.swift`：原生界面。
- `Madus.xcodeproj/`：可直接打开的 Xcode 工程，由 `scripts/generate-ios-project.mjs` 生成。

本项目使用平台已有接口，音源与内容版权归原作者 / 平台所有。仅私下分享 App 不改变内容本身的授权范围。
