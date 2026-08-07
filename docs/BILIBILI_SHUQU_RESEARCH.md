# B 站智能识曲调研（Madus 1.9.0）

> 日期：2026-08-05
> 结论：**半可用，已接入轻量入口。** 官方链路读的是稿件/分 P 上创作者标注的 BGM 标签，不是通用音频指纹，冷门/魔改/变速/未标注稿件无结果。

## 1. 调研检查清单

| 项目 | 结论 |
|---|---|
| Web 播放器识曲入口 | 有，音符/识别 BGM 入口；依赖稿件自带 BGM 标签 |
| App 与 Web 差异 | 未发现可公开访问的音频指纹上传接口；App 端同类功能同样读稿件 BGM 元数据 |
| Request | `GET https://api.bilibili.com/x/web-interface/view/detail/tag` |
| 参数 | `bvid`（或 `aid`），可选 `cid` 精确到分 P |
| 鉴权 | Cookie（SESSDATA 等），与 Madus `mergedCookie()` 兼容；无 WBI/设备指纹要求 |
| 是否上传音频 | 否。该接口不是按播放进度/指纹识曲 |
| 失败码 | `code=0` 但空数组 = 未标注；`code != 0` 按 B 站通用错误码处理 |
| 日限/风控 | 未观察到特殊风控；沿用普通 Web 接口频率即可 |
| 合规 | 只调用 B 站页面同款公开 JSON 接口，不逆向客户端、不抓违规包 |

## 2. 接口细节

**URL**

```text
https://api.bilibili.com/x/web-interface/view/detail/tag?bvid=BVxxxx&cid=123
```

**方法**：GET

**请求头**：普通 Web UA + Referer `https://www.bilibili.com/video/{bvid}` + Cookie。

**响应示例**

```json
{
  "code": 0,
  "message": "0",
  "ttl": 1,
  "data": [
    {
      "tag_id": 0,
      "tag_name": "发现《Other Side》",
      "music_id": "MA456128506519140428",
      "tag_type": "bgm",
      "jump_url": "https://music.bilibili.com/h5/music-detail?music_id=MA456128506519140428"
    }
  ]
}
```

只取 `tag_type == "bgm"`；`tag_name` 常见形式为 `发现《歌名》`，少数会带歌手。`music_id` 可用于站内音乐页，Madus 当前用歌名走现有 B 站 search。

**补充字段**：部分稿件在 `https://api.bilibili.com/x/web-interface/player/v2?bvid=..&cid=..` 的 `data.bgm_info.music_title` 也有同源标签，Madus 已作为兜底读取。

## 3. 参考资料

- bilibili-API-collect 文档（视频标签/BGM）：https://sessionhu.github.io/bilibili-API-collect/docs/video/tags.html
- BBPlayer 文档提到 `bgm_info.music_title`：https://mintlify.wiki/bbplayer-app/BBPlayer/guides/lyrics-management
- 原 SocialSisterYi/bilibili-API-collect 仓库已停更/关停，不依赖其长期可访问性：https://github.com/SocialSisterYi/bilibili-API-collect

## 4. 实现

`BilibiliApi.recognizeBgm(bvid, cid, progressMs)`：

1. 请求 `view/detail/tag`，解析 `tag_type=="bgm"`。
2. 再读 `player/v2.bgm_info.music_title` 补充去重。
3. 抽歌名/歌手 → 转 `SongCandidate` → 走 `SongRanker.buildSearchQueries + rankTracks` 在 B 站搜可播视频。

UI：AI 搜歌页右上角「B站识曲」轻量文字按钮，取当前播放曲目 `bvid/cid`，结果以对话消息展示。未恢复 1.7.x 半屏 BGM 面板。

## 5. 失败文案

`B站未识别到曲目，可改用文字/哼唱。`

## 6. 边界

- 只对创作者标注 BGM 标签的稿件有效。
- 识曲只解决「是什么歌」，B 站落地脏结果仍由通用 `SongRanker` 过滤。
- 不接第三方 ACR/Shazam Key，不做破解或逆向。
