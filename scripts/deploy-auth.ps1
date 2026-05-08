[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$DistDir = "dist",
    [string]$Target = "minecraft@192.168.1.103",
    [string]$RemoteHome = "/home/minecraft",
    [string]$RemoteServerModsDir = "/home/minecraft/mc-rpg/mods",
    [string]$RemoteWebRoot = "/var/www/mc-rpg",
    [string]$ServiceName = "mc-rpg.service",
    [switch]$SkipRestart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$distFullPath = Join-Path $repoRoot $DistDir
$metadataPath = Join-Path $distFullPath "auth-release.json"
$manifestPath = Join-Path $distFullPath "manifest.json"

if (-not (Test-Path $metadataPath)) {
    throw "Release metadata not found: $metadataPath. Run scripts/release-auth.ps1 first."
}

foreach ($command in @("scp", "ssh")) {
    $null = Get-Command $command -ErrorAction Stop
}

$metadata = Get-Content $metadataPath -Raw | ConvertFrom-Json
$serverFileName = $metadata.artifacts.server.fileName
$clientFileName = $metadata.artifacts.client.fileName

$serverJarPath = Join-Path $distFullPath $serverFileName
$clientJarPath = Join-Path $distFullPath $clientFileName

foreach ($path in @($serverJarPath, $clientJarPath, $manifestPath)) {
    if (-not (Test-Path $path)) {
        throw "Required release file not found: $path"
    }
}

$remoteClientModsDir = "$RemoteWebRoot/client/mods"

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

Write-Host "==> Uploading auth artifacts to $Target" -ForegroundColor Cyan
Invoke-External -Command "scp" -Arguments @(
    $serverJarPath,
    $clientJarPath,
    $manifestPath,
    "${Target}:$RemoteHome/"
) -Action "Upload dist artifacts"

Write-Host "==> Installing auth artifacts on remote host" -ForegroundColor Cyan
Invoke-External -Command "ssh" -Arguments @(
    $Target,
    "mkdir -p '$RemoteServerModsDir'"
) -Action "Create remote server mods directory"

Invoke-External -Command "ssh" -Arguments @(
    $Target,
    "install -m 644 '$RemoteHome/$serverFileName' '$RemoteServerModsDir/$serverFileName'"
) -Action "Install server auth mod"

Invoke-External -Command "ssh" -Arguments @(
    $Target,
    "sudo mkdir -p '$remoteClientModsDir'"
) -Action "Create remote client mods directory"

Invoke-External -Command "ssh" -Arguments @(
    $Target,
    "sudo install -m 644 '$RemoteHome/$clientFileName' '$remoteClientModsDir/$clientFileName'"
) -Action "Install client auth mod"

Invoke-External -Command "ssh" -Arguments @(
    $Target,
    "sudo install -m 644 '$RemoteHome/manifest.json' '$RemoteWebRoot/manifest.json'"
) -Action "Install manifest"

Invoke-External -Command "ssh" -Arguments @(
    $Target,
    "sha256sum '$RemoteServerModsDir/$serverFileName' && sha256sum '$remoteClientModsDir/$clientFileName'"
) -Action "Verify remote hashes"

if (-not $SkipRestart) {
    Write-Host "==> Restarting $ServiceName" -ForegroundColor Cyan
    Invoke-External -Command "ssh" -Arguments @(
        $Target,
        "sudo systemctl restart '$ServiceName'"
    ) -Action "Restart service"

    Invoke-External -Command "ssh" -Arguments @(
        $Target,
        "sudo systemctl status '$ServiceName' --no-pager -l"
    ) -Action "Show service status"
}

Write-Host ""
Write-Host "Deploy complete for $Target" -ForegroundColor Green
