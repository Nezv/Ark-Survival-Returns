# Movement tuning

The development server config is `run/config/arksurvivalreturns-server.toml`; the committed example is `config/arksurvivalreturns-server.toml`. Restart after editing. Damage and HP formulas are unchanged.

`movement.playerSprintBlocksPerSecond = 5.612` is the normal unbuffed player sprint benchmark. Each `[movement.<species>]` section exposes `sprintRatio`, `waterRetention` and `strideScale`. The ratio describes full pursuit/flee speed on unobstructed ordinary ground, not wandering speed or a guarantee through obstacles. Player potion buffs do not change the benchmark.

| Species | Sprint ratio | Target blocks/second | Water retention |
|---|---:|---:|---:|
| Rex | 1.8 | 10.10 | 100% |
| Giga | 2.0 | 11.22 | 100% |
| Velociraptor | 2.2 | 12.35 | 100% |
| Titanosaur | 1.25 | 7.02 | 65% |
| Brontosaurus | 1.05 | 5.89 | 65% |
| Triceratops | 1.1 | 6.17 | 65% |
| Therizinosaurus | 1.5 | 8.42 | 65% |
| Argentavis / Pteranodon | 0.85 | 4.77 | 65% |

The land conversion accounts for vanilla Mob applying its controlled speed both to forward input and movement acceleration. Swimming compensates for water drag while retaining collision, gravity and buoyancy. Actual paths, turning and currents still affect travel. Terrain step height scales with body size, capped at three blocks.

Animation timing matches each gait clip to the ground speed. `tools/build_behavior_clips.py` measures how fast a clip's planted feet slide through the rig (forward kinematics of the imported skeleton) and writes it to `BehaviorClips`; the clip then plays at actual speed / measured speed, clamped to 0.3–2.2, so the feet stay put and a giant moving slower than its stride steps slower instead of skating. Clips without a measurable stance fall back to the older approximation from body height and clip duration. Increase `strideScale` to slow the visual cadence without changing movement. Each individual also plays its clips at its own rate (±6%), so a herd does not step in unison.

Wandering walks near the pace the walk clip was authored for (85% of its measured foot speed), kept between a fifth and two fifths of the full sprint and varied ±10% per individual; pursuit and escape use the full sprint, varied a few percent per individual so a pack spreads out.

Bodies turn instead of snapping. Idle turns use the rig's authored rate (about 75 degrees per turn clip, or 18/√height degrees per tick without a clip, clamped to 1.5–12). Path steering has a minimum rate of 12/√width, clamped to 6–12 degrees per tick, so a long idle turn clip cannot stall a small walker for several seconds. A path node more than 90 degrees from the facing causes a short pivot with the synced TURN action; smaller changes use a slower walking arc. The client plays the rig's turn-left or turn-right clip at a rate matched to the turn; flyers bank and swimmers use their left and right swim clips. The body follows the facing smoothly and the head stays within 50 degrees of it. If a rig steps the wrong way while turning, set the client option `mirrorTurnClips = true`.

Land routines defer new paths while airborne. Reaching the home block ends return recovery, and failed roaming or drinking routes never start chase recovery. A wide body can finish a short final approach under continuous steering after its vanilla path consumes the last node, provided the entire swept body fits through loaded, dry terrain.

Locomotion clip selection (walk/run versus idle) uses `LocomotionSignal`, a hysteresis over the same measured travel with a 1.0 blocks/second start and a three-tick, 0.25 blocks/second stop. It deliberately ignores GeckoLib's render-state movement flag, which counts collision creep, pack jostle and sub-walking drift as travel and keeps the walk clip alive after the creature has stopped.

Therizinosaurus is now 2× original size. Titanosaur is 6× original size (50% larger than the previous 4× version). Rex/Giga stay 3× and remaining species 2×. Mesh, animation translation tracks and collision dimensions are imported together. Existing entities receive the new speed baseline without healing or rerolling levels.
