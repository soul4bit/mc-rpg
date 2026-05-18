[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$DistDir = "dist",
    [string]$Target = "minecraft@192.168.1.103",
    [string]$RemoteHome = "/home/minecraft",
    [string]$RemoteStageDir = "/home/minecraft/obsidiangate-deploy",
    [string]$RemoteServerRoot = "/home/minecraft/mc-rpg",
    [string]$RemoteServerModsDir = "/home/minecraft/mc-rpg/mods",
    [string]$RemoteWebRoot = "/var/www/mc-rpg",
    [string]$ServiceName = "mc-rpg.service",
    [string]$RemoteDeployCommand = "/usr/local/bin/obsidiangate-deploy",
    [switch]$LegacyPromptSudo,
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
$serverDirPath = Join-Path $distFullPath "server"
$launcherDirPath = Join-Path $distFullPath "launcher"

if (-not (Test-Path $metadataPath)) {
    throw "Modpack release metadata not found: $metadataPath. Run scripts/release-modpack.ps1 first."
}

foreach ($command in @("scp", "ssh")) {
    $null = Get-Command $command -ErrorAction Stop
}

$metadata = Get-Content $metadataPath -Raw | ConvertFrom-Json
$serverFileName = $metadata.artifacts.server.fileName
$launcherUpdateFileName = if ($metadata.launcherUpdate -and $metadata.launcherUpdate.fileName) {
    [string]$metadata.launcherUpdate.fileName
} else {
    "obsidian-gate-launcher.jar"
}
$serverJarPath = Join-Path $distFullPath $serverFileName

$requiredPaths = @($serverJarPath, $manifestPath, $clientDirPath)
if (Test-Path $launcherDirPath) {
    $requiredPaths += $launcherDirPath
}

foreach ($path in $requiredPaths) {
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
$uploadPaths = @($clientDirPath, $serverJarPath, $manifestPath)
if (Test-Path $serverDirPath) {
    $uploadPaths += $serverDirPath
}
if (Test-Path $launcherDirPath) {
    $uploadPaths += $launcherDirPath
}
Invoke-External -Command "scp" -Arguments @(
    @("-r") +
    $uploadPaths +
    @("${Target}:$RemoteStageDir/")
) -Action "Upload modpack release"

Write-Host "==> Installing modpack release on remote host" -ForegroundColor Cyan
$remoteScript = $null

if ($LegacyPromptSudo) {
    $remoteCommands = [System.Collections.Generic.List[string]]::new()
    $remoteCommands.Add("set -e")
    $remoteCommands.Add("sudo -v")
    $remoteCommands.Add("mkdir -p '$RemoteServerModsDir'")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/server/mods' ]; then")
    $remoteCommands.Add("  find '$RemoteServerModsDir' -mindepth 1 -maxdepth 1 ! -name 'obsidiangate-forge-auth-server-*.jar' -exec rm -rf {} +")
    $remoteCommands.Add("  cp -a '$RemoteStageDir/server/mods/.' '$RemoteServerModsDir/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("install -m 644 '$RemoteStageDir/$serverFileName' '$RemoteServerModsDir/$serverFileName'")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/server/config' ]; then")
    $remoteCommands.Add("  preserved_spawn_config=`$(mktemp)")
    $remoteCommands.Add("  if [ -f '$RemoteServerRoot/config/obsidiangate-spawn-protection.properties' ] && [ ! -f '$RemoteStageDir/server/config/obsidiangate-spawn-protection.properties' ]; then")
    $remoteCommands.Add("    cp '$RemoteServerRoot/config/obsidiangate-spawn-protection.properties' `"`$preserved_spawn_config`"")
    $remoteCommands.Add("  else")
    $remoteCommands.Add("    rm -f `"`$preserved_spawn_config`"")
    $remoteCommands.Add("  fi")
    $remoteCommands.Add("  rm -rf '$RemoteServerRoot/config'")
    $remoteCommands.Add("  mkdir -p '$RemoteServerRoot/config'")
    $remoteCommands.Add("  cp -a '$RemoteStageDir/server/config/.' '$RemoteServerRoot/config/'")
    $remoteCommands.Add("  if [ -f `"`$preserved_spawn_config`" ]; then")
    $remoteCommands.Add("    install -m 644 `"`$preserved_spawn_config`" '$RemoteServerRoot/config/obsidiangate-spawn-protection.properties'")
    $remoteCommands.Add("    rm -f `"`$preserved_spawn_config`"")
    $remoteCommands.Add("  fi")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/server/scripts' ]; then")
    $remoteCommands.Add("  rm -rf '$RemoteServerRoot/scripts'")
    $remoteCommands.Add("  mkdir -p '$RemoteServerRoot/scripts'")
    $remoteCommands.Add("  cp -a '$RemoteStageDir/server/scripts/.' '$RemoteServerRoot/scripts/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("sudo mkdir -p '$RemoteWebRoot'")
    $remoteCommands.Add("if command -v rsync >/dev/null 2>&1; then")
    $remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/client'")
    $remoteCommands.Add("  sudo rsync -a --delete '$RemoteStageDir/client/' '$RemoteWebRoot/client/'")
    $remoteCommands.Add("else")
    $remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/client'")
    $remoteCommands.Add("  sudo cp -a '$RemoteStageDir/client/.' '$RemoteWebRoot/client/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("if [ -d '$RemoteStageDir/launcher' ]; then")
    $remoteCommands.Add("  sudo mkdir -p '$RemoteWebRoot/launcher'")
    $remoteCommands.Add("  sudo cp -a '$RemoteStageDir/launcher/.' '$RemoteWebRoot/launcher/'")
    $remoteCommands.Add("fi")
    $remoteCommands.Add("sudo install -m 644 '$RemoteStageDir/manifest.json' '$RemoteWebRoot/manifest.json'")
    $remoteCommands.Add("sha256sum '$RemoteServerModsDir/$serverFileName'")
    $remoteCommands.Add("sha256sum '$RemoteWebRoot/manifest.json'")

    if (-not $SkipRestart) {
        $remoteCommands.Add("sudo systemctl restart '$ServiceName'")
        $remoteCommands.Add("sudo systemctl status '$ServiceName' --no-pager -l")
    }

    $remoteScript = $remoteCommands -join "`n"
} else {
    $skipRestartFlag = if ($SkipRestart) { "1" } else { "0" }
    $remoteScript = "sudo -n '$RemoteDeployCommand' '$RemoteStageDir' '$serverFileName' '$RemoteServerModsDir' '$RemoteWebRoot' '$ServiceName' '$skipRestartFlag' '$RemoteServerRoot'"
}

Invoke-External -Command "ssh" -Arguments @(
    $sshTtyArgs +
    @(
        $Target,
        $remoteScript
    )
) -Action "Install modpack web files and restart service"

if (Test-Path $launcherDirPath) {
    Write-Host "==> Verifying launcher update artifact in web root" -ForegroundColor Cyan
    $remoteLauncherVerifyScript = @"
set -e
if [ -d '$RemoteStageDir/launcher' ]; then
  if mkdir -p '$RemoteWebRoot/launcher' 2>/dev/null && cp -a '$RemoteStageDir/launcher/.' '$RemoteWebRoot/launcher/' 2>/dev/null; then
    :
  else
    sudo mkdir -p '$RemoteWebRoot/launcher'
    sudo cp -a '$RemoteStageDir/launcher/.' '$RemoteWebRoot/launcher/'
  fi
fi
test -f '$RemoteWebRoot/launcher/$launcherUpdateFileName'
sha256sum '$RemoteWebRoot/launcher/$launcherUpdateFileName'
"@

    Invoke-External -Command "ssh" -Arguments @(
        $sshTtyArgs +
        @(
            $Target,
            $remoteLauncherVerifyScript
        )
    ) -Action "Verify launcher update artifact"
}

Write-Host ""
Write-Host "Modpack deploy complete for $Target" -ForegroundColor Green
