@AGENTS.md

## Registering changes in Dashboard.csv

Every change the user asks for is registered in `Dashboard.csv` in the same commit as the change, where it shows at row level:

- A request with no row of its own gets a new row at the end: type, next free ID of that type, item, description of what the game does now, proposer, executor, status. A dated note at the end of a loosely related row does not count.
- A change to an existing item updates that row: the Description says what the game does now, and the Status opens with the current state, then a dated line (date, agent, what changed, what was verified, what is still open).
- Rows the change makes stale are corrected and point to the new row's ID.
- Name the registered IDs in the final summary to the user.
