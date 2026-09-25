# Asset pipeline scripts

`scripts/` holds the shared, repository-level creature conversion workflow.
Run these tools from the repository root so their relative paths resolve
consistently.

## Canonical workflow

| Script | Use |
| --- | --- |
| `workflow.py` | Orchestrates extraction, conversion, previews and validation. Use `--reuse-exports` for a repeatable offline rebuild. |
| `extract_dinosaurs.py` | Reads fresh data from the local ARK/Workshop installation. |
| `build_dinosaurs.py` | Converts preserved exported inputs into GeckoLib projects. |
| `validate_dinosaurs.py` | Validates hierarchy, clips, generated JSON and source-frame error bounds. |
| `preview_dinosaurs.py` | Produces bind-pose and animation previews from generated output. |

The normal commands are:

```powershell
python scripts/workflow.py --reuse-exports
python scripts/validate_dinosaurs.py
```

For a targeted rebuild, prefer the shared pipeline:

```powershell
python scripts/workflow.py --reuse-exports --species Tyranosaur
```

Per-creature rebuild wrappers are intentionally not maintained. Use
`workflow.py --reuse-exports --species <name>` so every targeted rebuild follows
the same extraction, build, validation and reporting path.

## Supporting tools

- `ark_geometry.py`, `ark_animation.py`, `mesh_detail.py`, and
  `creature_mesh_details.py` are reusable conversion primitives.
- `model_catalog.py`, `model_batch.py`, `build_skins.py`, and `skin_studio.py`
  are catalog or appearance utilities.
- `depth_preview.py`, `refine_unicorn.py`, and `restore_batch_fitting.py` are
  specialized repair/inspection tools.  Keep them until their last known
  outputs and use cases are explicitly retired; they are not part of every
  build.

## Adding or changing a script

1. Add a module-level description stating its input and output boundaries.
2. Keep reusable logic importable; place CLI parsing under an `if __name__ ==
   '__main__':` guard.
3. Never write into the installed Steam game directory.
4. Document an operator-facing command here or in the root README when the
   script becomes part of the supported workflow.
5. Validate affected creatures before importing runtime assets into `Ark/`.
