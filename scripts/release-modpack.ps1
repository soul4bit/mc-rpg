[CmdletBinding()]
param(
    [string]$ManifestPath = "examples/manifest.json",
    [string]$ClientSourceDir = "modpack/client",
    [string]$DistDir = "dist",
    [string]$ManifestVersion = (Get-Date -Format "yyyy.MM.dd"),
    [string]$LauncherUpdatePath = "launcher/obsidian-gate-launcher.jar",
    [switch]$SkipSourceManifestUpdate,
    [switch]$SkipAuthRelease,
    [switch]$SkipLauncherRelease
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

$manifestFullPath = Resolve-InputPath $ManifestPath
$clientSourceFullPath = Resolve-InputPath $ClientSourceDir
$distFullPath = Resolve-InputPath $DistDir
$authMetadataPath = Join-Path $distFullPath "auth-release.json"
$distManifestPath = Join-Path $distFullPath "manifest.json"
$modpackMetadataPath = Join-Path $distFullPath "modpack-release.json"

if (-not (Test-Path $manifestFullPath)) {
    throw "Manifest not found: $manifestFullPath"
}

if (-not (Test-Path $clientSourceFullPath)) {
    throw "Client source directory not found: $clientSourceFullPath"
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Normalize-ManifestPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return $Value.Trim().Replace('\', '/').TrimStart('/')
}

function Test-RelativeContentPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $trimmed = $Value.Trim()
    if ($trimmed -match '^[a-zA-Z][a-zA-Z0-9+\-.]*://') {
        return $false
    }
    return -not $trimmed.StartsWith("/")
}

function Get-RelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,

        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $rootPath = [System.IO.Path]::GetFullPath($Root)
    if (-not $rootPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootPath += [System.IO.Path]::DirectorySeparatorChar
    }

    $rootUri = New-Object System.Uri($rootPath)
    $fileUri = New-Object System.Uri(([System.IO.Path]::GetFullPath($Path)))
    $relativeUri = $rootUri.MakeRelativeUri($fileUri)
    return [System.Uri]::UnescapeDataString($relativeUri.ToString()).Replace('\', '/')
}

function Get-FileRecord {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo]$File,

        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    return [pscustomobject][ordered]@{
        path = $RelativePath
        sha256 = (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        size = [int64]$File.Length
        executable = $false
    }
}

function Get-ArtifactFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$Pattern
    )

    $artifacts = Get-ChildItem (Join-Path $repoRoot $Directory) -File -Filter $Pattern |
        Where-Object { $_.Name -notlike "original-*" -and $_.Name -notlike "*-shaded.jar" } |
        Sort-Object LastWriteTime -Descending

    if (-not $artifacts) {
        throw "Artifact not found in $Directory for pattern $Pattern"
    }

    return $artifacts[0]
}

function Get-ProjectVersion {
    $pomPath = Join-Path $repoRoot "pom.xml"
    [xml]$pom = Get-Content $pomPath
    return [string]$pom.project.version
}

function Build-LauncherArtifact {
    Write-Host "==> Building launcher update jar" -ForegroundColor Cyan
    & mvn "-f" (Join-Path $repoRoot "pom.xml") "package" "-DskipTests"
    if ($LASTEXITCODE -ne 0) {
        throw "Launcher Maven build failed."
    }

    return Get-ArtifactFile -Directory "target" -Pattern "obsidian-gate-launcher-*.jar"
}

function Copy-DirectoryContent {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,

        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    if (Test-Path $Destination) {
        Remove-Item $Destination -Recurse -Force
    }

    $null = New-Item -ItemType Directory -Path $Destination -Force

    Get-ChildItem $Source -Force | ForEach-Object {
        Copy-Item $_.FullName -Destination $Destination -Recurse -Force
    }
}

if (-not $SkipAuthRelease) {
    Write-Host "==> Preparing auth release artifacts" -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "release-auth.ps1") `
        -ManifestPath $ManifestPath `
        -DistDir $DistDir `
        -ManifestVersion $ManifestVersion
    if ($LASTEXITCODE -ne 0) {
        throw "release-auth.ps1 failed."
    }
}

foreach ($path in @($authMetadataPath, $distManifestPath)) {
    if (-not (Test-Path $path)) {
        throw "Required auth release file not found: $path"
    }
}

$authMetadata = Get-Content $authMetadataPath -Raw | ConvertFrom-Json
$manifest = Get-Content $distManifestPath -Raw | ConvertFrom-Json
$clientJarName = $authMetadata.artifacts.client.fileName
$clientJarPath = Join-Path $distFullPath $clientJarName

if (-not (Test-Path $clientJarPath)) {
    throw "Client auth jar not found in dist: $clientJarPath"
}

$distClientRoot = Join-Path $distFullPath "client"
Copy-DirectoryContent -Source $clientSourceFullPath -Destination $distClientRoot

$distClientModsDir = Join-Path $distClientRoot "mods"
$null = New-Item -ItemType Directory -Path $distClientModsDir -Force

Get-ChildItem $distClientModsDir -Filter "obsidiangate-forge-auth-client-*.jar" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force

Copy-Item $clientJarPath (Join-Path $distClientModsDir $clientJarName) -Force

$manifest.version = $ManifestVersion

$launcherRecord = $null
if (-not $SkipLauncherRelease) {
    $launcherJar = Build-LauncherArtifact
    $normalizedLauncherPath = Normalize-ManifestPath $LauncherUpdatePath
    if (-not (Test-RelativeContentPath $normalizedLauncherPath)) {
        throw "LauncherUpdatePath must be a relative web path: $LauncherUpdatePath"
    }

    $distLauncherPath = Join-Path $distFullPath ($normalizedLauncherPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))
    $distLauncherParent = Split-Path -Parent $distLauncherPath
    $null = New-Item -ItemType Directory -Path $distLauncherParent -Force
    Copy-Item $launcherJar.FullName $distLauncherPath -Force

    $distLauncherFile = Get-Item $distLauncherPath
    $launcherRecord = [pscustomobject][ordered]@{
        version = $ManifestVersion
        url = $normalizedLauncherPath
        sha256 = (Get-FileHash -LiteralPath $distLauncherFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        size = [int64]$distLauncherFile.Length
        required = $false
        artifactVersion = Get-ProjectVersion
        fileName = $distLauncherFile.Name
    }

    if ($manifest.PSObject.Properties.Name.Contains("launcherUpdate")) {
        $manifest.launcherUpdate = $launcherRecord
    } else {
        $manifest | Add-Member -NotePropertyName "launcherUpdate" -NotePropertyValue $launcherRecord
    }
}

$runtimePaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
if ($manifest.runtime -and $manifest.runtime.packages) {
    foreach ($package in $manifest.runtime.packages) {
        if (-not $package.url) {
            continue
        }

        $normalizedPath = Normalize-ManifestPath $package.url
        if (-not (Test-RelativeContentPath $normalizedPath)) {
            continue
        }

        $null = $runtimePaths.Add($normalizedPath)
        $runtimeFilePath = Join-Path $distClientRoot ($normalizedPath.Replace('/', [System.IO.Path]::DirectorySeparatorChar))

        if (-not (Test-Path $runtimeFilePath)) {
            throw "Runtime package declared in manifest was not found in client source: $normalizedPath"
        }

        $runtimeFile = Get-Item $runtimeFilePath
        $package.sha256 = (Get-FileHash -LiteralPath $runtimeFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $package.size = [int64]$runtimeFile.Length
    }
}

$ignoredNames = @(".gitkeep", ".DS_Store", "Thumbs.db")
$manifestFiles = [System.Collections.Generic.List[object]]::new()

Get-ChildItem $distClientRoot -Recurse -File -Force |
    Where-Object { $ignoredNames -notcontains $_.Name } |
    Sort-Object FullName |
    ForEach-Object {
        $relativePath = Get-RelativePath -Root $distClientRoot -Path $_.FullName
        if ($runtimePaths.Contains($relativePath)) {
            return
        }

        $manifestFiles.Add((Get-FileRecord -File $_ -RelativePath $relativePath))
    }

$manifest.files = @($manifestFiles)

$manifestJson = $manifest | ConvertTo-Json -Depth 10
Write-Utf8NoBom -Path $distManifestPath -Content ($manifestJson + [Environment]::NewLine)

if (-not $SkipSourceManifestUpdate) {
    Write-Utf8NoBom -Path $manifestFullPath -Content ($manifestJson + [Environment]::NewLine)
}

$metadata = [pscustomobject][ordered]@{
    generatedAt = (Get-Date).ToString("o")
    manifest = [pscustomobject][ordered]@{
        sourcePath = $ManifestPath
        version = $manifest.version
        distPath = "manifest.json"
    }
    client = [pscustomobject][ordered]@{
        sourcePath = $ClientSourceDir
        distPath = "client"
        fileCount = $manifestFiles.Count
    }
    artifacts = $authMetadata.artifacts
    launcherUpdate = $launcherRecord
}

$metadataJson = $metadata | ConvertTo-Json -Depth 10
Write-Utf8NoBom -Path $modpackMetadataPath -Content ($metadataJson + [Environment]::NewLine)

Write-Host ""
Write-Host "Modpack release prepared in $distFullPath" -ForegroundColor Green
Write-Host "Manifest version: $($manifest.version)"
Write-Host "Client source: $ClientSourceDir"
Write-Host "Web files: $($manifestFiles.Count)"
Write-Host "Client auth mod: $clientJarName"
if ($launcherRecord) {
    Write-Host "Launcher update: $($launcherRecord.url) sha256=$($launcherRecord.sha256) size=$($launcherRecord.size)"
}
