# 发布 Madus 到 Gitee Releases（类似 gh release create）
# 前置：环境变量 GITEE_TOKEN = 私人令牌（权限：projects / 发行版）
# 获取：https://gitee.com/profile/personal_access_tokens
# 用法（在 and/ 目录）：
#   $env:GITEE_TOKEN = "你的令牌"
#   .\scripts\publish-gitee-release.ps1 -Version 1.14.11
#   .\scripts\publish-gitee-release.ps1 -Version 1.14.11 -SetDefaultMain

param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$Owner = "dikoklhf",
    [string]$Repo = "madus",
    [string]$Token = $env:GITEE_TOKEN,
    [switch]$SetDefaultMain,
    [switch]$DeleteMaster
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "缺少 GITEE_TOKEN。请到 https://gitee.com/profile/personal_access_tokens 创建私人令牌后执行：`n  `$env:GITEE_TOKEN = 'xxx'"
}

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$apk = Join-Path $root "apk\Madus-$Version.apk"
if (-not (Test-Path $apk)) { throw "缺少 $apk ，请先打包" }
if ($apk -match '(?i)debug') { throw "禁止上传 debug 包" }

$tag = "v$Version"
$apiBase = "https://gitee.com/api/v5/repos/$Owner/$Repo"
$notesPath = Join-Path $root "scripts\release-notes-$Version.md"
$body = if (Test-Path $notesPath) {
    Get-Content $notesPath -Raw -Encoding UTF8
} else {
    @"
## Madus $Version

### 下载
- Madus-$Version.apk

### 国内
- Gitee：https://gitee.com/$Owner/$Repo/releases
- GitHub 备用：https://github.com/zyjshb/Madus/releases

安装：下载 → 允许未知来源 → 安装。
App 内：我的 → 检查更新（优先 Gitee）。
"@
}

function Invoke-Gitee {
    param([string]$Method, [string]$Url, [hashtable]$Form = $null, [string]$JsonBody = $null)
    $sep = if ($Url.Contains("?")) { "&" } else { "?" }
    $full = "$Url${sep}access_token=$([uri]::EscapeDataString($Token))"
    if ($Form) {
        return Invoke-RestMethod -Method $Method -Uri $full -Form $Form -TimeoutSec 300
    }
    if ($JsonBody) {
        return Invoke-RestMethod -Method $Method -Uri $full -ContentType "application/json;charset=utf-8" -Body $JsonBody -TimeoutSec 60
    }
    return Invoke-RestMethod -Method $Method -Uri $full -TimeoutSec 60
}

if ($SetDefaultMain) {
    Write-Host "==> set default branch = main ..."
    $payload = @{ default_branch = "main" } | ConvertTo-Json -Compress
    # Gitee 部分接口用 form
    try {
        Invoke-RestMethod -Method Patch -Uri "$apiBase?access_token=$([uri]::EscapeDataString($Token))" `
            -Body @{ default_branch = "main" } -TimeoutSec 30 | Out-Null
        Write-Host "default_branch -> main OK"
    } catch {
        Write-Host "PATCH form failed, try JSON: $($_.Exception.Message)"
        Invoke-Gitee -Method Patch -Url $apiBase -JsonBody $payload | Out-Null
        Write-Host "default_branch -> main OK (json)"
    }
}

if ($DeleteMaster) {
    Write-Host "==> delete branch master (if exists) ..."
    try {
        Invoke-Gitee -Method Delete -Url "$apiBase/branches/master" | Out-Null
        Write-Host "master deleted"
    } catch {
        Write-Host "delete master skipped: $($_.Exception.Message)"
    }
}

Write-Host "==> push code to gitee main ..."
git push gitee main:main
if ($LASTEXITCODE -ne 0) { throw "git push gitee 失败" }

# 若已有同 tag 发行版则删掉重建
Write-Host "==> check existing release $tag ..."
$existing = $null
try {
    $existing = Invoke-Gitee -Method Get -Url "$apiBase/releases/tags/$tag"
} catch { $existing = $null }

if ($existing -and $existing.id) {
    Write-Host "Release $tag 已存在 (id=$($existing.id))，删除后重建..."
    try {
        Invoke-Gitee -Method Delete -Url "$apiBase/releases/$($existing.id)" | Out-Null
    } catch {
        Write-Host "delete release warn: $($_.Exception.Message)"
    }
}

Write-Host "==> create release $tag ..."
# Gitee 创建发行版常用 form 字段
$form = @{
    tag_name         = $tag
    name             = "Madus $Version"
    body             = $body
    target_commitish = "main"
    prerelease       = "false"
}
$release = Invoke-RestMethod -Method Post `
    -Uri "$apiBase/releases?access_token=$([uri]::EscapeDataString($Token))" `
    -Form $form -TimeoutSec 60

$releaseId = $release.id
if (-not $releaseId) { throw "创建发行版失败：无 id。响应：$($release | ConvertTo-Json -Depth 4)" }
Write-Host "release id = $releaseId"

Write-Host "==> upload APK $($apk | Split-Path -Leaf) ..."
# multipart 上传附件
$uploadUri = "$apiBase/releases/$releaseId/attach_files?access_token=$([uri]::EscapeDataString($Token))"
$fileItem = Get-Item -LiteralPath $apk
# PowerShell 7+ -Form 支持文件；Windows PowerShell 5 用 .NET
if ($PSVersionTable.PSVersion.Major -ge 6) {
    $resp = Invoke-RestMethod -Method Post -Uri $uploadUri -Form @{
        file = $fileItem
    } -TimeoutSec 600
} else {
    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $fs = [System.IO.File]::OpenRead($fileItem.FullName)
    try {
        $streamContent = [System.Net.Http.StreamContent]::new($fs)
        $streamContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/vnd.android.package-archive")
        $multipart.Add($streamContent, "file", $fileItem.Name)
        $task = $client.PostAsync($uploadUri, $multipart)
        $task.Wait()
        $result = $task.Result
        $text = $result.Content.ReadAsStringAsync().Result
        if (-not $result.IsSuccessStatusCode) {
            throw "上传失败 HTTP $([int]$result.StatusCode): $text"
        }
        $resp = $text | ConvertFrom-Json
    } finally {
        $fs.Dispose()
        $multipart.Dispose()
        $client.Dispose()
    }
}

Write-Host "上传完成"
Write-Host "Gitee Release: https://gitee.com/$Owner/$Repo/releases/tag/$tag"
if ($resp) {
    Write-Host ($resp | ConvertTo-Json -Depth 4 -Compress)
}
