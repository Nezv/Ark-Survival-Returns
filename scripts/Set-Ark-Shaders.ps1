# Seeds the authored client defaults; -ResetPreset backs up and reapplies Bliss.
[CmdletBinding()]
param([switch]$ResetPreset, [switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
if ($ResetPreset -and $VerifyOnly) { throw 'Choose ResetPreset or VerifyOnly, not both.' }
$arkRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'Ark'
$defaults = Join-Path $arkRoot 'config/client-defaults'
$run = Join-Path $arkRoot 'run'
$manifest = Get-Content -LiteralPath (Join-Path $arkRoot 'config/client-mods.lock.json') -Raw | ConvertFrom-Json
$bliss = $manifest.entries | Where-Object { $_.project -eq 'bliss-shader' }
$archive = Join-Path $run ('shaderpacks/' + $bliss.filename)
if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) { throw 'Bliss is missing. Run Install-Ark-Extras.bat first.' }
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $bliss.sha512) { throw 'Bliss checksum mismatch.' }
$stamp = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$backupRoot = Join-Path $run ('shader-backups/' + $stamp)
foreach ($source in Get-ChildItem -LiteralPath $defaults -File -Recurse) {
    $relative = $source.FullName.Substring($defaults.Length + 1)
    $target = Join-Path $run $relative
    if ($VerifyOnly) {
        if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "Missing shader configuration: $relative" }
        continue
    }
    if ((Test-Path -LiteralPath $target) -and -not $ResetPreset) { continue }
    $content = [IO.File]::ReadAllText($source.FullName)
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        $backup = Join-Path $backupRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $backup) -Force | Out-Null
        Copy-Item -LiteralPath $target -Destination $backup
        # Keep Iris preferences outside the three settings owned by this preset.
        if ($source.Name -eq 'iris.properties') {
            $keys = @('enableShaders', 'shaderPack', 'maxShadowRenderDistance')
            $lines = @(Get-Content -LiteralPath $target | Where-Object {
                $key = ($_ -split '[=:]', 2)[0].Trim()
                $keys -notcontains $key
            })
            $content = ($lines -join "`n") + "`n" + $content
        }
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    [IO.File]::WriteAllText($target, $content, [Text.UTF8Encoding]::new($false))
    Write-Host "Configured $relative"
}
if (Test-Path -LiteralPath $backupRoot) { Write-Host "Previous settings backed up to $backupRoot" }
if ($VerifyOnly) { Write-Host 'Bliss archive and installed configuration files verified; user adjustments are allowed.' }
