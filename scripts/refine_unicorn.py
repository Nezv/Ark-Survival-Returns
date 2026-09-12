"""Replace only the Unicorn cuboids with a coarse Minecraft-style block fit.

The existing Unicorn bone hierarchy and converted animation tracks are treated
as immutable inputs. This script rewrites only geometry cube arrays and the
Blockbench cube elements/outliner leaves; it never regenerates animation JSON.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path

from ark_geometry import Skeleton
from build_dinosaurs import CREATURES, PALETTES, NATIVE_ASSETS, blockbench, fitted_cubes, write_json
from extract_dinosaurs import SPECIES, resource_names


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def outline_skeleton(node):
    """Project an outliner onto its bone hierarchy, ignoring cube UUID leaves."""
    if isinstance(node, str):
        return '#cube'
    return (node.get('uuid'), tuple(outline_skeleton(child) for child in node.get('children', []) if isinstance(child, dict)))


def refine():
    label = 'Unicorn'
    identifier, bb_name = resource_names(label)
    out = CREATURES / label
    geo_path = out / f'geo/{identifier}.geo.json'
    anim_path = out / f'animations/{identifier}.animation.json'
    bb_path = out / bb_name
    report_path = out / 'build_report.json'

    old_geo = json.loads(geo_path.read_text(encoding='utf-8'))
    old_anim_bytes = anim_path.read_bytes()
    old_anim = json.loads(old_anim_bytes)
    old_bb = json.loads(bb_path.read_text(encoding='utf-8'))
    old_bone_projection = copy.deepcopy(old_geo['minecraft:geometry'][0]['bones'])
    for bone in old_bone_projection:
        bone.pop('cubes', None)
    old_anim_digest = digest(old_anim)
    old_bb_anim_digest = digest(old_bb['animations'])
    old_bb_groups_digest = digest(old_bb['groups'])
    old_skeleton_digest = digest(old_bone_projection)
    old_source_skeleton = (out / 'source/skeleton.json').read_bytes()

    # Skeleton() must see Equus in the native set so the source horn is loaded
    # and native bind rotations/pivots remain available. Fitting itself is then
    # deliberately switched to the coarse principal-axis path.
    skeleton = Skeleton(SPECIES[label], out / 'source')
    NATIVE_ASSETS.discard(SPECIES[label])
    try:
        cubes = fitted_cubes(skeleton)
    finally:
        NATIVE_ASSETS.add(SPECIES[label])
    assert 200 <= len(cubes) <= 500, len(cubes)

    from build_dinosaurs import geometry
    fitted_geo = geometry(skeleton, cubes, identifier)
    fitted_bones = copy.deepcopy(fitted_geo['minecraft:geometry'][0]['bones'])
    for bone in fitted_bones:
        bone.pop('cubes', None)
    assert digest(fitted_bones) == old_skeleton_digest, 'Unicorn skeleton changed during mesh refinement'

    # Preserve every existing geometry field and replace only each bone's cubes.
    new_geo = copy.deepcopy(old_geo)
    for old_bone, fitted_bone in zip(new_geo['minecraft:geometry'][0]['bones'], fitted_geo['minecraft:geometry'][0]['bones']):
        if fitted_bone.get('cubes'):
            old_bone['cubes'] = fitted_bone['cubes']
        else:
            old_bone.pop('cubes', None)
    write_json(geo_path, new_geo, pretty=True)

    # Generate the matching cube element list and refreshed cube leaves, then
    # restore the original embedded animations and metadata byte-for-byte in
    # meaning. Bone group UUIDs are deterministic and must remain unchanged.
    generated_bb = blockbench(skeleton, cubes, {}, label, out / f'textures/entity/{identifier}.png')
    assert digest(generated_bb['groups']) == old_bb_groups_digest, 'Unicorn Blockbench bone groups changed'
    assert [outline_skeleton(x) for x in generated_bb['outliner']] == [outline_skeleton(x) for x in old_bb['outliner']], \
        'Unicorn Blockbench skeleton outliner changed'
    new_bb = copy.deepcopy(old_bb)
    new_bb['elements'] = generated_bb['elements']
    new_bb['outliner'] = generated_bb['outliner']
    new_bb['animations'] = old_bb['animations']
    assert digest(new_bb['animations']) == old_bb_anim_digest
    bb_path.write_text(json.dumps(new_bb, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

    report = json.loads(report_path.read_text(encoding='utf-8'))
    report['cubes'] = len(cubes)
    report['model_method'] = 'Coarse principal-axis Minecraft cuboids fitted per original bone/material; Unicorn horn remains on its native c_neck3 socket bone.'
    report.setdefault('refinement', {})['mesh_only'] = True
    report['refinement']['previous_cubes'] = len(old_bb['elements'])
    report['refinement']['new_cubes'] = len(cubes)
    report['refinement']['skeleton_sha256'] = old_skeleton_digest
    report['refinement']['animation_sha256'] = old_anim_digest
    write_json(report_path, report, pretty=True)
    (out / 'README.md').write_text((out / 'README.md').read_text(encoding='utf-8').replace('6172 fitted cubes', f'{len(cubes)} fitted cubes'), encoding='utf-8')

    # Refresh previews from the new geometry while retaining the existing
    # animation resource itself. This is an image/GIF output only.
    from ark_animation import animation_tracks, convert_clip, read_clips
    animations = {}
    for clip in read_clips(skeleton):
        angles, offsets, _ = convert_clip(skeleton, clip)
        animations[clip['name']] = animation_tracks(skeleton, clip, angles, offsets)[0]
    from preview_dinosaurs import previews
    previews(out, skeleton, cubes, animations, label)

    assert anim_path.read_bytes() == old_anim_bytes, 'Animation JSON was modified'
    assert (out / 'source/skeleton.json').read_bytes() == old_source_skeleton, 'Source skeleton was modified'
    check_geo = json.loads(geo_path.read_text(encoding='utf-8'))
    check_bones = copy.deepcopy(check_geo['minecraft:geometry'][0]['bones'])
    for bone in check_bones:
        bone.pop('cubes', None)
    assert digest(check_bones) == old_skeleton_digest
    print(f'Unicorn mesh refined: {len(old_bb["elements"])} -> {len(cubes)} Minecraft-style cubes; skeleton and animations unchanged.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    refine()
