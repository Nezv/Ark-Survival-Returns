# Camp and rescue

P02 gives a fresh duo a camp, a rescue window after a bad fight and a first-hour material route that
does not depend on a rare biome. This file documents the shipped rules; balance values live in the
server config under `[camp]` and `[downed]`. The Recovery Cache (death loot kept in a marker) was
removed on 2026-09-26: a real death drops items as in vanilla.

## The first hour

- **Plant fiber** drops from short and tall grass next to the berries; shears suppress both.
- **Flint** comes from gravel, as in vanilla, and repairs the flint knife.
- **A starter kit** lands on a player's first join in a world: one primitive bedroll, two fiber
  bandages, eight plant fiber and one flint knife. The granted flag is world SavedData, so
  rejoining never duplicates it. Disable with `camp.starterKit=false`.
- **Recipes:** flint knife = flint + stick + fiber; keratin spear = keratin + two sticks + fiber
  (keratin drops from horned, plated and beaked creatures); fiber bandage = three fiber + string
  (yields two); bedroll = four plant fiber + leather.

## Primitive bedroll

A flat grass mat, two blocks long like a bed (foot where it is placed, head beyond it), and walkable. Using it sets the personal respawn point through the vanilla respawn data,
so the point belongs to the player rather than the block: destroying, exploding or picking up the
bedroll never strands its owner. Sneak-use rolls it back into an item. The world spawn remains the
fallback when no bedroll has ever been used.

## Downed state and revive

A hit that would reduce a player to zero health instead leaves them downed for
`downed.windowTicks` (default 1200 = one minute).

- The downed player cannot attack, interact, use items, drop items, mount or move far from where
  they fell; the same enforcement used for sedation.
- Further damage does not kill but shortens the window by
  `damageBleedFactor` ticks per point (default 1). When it reaches zero, the player dies for real
  and drops their items as usual.
- A tribe member right-clicks the downed player with a fiber bandage to revive them at
  `reviveHealthFraction` (default 30% of maximum health). `downed.requireTribe=false` lets any
  player revive.
- **Outright fatal** by default: void, lava and fire, `/kill`, and any single hit at or above
  `overkillMultiplier × max health` (default 1.5). These are not rescue windows.
- The state persists across relogging with the remaining ticks; being offline neither drains nor
  pauses it beyond the absence of ticks.

## Verification and limits

Automated coverage: `DownedPolicyTest` covers the lethality matrix; `CampGameTests` cover the
starter kit and the bedroll contract; `DownedGameTests` cover the tribe gate, the revive health
and bandage consumption; `JournalGameTests` check that both chapters load with all item tasks and
recipes resolving.

NeoForge refuses advancement grants to `FakePlayer`, so the hidden discovery awards themselves
(`journal/first_downed`, `journal/first_revive`)
are verified by the manual playtest rather than the headless suite.

## Duo playtest checklist

1. Join a fresh world with two players: both receive the starter kit exactly once.
2. Craft a bedroll, set the respawn point, then break the bedroll and die: you respawn at the
   saved point.
3. Let one player drop to zero health from a normal predator: the countdown HUD appears, actions
   are locked, and a fiber bandage from the partner revives at 30% health.
4. Die to lava and to the void: both should kill outright (no rescue window).
5. Open the journal: the Camp and Rescue chapter tracks the new objectives for both players.
