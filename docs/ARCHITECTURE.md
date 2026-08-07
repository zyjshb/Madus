# Madus Mobile · 架构

## 目标

独立 APK：B 站登录 / 搜索 / 播放，**不依赖** Node `server.js`。

## 音源

| 实现 | 状态 |
|------|------|
| DemoSource | 公开 MP3 演示 |
| BilibiliSource | WebView Cookie + 直连 api.bilibili.com |
| 网易 / QQ | **已删除** |

## 播放

- `PlayerEngine`：ExoPlayer + B 站 CDN 所需 Referer  
- `PlaybackService`：MediaSession  

## B 站链路

```
WebView 登录 → CookieManager(SESSDATA…)
→ 搜索 /x/web-interface/search/type
→ view 取 cid
→ playurl dash.audio
→ ExoPlayer
```

## UI

曲库：**点音源卡片登录**（无「管理登录」二级页）。
