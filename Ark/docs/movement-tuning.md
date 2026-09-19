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

Animation timing uses measured horizontal travel, body height and each imported clip's duration. Large bodies therefore take slower, longer strides at the same ground speed. Increase `strideScale` to slow the visual cadence without changing movement. This is a tunable stride approximation; visual foot placement still needs playtesting.

Therizinosaurus is now 2× original size. Titanosaur is 6× original size (50% larger than the previous 4× version). Rex/Giga stay 3× and remaining species 2×. Mesh, animation translation tracks and collision dimensions are imported together. Existing entities receive the new speed baseline without healing or rerolling levels.
