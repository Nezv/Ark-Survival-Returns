# First Guardian: beacon monoliths

P05 uses Ark's own beacon monolith and the authored dragon from I10. No Ancient Remnants
installation, Allosaurus hunt, heart, altar or activation interaction is required. A monolith stands
empty until a player climbs into its eye; then its dragon comes, circles the head and defends the nest.

![Three beacon monoliths](sky-beacons.png)

## Find a beacon

Explore **new Overworld chunks**, or use /locate structure arksurvivalreturns:sky_beacon.
The native structure set uses 32-chunk spacing and 30-chunk separation: neighbouring candidate
centres along either grid axis differ by 496–528 blocks (normally about 512). All three variants
share this set, so adding colours does not multiply beacon density. Terrain can reject a candidate.

Each original block template is 33 × 100 × 33: a weathered stone pillar of stacked, tilted plates and
cubes in the manner of the Ancient Remnants monoliths, tapering to a point like an Ark obelisk, with a
channel cut down the shaft, a glowing diamond emblem and a broken, forked crown. It is built from stone,
andesite, cobblestone, tuff and stone bricks, mossier towards the ground, with moss, grass and azalea on
the ledges and vines trailing from the walls and the tip. Each variant has its own seed and works a local
stone into the grey: granite (red), diorite and calcite (white), deepslate (black).

The monolith hangs just above the ground: its tip is about seven blocks over the highest ground under
it (or over the sea), with vines reaching down. It is slender, at most 31 blocks across at the head and
narrowing to a point, so little ground lies in its shade. Sites that would push the
crown past the build height are skipped. Terrain adaptation is disabled. Existing chunks and player
builds are not retrofitted; beacons generated before this design keep their old shape.

**The eye.** A round opening some 60 blocks above the ground holds the dragon's nest (a dragon egg) on a floor
of earth, moss and old bones, and a **Loot Crate** against the wall. The crate is a Storage Crate with
the loot table arksurvivalreturns:chests/sky_beacon, rolled when first opened: 3-5 stacks of medicine,
taming supplies and rations, and 2-4 stacks of materials (keratin, pelts, fangs, amber, raw tin, raw
copper, sulphur, wing membrane). Nothing leads up to the eye: climb, build or fly.

The template holds no dragon, only a marker entity on the nest that stands for it. Once a second the
game looks for such a mark beside each living, non-spectator player; when the player is in the core
(within 7.5 blocks of the eye's middle: inside the opening or on its lip) the dragon arrives on its
flight path round the head, 20 blocks out and clear of the stone, with its warning call, and the mark
is removed. It comes once: a slain dragon is not replaced. Shaft, head and flanks do not count, and
the ground below is quiet. The arrival honours guardian.enabled, the natural-spawn config switch and
minecraft:spawn_mobs at that moment, so a beacon reached while they are off wakes when they are back
on. It never loads a chunk: with no loaded room on the path the mark waits. The wyverns have no sleep
clip (fly, hover, idle, walk, fire, glide, fly-fire), so the dragon does not lie in the nest beforehand.
The guardian is deliberately excluded from ordinary wildlife population counts and never despawns.
Beacons generated before this rule keep the dragon they were built with.

For an operator preview, /place structure arksurvivalreturns:sky_beacon chooses a variant and
resolves the height above the local terrain. /place template arksurvivalreturns:sky_beacon/red ~ ~ ~
places the red template at the command position; use sufficient open space and height.
The same template command accepts white and black. Both carry the mark, so a placed beacon wakes like
a generated one.

## Dragon and variants

- Red emblem / red wyvern; pale blue emblem / white wyvern; violet emblem / black wyvern.
- Each uses its authored geometry, exact original atlas, and seven supplied animation clips.
  The original files under Creatures/Dragon/Dragon/out remain unchanged.
- The imported wyverns are also used by ordinary dragon creatures. Their existing behaviour
  role names alias the closest supplied clips; there are no newly authored bite, landing or death clips.
- Variants have the same combat rules and health; they are visual variants, not difficulty tiers.
- The beacon guardian is a separate entity (arksurvivalreturns:guardian_dragon), untamable,
  immune to sedation, persistent and independent of the legacy Guardian Giganotosaurus.
- Its home is the nest. Once it has come it circles the monolith's head and attacks visible Survival players within
  40 blocks of the nest in three dimensions, so the ground under the monolith (about 64 blocks below
  the nest) is safe to walk and the climb is not. Whoever wounds it, or whose tame does, is hunted
  within 96 blocks of the nest for the next 30 seconds: the ground is no safe firing line. It returns
  when they leave. Creative, spectator, downed and sedated players are excluded. Peaceful disables
  its aggression.
- The flying attack has a wind-up and rechecks range, sight and eligibility at impact. It damages
  an intruder without setting terrain on fire. It never follows a player across the map.
- Movement checks loaded chunks, world-border bounds and body collision. There are no chunk tickets.
- Home, palette, health and kill credit survive saves. Retreat does not regenerate health,
  remove the dragon or start a new ritual. A defeated beacon stays defeated.

Guardian config: baseHealth (400), damageMultiplier (1), armor (8), and barRange (64).
Health is fixed at spawn; extra players and tames do not change it. The nearby boss bar uses
the palette's colour.

## Victory

The killing player (or a tame's owner) receives tribe credit. A remembered attacker can receive
credit for a later environmental finish. Victory is saved per beacon before rewards are issued.
The trophy and the tribe's first Workshop Schematic drop in the nest, beside the loot crate, rather
than falling from the aerial kill location. The schematic flag remains authoritative if the
physical item is lost. Nearby members of the credited tribe receive journal/first_guardian,
which completes the journal's boss objective without any earlier quest or heart requirement.
The kill counts as boss XP.

The old optional Ancient Remnants ritual, its entity id, saved encounter records and operator
commands remain for existing saves; they are not used by newly generated beacons. I12 is no
longer a prerequisite for P05 and remains separate work for other bosses.

## Build and verification

- python tools/build_sky_beacons.py creates the three native NBT templates, the worldgen JSON and
  the crate's loot table. SkyBeaconStructure (SIZE, NEST, CRATE) must match its constants.
- python tools/import_creatures.py --dragon-only imports only the authored dragon variants.
- python tools/preview_sky_beacons.py renders the geometry preview above; it is not a game screenshot.
- runData generates names and test instances. build and runGameTestServer check gameplay.
- sky_beacon_assets, sky_beacon_placement, sky_beacon_persistence and sky_beacon_rewards
  cover structure registration/spacing, the loot table, actual template placement (the dragon's mark,
  nest, crate and the open eye), the arrival (not from the shaft, the head or the flank; from the lip of
  the eye; one dragon of the beacon's palette, outside the stone, once), saved home/palette/wounds,
  attack exclusions, the hunt after a wound, and one-time progression rewards in the nest.

In-client visual review is still required for the monolith's look in real terrain, the height above
the ground, wing clearance around the head, the arrival as seen from the eye, flight animation, lighting, and combat feel. The automated preview does not validate those artistic details.
