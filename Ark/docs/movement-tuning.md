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

Bodies have weight (`Inertia`). One length stands for a body's bulk: the square root of its width times its height. A calm turn is at most 190 / bulk^0.8 degrees a second, between 12 and 170, a quarter more for hunters and 1.6 times that in a chase or a flight: about 140 for a Megalocerus, 75 for a Parasaur, 50 for a Triceratops, 36 for a Rex and 12 for a Titanosaur. The turn gathers speed over a quarter second to a second, holds its rate and eases onto the heading, so an about-face takes a stag a second and a half, a Triceratops over three and a Rex nearly six (under four in a chase); nothing snaps round. Pace builds from a standstill over 6 + 4.4 x bulk ticks to full speed (0.6 s for a stag, 1 s for a Parasaur, 2.6 s for a Rex, 5 s at most) and runs out in six tenths of that when the way ends; a body with a target or a strike in progress brakes two and a half times harder. Running its pace out, it never carries itself over an edge or into water. Into a bend the body slows so its arc tightens, and a path node farther to the side than 95 - 4 x bulk degrees (55 to 90) makes it stop and pivot with the synced TURN action before it steps off. The client plays the rig's turn clip at the speed of the turn. A ridden mount still answers its rider directly.

Land routines defer new paths while airborne. Reaching the home block ends return recovery, and failed roaming or drinking routes never start chase recovery. A wide body can finish a short final approach under continuous steering after its vanilla path consumes the last node, provided the entire swept body fits through loaded, dry terrain. For a large carnivore in pursuit, natural trunks and foliage do not count as obstacles on that final leg or in its path: the body knocks them down on contact.

Locomotion clip selection (walk/run versus idle) uses `LocomotionSignal`, a hysteresis over the same measured travel with a 1.0 blocks/second start and a three-tick, 0.25 blocks/second stop. It deliberately ignores GeckoLib's render-state movement flag, which counts collision creep, pack jostle and sub-walking drift as travel and keeps the walk clip alive after the creature has stopped.

Therizinosaurus is now 2× original size. Titanosaur is 6× original size (50% larger than the previous 4× version). Rex/Giga stay 3× and remaining species 2×. Mesh, animation translation tracks and collision dimensions are imported together. Existing entities receive the new speed baseline without healing or rerolling levels.
