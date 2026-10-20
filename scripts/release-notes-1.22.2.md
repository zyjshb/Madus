## 修复切歌闪退：Java 21 API 在 Android 上不存在

- 修掉「播放一段时间后切下一首一直加载不出来、然后闪退」的问题。根因是 1.22.0 里用了 `MutableList.addFirst(...)`，它实际解析到 Java 21 的 `SequencedCollection` 接口方法，Android 运行时没有该接口，切歌重排时抛 `NoSuchMethodError`。
- 崩溃点已定位到 `RecommendationFusion.repeatProfiles`：只要当天有过播放记录，下一次重排就会命中，所以表现为「第一首没事，播一会儿切歌就崩」。
- 新增源码级守卫测试 `Jdk21ApiGuardTest`：扫描主源码里的 `addFirst / addLast / removeFirst / removeLast / getFirst / getLast`，防止这类「编译和单测都过、真机才崩」的 JDK 21 API 再次混进来。
- 保留 1.22.1 的诊断能力：崩溃堆栈与每 60 秒的内存/队列心跳仍会写入日志，并镜像到系统「下载/Madus/」。
- 推荐融合逻辑（重复收听窗口、歌手反馈、听歌模式、待播接缝、每日 5 点刷新）与 1.22.0 一致。

已在雷电模拟器（Android 14 / API 34，已登录）上验证：连续播放并连续切歌 3 次，进程存活、无崩溃、每首都成功取流。

### 下载

- 安装包：`Madus-1.22.2.apk`
- [Gitee 国内下载](https://gitee.com/dikoklhf/madus/releases/tag/v1.22.2)
- [GitHub 下载](https://github.com/zyjshb/Madus/releases/tag/v1.22.2)

也可在应用内进入「我的 → 检查更新」。

126 项自动测试及 Release 构建通过。

SHA-256：34973f888d17fab509f7d95624bad3d47db3a9afcf00ee0eaa13c765f817833d
