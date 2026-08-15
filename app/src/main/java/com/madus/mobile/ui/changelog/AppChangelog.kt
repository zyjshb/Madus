package com.madus.mobile.ui.changelog

/**
 * 应用内更新日志。
 *
 * 格式参考 [Keep a Changelog](https://keepachangelog.com/)：
 * **新增 / 变更 / 修复** 分类；发版时往 [entries] 最前面加一条。
 *
 * 打开方式：我的 → 检查更新 → **连点 3 次**。
 */
object AppChangelog {

    data class Entry(
        val version: String,
        val date: String,
        /** 新增能力 */
        val added: List<String> = emptyList(),
        /** 行为或体验调整 */
        val changed: List<String> = emptyList(),
        /** 缺陷修复 */
        val fixed: List<String> = emptyList(),
        /** 已知限制（可选） */
        val known: List<String> = emptyList(),
    ) {
        /** 是否有任何正文 */
        val isEmpty: Boolean
            get() = added.isEmpty() && changed.isEmpty() && fixed.isEmpty() && known.isEmpty()
    }

    val entries: List<Entry> = listOf(
        Entry(
            version = "1.16.8",
            date = "2026-08-15",
            fixed = listOf(
                "连点换壁纸不再叠一堆「已换一张」，底栏不会被挡住",
            ),
        ),
        Entry(
            version = "1.16.7",
            date = "2026-08-15",
            added = listOf(
                "画境可开关跟随壁纸配色，也能改回香槟金",
                "画境壁纸可调模糊",
            ),
            changed = listOf(
                "玻璃通透是看后面，着色会染上强调色",
                "壁纸栏不再同时出现「相册自选」和「从相册选择」",
                "检查更新和更新日志跟画境走",
            ),
        ),
        Entry(
            version = "1.16.6",
            date = "2026-08-15",
            changed = listOf(
                "画境迷你条点开进电台，跟简约一样，不再进独立播放页",
            ),
            fixed = listOf(
                "画境标题和列表字还是黑的，亮壁纸上看不见",
            ),
        ),
        Entry(
            version = "1.16.5",
            date = "2026-08-15",
            changed = listOf(
                "画境电台页不再叠迷你条；迷你条可切上一首/下一首",
                "底栏玻璃会糊到壁纸；配色跟壁纸走，亮图自动压暗",
            ),
            fixed = listOf(
                "换一张壁纸没反应",
                "点随机再点固定会卡退",
            ),
        ),
        Entry(
            version = "1.16.4",
            date = "2026-08-15",
            added = listOf(
                "画境壁纸可每日随机（t.alcy.cc），能固定、下载、相册自选",
            ),
            changed = listOf(
                "画境改回简约排版和功能，只留壁纸和玻璃底栏",
            ),
        ),
        Entry(
            version = "1.16.3",
            date = "2026-08-14",
            changed = listOf(
                "画境电台改成 16:9 封面舞台 + 双列封面墙",
                "首页私人 FM 改成整卡封面，点卡开播",
            ),
        ),
        Entry(
            version = "1.16.2",
            date = "2026-08-14",
            added = listOf(
                "新主题「画境」：整页壁纸，可从相册自选",
            ),
            changed = listOf(
                "液态玻璃换成画境：香槟金描边、编号歌单、悬浮玻璃底栏",
            ),
        ),
        Entry(
            version = "1.16.1",
            date = "2026-08-14",
            changed = listOf(
                "液态首页改成接着听大海报 + 封面货架",
                "曲库改成大封面钉选，搜索空态直接出最近听过",
                "电台改成全宽电台卡 + 推荐封面墙",
            ),
        ),
        Entry(
            version = "1.16.0",
            date = "2026-08-14",
            changed = listOf(
                "液态主题重做层级与排版：玻璃只给底栏、迷你条、返回钮和操作表",
                "液态底栏改成图标加字的悬浮胶囊，去掉假手势条",
                "液态点歌出迷你条，点条进播放页；电台改成浏览，不再当第二套播放台",
                "首页改货架，搜索改胶囊，曲库和我的改成分组列表",
            ),
        ),
        Entry(
            version = "1.15.9",
            date = "2026-08-14",
            changed = listOf(
                "液态底栏改悬浮，底下留一条全面屏提示",
                "迷你条只能暂停，不能切歌",
                "切页、迷你条进出、切歌标题带过渡",
            ),
        ),
        Entry(
            version = "1.15.8",
            date = "2026-08-14",
            changed = listOf(
                "推荐「更多」挪到右上角，弹出实色菜单，不再透明",
            ),
        ),
        Entry(
            version = "1.15.7",
            date = "2026-08-14",
            changed = listOf(
                "推荐「更多」改成 iOS 那种操作表：一组选项 + 单独取消",
                "玻璃改用更接近苹果的材质（模糊更开、边更实）",
            ),
        ),
        Entry(
            version = "1.15.6",
            date = "2026-08-14",
            added = listOf(
                "我的里可直接退出 B 站登录",
            ),
            changed = listOf(
                "推荐「更多」改成分组列表，不再横滑一排胶囊",
                "推荐播放时封面铺到玻璃底栏后面，去掉底下那条白边",
                "切 Tab、出迷你条、点播放带一点弹簧",
            ),
            fixed = listOf(
                "没登录却显示已登录：过期 Cookie 不再当登录",
            ),
        ),
        Entry(
            version = "1.15.5",
            date = "2026-08-14",
            changed = listOf(
                "撤回上一版误做的悬浮叠层",
                "推荐改成大封面 + 简单播放键，不再把按钮塞进一块玻璃盒子",
                "底栏改成胶囊，只留图标",
            ),
        ),
        Entry(
            version = "1.15.3",
            date = "2026-08-14",
            changed = listOf(
                "推荐：封面铺满当墙，控件坐在一块玻璃托盘上",
                "液态底栏能透出后面的内容，不再是一层白膜",
            ),
        ),
        Entry(
            version = "1.15.2",
            date = "2026-08-14",
            changed = listOf(
                "液态底栏改回五个入口，迷你条和 Tab 合成一块玻璃",
                "首页用大封面撑，不再做灰底设置页",
            ),
        ),
        Entry(
            version = "1.15.1",
            date = "2026-08-14",
            changed = listOf(
                "液态玻璃重做：只给底栏和迷你条用玻璃，页面改实色分组",
                "液态底栏改成首页/曲库/我的，搜索单独一颗圆钮",
                "首页改横滑封面，我的改左对齐设置列表",
            ),
        ),
        Entry(
            version = "1.15.0",
            date = "2026-08-14",
            added = listOf(
                "主题可切「简约 / 液态玻璃」",
                "液态玻璃：首页、搜索、推荐、曲库、我的整页另排，浮动底栏和迷你条",
                "液态可调深浅和玻璃浓度",
            ),
            changed = listOf(
                "简约仍是默认，原来的形态和色板还在",
            ),
        ),
        Entry(
            version = "1.14.48",
            date = "2026-08-13",
            changed = listOf(
                "推荐：只有播过才算看过；点赞后会重排后面几条",
            ),
            fixed = listOf(
                "评论里的图片可以点开放大",
            ),
        ),
        Entry(
            version = "1.14.47",
            date = "2026-08-13",
            changed = listOf(
                "推荐：长视频看一半才算喜欢，很快划走会降这类",
                "连着划走同一类，后面先躲开",
                "刚点赞会搅动后面几条，续刷也不再一条 related 就停",
            ),
        ),
        Entry(
            version = "1.14.46",
            date = "2026-08-13",
            changed = listOf(
                "听歌默认改「较高」，走高码率音频，不再捡最糊那条",
                "新音效「精听」：人声清楚、低音不轰",
                "点迷你条回到推荐电台",
            ),
            fixed = listOf(
                "听歌先取音频轨；有更高码的也会用上",
            ),
        ),
        Entry(
            version = "1.14.45",
            date = "2026-08-13",
            changed = listOf(
                "听歌页可直接切循环 / 随机 / 单曲，不用再进队列",
                "听歌页可点喜欢，不用再绕到视频页",
                "听歌页可点音效",
                "点迷你条进听歌页，不再绕去推荐电台",
                "迷你条可上一首，切歌时会转圈",
            ),
            fixed = listOf(
                "听歌页切歌会转圈，不再像卡住；播不了会写出原因",
                "刚起播也能拖进度，不再要等时长出来才动得了",
                "听歌切歌先切封面标题，少一段停在上一首的空窗",
            ),
        ),
        Entry(
            version = "1.14.44",
            date = "2026-08-13",
            fixed = listOf(
                "看一半上滑再滑回来，视频会从上次位置接着播，不再从头开始",
            ),
        ),
        Entry(
            version = "1.14.43",
            date = "2026-08-12",
            changed = listOf(
                "「我的」检查更新会直接写出有没有新版本",
                "更新页始终可打开 Gitee / GitHub 下载，不依赖检测结果",
            ),
            fixed = listOf(
                "检查更新不再把 Gitee 列表里的旧版当成最新",
            ),
        ),
        Entry(
            version = "1.14.42",
            date = "2026-08-12",
            fixed = listOf(
                "点「播放 为你推荐」一次即可起播，不再二次弹出按钮或要点两次才出画面",
            ),
        ),
        Entry(
            version = "1.14.41",
            date = "2026-08-12",
            fixed = listOf(
                "修复推荐页起播按钮二次闪现或卡在加载中",
            ),
        ),
        Entry(
            version = "1.14.40",
            date = "2026-08-12",
            added = listOf(
                "推荐：本地统一行为事件 + B 站分区/标签内容画像",
                "点赞/收藏后异步软插入第 2~4 位，不打断当前播放",
                "四层兴趣打分：实时 / 小时 / 长期 / 负反馈，带主题冷却",
            ),
            changed = listOf(
                "推荐流重排器升级：同 UP、同主题窗口 + 探索/每日基线配额",
                "快速跳过会被记录并临时降低同类推荐权重",
            ),
        ),
        Entry(
            version = "1.14.39",
            date = "2026-08-12",
            changed = listOf(
                "推荐：近 1 小时点赞会更快影响推荐流，并做 UP/题材打散",
            ),
        ),
        Entry(
            version = "1.14.38",
            date = "2026-08-12",
            changed = listOf(
                "普通搜索对齐 B 站：综合排序、每页 42 条，可下拉加载更多",
            ),
        ),
        Entry(
            version = "1.14.37",
            date = "2026-08-12",
            changed = listOf(
                "桌面图标蛇标略缩小，周围多留白，不那么顶边",
            ),
        ),
        Entry(
            version = "1.14.36",
            date = "2026-08-12",
            changed = listOf(
                "应用图标与开屏换新品牌蛇标（含播放键）",
                "开屏：轻放大渐入 → 定格 → 渐出，底色对齐新 logo",
            ),
        ),
        Entry(
            version = "1.14.35",
            date = "2026-08-10",
            added = listOf(
                "播放设置：网络使用四档（最省 / 均衡 / 流畅 / 充足）",
            ),
            changed = listOf(
                "预取与推荐续刷按档位调节；不关点播切歌等功能",
            ),
        ),
        Entry(
            version = "1.14.34",
            date = "2026-08-10",
            fixed = listOf(
                "修复自定义缓冲导致启动即崩（改回官方缓冲）",
            ),
            changed = listOf(
                "后台少预取、推荐流轻量续刷，减轻打游戏抢网",
                "整体缓冲略收（约 12–20 秒），比原先 50 秒省网",
            ),
        ),
        Entry(
            version = "1.14.33",
            date = "2026-08-10",
            changed = listOf(
                "外站导入改为每批 500 首：先导一批，可点「继续添加」再导下一批",
            ),
        ),
        Entry(
            version = "1.14.32",
            date = "2026-08-10",
            changed = listOf(
                "网易/QQ 等外站导入上限由 500 提到 2000 首（过大仍会截断并提示）",
            ),
            fixed = listOf(
                "退出再马上点歌单，不再误触「播放全部」自己开播（等手指抬起后再允许播放）",
            ),
        ),
        Entry(
            version = "1.14.31",
            date = "2026-08-10",
            fixed = listOf(
                "退出后连点不再误触「播放全部」：仅约 0.3 秒挡播放，滑动返回不卡",
                "退出再进歌单导航更轻，减少卡手感",
            ),
        ),
        Entry(
            version = "1.14.30",
            date = "2026-08-10",
            fixed = listOf(
                "去掉进歌单 1 秒多锁操作，打开和点播放更跟手",
                "同一歌单再进直接出缓存列表，不再先闪加载中",
            ),
        ),
        Entry(
            version = "1.14.29",
            date = "2026-08-10",
            fixed = listOf(
                "首页/曲库点歌单封面只进列表，返回不会掉进播放台",
                "歌单内点播放留在列表页（迷你条），不再自动跳推荐台",
            ),
        ),
        Entry(
            version = "1.14.28",
            date = "2026-08-10",
            fixed = listOf(
                "点歌单封面只进列表；须点「播放全部」或某一首才会进播放台",
            ),
        ),
        Entry(
            version = "1.14.27",
            date = "2026-08-10",
            fixed = listOf(
                "第二次点歌单封面只进详情，不再自动跳推荐并开播",
            ),
        ),
        Entry(
            version = "1.14.26",
            date = "2026-08-10",
            fixed = listOf(
                "退出再进歌单不再误触「播放全部」跳到推荐页开播",
            ),
        ),
        Entry(
            version = "1.14.25",
            date = "2026-08-09",
            fixed = listOf(
                "退出歌单不再闪一下「0 首」空界面",
                "歌单第一首点上一首直接到最后一首（不用点两次）",
            ),
        ),
        Entry(
            version = "1.14.24",
            date = "2026-08-09",
            fixed = listOf(
                "B 站收藏夹点第二次变空白",
                "本地/导入歌单反复进出空白与正常界面循环",
                "歌单详情系统返回与顶栏返回一致，不再叠多层详情页",
            ),
            changed = listOf(
                "打开收藏夹不再自动 purge（仍过滤失效展示；清失效改手动）",
            ),
        ),
        Entry(
            version = "1.14.23",
            date = "2026-08-09",
            fixed = listOf(
                "B 站收藏夹打开时自动清除失效视频，列表不再显示已失效稿",
                "清理改为官方 clean + 批量删除双通道，残留失效也会删掉",
            ),
        ),
        Entry(
            version = "1.14.22",
            date = "2026-08-09",
            added = listOf(
                "B 站收藏夹可一键清除已删除/失效视频（单夹或全部）",
            ),
            fixed = listOf(
                "歌单循环到第一首后点上一首误重播当前的问题",
            ),
        ),
        Entry(
            version = "1.14.21",
            date = "2026-08-09",
            fixed = listOf(
                "外站导入歌单播完不再自动塞推荐视频，改为整表循环",
                "B 站收藏夹播放列表不再只剩当前页 40 首，会加载完整歌单",
                "UP 投稿/合集点播同样拉全量入队并循环",
            ),
        ),
        Entry(
            version = "1.14.20",
            date = "2026-08-07",
            added = listOf(
                "更新安装后自动清理应用内下载的安装包",
            ),
            changed = listOf(
                "关于连点彩蛋改为画板",
                "同一曲听约 5 分钟提示可换歌",
            ),
        ),
        Entry(
            version = "1.14.17",
            date = "2026-08-07",
            changed = listOf(
                "关于连点彩蛋改回小恐龙",
                "外观设置与关于拆开",
            ),
        ),
        Entry(
            version = "1.14.16",
            date = "2026-08-07",
            changed = listOf("关于连点彩蛋曾改为电风扇"),
        ),
        Entry(
            version = "1.14.15",
            date = "2026-08-07",
            added = listOf(
                "超长曲听太久时顶部轻提示可换歌",
                "B 站收藏夹封面补全",
            ),
        ),
        Entry(
            version = "1.14.14",
            date = "2026-08-07",
            changed = listOf(
                "换歌搜索框不预填旧歌名",
                "导入输入框改为空白",
            ),
        ),
        Entry(
            version = "1.14.13",
            date = "2026-08-07",
            changed = listOf("请更新到 1.14.14"),
        ),
        Entry(
            version = "1.14.12",
            date = "2026-08-07",
            changed = listOf("请更新到 1.14.13"),
        ),
        Entry(
            version = "1.14.11",
            date = "2026-08-07",
            added = listOf(
                "检查更新：国内优先 Gitee，GitHub 作备用；失败自动换源",
                "更新页提供 Gitee / GitHub 浏览器下载入口",
            ),
        ),
        Entry(
            version = "1.14.10",
            date = "2026-08-07",
            fixed = listOf(
                "1.14.9 更新后无法打开（启动崩溃）：缓冲策略封装错误",
                "改回官方缓冲实现；游戏轻量功能保留（预取/写盘/刷新），可正常启动",
            ),
        ),
        Entry(
            version = "1.14.9",
            date = "2026-08-07",
            added = listOf(
                "播放设置：「游戏轻量」开关（默认关）— 更小缓冲、暂停边听写盘、后台更省",
            ),
            changed = listOf(
                "P2：可切换缓冲策略；不关任何功能，关开关即恢复正常参数",
            ),
            known = listOf(
                "打游戏时建议：继续播放开着 + 需要时再开游戏轻量",
            ),
        ),
        Entry(
            version = "1.14.8",
            date = "2026-08-07",
            changed = listOf(
                "P1 后台降载：进其它 App/打游戏时减少预取、通知刷新与进度写入频率",
                "后台只预解析下一首；推荐无限流仅在快见底时轻量续刷",
                "后台释放封面内存缓存，给游戏让出一点内存",
            ),
            known = listOf(
                "不删不改任何功能；回前台后行为与原来一致",
            ),
        ),
        Entry(
            version = "1.14.7",
            date = "2026-08-07",
            added = listOf(
                "播放设置：「打游戏时继续播放」（默认开）— 游戏按钮音效不再把歌掐掉",
            ),
            changed = listOf(
                "切到后台/打游戏时降低进度刷新频率，省一点 CPU 与电量，功能不变",
            ),
            fixed = listOf(
                "打游戏点按钮导致 Madus 音乐暂停、无法边玩边听的问题",
            ),
        ),
        Entry(
            version = "1.14.6",
            date = "2026-08-07",
            added = listOf(
                "更新页：「重新下载」「浏览器下载」备用入口",
                "下载后强制校验：大小、ZIP 头、AndroidManifest / dex，坏包不进安装器",
            ),
            changed = listOf(
                "安装包改存 files 目录（避免 cache 被清导致解析失败）",
                "安装前给所有系统安装器授予 FileProvider 读权限，兼容更多 ROM",
            ),
            fixed = listOf(
                "应用内更新偶发「解析软件包时出现问题」（不完整/非 APK 内容被拿去安装）",
            ),
        ),
        Entry(
            version = "1.14.5",
            date = "2026-08-07",
            added = listOf(
                "检查更新：真实进度条 + 已下/总大小百分比，一眼能看出是否已开始下载",
            ),
            changed = listOf(
                "下载进度在主线程刷新，连接/开始/完成状态更明确",
                "手动跟随 GitHub 重定向，更稳拿到安装包大小",
                "已下载的包会缓存；开完「安装未知应用」返回后可继续安装，不必重下",
            ),
            fixed = listOf(
                "应用内更新下载过程无反馈、不易判断是否在下的问题",
            ),
        ),
        Entry(
            version = "1.14.4",
            date = "2026-08-07",
            added = listOf(
                "换歌：队列点搜索图标 / 本地歌单 ⋯「搜索换歌」→ 搜索正确结果点一下替换",
                "若来自本地导入歌单，替换会写回歌单并同步队列",
            ),
        ),
        Entry(
            version = "1.14.3",
            date = "2026-08-07",
            changed = listOf(
                "检查更新：进入更新页展示当前/最新版与说明，由用户点「更新到最新版」才下载安装",
                "不会一点「检查更新」就自动下载",
            ),
        ),
        Entry(
            version = "1.14.2",
            date = "2026-08-07",
            added = listOf(
                "检查更新：应用内可下载正式 APK 并调起安装",
            ),
            changed = listOf(
                "更新只下载正式包，自动忽略 debug",
                "未开「安装未知应用」时会引导授权后再装",
            ),
        ),
        Entry(
            version = "1.14.1",
            date = "2026-08-07",
            added = listOf(
                "「我的 → 检查更新」可打开 GitHub Releases 下载最新版",
            ),
            changed = listOf(
                "检查更新副标题改为「版本号 · 下载最新」",
            ),
        ),
        Entry(
            version = "1.14.0",
            date = "2026-08-07",
            changed = listOf(
                "外站歌单导入恢复「高召回」匹配：搜「歌名 + 歌手」取结果，标题含歌名优先",
                "仅轻度清洗 VIP / 括号噪声，去掉 1.13.9 过严的打分阉割",
            ),
            known = listOf(
                "部分 VIP / 冷门曲仍可能匹配到标题相近但内容不准的 B 站稿件",
            ),
        ),
        Entry(
            version = "1.13.9",
            date = "2026-08-07",
            added = listOf(
                "设置 → 主题色：新增「深色墨黑」（纯黑主体）",
            ),
            changed = listOf(
                "外站导入曾改为强匹配（歌名+歌手严判）；因漏歌过多，已在 1.14.0 回调",
            ),
        ),
        Entry(
            version = "1.13.8",
            date = "2026-08-07",
            changed = listOf(
                "UP 主页投稿列表改为与收藏夹相同的小说式分页：每页 40，上一页 / 下一页，替换本页不无限堆",
            ),
        ),
        Entry(
            version = "1.13.7",
            date = "2026-08-07",
            changed = listOf(
                "短视频右侧 UP 头像去掉「主页」文字，只保留圆头像可点",
                "哼唱：ACR 优先 humming（audio 仅兜底），低把握不强推唯一答案",
                "哼唱候选可点击，按选中歌名再搜 B 站",
            ),
        ),
        Entry(
            version = "1.13.6",
            date = "2026-08-07",
            added = listOf(
                "清屏短视频右侧抖音式 UP 头像：点头像或 @昵称 进入 UP 主页",
            ),
            changed = listOf(
                "AI 搜页去掉「B站识曲」按钮（推荐页悬浮球保留）",
                "哼唱链路重做：双引擎错误透出；B 站落地策略调整",
            ),
        ),
        Entry(
            version = "1.13.5",
            date = "2026-08-07",
            added = listOf(
                "「我的 → 导入音乐」接通外站歌单导入（不再是空壳提示）",
            ),
            changed = listOf(
                "外站导入：主推多行「歌名 - 歌手」；支持网易云 / QQ / 酷狗 / 酷我链接；去掉汽水",
                "识曲悬浮球切换 Tab 后记住位置与收起状态",
                "推荐页上滑菜单：竖滑不抢横向滚动",
            ),
        ),
        Entry(
            version = "1.13.4",
            date = "2026-08-07",
            added = listOf(
                "UP 主页：card + stat 补齐头像 / 粉丝 / 关注 / 获赞；投稿 WBI 分页",
                "UP 主页关注按钮同步 B 站（需登录）",
                "播放队列显示完整列表（含已播），长按拖到目标位置排序",
            ),
            fixed = listOf(
                "上一首：失败跳歌不再往错误方向跳，并校准当前下标",
            ),
        ),
        Entry(
            version = "1.13.3",
            date = "2026-08-07",
            added = listOf(
                "推荐页左侧「B站识曲」悬浮球：读取官方 BGM 标签并搜可播；空闲收成细条",
            ),
            changed = listOf(
                "哼唱：ACR 与讯飞交叉合并；前 3 候选搜 B 站音乐区",
            ),
        ),
        Entry(
            version = "1.13.2",
            date = "2026-08-06",
            added = listOf(
                "UP 主页关注 / 取消关注",
                "评论 [doge] 等表情按官方 emote 图显示",
            ),
            changed = listOf(
                "UP 资料改 card 接口；投稿风控时用昵称搜索兜底",
                "合集改为线稿列表行",
            ),
        ),
        Entry(
            version = "1.13.1",
            date = "2026-08-06",
            added = listOf(
                "评论配图；楼中楼分页「加载更多」",
            ),
            changed = listOf(
                "B 站收藏夹小说式上一页 / 下一页",
                "网易云导入拉全量 trackIds + song/detail",
                "搜索错字：多排序 + 联想 + 去标点",
                "推荐更跟喜欢 / 本地 / 收藏种子走",
            ),
        ),
        Entry(
            version = "1.13.0",
            date = "2026-08-06",
            added = listOf(
                "曲库导入外站歌单（链接或文本）→ B 站匹配写入本地",
                "点 UP 名进主页：投稿 + 合集",
                "评论可展开楼中楼",
            ),
            changed = listOf(
                "队列可调顺序；推荐「全屏」进上滑菜单并可左右滑",
                "收藏夹分页加载",
            ),
        ),
        Entry(
            version = "1.12.x",
            date = "2026-08-06",
            changed = listOf(
                "普通搜索恢复 B 站全站视频（不再被识曲锁死音乐区）",
                "AI / 哼唱仍走识曲过滤，与普通搜索互不影响",
            ),
            added = listOf(
                "ACRCloud 哼唱识别（可与讯飞并存）",
            ),
        ),
        Entry(
            version = "1.11.x",
            date = "2026-08-06",
            changed = listOf(
                "哼唱改走讯飞歌曲识别，不再用大模型听辨哼唱",
                "录音预处理：裁静音、归一化、交叉比对",
            ),
            fixed = listOf(
                "修哼唱乱识别、空结果等问题（多版本迭代）",
            ),
        ),
        Entry(
            version = "1.10.x – 1.7.x",
            date = "2026-08",
            added = listOf(
                "AI 搜歌：文字 / 图 / 上传音视频",
                "本片 BGM 识别（后改为推荐页轻量官方标签球）",
            ),
            changed = listOf(
                "多语种搜歌、模型过程面板、B 站相关度打分等",
            ),
        ),
        Entry(
            version = "1.5.x – 1.1.x",
            date = "2026-08",
            added = listOf(
                "AI 搜歌初版、B 站登录 / 搜索 / 推荐",
                "清屏短视频手势、合集连播、本地歌单与缓存",
                "线稿主题、用户协议、媒体通知与前台服务",
            ),
            fixed = listOf(
                "熄屏续播、进程被杀、进度条与切歌等稳定性问题",
            ),
        ),
    )
}
