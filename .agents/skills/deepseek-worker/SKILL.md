---
name: deepseek-worker
description: Delegate a concrete repository task to the external DeepSeek V4.1 Flash API through an isolated Codex CLI worktree. Use whenever a prompt begins or ends with the exact sentence "DeepSeek this.", or when the user explicitly asks for the DeepSeek worker. Do not use for ordinary OpenAI subagent delegation.
---

# DeepSeek worker

Use `scripts/Invoke-DeepSeekWorker.ps1` to run the external model alias
`deepseek-flash`, currently DeepSeek V4.1 Flash. The script uses the official
DeepSeek Codex model catalog in `references/models.json`, the Responses API at
`https://api.deepseek.com/`, and the caller's `DEEPSEEK_API_KEY`. It does not
replace the primary Codex model or modify the user's normal Codex configuration.

Nested third-party Codex sessions cannot execute commands under the Windows
sandbox on this host. The worker therefore runs read-only and returns a
schema-constrained Git patch. The wrapper checks and applies that patch only in
the disposable worktree. Do not use the dangerous sandbox-bypass CLI option.

## Workflow

1. Remove the trigger sentence `DeepSeek this.` from the user's prompt.
2. Write a focused task file containing:
   - the concrete goal;
   - files allowed and forbidden;
   - all relevant source excerpts, command output, and reproduction evidence the
     worker needs, because it cannot inspect the filesystem itself;
   - exact acceptance commands;
   - a deliverable requiring changed files and check results.
3. Run:

   ```powershell
   & .\.agents\skills\deepseek-worker\scripts\Invoke-DeepSeekWorker.ps1 -TaskFile <absolute-task-file>
   ```

4. Read the returned summary JSON. Independently inspect the worker commit and
   rerun every acceptance command in its worktree. Never accept weakened tests,
   deleted coverage, hard-coded expected output, unrelated changes, or the
   worker's own identity claim as proof of provider identity.
5. If it passes, cherry-pick the worker commit into the user's branch when the
   original request authorizes implementation. If it fails, write precise review
   feedback to a new task file and retry at most twice.
6. Report the provider (`DeepSeek`), API model alias (`deepseek-flash`), worker
   thread ID, commit, and every field in `usage` from `turn.completed`.

Provider proof comes from the script's fixed provider configuration and retained
JSONL event log, not from text the worker writes. Never print or store the API key.
