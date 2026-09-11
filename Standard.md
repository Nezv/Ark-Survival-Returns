# Workspace Standards

## Standardized change log

Maintain [CHANGELOG.md](CHANGELOG.md) in the repository root as the canonical, player-facing patch history.

1. Keep an **Unreleased** section first, followed by completed patches in reverse chronological order. Each patch uses `## <version or patch name> — YYYY-MM-DD` and a short summary of its theme. Use the project timezone, America/Sao_Paulo. A patch label does not imply a published release or change the mod's package version.
2. During execution, record implemented changes under Unreleased. Use **Added**, **Changed**, **Fixed**, and **Removed** as needed; omit empty categories. Describe the resulting player experience and meaningful configuration changes. Keep proposals and deferred mechanics out of implemented-change lists.
3. Include **Compatibility and known limitations** for save migration, required restarts, dependencies, temporary settings and unfinished systems. Include **Validation** with the checks actually performed and any remaining playtesting. Never describe a build check as visual verification.
4. On completion, move the accumulated entry into a dated patch section and retain an empty Unreleased section for the next patch. Use an agreed version/name; otherwise use a descriptive patch name. Tuning during the same unfinished patch updates its entry rather than creating a new release for every edit.
5. Backfill history only from reliable dated commits, logs or implementation records. Distinguish implementation, verification and publication dates. When individual dates are missing, consolidate the known implemented state into a beta baseline dated when recorded; do not invent earlier releases or assign all features to the date of a later verification run.
6. Keep completed entries stable except for factual corrections. Link detailed documentation instead of copying research, command logs or internal discussion. Update the changelog alongside each patch and check it against the final code/configuration and validation results.

## GitHub backup directive

After every main execution (an implemented patch or another completed substantive task), back up the completed work to the configured GitHub remote:

1. Review `git status` and the diff, then run `git add -- <completed-task paths>` to stage the relevant changes, including required documentation and assets. Preserve unrelated work and never include secrets, local saves, build outputs or disposable caches.
2. Run `git commit -m "<concise description of the completed work>"` after the appropriate validation. If there are no changes, do not create an empty commit.
3. Run `git push` to the current branch's configured upstream; when an upstream is missing, use the intended existing remote and current branch. Never force-push or rewrite history as part of a backup.
4. Verify that the push succeeded and report the commit and backup result. If authentication, connectivity or a remote conflict prevents backup, retain the local commit and report the specific blocker; do not describe an unpushed commit as a GitHub backup.

This directive authorizes routine add/commit/push for completed work without repeated confirmation. It does not bypass the proposal-and-feedback stage below or authorize unrelated changes.

## Standardized patch methodology

A new chat is open for each patch around a namespace/theme. The agent will then be responsible for the dynamics implemented and fixed under that namespace. Then the following steps shall occur:

1. In a first moment, the idea is to broaden the concept. Consider existing projects in the internet and player experience into proposing innovative changes to the patch, then propose to add or skip certain mechanics based on these changes, like a report. Here, also estimate performance impact of each proposal on what regards playing on multiplayer.

2. In a second moment, I'll provide feedback on the direction chosen for the update and you *execute*.
3. Anything past the execution is tuning and debugging.
