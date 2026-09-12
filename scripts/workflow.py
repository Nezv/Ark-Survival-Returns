"""One command: extract installed ARK assets, fit models, bind clips, validate."""
import argparse
from pathlib import Path
from extract_dinosaurs import ROOT,SPECIES,extract,CREATURES,WORKSHOP
from build_dinosaurs import build

def write_workflow_report():
    import hashlib,json,platform
    import numpy,scipy,PIL
    rows=[]
    for label in SPECIES:
        p=CREATURES/label/'validation.json'
        if p.exists():
            row=json.loads(p.read_text());row['species']=label;rows.append(row)
    report={'species':rows,'total_clips':sum(r['clips'] for r in rows),
        'total_bones':sum(r['bones'] for r in rows),'total_cubes':sum(r['cubes'] for r in rows),
        'total_source_frames_checked':sum(r['source_frames_checked'] for r in rows),
        'python':platform.python_version(),'packages':{'numpy':numpy.__version__,'scipy':scipy.__version__,'Pillow':PIL.__version__},
        'script_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'scripts').glob('*.py'))},
        'umodel_sha256':hashlib.sha256((ROOT/'tools/umodel.exe').read_bytes()).hexdigest()}
    (ROOT/'workflow_report.json').write_text(json.dumps(report,indent=2)+'\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--content',type=Path,default=Path('C:/Program Files (x86)/Steam/steamapps/common/ARK/ShooterGame/Content'))
    parser.add_argument('--umodel',type=Path,default=ROOT/'tools/umodel.exe')
    parser.add_argument('--workshop',type=Path,default=WORKSHOP)
    parser.add_argument('--species',nargs='+',choices=SPECIES,default=list(SPECIES))
    parser.add_argument('--reuse-exports',action='store_true',help='Build using preserved source files, without reading ARK')
    args=parser.parse_args()
    if not args.reuse_exports:
        extract(args.content,args.umodel.resolve(),args.species,args.workshop)
    for label in args.species:build(label,refresh=not args.reuse_exports)
    from validate_dinosaurs import validate
    for label in args.species:validate(label)
    if set(args.species)==set(SPECIES):
        from preview_dinosaurs import contact_sheet
        contact_sheet(ROOT,list(SPECIES))
    write_workflow_report()

if __name__=='__main__':main()
