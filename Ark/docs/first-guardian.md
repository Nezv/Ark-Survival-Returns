# First Guardian

A complete ritual boss encounter built around the existing Giganotosaurus and one Ancient Remnants
structure (P05). The whole pipeline is server-authoritative, persists one record per ritual anchor and
issues its rewards exactly once. Settings live under `[guardian]`.

## The ritual and the structure

Ancient Remnants registers no altar block: each monolith is a jigsaw structure whose floating centre
is the invulnerable `ancient_remnants:elderheart` entity. Ark therefore does not edit, copy or extend
the third-party structure. It anchors on an existing block and verifies everything at the moment of
interaction:

- the clicked block must be inside a configured structure start, resolved through
  `StructureManager.getStructureWithPieceAt`;
- an `ancient_remnants:elderheart` must stand within `guardian.activationRadius` of that block;
- the arena chunks around the anchor must already be loaded, and a full Giga-sized volume must be
  clear and standing on solid ground (only loaded columns are searched).

The encounter record is keyed by dimension, structure instance (structure-start chunk) and structure
id, so two monoliths never share an attempt, and a structure that generates twice produces two
independent arenas. Default structure: `ancient_remnants:sentinel_monolith`.

The integration is optional at load time: without Ancient Remnants the encounter code never runs and
the journal explains that the mod is required. The manual test instance needs both
`ancient_remnants-neoforge-26.1-1.3.1.jar` and its `fragmentum` dependency (26.1-4.0.5 or newer).

## The Allosaur Heart

- Drops exactly once from a **natural wild** Allosaurus whose killing blow came from a player or that
  player's tame. Looting cannot multiply it, and tamed, spawn-egg or command-spawned Allosaurs never
  drop one, so no tame or farm becomes an infinite key source.
- It is the ritual key. It is consumed **after** the Guardian exists and the encounter record is
  saved; any failure (no room, unloaded arena, downed player, active encounter) leaves it untouched.
- The heart is also a JEI-visible item with English and Portuguese names, a tooltip and a journal
  objective.

## Lifecycle

```text
LOCKED -> READY -> ACTIVE -> RESETTING -> READY
                  |  ^                    |
                  v  |                    v
                (retry, no heart)      (rematch needs a new heart)
                     \-> DEFEATED -----/
```

- **LOCKED** — no record yet; offering a heart starts the attempt.
- **READY** — the attempt is unlocked but the boss is not spawned. Activating the anchor again is
  free: no heart is consumed.
- **ACTIVE** — exactly one Guardian owns the encounter UUID. A second activation at the same anchor
  is refused.
- **RESETTING** — the tribe retreated; the boss was removed and a short cooldown runs before the free
  retry window reopens.
- **DEFEATED** — victory was persisted before rewards were issued. A deliberate rematch starts a new
  ACTIVE record with `rewardsIssued` already set, so payouts cannot repeat.

The record stores the anchor, structure id, tribe, state, boss UUID, participants, registered tames,
scaled health, reward status and timers. A server restart, chunk unload or reconnect can therefore
never duplicate the boss, consume a second heart or issue rewards twice.

## Activation rules

Activation succeeds only when all of these hold:

- the player holds an Allosaur Heart (or the attempt is already READY);
- the clicked block is inside the designated structure and close to its monolith;
- the arena chunks are loaded and the spawn volume is clear, safe and on solid ground;
- no encounter is ACTIVE or RESETTING at this anchor;
- the player is not downed and not under player sedation.

The encounter belongs to the player's FTB Teams tribe. Nearby tribe members inside
`guardian.joinRadius` become participants automatically — joining a fight never costs another heart.

## Guardian Giganotosaurus

A dedicated entity type outside the normal creature roster, so ordinary wild Gigas are unchanged:

- named **Guardian Giganotosaurus**, untamable and immune to sedation;
- excluded from population accounting and culling, never despawns;
- uses the ordinary Giga model, animations and authored hit-frame damage;
- targets only registered participants and their registered tames, and only inside the arena;
- returns to its lair when leashed out of `guardian.arenaRadius` and resets aggro instead of being
  dragged across the map;
- drops no loot-table entries; the victory trophy and schematic are placed by the encounter service.

Health is snapshotted at activation from configurable values: a base pool for the first player, a
larger share for the second, and a smaller share per registered tame, capped at
`guardian.maxTameContribution`. Disconnects during the fight never re-scale or heal the boss.

## Combat tames

At activation, up to four alive tribe-owned tames within `guardian.tameRegistrationRadius` are
registered. Registered tames may be targeted and deal full damage; riding stays allowed, tame deaths
stay real and no tame is ever teleported. Tames that were not registered deal a strongly reduced
configurable share of their damage, so an unregistered army cannot carry the fight. A tame alone
never grants rewards to a player.

## Boss bar

A purple server-controlled boss bar shows the Guardian's name and current health to participants and
to any player within `guardian.barRange`. It is removed when the encounter resets, the boss dies, the
player disconnects or leaves range, and it returns after reconnecting to an active fight. The
ordinary look-at creature bar skips the Guardian so only one bar describes the encounter.

## Retreat and reset

Retreating is a valid strategy, not a trap. When no living, connected participant remains within
`guardian.joinRadius` of the anchor for `guardian.graceTicks`, the Guardian is removed without
drops, the attempt enters RESETTING, and after `guardian.resetDelayTicks` the tribe may summon it
again for free. A death while the partner keeps fighting never triggers a reset. The failed attempt
costs food, medicine and ammunition, never another heart.

## Victory and rewards

On legitimate death the encounter is marked DEFEATED before anything is paid:

- the tribe receives the **Workshop Schematic** progression flag (`tribe_progress` SavedData), which
  is authoritative for future workshop research;
- the physical **Workshop Schematic** item and one **Guardian Trophy** drop at the kill;
- each participant receives the hidden `journal/first_guardian` discovery advancement, which
  completes the shared FTB chapter and pays the individual quest rewards;
- the tribe is notified and the boss bar and boss record are cleared.

Losing the physical schematic never blocks the campaign: `/arkguardian grant <player>` re-issues it
and re-grants the flag. `/arkguardian status` reports the caller's tribe encounters, state, location
and boss health; `/arkguardian reset` returns the nearest encounter to READY, and
`/arkguardian clear` removes the nearest record and its boss.

## Failure and recovery

The encounter composes with P02: downed players can be revived normally, and a fatal death drops
items at the arena as usual. Leaving to re-equip and reclaim gear is a legitimate retreat.

## Performance boundaries

- No chunk is ever force-loaded: every structure, arena, spawn and tame check requires the relevant
  chunk to be loaded already.
- Only ACTIVE and RESETTING records are visited, once per second; idle anchors cost nothing.
- The world is never scanned for structures; the encounter is resolved from a player interaction.
- Boss-bar updates follow damage and participant changes, plus a bounded per-second viewer sync.
- At most one Guardian exists per anchor, enforced by the record state.

## Config quick reference

| Key | Default | Meaning |
|---|---|---|
| `guardian.enabled` | true | Master switch |
| `guardian.announce` | true | Tribe chat for awaken/reset/victory |
| `guardian.structures` | `["ancient_remnants:sentinel_monolith"]` | Accepted structure ids |
| `guardian.activationRadius` | 16 | Heart must be offered this close to the monolith |
| `guardian.arenaRadius` | 40 | Leash around the lair |
| `guardian.joinRadius` | 48 | Tribe members joining the encounter |
| `guardian.graceTicks` | 600 | Empty-arena grace before reset |
| `guardian.resetDelayTicks` | 200 | Cooldown before the free retry |
| `guardian.maxTameContribution` | 4 | Registered tames that add health |
| `guardian.tameRegistrationRadius` | 24 | Tame registration reach |
| `guardian.barRange` | 64 | Who sees the encounter bar |
| `guardian.spawnRadius` | 16 | Ground search radius for the spawn |
| `guardian.baseHealth` | 400 | Pool for one participant |
| `guardian.healthPerExtraPlayer` | 200 | Additional player share |
| `guardian.healthPerTame` | 60 | Additional tame share |
| `guardian.damageMultiplier` | 1.0 | Attack damage vs. an ordinary Giga |
| `guardian.armor` | 8 | Armor points |
| `guardian.unregisteredTameDamageFactor` | 0.1 | Damage share of unregistered tames (0 disables) |

## Automated coverage

`guardian_heart` covers the wild/player-tame kill drop and the tamed and spawned exclusions;
`guardian_policy` covers the health scaling, tame cap, tame damage and reset/retry math;
`guardian_registration` covers untamability, sedation immunity, persistence and the armor default;
`guardian_persistence` round-trips the encounter codec and the world store; `guardian_rewards` proves
the defeat-before-payout order and exactly-once rewards. The guardian suite runs in its own
`arksurvivalreturns:guardian` test environment so its oversized boss cannot disturb the shared
timing-sensitive batches. Visual checks are tracked in Dashboard.csv.
