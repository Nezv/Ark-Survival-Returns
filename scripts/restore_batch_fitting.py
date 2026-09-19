"""Restore the earlier cuboid fitter without regenerating skeletons or clips.

Run from the repository root: python scripts/restore_batch_fitting.py
An optional --species list limits the replacement to named collection members.
"""
import argparse
import copy
import hashlib
import json
import re
from pathlib import Path

from ark_geometry import Skeleton
from build_dinosaurs import CREATURES, fitted_cubes, fitting_method, geometry, blockbench, write_json
from extract_dinosaurs import SPECIES, resource_names
from model_catalog import MODELS
from refine_unicorn import digest, outline_skeleton
from preview_dinosaurs import previews
from validate_dinosaurs import validate


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def bones_only(geo):
    return [{k:v for k,v in b.items() if k!='cubes'} for b in geo['minecraft:geometry'][0]['bones']]


def protected_hashes(out):
    # Include every original source export, standalone clip and palette texture.
    return {str(p.relative_to(out)):hashlib.sha256(p.read_bytes()).hexdigest()
            for folder in ['source','animations','textures']
            for p in sorted((out/folder).rglob('*')) if p.is_file()}


def prepare(label):
    out=CREATURES/label
    identifier,bbname=resource_names(label)
    oldgeo=read(out/f'geo/{identifier}.geo.json')
    oldbb=read(out/bbname)
    s=Skeleton(SPECIES[label],out/'source')
    cubes=fitted_cubes(s)
    fitted=geometry(s,cubes,identifier)
    assert bones_only(oldgeo)==bones_only(fitted),f'{label}: skeleton mismatch'
    bb=blockbench(s,cubes,{},label,out/f'textures/entity/{identifier}.png')
    assert bb['groups']==oldbb['groups'],f'{label}: Blockbench group mismatch'
    assert [outline_skeleton(x) for x in bb['outliner']]==[outline_skeleton(x) for x in oldbb['outliner']],f'{label}: hierarchy mismatch'
    newgeo=copy.deepcopy(oldgeo)
    for a,b in zip(newgeo['minecraft:geometry'][0]['bones'],fitted['minecraft:geometry'][0]['bones']):
        a.pop('cubes',None)
        if 'cubes' in b:a['cubes']=b['cubes']
    newbb=copy.deepcopy(oldbb)
    newbb['elements']=bb['elements'];newbb['outliner']=bb['outliner']
    return dict(label=label,out=out,identifier=identifier,bbname=bbname,skeleton=s,
                cubes=cubes,geo=newgeo,bb=newbb,oldgeo=oldgeo,oldbb=oldbb,
                protected=protected_hashes(out))


def replace(item):
    label=item['label'];out=item['out'];identifier=item['identifier']
    report=read(out/'build_report.json')
    oldcount=len(item['oldbb']['elements']);count=len(item['cubes'])
    report['cubes']=count
    report['model_method']=fitting_method(SPECIES[label])
    report['fitting_restoration']={
        'mesh_only':True,'previous_cubes':oldcount,'new_cubes':count,
        'skeleton_sha256':digest(bones_only(item['oldgeo'])),
        'embedded_animations_sha256':digest(item['oldbb']['animations']),
        'protected_file_count':len(item['protected']),
        'protected_files_unchanged':True,
    }
    write_json(out/f'geo/{identifier}.geo.json',item['geo'],True)
    write_json(out/item['bbname'],item['bb'],True)
    write_json(out/'build_report.json',report,True)
    readme=out/'README.md'
    readme.write_text(re.sub(r'\d[\d,]* fitted cubes',f'{count} fitted cubes',readme.read_text(encoding='utf-8')),encoding='utf-8')
    result=validate(label)
    animation=read(out/f'animations/{identifier}.animation.json')['animations']
    previews(out,item['skeleton'],item['cubes'],animation,label)
    assert protected_hashes(out)==item['protected'],f'{label}: protected files changed'
    savedbb=read(out/item['bbname'])
    assert savedbb['animations']==item['oldbb']['animations']
    assert savedbb['groups']==item['oldbb']['groups']
    assert bones_only(read(out/f'geo/{identifier}.geo.json'))==bones_only(item['oldgeo'])
    print(f'{label}: {oldcount} -> {count} cubes; skeleton, textures, source files and animations preserved',flush=True)
    return {'species':label,'before':oldcount,'after':count,'clips':result['clips'],
            'source_frames':result['source_frames_checked'],'preserved':True}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--species',nargs='+',choices=list(MODELS),default=list(MODELS))
    args=parser.parse_args()
    # Verify every selected rig is compatible before replacing the first file.
    prepared=[prepare(label) for label in args.species]
    print(f'Preflight passed for {len(prepared)} models; all original rig metadata matches.',flush=True)
    rows=[replace(item) for item in prepared]
    write_json(CREATURES/'Collection/fitting_restoration.json',{'method':'principal-axis cuboid slices','species':rows},True)
    if set(args.species)==set(MODELS):
        from model_batch import report
        report()


if __name__=='__main__':main()
