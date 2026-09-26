# Camp and recovery

P02 gives a fresh duo a camp, a way back after a bad fight and a first-hour material route that
does not depend on a rare biome. This file documents the shipped rules; balance values live in the
server config under `[camp]`, `[recovery]` and `[downed]`.

## The first hour

- **Plant fiber** drops from short and tall grass next to the berries; shears suppress both.
- **Flint** comes from gravel, as in vanilla, and repairs the two primitive weapons.
- **A starter kit** lands on a player's first join in a world: one primitive bedroll, two fiber
  bandages, eight plant fiber and one flint knife. The granted flag is world SavedData, so
  rejoining never duplicates it. Disable with `camp.starterKit=false`.
- **Recipes:** flint knife = flint + stick + fiber; spear = two sticks + flint + fiber; fiber
  bandage = three fiber + string (yields two); bedroll = four plant fiber + leather.

## Primitive bedroll

A flat grass mat, two blocks long like a bed (foot where it is placed, head beyond it), and walkable. Using it sets the personal respawn point through the vanilla respawn data,
so the point belongs to the player rather than the block: destroying, exploding or picking up the
bedroll never strands its owner. Sneak-use rolls it back into an item. The world spawn remains the
fallback when no bedroll has ever been used.

## Recovery caches

A player death replaces the vanilla drop pile with one Recovery Cache.

- The items are stored in world SavedData keyed by the owner, and the placed marker is only a
  visible place to return to. Breaking or exploding the marker turns the entry into an unplaced
  cache instead of deleting it.
- The owner, any FTB Teams tribe member, or a gamemaster can right-click the marker to collect
  everything. Overflow drops at the collector's feet.
- The owner is told the dimension and coordinates on death; tribe members are told too unless
  `recovery.notifyTribe=false`. A reminder is sent on login and respawn.
- If the death spot cannot hold a marker (void, lava, mid-air), the service searches outward for
  safe ground; if none exists it falls back to the owner's bedroll. If even that fails the entry is
  stored unplaced and shown by `/arkrecover list`.
- The backlog is capped by `recovery.maxCaches` (default 3). When a new death would exceed the cap,
  the oldest entries fold into the newest instead of being dropped.
- `keepInventory=true` produces no drops, so no cache is created; the player kept their items.

Commands:

```text
/arkrecover list          # index, place and stack count for your caches
/arkrecover claim <index> # claim an unplaced cache; placed caches must be visited
/arkrecover clear <player> # gamemaster cleanup, markers included
```

## Downed state and revive

A hit that would reduce a player to zero health instead leaves them downed for
`downed.windowTicks` (default 1200 = one minute).

- The downed player cannot attack, interact, use items, drop items, mount or move far from where
  they fell; the same enforcement used for sedation.
- Further damage does not kill but shortens the window by
  `damageBleedFactor` ticks per point (default 1). When it reaches zero, the player dies for real
  and the death follows the normal recovery-cache path.
- A tribe member right-clicks the downed player with a fiber bandage to revive them at
  `reviveHealthFraction` (default 30% of maximum health). `downed.requireTribe=false` lets any
  player revive.
- **Outright fatal** by default: void, lava and fire, `/kill`, and any single hit at or above
  `overkillMultiplier × max health` (default 1.5). These are not rescue windows.
- The state persists across relogging with the remaining ticks; being offline neither drains nor
  pauses it beyond the absence of ticks.

## Verification and limits

Automated coverage: `RecoveryMathTest` and `DownedPolicyTest` cover cap folding, list merging and
the lethality matrix; `CampGameTests` cover the starter kit and the bedroll contract;
`RecoveryGameTests` cover exact collection, no loose drops and cap folding; `DownedGameTests`
cover the tribe gate, the revive health and bandage consumption; `JournalGameTests` check that
both chapters load with all item tasks and recipes resolving.

NeoForge refuses advancement grants to `FakePlayer`, so the hidden discovery awards themselves
(`journal/first_loss`, `journal/first_recovery`, `journal/first_downed`, `journal/first_revive`)
are verified by the manual playtest rather than the headless suite.

## Duo playtest checklist

1. Join a fresh world with two players: both receive the starter kit exactly once.
2. Craft a bedroll, set the respawn point, then break the bedroll and die: you respawn at the
   saved point.
3. Die once with a full inventory: exactly one cache appears, coordinates reach the tribe in chat,
   and right-clicking restores the haul.
4. Die three more times with `recovery.maxCaches=3`: the oldest caches fold into the newest.
5. Let one player drop to zero health from a normal predator: the countdown HUD appears, actions
   are locked, and a fiber bandage from the partner revives at 30% health.
6. Die to lava and to the void: both should kill outright (no rescue window).
7. Break a recovery marker instead of collecting, then use `/arkrecover list` and
   `/arkrecover claim 1` to confirm nothing was lost.
8. Open the journal: the Camp and Recovery chapter tracks the new objectives for both players.
