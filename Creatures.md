# Creatures

Distribution of the 41 creature projects under [`Creatures/`](Creatures/), grouped by type and spawn group configuration. **All 41 are registered by the mod** and appear naturally in the Overworld; the 22 collection projects are no longer source-only. See [collection ecosystem](Ark/docs/collection-ecosystem.md) for the realms and habitat rules.

Spawn values come from [`Species.java`](Ark/src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java); portfolio status from the [collection overview](Creatures/Collection/README.md) and the [mod README](README.md).

- **Herbivore and Carnivore are land-exclusive.** Flying, Aquatic, Cold and Swamp are habitat types; a cold or swamp creature keeps its own routine instead of the generic land one.
- **Group config** is `size / weight`: individuals per group (`min–max`, `1` = solitary) / base selection weight.
- Runtime spawning targets **3 groups within 96 blocks** of each player (cap 24 Ark creatures, at most 2 new groups per dimension check).

| Type | Total | Spawns | Source-only |
| --- | ---: | ---: | ---: |
| Herbivore | 9 | 9 | 0 |
| Carnivore | 11 | 11 | 0 |
| Flying | 5 | 5 | 0 |
| Aquatic | 6 | 6 | 0 |
| Cold | 6 | 6 | 0 |
| Swamp | 4 | 4 | 0 |
| **Total** | **41** | **41** | **0** |

<table>
<thead>
<tr><th align="left">Creature</th><th align="left">Type</th><th align="left">Group config</th><th align="left">Realm</th></tr>
</thead>
<tbody>
<tr><td>Ankylosaurus</td><td>Herbivore</td><td>2–4 / 10</td><td>Land</td></tr>
<tr><td>Brontosaurus</td><td>Herbivore</td><td>2–4 / 10</td><td>Land</td></tr>
<tr><td>Lystrosaurus</td><td>Herbivore</td><td>4–6 / 14</td><td>Land</td></tr>
<tr><td>Paraceratherium</td><td>Herbivore</td><td>2–4 / 6</td><td>Land</td></tr>
<tr><td>Parasaur</td><td>Herbivore</td><td>4–6 / 14</td><td>Land</td></tr>
<tr><td>Pegomastax</td><td>Herbivore</td><td>4–6 / 10</td><td>Land</td></tr>
<tr><td>Therizinosaurus</td><td>Herbivore</td><td>2–4 / 12</td><td>Land</td></tr>
<tr><td>Titanosaur</td><td>Herbivore</td><td>1 / 6</td><td>Land</td></tr>
<tr><td>Triceratops</td><td>Herbivore</td><td>2–4 / 12</td><td>Land</td></tr>
<tr><td colspan="4"><hr></td></tr>
<tr><td>Acrocanthosaurus</td><td>Carnivore</td><td>1 / 6</td><td>Land</td></tr>
<tr><td>Allosaurus</td><td>Carnivore</td><td>4–6 / 9</td><td>Land</td></tr>
<tr><td>Carnotaurus</td><td>Carnivore</td><td>1 / 10</td><td>Land</td></tr>
<tr><td>Ceratosaurus</td><td>Carnivore</td><td>1 / 8</td><td>Land</td></tr>
<tr><td>Dilophosaur</td><td>Carnivore</td><td>4–6 / 12</td><td>Land</td></tr>
<tr><td>Giganotosaurus</td><td>Carnivore</td><td>1 / 8</td><td>Land</td></tr>
<tr><td>Ravager</td><td>Carnivore</td><td>4–6 / 5</td><td>Land</td></tr>
<tr><td>Spinosaurus</td><td>Carnivore</td><td>1 / 8</td><td>Land</td></tr>
<tr><td>Terrorbird</td><td>Carnivore</td><td>4–6 / 8</td><td>Land</td></tr>
<tr><td>Tyrannosaurus</td><td>Carnivore</td><td>1 / 12</td><td>Land</td></tr>
<tr><td>Velociraptor</td><td>Carnivore</td><td>4–6 / 14</td><td>Land</td></tr>
<tr><td colspan="4"><hr></td></tr>
<tr><td>Archaeopteryx</td><td>Flying</td><td>3–4 / 8</td><td>Air</td></tr>
<tr><td>Argentavis</td><td>Flying</td><td>3–4 / 4</td><td>Air</td></tr>
<tr><td>Dragon</td><td>Flying</td><td>1 / 1</td><td>Air</td></tr>
<tr><td>Pteranodon</td><td>Flying</td><td>3–4 / 18</td><td>Air</td></tr>
<tr><td>Quetzal</td><td>Flying</td><td>1 / 2</td><td>Air</td></tr>
<tr><td colspan="4"><hr></td></tr>
<tr><td>Cnidaria</td><td>Aquatic</td><td>1 / 8</td><td>Water</td></tr>
<tr><td>Liopleurodon</td><td>Aquatic</td><td>1 / 5</td><td>Water</td></tr>
<tr><td>Megalodon</td><td>Aquatic</td><td>1 / 7</td><td>Water</td></tr>
<tr><td>Mosasaurus</td><td>Aquatic</td><td>1 / 3</td><td>Water</td></tr>
<tr><td>Plesiosaur</td><td>Aquatic</td><td>1 / 8</td><td>Water</td></tr>
<tr><td>Tusoteuthis</td><td>Aquatic</td><td>1 / 3</td><td>Water</td></tr>
<tr><td colspan="4"><hr></td></tr>
<tr><td>Direwolf</td><td>Cold</td><td>4–6 / 9</td><td>Land</td></tr>
<tr><td>Mammoth</td><td>Cold</td><td>2–4 / 7</td><td>Land</td></tr>
<tr><td>Megalocerus</td><td>Cold</td><td>4–6 / 10</td><td>Land</td></tr>
<tr><td>Megapithecus</td><td>Cold</td><td>1 / 1</td><td>Land</td></tr>
<tr><td>Sabertooth</td><td>Cold</td><td>1–2 / 6</td><td>Land</td></tr>
<tr><td>Unicorn</td><td>Cold</td><td>1 / 3</td><td>Land</td></tr>
<tr><td colspan="4"><hr></td></tr>
<tr><td>Deinosuchus</td><td>Swamp</td><td>1 / 4</td><td>Semi-aquatic</td></tr>
<tr><td>Kaprosuchus</td><td>Swamp</td><td>1 / 7</td><td>Semi-aquatic</td></tr>
<tr><td>Sarco</td><td>Swamp</td><td>2–3 / 8</td><td>Semi-aquatic</td></tr>
<tr><td>Titanoboa</td><td>Swamp</td><td>1 / 6</td><td>Semi-aquatic</td></tr>
</tbody>
</table>

## Notes

- Every project is a completed, validated runtime model. The `Creatures/` folders remain the editable source.
- Special runtime spawns: Brontosaurus herds arrive with a linked Rex, Argentavis is capped at two nearby colonies, and flying species spawn as complete nest colonies.
- Water species are solitary roamer residents of one saved pool; Sarco is the only water-side species with a real 2–3 bask group. See the [collection ecosystem](Ark/docs/collection-ecosystem.md).
- Terrorbird is classified as a carnivore, matching its ARK behavior; Dragon is classified as a flying species and roosts alone.
- Unicorn and Megapithecus are rare solitary encounters rather than ordinary herd wildlife.
