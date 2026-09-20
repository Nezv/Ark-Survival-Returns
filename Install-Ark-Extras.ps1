# Downloads the reviewed, pinned client presentation pack and shared gameplay mods. Never changes saves or user configs.
[CmdletBinding()]
param([switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
$packRoot = Join-Path $PSScriptRoot 'Ark'
$allowedHosts = @('cdn.modrinth.com', 'maven.ftb.dev')
function Install-Pack([string]$relativeManifest) {
    $manifestPath = Join-Path $packRoot $relativeManifest
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Missing pack manifest: $manifestPath" }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    foreach ($entry in $manifest.entries) {
        if ($entry.filename -ne [IO.Path]::GetFileName($entry.filename)) { throw 'Unsafe filename in pack manifest.' }
        $uri = [uri]$entry.url
        if ($uri.Scheme -ne 'https' -or $allowedHosts -notcontains $uri.Host) { throw 'Pack downloads must use an approved HTTPS host.' }
        $folder = switch ($entry.kind) {
            'shader' { Join-Path $packRoot 'run/shaderpacks' }
            'shared' { Join-Path $packRoot 'shared-mods' }
            default { Join-Path $packRoot 'client-mods' }
        }
        $destination = Join-Path $folder $entry.filename
        if (Test-Path -LiteralPath $destination -PathType Leaf) {
            $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash
            if ($hash -ne $entry.sha512) { throw "Hash mismatch: $destination. Move that file aside and rerun this installer." }
            Write-Host "Verified $($entry.filename)"
            continue
        }
        if ($VerifyOnly) { throw "Missing $($entry.filename). Run Install-Ark-Extras.bat first." }
        New-Item -ItemType Directory -Path $folder -Force | Out-Null
        $partial = Join-Path $folder ($entry.filename + '.' + [guid]::NewGuid().ToString('N') + '.download')
        Write-Host "Downloading $($entry.project) $($entry.version)"
        try {
            Invoke-WebRequest -Uri $uri -OutFile $partial -UseBasicParsing -Headers @{'User-Agent'='ArkSurvivalReturns/0.1 (local pack)'}
            if ((Get-FileHash -LiteralPath $partial -Algorithm SHA512).Hash -ne $entry.sha512) { throw "Download checksum failed for $($entry.filename)" }
            Move-Item -LiteralPath $partial -Destination $destination
        }
        finally { if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial } }
    }
}
Install-Pack 'config/client-mods.lock.json'
Install-Pack 'config/shared-mods.lock.json'
Write-Host 'Ark extras are ready. Start-Ark-Mod.bat loads them automatically.' -ForegroundColor Green
Write-Host 'Enable Complementary Reimagined in Video Settings > Shader Packs when you want shaders.'

$devPlan = Get-Content -LiteralPath (Join-Path $packRoot 'config/dev-dependencies.json') -Raw | ConvertFrom-Json
foreach ($dependency in $devPlan.dependencies) {
    if ($dependency.status -ne 'pinned') { Write-Host ("Dev dependency {0}: {1} - {2}" -f $dependency.project, $dependency.status, $dependency.reason) }
}
