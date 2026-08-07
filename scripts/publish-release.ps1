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

Write-Host "==> push main (GitHub) ..."
git push -u origin main

Write-Host "==> push main (Gitee) ..."
git push gitee main:main 2>&1 | Write-Host

$tag = "v$Version"
# 说明文案不要写「正式版 / 无 debug」等字样
$notesFile = Join-Path $root "scripts\release-notes-$Version.md"
$notes = if (Test-Path $notesFile) {
    Get-Content $notesFile -Raw -Encoding UTF8
} else {
    @"
## Madus $Version

### 下载
- Madus-$Version.apk

### 国内
- Gitee：https://gitee.com/dikoklhf/madus/releases
- GitHub 备用：https://github.com/zyjshb/Madus/releases

安装：下载 → 允许未知来源 → 安装。
App 内：我的 → 检查更新（优先 Gitee）。
"@
}

Write-Host "==> create GitHub release $tag ..."

& $gh release view $tag --repo $Repo 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Release $tag 已存在，删除后重建..."
    & $gh release delete $tag --repo $Repo --yes
}

$tmpNotes = Join-Path $env:TEMP "madus-release-notes-$Version.md"
Set-Content -Path $tmpNotes -Value $notes -Encoding UTF8
& $gh release create $tag $apkRelease `
    --repo $Repo `
    --title "Madus $Version" `
    --notes-file $tmpNotes

Write-Host "GitHub: https://github.com/$Repo/releases/tag/$tag"

# Gitee 发行版（需要 GITEE_TOKEN）
if ($env:GITEE_TOKEN) {
    Write-Host "==> publish Gitee release ..."
    & "$PSScriptRoot\publish-gitee-release.ps1" -Version $Version -SetDefaultMain -DeleteMaster
} else {
    Write-Host "提示: 未设置 GITEE_TOKEN，跳过 Gitee 发行版上传。"
    Write-Host "  创建令牌: https://gitee.com/profile/personal_access_tokens"
    Write-Host "  然后: `$env:GITEE_TOKEN='xxx'; .\scripts\publish-gitee-release.ps1 -Version $Version -SetDefaultMain -DeleteMaster"
}
