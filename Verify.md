# Verify

Everything that has **not** been confirmed in an interactive game client. Automated checks (Gradle
build, JUnit, headless GameTests, datagen, installer checksums) are recorded in
[CHANGELOG.md](CHANGELOG.md) and do not count as in-game verification. When an item below passes a
client playtest, delete it here; if it changed player-facing behavior, add the confirmed result to
the matching changelog validation.

Entries are grouped by patch, newest first. Each says what to do and what to look for.

## First Guardian (P05) — added 2026-09-21

- **Install the integration mods.** Load Ancient Remnants 26.1-1.3.1 plus fragmentum 26.1-4.0.5 (or
  newer) in the instance before testing; the journal's second quest explains the requirement.
- **Heart drop.** Kill a wild Allosaurus with a spear and with a tribe tame: each should drop exactly
  one heart. Kill a tamed and a command-spawned one and confirm neither drops a heart.
- **Structure binding.** Find a Sentinel Monolith, stand beneath the floating monolith and use the
  heart on an altar block: one Guardian should spawn, the heart should be consumed and the purple bar
  should appear. Use a heart on a block outside the structure and confirm it is retained with no
  reaction.
- **Duo and tame scaling.** Start the fight with two players and two to four registered tames;
  confirm the boss engages, follows targets inside the arena and drops aggro after a retreat. Check
  the bar health against the boss and confirm an unrelated player outside the arena sees no bar.
- **Retreat and retry.** Leave the arena, wait for the reset grace, then activate the anchor again
  with an empty hand: the Guardian should return without another heart. Kill one of two players while
  the other keeps fighting and confirm no reset happens.
- **Restart safety.** Restart the server during an ACTIVE attempt and reconnect: exactly one Guardian
  must remain and the bar must return.
- **Victory.** Defeat the Guardian and confirm the schematic and trophy drops, the tribe flag, the
  journal completion and the participant advancement, then use another heart to confirm a rematch
  does not pay twice.
- **World compatibility.** Confirm ordinary wild Gigas, unrelated Ancient Remnants structures and the
  blessing behavior of untouched monoliths are unchanged.

Automated so far: all 58 headless GameTests, including the five guardian tests in the
`arksurvivalreturns:guardian` environment.

## Homestead Economy (P04) — added 2026-09-20

- **Berry bushes.** Use each of the four berries on grass/dirt/farmland: the right bush appears and
  the berry is consumed. Watch one grow through its four stages, harvest a ripe one (berries appear,
  bush resets), and break another to confirm the drop. Bone meal and shears should do nothing special.
- **Feeding trough.** Fill it with cooked meat next to a hungry tame: every few seconds one item
  should disappear and the animal should heal. A wild creature nearby must never eat from it.
  Empty-hand takes the stack back; breaking the trough drops it.
- **Drying rack.** Insert raw meat/fish/berries, wait the configured batches, and take a dried ration.
  Eating one should restore nutrition; verify the rack's contents drop when broken.
- **Cooking pot.** The screen opens with four slots and a meal slot; placing an invalid combination
  must never consume anything. Cook a hearty stew and a trail mix, eat both and confirm the
  regeneration and speed effects, then shift-click the meal out.
- **Concentrated sedative.** Craft it (3 narcoberries + fiber), eat one to confirm the stronger
  knock-out, and craft improved tranquilizer arrows (4 arrows + 1 concentrate). Fire one at a large
  creature and confirm the torpor gain is clearly above the base arrow.
- **Charcoal kiln.** Insert logs, wait the batches, take charcoal; the block must not need fuel.
  Inserting dirt must be refused.
- **Primitive forge.** Insert raw ore and confirm the ingot comes out without fuel; the vanilla
  furnace must still smelt the same ore with fuel.
- **Storage crate.** Craft it, open the 27-slot screen, move stacks in and out, break it and confirm
  the contents drop, then place it again and confirm a server restart preserves the contents.
- **Homestead chapter.** The fourth journal chapter (six quests) should show both languages, and its
  item tasks should tick from station outputs; both tribe members share progress.

Automated so far: 52 JUnit tests, all 55 headless GameTests, FTB loading 4 chapters / 23 quests.

## Weight and Working Tames (P03) — added 2026-09-20

- **Mass gauge and bands.** Fill the inventory to ~75%: gauge turns amber with one warning, and
  sprint must still work. Cross 100%: sprint stops and movement begins to slow; at 125% the slowdown
  should bottom out but walking, jumping and dropping items must still work. Empty the inventory and
  the modifier must clear.
- **Manual overloading.** Drag items into a tame's hold past its capacity: insertion must always be
  allowed, and only movement suffers. `mass.preset=RELAXED` and `OFF` should respectively shift and
  remove every effect after a config reload.
- **Harnesses and capacity.** A tamed Triceratops shows bare capacity until the pack harness is in
  the rig slot; a Brontosaurus should demand the reinforced harness. Both harness recipes craft in a
  survival inventory; the rig drops on death.
- **Mount load and HUD.** Ride a loaded tame: the HUD should switch to the mount bar and count your
  carried inventory; dismounting returns the bar to the player and the mount should keep its own
  slowdown.
- **Fast Load / Unload.** With a chest beside a tamed creature, Load must fill the hold up to the
  automation ceiling and leave the overflow; Unload must empty the hold into the storage. Both must
  ignore unloaded chunks, and the buttons must be visible and clickable in the tame screen.
- **Flight overload.** Fill a flyer past capacity: takeoff must be refused with a warning; if it is
  already airborne when overloaded, it must descend under control and land without falling.
- **Swim overload.** Fill an aquatic mount past capacity: it must not dive and should drift upward;
  with the rider at low air it must surface before drowning them.
- **Work orders.** Give a Triceratops the WORK order on a grass/Tuft/berry field: it should cut
  tufts, graze grass in place, reset ripe bushes and fill its hold with fiber/berries. Walk away and
  it must pause; a non-owner tribe member without the WORK flag must not supervise.
  `/arktribe perm <player> work on` should allow it.
- **Ankylosaurus mining.** The worker should mine natural stone and ores, receive vanilla drops plus
  the configured ore bonus, ignore blocks it cannot reach in loaded chunks, and never break a block
  you placed yourself.
- **Work journal chapter.** The third chapter (Working Tames) should show 5 quests, both languages,
  and its item tasks should tick from the creature-gathered materials.

Automated so far: 52 JUnit tests, all 50 headless GameTests, FTB loading 3 chapters / 17 quests.

## Camp and Recovery (P02) — added 2026-09-20

- **Downed HUD.** Take lethal damage from an ordinary hit: red vignette, countdown bar, "DOWNED" label
  and the bandage hint should appear; further hits should shorten the bar; it should clear on revive,
  bleed-out and relogging.
- **Revive flow.** A tribe member uses a fiber bandage on the downed player: revive at 30% health,
  bandage consumed, both players get their messages, and `journal/first_revive` is awarded to the
  reviver. A stranger must be refused while `downed.requireTribe=true`.
- **Recovery cache.** Die with a full inventory: exactly one marker, coordinates in tribe chat,
  right-click restores the whole haul. Break a marker instead of collecting, then `/arkrecover list`
  and `/arkrecover claim 1`. Die to lava/void: the fallback should place the cache at the bedroll or
  leave it claimable.
- **Cap folding.** With `recovery.maxCaches=3`, a fourth death should fold the oldest cache into the
  newest without losing items.
- **Field bedroll.** Use sets respawn; break it and die: respawn still lands there; sneak-use rolls it
  back up. The marker and bedroll are box models that should read clearly in the world.
- **Starter kit.** First join message and items; rejoining must not duplicate anything.
- **Grass route.** Plant fiber and berries drop on break; shears suppress both.
- **Journal chapter.** Camp and Recovery layout, titles and rewards; both tribe members share
  progress and each claims rewards once.
- **Hidden discoveries.** `journal/first_loss`, `journal/first_recovery`, `journal/first_downed` and
  `journal/first_revive` are awarded (the headless suite cannot: NeoForge refuses advancement grants
  to fake players).

Automated so far: 47 JUnit tests, all 44 headless GameTests, FTB loading 2 chapters / 12 quests.

## Survival Journal and Tribes (P01) — added 2026-09-20

- **Journal.** J keybind and item use open the FTB book; layout, chapter icons and the pinned-objective
  HUD read correctly; keybind conflicts checked.
- **Party flow.** `/ftbteams` create, invite, accept; shared quest progress and per-player rewards
  across two clients.
- **Tribe permissions.** `/arktribe status|perm|reset` messages and effects; flags reset when a player
  leaves or changes teams; owner keeps every permission.
- **Map entitlement.** A rank-5-origin tame unlocks the map for its owner; `/arkmap` grant/revoke; the
  locked-map message while `progression.mapRequiresUnlock=true`.
- **Client pack.** FTB journal + shaders + Xaero together: no overlay z-fighting or UI scaling issues.
- **Pack distribution.** FTB reads `config/ftbquests/quests/` from the instance; verify the manual
  copy step on a non-dev launcher.

Automated so far: 41 JUnit tests, all 40 headless GameTests, FTB pack load.

## Minecraft 26.1.2 migration — added 2026-09-20

- Shader, audio and visual pass on 26.1.2 with the repinned client pack (Sodium 0.9.2, Iris 1.11.4,
  Xaero 1.46.0, bridge 0.1.2).

## Unified Spatial Audio — added 2026-09-19

- Sound balance and loudness; reverb and occlusion inside caves, water and build interiors; footstep
  material transitions; ambience changes at day/night/weather boundaries.

## Anatomy-driven creature textures — added 2026-09-19

- All five painted variants per species read correctly in-game (eyes, claws, horns, jaw and tongue
  regions aligned, no visible 3×3-cell seams); variant stability after save/reload and for a second
  client.

## Vanilla spawning — added 2026-09-18

- Nest appearance, perching and egg defense; natural spawn density after the vanilla-spawner rework;
  the removed habitat map markers are gone from the UI.

## Taming, torpor and riding — added 2026-09-16

- Seat alignment and rider appearance in-client, especially the 24 of 41 meshes that are not
  normalised to their entity origin; collapse, unconscious-loop and wake poses; torpor bar
  readability; the long knock-out maintenance loop feels right; rider appearance on side-mounted rigs.

## Collection ecosystems (Unreleased) — added 2026-09-16

- Authored sleep poses and red night eyes visually verified; water, swamp, snow and nest visuals;
  marker readability was removed with the map markers; cold-biome balance; movement and containment
  in real terrain for flyers, swimmers and semi-aquatic species.

## Theme alignment — added 2026-09-16

- Interactive balance after the removals; the animal-bone supply feels sufficient; players returned
  from a removed dimension land somewhere sensible.

## Land ecosystem — added 2026-09-14

- Narrow-water and bank navigation for wide creatures; natural population balance; multiplayer
  performance with the 16 GB / 24 render-chunk profile.

## Debug spyglass — added 2026-09-13

- Overlay appearance and live two-player inspection (nearest/invisible targets, range, walls).

## Source model collection — added 2026-09-12

- No interactive game or Blockbench test has been run on the 21 source-only projects or on the
  triangle-fitting for tusks, antlers, wings, fins and fur.

## Creature expansion — added 2026-09-12

- Visual quality of the ten added creatures, combat balance, natural habitat selection and
  multiplayer performance.

## Flying ecosystem — added 2026-09-11

- Nest and flight visuals, audio balance, perching and swoop timing on the target machine.

## Beta baseline systems — added 2026-09-20 (retroactively listed)

- Natural encounter frequency, animation and foot placement, map visuals, shader performance and
  actual GPU selection on the user's machine.
