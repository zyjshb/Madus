# Codex / DeepSeek 工人 · 调用约定（Grok 总管）

**日期：** 2026-08-06  
**目的：** Grok 当总管，按任务类型决定亲自做 or 派 `codex exec`（DeepSeek）写代码，省 Grok token。

## 1. 本机入口

| 命令 | 作用 |
|------|------|
| `codex` | `C:\Users\djnio\bin\codex.cmd` → 自动找桌面版最新 `codex.exe` |
| `codex-worker.ps1` | 非交互工人包装（`--skip-git-repo-check`、写 last message） |

默认模型见 `~/.codex/config.toml`：`deepseek-v4-flash` @ `api.deepseek.com`。

### 探测

```bat
codex --version
codex doctor
```

### 工人（推荐）

```powershell
powershell -NoProfile -File C:\Users\djnio\bin\codex-worker.ps1 `
  -Cd "C:\Users\djnio\Desktop\audio\Mineradio-main\and" `
  -Sandbox workspace-write `
  -Task "任务说明……"
```

- 只读摸底：`-Sandbox read-only`
- 可改工作区：`-Sandbox workspace-write`（默认）
- 极少数才：`-FullAuto`（跳过审批+沙箱，危险）

结果最后一条消息：`%USERPROFILE%\.codex\worker-out\last_*.md`

## 2. 总管分流规则（Grok 判断）

**亲自做（Grok）：**

- 架构/定调、产品取舍、记忆交接、版本号与 changelog 定稿
- 小 diff（≤~3 文件、逻辑清晰）
- 安全敏感：登录 Cookie、签名、密钥、打包签名
- 验收：读 diff、对齐 HANDOFF、必要时编译
- 用户明确要「你来写 / 别派」

**派 DeepSeek（Codex）：**

- 大面积机械改动、多文件样板、按已定方案实现
- 探索性试错（API 试调用、过滤规则调参）且总管已写清边界
- 长上下文读代码再改，省 Grok 轮次

**禁止派工人独自决定：**

- 恢复 BGM 悬浮球等已否产品决策
- 硬编码单曲 if-else
- 加外站播放源 / 乱接密钥
- `rm -rf`、改全局 PATH、提交密钥

## 3. 派任务时 prompt 模板

```
【项目】Madus Android，目录 and/，包名 com.madus.mobile
【先读】docs/HANDOFF_NEXT_CHAT.md 第 0.1 当前定稿
【任务】……
【只改】列出文件/目录
【禁止】……
【完成标准】……
【结束】用中文写 5 行内变更摘要；不要改 HANDOFF 除非明确要求
```

## 4. 总管验收清单

1. `git status` / 文件 mtime / diff 范围是否越界  
2. 是否触碰硬约束（队列 Stop、surface、player.release）  
3. 需要的话 `JAVA_HOME=...\tools\jdk17` + `gradlew.bat :app:assembleDebug`  
4. 更新 `docs/HANDOFF_NEXT_CHAT.md` / `AppChangelog`（总管做，或明确让工人做）

## 5. 注意

- `and/` 当前**不是** git 仓库 → 必须 `--skip-git-repo-check`（worker 已带）  
- Desktop 升级后 hash 目录会变 → `codex.cmd` 自动选最新  
- 新开终端才吃到 User PATH；当前会话可：`$env:Path = "C:\Users\djnio\bin;" + $env:Path`
