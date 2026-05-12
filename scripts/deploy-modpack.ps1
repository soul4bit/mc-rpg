[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$DistDir = "dist",
    [string]$Target = "minecraft@192.168.1.103",
    [string]$RemoteHome = "/home/minecraft",
    [string]$RemoteStageDir = "/home/minecraft/obsidiangate-deploy",
    [string]$RemoteServerModsDir = "/home/minecraft/mc-rpg/mods",
    [string]$RemoteWebRoot = "/var/www/mc-rpg",
    [string]$ServiceName = "mc-rpg.service",
    [switch]$SkipRestart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Resolve-InputPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    if ([System.IO.Path]::IsPathRooted($Value)) {
        return $Value
    }

    return (Join-Path $repoRoot $Value)
}

$distFullPath = Resolve-InputPath $DistDir
$metadataPath = Join-Path $distFullPath "modpack-release.json"
$manifestPath = Join-Path $distFullPath "manifest.json"
$clientDirPath = Join-Path $distFullPath "client"

if (-not (Test-Path $metadataPath)) {
    throw "Modpack release metadata not found: $metadataPath. Run scripts/release-modpack.ps1 first."
}

foreach ($command in @("scp", "ssh")) {
    $null = Get-Command $command -ErrorAction Stop
}

$metadata = Get-Content $metadataPath -Raw | ConvertFrom-Json
$serverFileName = $metadata.artifacts.server.fileName
$serverJarPath = Join-Path $distFullPath $serverFileName

foreach ($path in @($serverJarPath, $manifestPath, $clientDirPath)) {
    if (-not (Test-Path $path)) {
        throw "Required release file not found: $path"
    }
}

$sshTtyArgs = @("-tt")

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,

        [Parameter(Mandatory = $true)]
        [string]$Action
    )

    if (-not $PSCmdlet.ShouldProcess($Target, $Action)) {
        return
    }

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $Command $($Arguments -join ' ')"
    }
}

Write-Host "==> Creating remote staging directory" -ForegroundColor Cyan
Invoke-External -Command "ssh" -Arguments @(
    $sshTtyArgs +
    @(
        $Target,
        "mkdir -p '$RemoteStageDir'"
    )
) -Action "Create remote staging directory"

Write-Host "==> Uploading modpack release to $Target" -ForegroundColor Cyan
Invoke-External -Command "scp" -Arguments @(
    "-r",
    $clientDirPath,
    $serverJarPath,
    $manifestPath,
    "${Target}:$RemoteStageDir/"
) -Action "Upload modpack release"

Write-Host "==> Installing modpack release on remote host" -ForegroundColor Cyan
$remoteCommands = [System.Collections.Generic.List[string]]::new()
$remoteCommands.Add("set -e")
$remoteCommands.Add("sudo -v")
$remoteCommands.Add("mkdir -p '$RemoteServerModsDir'")
$remoteCommands.Add("install -m 644 '$RemoteStageDir/$serverFileName' '$RemoteServerModsDir/$serverFileName'")
$remoteCommands.Add("sudo mkdir -p '$RemoteWebRoot'")
$remoteCommands.Add("if command -v rsync >/dev/null 2>&1; then")
$remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/client'")
$remoteCommands.Add("  sudo rsync -a --delete '$RemoteStageDir/client/' '$RemoteWebRoot/client/'")
$remoteCommands.Add("else")
$remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/client'")
$remoteCommands.Add("  sudo cp -a '$RemoteStageDir/client/.' '$RemoteWebRoot/client/'")
$remoteCommands.Add("fi")
$remoteCommands.Add("sudo install -m 644 '$RemoteStageDir/manifest.json' '$RemoteWebRoot/manifest.json'")
$remoteCommands.Add("sha256sum '$RemoteServerModsDir/$serverFileName'")
$remoteCommands.Add("sha256sum '$RemoteWebRoot/manifest.json'")

if (-not $SkipRestart) {
    $remoteCommands.Add("sudo systemctl restart '$ServiceName'")
    $remoteCommands.Add("sudo systemctl status '$ServiceName' --no-pager -l")
}

$remoteScript = $remoteCommands -join "`n"

Invoke-External -Command "ssh" -Arguments @(
    $sshTtyArgs +
    @(
        $Target,
        $remoteScript
    )
) -Action "Install modpack web files and restart service"

Write-Host ""
Write-Host "Modpack deploy complete for $Target" -ForegroundColor Green
