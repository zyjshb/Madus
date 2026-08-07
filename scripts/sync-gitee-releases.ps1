# Sync formal Madus-*.apk releases to Gitee (mirror of GitHub releases).
# Requires: $env:GITEE_TOKEN
# Usage:
#   .\scripts\sync-gitee-releases.ps1
#   .\scripts\sync-gitee-releases.ps1 -From 1.14.1 -To 1.14.11

param(
    [string]$From = "1.14.1",
    [string]$To = "1.14.11",
    [string]$Owner = "dikoklhf",
    [string]$Repo = "madus",
    [string]$Token = $env:GITEE_TOKEN,
    [switch]$SetDefaultMain
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Missing GITEE_TOKEN"
}

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$publish = Join-Path $PSScriptRoot "publish-gitee-release.ps1"

function Parse-Ver([string]$v) {
    $p = $v.TrimStart('v').Split('.') | ForEach-Object { [int]$_ }
    while ($p.Count -lt 3) { $p += 0 }
    return $p
}

function Cmp-Ver([string]$a, [string]$b) {
    $pa = Parse-Ver $a; $pb = Parse-Ver $b
    for ($i = 0; $i -lt 3; $i++) {
        if ($pa[$i] -ne $pb[$i]) { return $pa[$i] - $pb[$i] }
    }
    return 0
}

$apks = Get-ChildItem (Join-Path $root "apk") -Filter "Madus-*.apk" |
    Where-Object { $_.Name -notmatch '(?i)debug' -and $_.Name -match 'Madus-(\d+\.\d+\.\d+)\.apk' } |
    ForEach-Object {
        $m = [regex]::Match($_.Name, 'Madus-(\d+\.\d+\.\d+)\.apk')
        [pscustomobject]@{ Version = $m.Groups[1].Value; Path = $_.FullName }
    } |
    Where-Object { (Cmp-Ver $_.Version $From) -ge 0 -and (Cmp-Ver $_.Version $To) -le 0 } |
    Sort-Object { (Parse-Ver $_.Version)[0]; (Parse-Ver $_.Version)[1]; (Parse-Ver $_.Version)[2] }

if (-not $apks) { throw "No APKs in range $From .. $To under apk/" }

Write-Host "Will publish $($apks.Count) release(s) to Gitee $Owner/$Repo"

if ($SetDefaultMain) {
    Write-Host "==> set default_branch=main (best effort)"
    try {
        Invoke-RestMethod -Method Patch `
            -Uri "https://gitee.com/api/v5/repos/$Owner/$Repo`?access_token=$([uri]::EscapeDataString($Token))" `
            -Body @{ default_branch = "main"; name = "Madus"; description = "Minimalist Android music client with Bilibili source and line-sketch UI. Mirror of https://github.com/zyjshb/Madus" ; homepage = "https://github.com/zyjshb/Madus" } `
            -TimeoutSec 30 | Out-Null
    } catch {
        Write-Host "WARN repo patch: $($_.Exception.Message)"
        # description-only
        try {
            Invoke-RestMethod -Method Patch `
                -Uri "https://gitee.com/api/v5/repos/$Owner/$Repo`?access_token=$([uri]::EscapeDataString($Token))" `
                -Body @{
                    name        = "Madus"
                    description = "Minimalist Android music client · Bilibili · line-sketch UI · https://github.com/zyjshb/Madus"
                    homepage    = "https://github.com/zyjshb/Madus"
                } -TimeoutSec 30 | Out-Null
            Write-Host "OK: description/homepage updated"
        } catch {
            Write-Host "WARN description: $($_.Exception.Message)"
        }
    }
}

foreach ($item in $apks) {
    $v = $item.Version
    Write-Host ""
    Write-Host "======== Madus $v ========"
    & $publish -Version $v -Token $Token
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED $v"
    } else {
        Write-Host "OK $v"
    }
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "Done. Open: https://gitee.com/$Owner/$Repo/releases"
