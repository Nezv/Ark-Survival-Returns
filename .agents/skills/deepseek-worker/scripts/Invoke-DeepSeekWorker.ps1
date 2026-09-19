[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TaskFile,

    [string]$BranchLabel = "task"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
    throw "DEEPSEEK_API_KEY is not set."
}

$taskPath = (Resolve-Path -LiteralPath $TaskFile).Path
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$skillRoot = Split-Path -Parent $scriptDir
$catalogPath = (Resolve-Path -LiteralPath (Join-Path $skillRoot "references\models.json")).Path
$schemaPath = (Resolve-Path -LiteralPath (Join-Path $skillRoot "references\worker-result.schema.json")).Path
$repoRoot = (& git -C $scriptDir rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    throw "The worker skill must run inside a Git repository."
}

$safeLabel = ($BranchLabel.ToLowerInvariant() -replace '[^a-z0-9-]', '-') -replace '-+', '-'
$safeLabel = $safeLabel.Trim('-')
if ([string]::IsNullOrWhiteSpace($safeLabel)) { $safeLabel = "task" }
$runId = "{0}-{1}-{2}" -f (Get-Date -Format "yyyyMMdd-HHmmss"), $safeLabel, ([Guid]::NewGuid().ToString("N").Substring(0, 8))
$branch = "deepseek/$runId"
$worktreeBase = Join-Path $repoRoot ".deepseek-worktrees"
$runBase = Join-Path $repoRoot ".deepseek-runs"
$worktree = Join-Path $worktreeBase $runId
$runDir = Join-Path $runBase $runId

New-Item -ItemType Directory -Force -Path $worktreeBase, $runDir | Out-Null
& git -C $repoRoot worktree add -q -b $branch $worktree HEAD
if ($LASTEXITCODE -ne 0) { throw "git worktree add failed." }

$taskCopy = Join-Path $runDir "task.md"
$eventsPath = Join-Path $runDir "events.jsonl"
$lastMessagePath = Join-Path $runDir "last-message.md"
$patchPath = Join-Path $runDir "worker.patch"
$summaryPath = Join-Path $runDir "summary.json"
Copy-Item -LiteralPath $taskPath -Destination $taskCopy

$catalogValue = $catalogPath.Replace('\', '/')
$providerConfig = 'model_providers.deepseek={ name="DeepSeek", base_url="https://api.deepseek.com/", wire_api="responses", env_key="DEEPSEEK_API_KEY" }'
$catalogConfig = 'model_catalog_json="' + $catalogValue + '"'
$task = [IO.File]::ReadAllText($taskPath)
$prompt = @'
You are the external DeepSeek worker. You cannot inspect or write the filesystem.
Use only the complete context in the task below. Return a JSON object matching the
provided output schema. Put every requested repository change into `unified_diff`
as a standard Git unified diff with repository-relative paths. Do not use tools.
'@ + "`n`n" + $task
$codexArgs = @(
    "exec",
    "--ignore-user-config",
    "--ephemeral",
    "--json",
    "--color", "never",
    "--cd", $worktree,
    "--sandbox", "read-only",
    "--model", "deepseek-flash",
    "--config", 'model_provider="deepseek"',
    "--config", $catalogConfig,
    "--config", $providerConfig,
    "--output-schema", $schemaPath,
    "--output-last-message", $lastMessagePath,
    $prompt
)

$rawEvents = @(& codex @codexArgs 2>&1)
$exitCode = $LASTEXITCODE
[IO.File]::WriteAllLines($eventsPath, [string[]]$rawEvents, [Text.UTF8Encoding]::new($false))

$parsedEvents = foreach ($line in $rawEvents) {
    if ($line -isnot [string] -or -not $line.TrimStart().StartsWith('{')) { continue }
    try { $line | ConvertFrom-Json } catch { }
}
$threadEvent = @($parsedEvents | Where-Object type -eq "thread.started" | Select-Object -Last 1)
$turnEvent = @($parsedEvents | Where-Object type -eq "turn.completed" | Select-Object -Last 1)

$commit = $null
$workerResult = $null
if ($exitCode -eq 0 -and (Test-Path -LiteralPath $lastMessagePath)) {
    $workerResult = Get-Content -Raw -LiteralPath $lastMessagePath | ConvertFrom-Json
    if (-not [string]::IsNullOrWhiteSpace($workerResult.unified_diff)) {
        [IO.File]::WriteAllText($patchPath, $workerResult.unified_diff.TrimEnd() + "`n", [Text.UTF8Encoding]::new($false))
        & git -C $worktree apply --check --whitespace=error-all $patchPath
        if ($LASTEXITCODE -ne 0) { throw "DeepSeek returned a patch that failed git apply --check." }
        & git -C $worktree apply --whitespace=error-all $patchPath
        if ($LASTEXITCODE -ne 0) { throw "Failed to apply the validated DeepSeek patch." }
    }
}

$status = @(& git -C $worktree status --porcelain)
if ($exitCode -eq 0 -and $status.Count -gt 0) {
    & git -C $worktree add -A
    if ($LASTEXITCODE -ne 0) { throw "Failed to stage DeepSeek worker changes." }
    & git -C $worktree commit -m "DeepSeek worker: $safeLabel"
    if ($LASTEXITCODE -ne 0) { throw "Failed to commit DeepSeek worker changes." }
    $commit = (& git -C $worktree rev-parse HEAD).Trim()
}

$usage = if ($turnEvent.Count -gt 0) { $turnEvent[0].usage } else { $null }
$summary = [ordered]@{
    provider = "DeepSeek"
    model = "deepseek-flash"
    api_base = "https://api.deepseek.com/"
    thread_id = if ($threadEvent.Count -gt 0) { $threadEvent[0].thread_id } else { $null }
    exit_code = $exitCode
    branch = $branch
    worktree = $worktree
    commit = $commit
    task_file = $taskCopy
    events_file = $eventsPath
    last_message_file = $lastMessagePath
    patch_file = if (Test-Path -LiteralPath $patchPath) { $patchPath } else { $null }
    worker_summary = if ($null -ne $workerResult) { $workerResult.summary } else { $null }
    usage = $usage
}
$summaryJson = $summary | ConvertTo-Json -Depth 8
[IO.File]::WriteAllText($summaryPath, $summaryJson + "`n", [Text.UTF8Encoding]::new($false))
Write-Output $summaryJson

if ($exitCode -ne 0) { exit $exitCode }
