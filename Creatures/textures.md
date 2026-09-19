# Procedural creature textures

Each creature receives the five runtime variants `Ivory`, `Darken`, `Emerald`, `Midnight`, and `Burgundy`.
The anatomy group controls atlas density; the shared painter derives the detailed body regions directly from each rig.

| Anatomy | Creatures | UV density | Procedural treatment |
|---|---|---:|---|
| Bipedal theropod | Acrochantosaur, Allosaurus, Carnotaurus, Ceratosaurus, Dilophosaur, Giganotosaur, Spinosaurus, Terrorbird, Therezinosaur, Tyranosaur, Velociraptor | 2.00 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Armored or horned quadruped | Ankylosaurus, Lystrosaurus, Parasaur, Triceratops | 1.70 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Sauropod | Brontosaur, Titanosaur | 0.70 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Winged vertebrate | Archaeopteryx, Argentavis, Piterodon, Quetzal | 1.35 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Winged dragon | Dragon | 0.85 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Crocodyliform | Deinosuchus, Kaprosuchus, Sarco | 1.35 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Serpentine | Titanoboa | 1.70 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Marine flipper | Liopleurodon, Mosasaurus, Plesiosaur | 1.45 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Shark | Megalodon | 1.35 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Cephalopod | Tusoteuthis | 1.45 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Cnidarian | Cnidaria | 1.10 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Quadrupedal mammal | Direwolf, Mammoth, Megalocerus, Paraceratherium, Ravager, Sabertooth, Unicorn | 1.60 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Primate | Megapithecus | 1.15 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |
| Small biped | Pegomastax | 1.80 px/unit | Five ventral-to-dorsal layers; five seeded 3×3 shades per layer; mirrored paired faces; separate eyes, tongue, claws, and nails |

## Generate and ship

```powershell
python Creatures/Textures.py --ship-runtime
```

Use `--dry-run` to validate every atlas without writing files. Use `--creatures NAME ...` or `--anatomies NAME ...` for a subset.
