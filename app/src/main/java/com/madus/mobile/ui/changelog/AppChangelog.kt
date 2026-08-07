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
            version = "1.14.13",
            date = "2026-08-07",
            added = listOf(
                "关于 Madus 连点 7 次可打开隐藏页（版本号 + 画板）",
            ),
            changed = listOf(
                "换歌搜索框不预填旧歌名",
                "导入输入框改为空白",
            ),
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
