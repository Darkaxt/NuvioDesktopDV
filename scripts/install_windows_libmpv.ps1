param(
    [Parameter(Mandatory = $true)]
    [string]$SourceDll
)

$ErrorActionPreference = 'Stop'

$expectedSha256 = '04e4d27dc385dc18ddd93fbe69c75e635b864f3c6ec53a9122df54c2f7842f4a'
$resolvedSource = (Resolve-Path -LiteralPath $SourceDll).Path
$actualSourceSha256 = (Get-FileHash -LiteralPath $resolvedSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualSourceSha256 -ne $expectedSha256) {
    throw "Unexpected libmpv SHA-256: $actualSourceSha256"
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $repositoryRoot 'composeApp\src\desktopMain\native\windows\runtime\libmpv-2.dll'
Copy-Item -LiteralPath $resolvedSource -Destination $destination -Force

$installedSha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
if ($installedSha256 -ne $expectedSha256) {
    throw "Installed libmpv hash mismatch: $installedSha256"
}

[ordered]@{
    source = $resolvedSource
    destination = $destination
    sha256 = $installedSha256
} | ConvertTo-Json
