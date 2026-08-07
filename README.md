# Madus

简约 Android 听歌客户端：线稿 UI · B 站音源 · 推荐电台 · AI 搜歌 / 哼唱。

> 个人/学习向客户端。音源与内容版权归原平台及权利人所有；请合规使用。

## 功能概览

- B 站登录、搜索、收藏、UP 主页
- 推荐电台 / 清屏短视频
- 外站歌单导入（歌名匹配到 B 站）
- AI 搜歌、哼唱识别（需自备 Key）

## 安装

1. 打开 [Releases](../../releases/latest) 下载最新 `Madus-*.apk`
2. 允许「未知来源」安装（各品牌路径略有不同）
3. 若系统提示风险，属侧载常见提示，请自行判断来源

## 构建

```powershell
# 需要 JDK 17
$env:JAVA_HOME='你的\jdk17路径'
.\gradlew.bat :app:assembleRelease
```

产物：`app/build/outputs/apk/release/`

## 更新

App 内「我的 → 检查更新」会打开本仓库 Releases 页（需在 `AppUpdate.kt` 配置正确地址）。

## License

代码以 [MIT](LICENSE) 授权。**不包含**任何第三方音源/平台的内容授权。
