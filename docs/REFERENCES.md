# 设计与架构参考（调研摘要）

由并行 agent 调研整理，已融入当前骨架实现。

## UI 参考

| 来源 | 采用 |
|------|------|
| Spotify | 底栏 + 迷你播放条 + 全屏 Now Playing；列表左封面右文案 |
| Apple Music / Podcasts | 播放页留白与主次控件层级 |
| Retro Music / InnerTune / Vinyl（开源） | 曲库分区清晰、Compose 分层、歌词作为播放页状态而非新 Tab |
| 黑白 editorial / monochrome 趋势 | 纸墨色、细线、少阴影、一个实心主操作 |

## 明确不采用

霓虹渐变、默认开粒子/频谱花活、底栏塞过多 Tab、厚重毛玻璃堆叠、首页信息流过载。

## 架构参考

| 模式 | 采用 |
|------|------|
| MusicSource 插件 | `source/MusicSource.kt` + Demo/三端 stub |
| 统一领域模型 | `domain/Models.kt` |
| 播放与 UI 解耦 | `PlayerController` 现为内存；后续换 Media3 Service |
| 鉴权与曲库分离 | 登录页独立；凭证后续加密存储 |
| MVP 阶梯 | 壳 → 假数据 → 真播放 → 单源 → 多源 |

## 合规备注

真实音源接入应走可维护的适配器 +（理想情况）自有 BFF/官方允许方式；Cookie/接口变更与商店政策需单独评估，不写进 UI 层。
