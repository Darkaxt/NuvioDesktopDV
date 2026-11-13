param(
    # Extracted nuviodv-windows-x64 directory of the pinned Darkaxt/mpv release.
    [Parameter(Mandatory = $true)]
    [string]$SourceDir
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$nativeDir = Join-Path $repositoryRoot 'composeApp\src\desktopMain\native\windows'
$runtimeDir = Join-Path $nativeDir 'runtime'
$pinFile = Join-Path $nativeDir 'runtime-pins.sha256'
$resolvedSource = (Resolve-Path -LiteralPath $SourceDir).Path

# runtime-pins.sha256 is the single pin list, shared with the release workflow and tests.
$pins = Get-Content -LiteralPath $pinFile | Where-Object { $_.Trim() } | ForEach-Object {
    $hash, $name = $_ -split '\s+', 2
    [pscustomobject]@{ Name = $name.Trim(); Sha256 = $hash.ToLowerInvariant() }
}

foreach ($pin in $pins) {
    $source = Join-Path $resolvedSource $pin.Name
    $actual = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $pin.Sha256) {
        throw "Unexpected SHA-256 for $($pin.Name): $actual"
    }
}

$installed = foreach ($pin in $pins) {
    $destination = Join-Path $runtimeDir $pin.Name
    Copy-Item -LiteralPath (Join-Path $resolvedSource $pin.Name) -Destination $destination -Force
    $actual = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $pin.Sha256) {
        throw "Installed $($pin.Name) hash mismatch: $actual"
    }
    [ordered]@{ name = $pin.Name; destination = $destination; sha256 = $actual }
}

# Licence and source notices shipped in the installed app (NOTICE.md is maintained by hand).
$noticeDir = Join-Path $nativeDir 'app-resources\windows\licenses\libmpv'
foreach ($notice in @('Copyright', 'LICENSE.GPL', 'manifest.json', 'sources.json', 'licenses', 'dependency-licenses')) {
    Copy-Item -LiteralPath (Join-Path $resolvedSource $notice) -Destination $noticeDir -Recurse -Force
}

$installed | ConvertTo-Json
