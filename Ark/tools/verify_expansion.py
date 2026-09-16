"""Check behavior clip contracts and scaled native-axis geometry in runtime assets.

Covers the September 12 expansion and the ice/flying/aquatic/swamp collection, including the
per-realm water and flight clip sets.
"""
import copy
import json
import sys
from pathlib import Path
import numpy as np
from expansion_catalog import EXPANSION
from collection_catalog import COLLECTION, import_clips
from import_creatures import ROOT, ASSETS
sys.path.insert(0,str(ROOT.parent/'scripts'))
from preview_dinosaurs import decode_geometry, pose

POSE_CLIPS = ('idle', 'walk', 'run', 'attack')
SWIM_CLIPS = ('idle', 'walk', 'run')

def corners(decoded,clip,time):
    names,pivots,parents,cubes=decoded
    positions,matrices=pose(names,pivots,parents,clip,time,decoded.rotations)
    return np.concatenate([positions[i]+(vertices-pivots[i])@matrices[i].T for i,vertices,_ in cubes])

def contract(entry, collection):
    """Clip names the importer must have copied for this entry."""
    if collection:
        return set(import_clips(entry))
    return {entry[k] for k in ('idle','walk','attack','run','food','warning')} | {'Ark-Sleep'}

def checked_clips(entry, collection):
    """Role clips whose converted poses are compared against the source rig."""
    names = [entry[k] for k in POSE_CLIPS]
    if collection and entry.get('swim'):
        names += [entry['swim'][k] for k in SWIM_CLIPS]
        names += [entry['fly'][k] for k in ('move', 'hover', 'attack') if (entry.get('fly') or {}).get(k)]
    return [name for name in names if name]

def main():
    report=json.loads((ROOT/'docs/creature-import.json').read_text())
    checks=[]; poses=0
    for entry,collection in [(e,False) for e in EXPANSION]+[(e,True) for e in COLLECTION]:
        identifier=entry['id'];source=ROOT.parent/'Creatures'/entry['folder']
        source_geo=json.loads(next((source/'geo').glob('*.json')).read_text())
        original=decode_geometry(source_geo)
        runtime=decode_geometry(json.loads((ASSETS/f'geckolib/models/entity/{identifier}.geo.json').read_text()))
        source_clips=json.loads(next((source/'animations').glob('*.json')).read_text())['animations']
        runtime_clips=json.loads((ASSETS/f'geckolib/animations/entity/{identifier}.animation.json').read_text())['animations']
        imported=next(row for row in report if row['id']==identifier)
        assert set(runtime_clips)==contract(entry,collection),(identifier,set(runtime_clips)^contract(entry,collection))
        assert original[0]==runtime[0] and original[2]==runtime[2],identifier
        factor=imported['scale'];desc=source_geo['minecraft:geometry'][0]['description']
        floor=desc['visible_bounds_offset'][1]-(desc['visible_bounds_height']-2)/4
        shift=np.array([0,-floor*16*factor,0])
        max_error=0
        for name in checked_clips(entry,collection):
            clip=copy.deepcopy(source_clips[name])
            for i,p in enumerate(original[2]):
                if p<0:
                    for vector in clip.get('bones',{}).get(original[0][i],{}).get('position',{}).values():
                        if isinstance(vector,list):vector[0]=vector[2]=0
            for t in (0,clip['animation_length']*.5,clip['animation_length']*.99):
                expected=corners(original,clip,t)*factor+shift
                actual=corners(runtime,runtime_clips[name],t)
                error=float(np.max(np.abs(expected-actual)))
                assert error<.0003,(identifier,name,t,error)
                max_error=max(max_error,error);poses+=1
        checks.append({'id':identifier,'realm':entry.get('realm','LAND'),'family':entry['family'],
                       'timid':entry['timid'],'sampled_poses':len(checked_clips(entry,collection))*3,
                       'max_cube_coordinate_error':max_error})
    (ROOT/'docs/creature-expansion-validation.json').write_text(json.dumps(checks,indent=2)+'\n')
    print(f'PASS: {len(checks)} new runtime models, {poses} sampled poses, exact clip contracts and original hierarchies; scale/translation preserved.')

if __name__=='__main__':main()
