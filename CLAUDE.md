@AGENTS.md

## Registering changes in Dashboard.csv

Every change the user asks for is registered in `Dashboard.csv` in the same commit as the change, where it shows at row level:

- A request with no row of its own gets a new row at the end: type, next free ID of that type, item, description of what the game does now, proposer, executor, status. A dated note at the end of a loosely related row does not count.
- A change to an existing item rewrites that row in place: the Description says what the game does now, and the Status opens with the current state, then one dated line (date, agent, what changed, what was verified) and one `Open:` line. They replace the previous ones; nothing is appended (size rules: AGENTS.md, Source of truth).
- Rows the change makes stale are corrected and point to the new row's ID.
- Name the registered IDs in the final summary to the user.

## Build and GameTests

Do not rerun `./gradlew build` or `./gradlew runGameTestServer` after a change, and do not report that they pass: they always do. Run them only when the work is being shipped or when a bug is being fixed. For Claude this replaces the line in AGENTS.md that asks for both after gameplay changes.
