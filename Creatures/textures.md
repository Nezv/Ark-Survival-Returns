# Procedural creature textures

Each creature receives the five runtime variants `Ivory`, `Darken`, `Emerald`, `Midnight`, and `Burgundy`.
The anatomy group controls atlas density; the shared painter derives the detailed body regions directly from each rig.

| Anatomy | Creatures | UV density | Procedural treatment |
|---|---|---:|---|
| Sauropod | Brontosaur, Titanosaur | 0.70 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Winged vertebrate | Archaeopteryx, Argentavis, Piterodon, Quetzal | 1.35 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Winged dragon | Dragon | 0.85 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Crocodyliform | Deinosuchus, Kaprosuchus, Sarco | 1.35 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Serpentine | Titanoboa | 1.70 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Marine flipper | Liopleurodon, Mosasaurus, Plesiosaur | 1.45 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Shark | Megalodon | 1.35 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Cephalopod | Tusoteuthis | 1.45 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Cnidarian | Cnidaria | 1.10 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Quadrupedal mammal | Megalocerus, Paraceratherium, Ravager, Unicorn | 1.60 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |

## Hand-made skins

These species are designed box by box on their ARK rigs, with skins painted texel by texel from where
each texel lies on the body (1 texel per runtime unit); this painter skips them:

- Mammoth: `scripts/handmade_mammoth.py`
- Megapithecus: `scripts/handmade_megapithecus.py`
- Sabertooth: `scripts/handmade_sabertooth.py`
- Ankylosaurus: `scripts/handmade_ankylosaurus.py`
- Tyranosaur: `scripts/handmade_tyrannosaurus.py`
- Triceratops: `scripts/handmade_triceratops.py`
- Direwolf: `scripts/handmade_direwolf.py`
- Parasaur: `scripts/handmade_parasaur.py`
- Velociraptor: `scripts/handmade_velociraptor.py`
- Spinosaurus: `scripts/handmade_spinosaurus.py`
- Allosaurus: `scripts/handmade_allosaurus.py`
- Carnotaurus: `scripts/handmade_carnotaurus.py`
- Giganotosaur: `scripts/handmade_giganotosaurus.py`
- Ceratosaurus: `scripts/handmade_ceratosaurus.py`
- Acrochantosaur: `scripts/handmade_acrocanthosaurus.py`
- Dilophosaur: `scripts/handmade_dilophosaur.py`
- Terrorbird: `scripts/handmade_terrorbird.py`
- Therezinosaur: `scripts/handmade_therizinosaurus.py`
- Pegomastax: `scripts/handmade_pegomastax.py`
- Lystrosaurus: `scripts/handmade_lystrosaurus.py`

Rebuild them with `python scripts/handmade_models.py`, then import with
`python Ark/tools/import_creatures.py --species mammoth megapithecus sabertooth ankylosaurus tyrannosaurus triceratops direwolf parasaur velociraptor spinosaurus allosaurus carnotaurus giganotosaurus ceratosaurus acrocanthosaurus dilophosaur terrorbird therizinosaurus pegomastax lystrosaurus`.

## Generate and ship

```powershell
python Creatures/Textures.py --ship-runtime
```

Use `--dry-run` to validate every atlas without writing files. Use `--creatures NAME ...` or `--anatomies NAME ...` for a subset.
