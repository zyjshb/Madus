# 发布 Madus 到 GitHub：推送代码 + 创建 Release 并上传 APK
# 前置：已安装 gh，并执行过  gh auth login
# 用法（在 and/ 目录）：
#   .\scripts\publish-release.ps1
#   .\scripts\publish-release.ps1 -Version 1.14.1

param(
    [string]$Version = "1.14.1",
    [string]$Repo = "zyjshb/Madus"
)

$ErrorActionPreference = "Stop"
$gh = "${env:ProgramFiles}\GitHub CLI\gh.exe"
if (-not (Test-Path $gh)) { $gh = "${env:LOCALAPPDATA}\Programs\GitHub CLI\gh.exe" }
if (-not (Test-Path $gh)) { throw "未找到 gh.exe，请先 winget install GitHub.cli" }

& $gh auth status
if ($LASTEXITCODE -ne 0) { throw "请先运行: gh auth login" }

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

# 只上传正式包，禁止上传 *-debug.apk
$apkRelease = Join-Path $root "apk\Madus-$Version.apk"
if (-not (Test-Path $apkRelease)) { throw "缺少 $apkRelease ，请先打包" }
if ($apkRelease -match '(?i)debug') { throw "禁止上传 debug 包" }

Write-Host "==> push main ..."
git push -u origin main

$tag = "v$Version"
$notes = @"
## Madus $Version

### 下载
- **Madus-$Version.apk**（正式版）

安装：下载 → 允许未知来源 → 安装。  
App 内：我的 → 检查更新 打开本页。

> 不提供 debug 包。
"@

Write-Host "==> create release $tag （仅正式 APK）..."

# 若 tag 已存在则删了重建（仅本机脚本方便重发）
& $gh release view $tag --repo $Repo 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Release $tag 已存在，删除后重建..."
    & $gh release delete $tag --repo $Repo --yes
}

& $gh release create $tag $apkRelease `
    --repo $Repo `
    --title "Madus $Version" `
    --notes $notes

Write-Host "完成: https://github.com/$Repo/releases/tag/$tag"
