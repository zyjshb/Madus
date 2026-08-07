# Publish Madus APK to Gitee Releases (like: gh release create)
# Requires: $env:GITEE_TOKEN = private token (projects scope)
# Create token: https://gitee.com/profile/personal_access_tokens
#
# Usage (in and/):
#   $env:GITEE_TOKEN = "YOUR_TOKEN"
#   .\scripts\publish-gitee-release.ps1 -Version 1.14.11
#   .\scripts\publish-gitee-release.ps1 -Version 1.14.11 -SetDefaultMain -DeleteMaster

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
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Missing GITEE_TOKEN. Create one at https://gitee.com/profile/personal_access_tokens then: `$env:GITEE_TOKEN='xxx'"
}

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

$apk = Join-Path $root "apk\Madus-$Version.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    throw "APK not found: $apk (build first)"
}
if ($apk -match '(?i)debug') {
    throw "Refuse to upload debug APK"
}

$tag = "v$Version"
$apiBase = "https://gitee.com/api/v5/repos/$Owner/$Repo"
$notesPath = Join-Path $root "scripts\release-notes-$Version.md"
if (Test-Path -LiteralPath $notesPath) {
    $body = Get-Content -LiteralPath $notesPath -Raw -Encoding UTF8
} else {
    $body = @"
## Madus $Version

### Download
- Madus-$Version.apk

### Mirrors
- Gitee: https://gitee.com/$Owner/$Repo/releases
- GitHub: https://github.com/zyjshb/Madus/releases

Install: download APK -> allow unknown sources -> install.
In-app: Me -> Check update (prefers Gitee).
"@
}

function Get-GiteeUri([string]$Path) {
    $sep = if ($Path.Contains("?")) { "&" } else { "?" }
    return "$Path${sep}access_token=$([uri]::EscapeDataString($Token))"
}

if ($SetDefaultMain) {
    Write-Host "==> set default_branch = main"
    try {
        Invoke-RestMethod -Method Patch -Uri (Get-GiteeUri $apiBase) `
            -Body @{ default_branch = "main" } -TimeoutSec 30 | Out-Null
        Write-Host "OK: default_branch -> main"
    } catch {
        Write-Host "WARN set default branch: $($_.Exception.Message)"
    }
}

if ($DeleteMaster) {
    Write-Host "==> delete branch master (if not default)"
    try {
        Invoke-RestMethod -Method Delete -Uri (Get-GiteeUri "$apiBase/branches/master") -TimeoutSec 30 | Out-Null
        Write-Host "OK: master deleted"
    } catch {
        Write-Host "WARN delete master: $($_.Exception.Message)"
    }
}

Write-Host "==> git push gitee main:main"
git push gitee main:main
if ($LASTEXITCODE -ne 0) { throw "git push gitee failed" }

Write-Host "==> check existing release $tag"
$existing = $null
try {
    $existing = Invoke-RestMethod -Method Get -Uri (Get-GiteeUri "$apiBase/releases/tags/$tag") -TimeoutSec 30
} catch {
    $existing = $null
}

if ($existing -and $existing.id) {
    Write-Host "Release $tag exists (id=$($existing.id)), deleting..."
    try {
        Invoke-RestMethod -Method Delete -Uri (Get-GiteeUri "$apiBase/releases/$($existing.id)") -TimeoutSec 30 | Out-Null
    } catch {
        Write-Host "WARN delete release: $($_.Exception.Message)"
    }
}

Write-Host "==> create release $tag"
$createUri = Get-GiteeUri "$apiBase/releases"
# Gitee expects form fields for create release
$release = Invoke-RestMethod -Method Post -Uri $createUri -Body @{
    tag_name         = $tag
    name             = "Madus $Version"
    body             = $body
    target_commitish = "main"
    prerelease       = "false"
} -TimeoutSec 60

$releaseId = $release.id
if (-not $releaseId) {
    throw "Create release failed (no id): $($release | ConvertTo-Json -Depth 5 -Compress)"
}
Write-Host "release id = $releaseId"

Write-Host "==> upload APK"
$uploadUri = Get-GiteeUri "$apiBase/releases/$releaseId/attach_files"
$fileItem = Get-Item -LiteralPath $apk

# Prefer HttpClient multipart (works on Windows PowerShell 5 + PS7)
Add-Type -AssemblyName System.Net.Http | Out-Null
$handler = [System.Net.Http.HttpClientHandler]::new()
$client = [System.Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromMinutes(15)
$multipart = [System.Net.Http.MultipartFormDataContent]::new()
$fs = [System.IO.File]::OpenRead($fileItem.FullName)
try {
    $streamContent = [System.Net.Http.StreamContent]::new($fs)
    $streamContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/octet-stream")
    $multipart.Add($streamContent, "file", $fileItem.Name)
    $task = $client.PostAsync($uploadUri, $multipart)
    $task.Wait()
    $httpResult = $task.Result
    $text = $httpResult.Content.ReadAsStringAsync().Result
    if (-not $httpResult.IsSuccessStatusCode) {
        throw "Upload failed HTTP $([int]$httpResult.StatusCode): $text"
    }
    Write-Host "Upload OK"
    Write-Host $text
} finally {
    $fs.Dispose()
    $multipart.Dispose()
    $client.Dispose()
}

Write-Host ""
Write-Host "Done: https://gitee.com/$Owner/$Repo/releases/tag/$tag"
