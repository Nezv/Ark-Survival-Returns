[CmdletBinding()]
param(
    [ValidateSet('Push', 'Pull', 'Sync')]
    [string]$Direction = 'Sync',
    [string]$RemotePath = 'gdrive:Ark Management Dashboard.csv'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSCommandPath
$csvPath = Join-Path $repositoryRoot 'Dashboard.csv'
$statePath = Join-Path $repositoryRoot '.dashboard-sync.json'
$tempDirectory = Join-Path $env:TEMP 'arksurvivalreturns-dashboard'
$tempPath = Join-Path $tempDirectory 'dashboard-remote.csv'
$syncExit = 0

function Get-RclonePath {
    $command = Get-Command rclone.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    $packages = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    $candidate = Get-ChildItem -Path $packages -Recurse -Filter rclone.exe -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($candidate) { return $candidate.FullName }
    throw 'rclone was not found. Install it with: winget install Rclone.Rclone'
}

function Get-FileDigest([string]$Path) {
    for ($attempt = 1; $attempt -le 5; $attempt++) {
        try {
            $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
            try {
                $sha = [System.Security.Cryptography.SHA256]::Create()
                try {
                    return ([BitConverter]::ToString($sha.ComputeHash($stream)) -replace '-', '')
                }
                finally { $sha.Dispose() }
            }
            finally { $stream.Dispose() }
        }
        catch {
            if ($attempt -eq 5) {
                throw "Could not read $Path. Close it in any editor or spreadsheet program and try again."
            }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Get-NormalizedCsv([string]$Path) {
    if ((Get-Item -LiteralPath $Path).Length -eq 0) { return '' }
    (Import-Csv -LiteralPath $Path | ConvertTo-Csv -NoTypeInformation) -join "`n"
}

function Read-SyncState {
    if (Test-Path -LiteralPath $statePath -PathType Leaf) {
        return Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    }
    return $null
}

function Write-SyncState([string]$LocalHash, [string]$RemoteHash) {
    if (-not $LocalHash -or -not $RemoteHash) {
        throw 'Refusing to store an incomplete sync state.'
    }
    [pscustomobject]@{
        localHash  = $LocalHash
        remoteHash = $RemoteHash
        syncedAt   = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
}

function Invoke-Rclone([string[]]$Arguments) {
    $output = & $script:rclonePath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "rclone exited with code $LASTEXITCODE.`n$($output -join "`n")"
    }
}

function Export-Remote {
    Invoke-Rclone @('copyto', $RemotePath, $tempPath, '--ignore-times', '--log-level', 'ERROR')
}

function Import-Remote {
    Invoke-Rclone @('copyto', $csvPath, $RemotePath, '--ignore-times', '--log-level', 'ERROR')
    Export-Remote
    Get-FileDigest $tempPath
}

function Import-Local {
    Copy-Item -LiteralPath $tempPath -Destination $csvPath -Force
    Get-FileDigest $csvPath
}

try {
    if (-not (Test-Path -LiteralPath $csvPath -PathType Leaf)) {
        throw "Dashboard.csv was not found at $csvPath."
    }
    New-Item -ItemType Directory -Force -Path $tempDirectory | Out-Null
    $script:rclonePath = Get-RclonePath

    Export-Remote
    $localHash = Get-FileDigest $csvPath
    $remoteHash = Get-FileDigest $tempPath
    $state = Read-SyncState

    switch ($Direction) {
        'Push' {
            $remoteHash = Import-Remote
            Write-SyncState -LocalHash $localHash -RemoteHash $remoteHash
            Write-Host "Pushed Dashboard.csv to $RemotePath." -ForegroundColor Green
        }
        'Pull' {
            $localHash = Import-Local
            Write-SyncState -LocalHash $localHash -RemoteHash $remoteHash
            Write-Host "Pulled $RemotePath into Dashboard.csv." -ForegroundColor Green
        }
        'Sync' {
            if (-not $state) {
                if ((Get-NormalizedCsv $csvPath) -eq (Get-NormalizedCsv $tempPath)) {
                    Write-SyncState -LocalHash $localHash -RemoteHash $remoteHash
                    Write-Host 'Seeded sync state; both sides already match.' -ForegroundColor Green
                }
                elseif ((Get-Item -LiteralPath $tempPath).Length -eq 0) {
                    $remoteHash = Import-Remote
                    Write-SyncState -LocalHash $localHash -RemoteHash $remoteHash
                    Write-Host 'Seeded the empty sheet from Dashboard.csv.' -ForegroundColor Green
                }
                else {
                    throw 'No sync state exists and the sheet already has content. Run once with -Direction Push or -Direction Pull.'
                }
            }
            else {
                $localChanged = $localHash -ne $state.localHash
                $remoteChanged = $remoteHash -ne $state.remoteHash
                if (-not $localChanged -and -not $remoteChanged) {
                    Write-Host 'Dashboard.csv and the sheet are already in sync.' -ForegroundColor Green
                }
                elseif ($localChanged -and -not $remoteChanged) {
                    $remoteHash = Import-Remote
                    Write-SyncState -LocalHash $localHash -RemoteHash $remoteHash
                    Write-Host 'Local changes pushed to the sheet.' -ForegroundColor Green
                }
                elseif ($remoteChanged -and -not $localChanged) {
                    $localHash = Import-Local
                    Write-SyncState -LocalHash $localHash -RemoteHash $remoteHash
                    Write-Host 'Sheet changes pulled into Dashboard.csv.' -ForegroundColor Green
                }
                else {
                    throw 'Both Dashboard.csv and the sheet changed since the last sync. Choose a winner with -Direction Push or -Direction Pull.'
                }
            }
        }
    }
}
catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $syncExit = 1
}
exit $syncExit
