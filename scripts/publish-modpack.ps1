[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ManifestPath = "examples/manifest.json",
    [string]$ClientSourceDir = "modpack/client",
    [string]$DistDir = "dist",
    [string]$ManifestVersion = (Get-Date -Format "yyyy.MM.dd"),
    [switch]$SkipSourceManifestUpdate,
    [string]$Target = "minecraft@192.168.1.103",
    [string]$RemoteHome = "/home/minecraft",
    [string]$RemoteStageDir = "/home/minecraft/obsidiangate-deploy",
    [string]$RemoteServerModsDir = "/home/minecraft/mc-rpg/mods",
    [string]$RemoteWebRoot = "/var/www/mc-rpg",
    [string]$ServiceName = "mc-rpg.service",
    [string]$RemoteDeployCommand = "/usr/local/bin/obsidiangate-deploy",
    [switch]$LegacyPromptSudo,
    [switch]$SkipRestart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$releaseScript = Join-Path $PSScriptRoot "release-modpack.ps1"
$deployScript = Join-Path $PSScriptRoot "deploy-modpack.ps1"

& $releaseScript `
    -ManifestPath $ManifestPath `
    -ClientSourceDir $ClientSourceDir `
    -DistDir $DistDir `
    -ManifestVersion $ManifestVersion `
    -SkipSourceManifestUpdate:$SkipSourceManifestUpdate

if ($LASTEXITCODE -ne 0) {
    throw "release-modpack.ps1 failed."
}

& $deployScript `
    -DistDir $DistDir `
    -Target $Target `
    -RemoteHome $RemoteHome `
    -RemoteStageDir $RemoteStageDir `
    -RemoteServerModsDir $RemoteServerModsDir `
    -RemoteWebRoot $RemoteWebRoot `
    -ServiceName $ServiceName `
    -RemoteDeployCommand $RemoteDeployCommand `
    -LegacyPromptSudo:$LegacyPromptSudo `
    -SkipRestart:$SkipRestart `
    -WhatIf:$WhatIfPreference

if ($LASTEXITCODE -ne 0) {
    throw "deploy-modpack.ps1 failed."
}
