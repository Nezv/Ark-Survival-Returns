"""Build the source-only ice, flying, aquatic and swamp creature collection."""
import argparse
import json
import re
from pathlib import Path
from extract_dinosaurs import ROOT,CREATURES,WORKSHOP,SPECIES,extract
from model_catalog import MODELS

GROUPS={
    'ice-and-land':['Direwolf','Megalocerus','Megapithecus','Mammoth','Unicorn','Sabertooth','Paraceratherium','Terrorbird','Ravager'],
    'flying':['Quetzal','Archaeopteryx','Dragon'],
    'aquatic':['Tusoteuthis','Cnidaria','Mosasaurus','Megalodon','Plesiosaur','Liopleurodon'],
    'swamp':['Kaprosuchus','Deinosuchus','Sarco','Titanoboa'],
}

def report():
    from preview_dinosaurs import contact_sheet
    from workflow import write_workflow_report
    rows=[]
    for label in MODELS:
        validation=json.loads((CREATURES/label/'validation.json').read_text())
        build=json.loads((CREATURES/label/'build_report.json').read_text())
        rows.append({'species':label,'ark_asset':MODELS[label]['asset'],**validation,
                     'source_vertices':build['source_vertices'],'source_triangles':build['source_triangles']})
    output=CREATURES/'Collection';output.mkdir(exist_ok=True)
    for group,labels in GROUPS.items():contact_sheet(ROOT,labels,output/f'{group}.png')
    contact_sheet(ROOT,list(SPECIES))
    summary={'requested':22,'completed':len(rows),'source_only':True,'pending':[],
             'total_bones':sum(r['bones'] for r in rows),'total_cubes':sum(r['cubes'] for r in rows),
             'total_clips':sum(r['clips'] for r in rows),'total_source_frames_checked':sum(r['source_frames_checked'] for r in rows),
             'species':rows}
    (output/'model_batch_report.json').write_text(json.dumps(summary,indent=2)+'\n')
    readme=output/'README.md'
    if readme.exists():
        text=readme.read_text(encoding='utf-8')
        totals=(f"The completed projects contain {summary['total_bones']:,} original bones, "
                f"{summary['total_cubes']:,} fitted cuboids and {summary['total_clips']:,} imported clips. "
                f"All {summary['total_source_frames_checked']:,} source animation frames passed validation.")
        text=re.sub(r'The completed projects contain[^\n]+',totals,text)
        readme.write_text(text,encoding='utf-8')
    write_workflow_report()
    print(f"Source collection: {len(rows)} creatures, {summary['total_bones']} bones, {summary['total_cubes']} cubes, {summary['total_clips']} clips.")

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reuse-exports',action='store_true')
    parser.add_argument('--report-only',action='store_true',help='Refresh the collection summary/previews after successful builds.')
    parser.add_argument('--content',type=Path,default=Path('C:/Program Files (x86)/Steam/steamapps/common/ARK/ShooterGame/Content'))
    parser.add_argument('--workshop',type=Path,default=WORKSHOP)
    parser.add_argument('--umodel',type=Path,default=ROOT/'tools/umodel.exe')
    args=parser.parse_args()
    if not args.report_only:
        from build_dinosaurs import build
        from validate_dinosaurs import validate
        if not args.reuse_exports:extract(args.content,args.umodel,list(MODELS),args.workshop)
        for label in MODELS:
            build(label,refresh=not args.reuse_exports)
            validate(label)
    report()

if __name__=='__main__':main()
