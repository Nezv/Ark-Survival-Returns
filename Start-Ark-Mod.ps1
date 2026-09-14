# Double-click Start-Ark-Mod.bat, or run this script from PowerShell.
# -Check builds the mod and prepares the client without opening Minecraft.
[CmdletBinding()]
param([switch]$Check)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSCommandPath
$modRoot = Join-Path $repositoryRoot 'Ark'
$gradleWrapper = Join-Path $modRoot 'gradlew.bat'
$previousJavaHome = $env:JAVA_HOME
$locationPushed = $false
$launcherExit = 0
try {
    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw "The mod Gradle wrapper was not found at $gradleWrapper. Keep this launcher beside the Ark folder."
    }

    $javaCandidates = @()
    if ($env:JAVA_HOME) { $javaCandidates += $env:JAVA_HOME.Trim('"') }
    $javaCandidates += Join-Path $env:USERPROFILE '.jbang\cache\jdks\25'
    $pathJava = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pathJava) { $javaCandidates += Split-Path -Parent (Split-Path -Parent $pathJava.Source) }

    $selectedJava = $null
    foreach ($candidate in $javaCandidates) {
        $releaseFile = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath $releaseFile -PathType Leaf) -and
            (Select-String -LiteralPath $releaseFile -Pattern '^JAVA_VERSION="25(?:[.\-+"\s])' -Quiet)) {
            $selectedJava = $candidate
            break
        }
    }
    if (-not $selectedJava) {
        throw 'Java 25 was not found. Install a JDK 25 and set JAVA_HOME to its folder, then try again.'
    }
    $env:JAVA_HOME = $selectedJava
    # Windows selects the GPU per executable. On this laptop the high-performance adapter is NVIDIA.
    $gpuKey = 'HKCU:\Software\Microsoft\DirectX\UserGpuPreferences'
    New-Item -Path $gpuKey -Force | Out-Null
    $javaGpuHomes = @($selectedJava)
    $javaLink = Get-Item -LiteralPath $selectedJava
    if ($javaLink.Target) { $javaGpuHomes += @($javaLink.Target) }
    foreach ($javaGpuHome in ($javaGpuHomes | Select-Object -Unique)) {
        foreach ($binary in @('java.exe', 'javaw.exe')) {
            $javaGpuPath = Join-Path $javaGpuHome ('bin\' + $binary)
            if (Test-Path -LiteralPath $javaGpuPath) {
                $currentGpu = (Get-ItemProperty -LiteralPath $gpuKey -Name $javaGpuPath -ErrorAction SilentlyContinue).$javaGpuPath
                $settings = @($currentGpu -split ';' | Where-Object { $_ -and $_ -notmatch '^GpuPreference=' })
                $settings += 'GpuPreference=2'
                New-ItemProperty -LiteralPath $gpuKey -Name $javaGpuPath -PropertyType String -Value (($settings -join ';') + ';') -Force | Out-Null
            }
        }
    }
    $devRuntime = ConvertFrom-StringData (Get-Content -LiteralPath (Join-Path $modRoot 'config\dev-runtime.properties') -Raw)
    Write-Host "Client memory limit: $($devRuntime.clientHeap). Render distance: $($devRuntime.renderDistance). Simulation distance: $($devRuntime.simulationDistance). Windows graphics preference: high-performance GPU."
    Write-Host 'Ark Survival Returns' -ForegroundColor Green
    Write-Host "Using Java: $selectedJava"
    Write-Host 'The first launch may take a few minutes to download dependencies and build the mod.'
    Push-Location -LiteralPath $modRoot
    $locationPushed = $true
    if ($Check) {
        & $gradleWrapper classes prepareClientRun prepareDevRuntime verifyClientPack verifyDevDependencies --console=plain
    }
    else {
        Write-Host 'Starting Minecraft with the mod. Keep this console open while you play.'
        & $gradleWrapper runClient --console=plain
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle exited with code $LASTEXITCODE. See the build or game error above."
    }
    if ($Check) { Write-Host 'Launch preparation passed. Double-click Start-Ark-Mod.bat to play.' -ForegroundColor Green }
}
catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    $launcherExit = 1
}
finally {
    if ($locationPushed) { Pop-Location }
    $env:JAVA_HOME = $previousJavaHome
}
exit $launcherExit
