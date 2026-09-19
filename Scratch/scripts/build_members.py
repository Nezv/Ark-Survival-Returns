"""Experimental mesh-to-cuboid abstraction; writes exclusively under Scratch.

Run: python Scratch/scripts/build_members.py --species Unicorn
Source geometry/animations remain in Creatures. No extraction or training needed.
"""
import argparse, copy, hashlib, json, re, shutil, sys, warnings
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation as R

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from ark_geometry import Skeleton
from extract_dinosaurs import SPECIES, resource_names
from build_dinosaurs import geometry, blockbench, native_cubes, write_json, PALETTES
from preview_dinosaurs import previews, decode_geometry, render, CORNERS


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def projection(geo):
    return [{k:v for k,v in b.items() if k != 'cubes'} for b in geo['minecraft:geometry'][0]['bones']]


def groups(s):
    """Anatomical hints only merge micro features; retain all load-bearing joints."""
    drivers = list(range(len(s.names)))
    for i, name in enumerate(s.names):
        n = name.lower()
        if any(t in n for t in ['lip','nose','uppereye','lowereye','tongue','transform']):
            p = s.parents[i]
            while p > 0 and not any(t in s.names[p].lower() for t in ['jaw','head','neck3']):
                p = s.parents[p]
            drivers[i] = p if p > 0 else i
    # Surface ownership from the source skin. Material boundaries do not split anatomy.
    result = {}
    for i in range(len(s.names)):
        ix = np.unique(np.concatenate([s.triangles[s.face_bone == i].ravel(), np.flatnonzero(s.dominant == i)]))
        if len(ix): result.setdefault(drivers[i], []).append(ix)
    return [(i, np.unique(np.concatenate(ix))) for i, ix in result.items()]


def frame(s, bone, points):
    """Choose a coarse frame once for a member, never per material or slice."""
    n = s.names[bone].lower()
    if 'feather' in n or 'wing' in n:
        # Feather cards describe a surface, not the bone's longitudinal volume.
        # Keep their broad faces horizontal in the spread-wing bind pose.
        _,_,vt=np.linalg.svd(points[:,[0,2]]-points[:,[0,2]].mean(0),full_matrices=False)
        axis=vt[0]
        yaw=np.degrees(np.arctan2(axis[0],axis[1]))
        return R.from_euler('y',round(yaw/15)*15,degrees=True)
    if any(t in n for t in ['back','body','spine','hip','head','main','jaw','eye']):
        return R.identity()
    children = [c for c in s.children[bone] if np.linalg.norm(s.wp[c]-s.wp[bone]) > .05]
    if children:
        # Prefer continuation of same named chain over side branches.
        stem = re.sub(r'[\d_]+', '', n)
        child = min(children, key=lambda c: (re.sub(r'[\d_]+','',s.names[c].lower()) != stem, -np.linalg.norm(s.wp[c]-s.wp[bone])))
        axis = s.wp[child]-s.wp[bone]
    else:
        axis = points.mean(0)-s.wp[bone]
    if np.linalg.norm(axis) < .1: return R.identity()
    axis /= np.linalg.norm(axis)
    # Zero roll, quantized yaw/pitch; sign of longitudinal axis is immaterial.
    yaw = np.degrees(np.arctan2(axis[0], axis[2]))
    pitch = -np.degrees(np.arcsin(np.clip(axis[1],-1,1)))
    return R.from_euler('YX', [round(yaw/15)*15, round(pitch/15)*15], degrees=True)


def fit(s):
    grid = .25
    out, members = [], []
    grouped=groups(s)
    budgets={i:1 for i,ix in grouped}
    lengths={i:float(np.ptp(s.source_vertices[ix],axis=0).max()) for i,ix in grouped}
    # Spend the detail budget on long anatomical regions, with a per-member cap.
    # Every slice shares its member's frame and is refitted to source points.
    for _ in range(max(0,150-len(grouped))):
        eligible=[i for i,ix in grouped if budgets[i]<8 and lengths[i]/(budgets[i]+1)>=.5 and 'eye' not in s.names[i].lower()]
        if not eligible:break
        i=max(eligible,key=lambda i:lengths[i]/budgets[i])
        budgets[i]+=1
    for bone, indices in grouped:
        # Preserve a separate accessory (Unicorn horn) as a silhouette feature.
        horn = np.array(['horn' in s.material_names[int(m)].lower() for m in s.material[indices]])
        parts = [(indices[~horn],False),(indices[horn],True)] if horn.any() else [(indices,False)]
        for ix, accessory in parts:
            if len(ix)<3: continue
            pts = s.source_vertices[ix]
            name = s.names[bone]
            rot = frame(s,bone,pts)
            if accessory:
                _,_,vt=np.linalg.svd(pts-pts.mean(0),full_matrices=False)
                axis=vt[0];axis*=1 if axis[1]>=0 else -1
                rot=R.from_euler('YX',[round(np.degrees(np.arctan2(axis[0],axis[2]))/15)*15,round(-np.degrees(np.arcsin(axis[1]))/15)*15],degrees=True)
            center = s.wp[bone].copy()
            local = rot.inv().apply(pts-center)
            detail = any(t in name.lower() for t in ['eye','toe','finger','ear','feather','frill','beak']) or accessory
            floor = .25 if detail else .5
            lo, hi = np.quantile(local,[.01,.99],axis=0)
            # One solid per joint by default. Allow a second step for large tapered
            # head/feather/accessory regions only when it saves >25% excess volume.
            segments=[(lo,hi)]
            axis=int(np.argmax(hi-lo))
            bird_head=s.asset.lower().startswith('argent') and name.lower()=='c_head'
            if bird_head:axis=2
            mid=(lo[axis]+hi[axis])/2
            subsets=[local[local[:,axis]<=mid], local[local[:,axis]>mid]]
            if (all(len(p)>=8 for p in subsets) and any(t in name.lower() for t in ['head','feather','neck3'])) or accessory:
                if all(len(p)>=3 for p in subsets):
                    candidate=[]
                    for k,p in enumerate(subsets):
                        a,b=np.quantile(p,[.01,.99],axis=0)
                        a[axis]=lo[axis] if k==0 else mid
                        b[axis]=mid if k==0 else hi[axis]
                        candidate.append((a,b))
                    if accessory or bird_head or sum(np.prod(np.maximum(b-a,floor)) for a,b in candidate)<.75*np.prod(np.maximum(hi-lo,floor)):
                        segments=candidate
            count=budgets[bone]
            if count>1:
                if any(t in name.lower() for t in ['back','body','tentacle']):axis=2
                edges=np.linspace(lo[axis],hi[axis],count+1)
                fitted=[]
                for k in range(count):
                    points=local[(local[:,axis]>=edges[k])&(local[:,axis]<=edges[k+1])]
                    if len(points)<3:continue
                    a,b=np.quantile(points,[.01,.99],axis=0)
                    a[axis],b[axis]=edges[k],edges[k+1]
                    fitted.append((a,b))
                if fitted:segments=fitted
            for j,(a,b) in enumerate(segments):
                # Quantize dimensions in anatomical coordinates. Export coordinates
                # can be fractional because the original bind axes are unchanged.
                a=np.floor((a-.035)/grid)*grid
                b=np.ceil((b+.035)/grid)*grid
                size=np.maximum(b-a,floor)
                offset=(a+b)/2
                world_center=center+rot.apply(offset)
                palette=4 if accessory or 'beak' in name.lower() or 'hoof' in name.lower() else 5 if 'eye' in name.lower() else 2 if any(t in name.lower() for t in ['feather','tail','frill']) else 3 if 'jaw' in name.lower() else 0
                if bird_head and j==1:palette=4
                out.append(dict(bone=bone,name=f'{name} / solid {j+1}'+(' / horn' if accessory else ''),origin=world_center-size/2,size=size,pivot=world_center,rotation=rot.as_euler('xyz',degrees=True),palette=palette))
            members.append(dict(bone=name,vertices=len(ix),cubes=len(segments),accessory=accessory))
    # Preserve readable eyes after replacing rounded heads with larger boxes.
    # Raycast laterally through ancestor boxes; seat a thin eye tile on the skin.
    for c in out:
        if 'eye' not in c['name'].lower():continue
        parent=s.parents[c['bone']]; ancestors=[]
        while parent>0:
            ancestors.append(parent);parent=s.parents[parent]
        sign=1 if c['pivot'][0]>=0 else -1
        start=c['pivot'].copy();direction=np.array([sign,0.,0.]);reach=0.
        for b in out:
            if 'eye' in b['name'].lower():continue
            inv=R.from_euler('xyz',b['rotation'],degrees=True).inv()
            o=inv.apply(start-b['pivot']);d=inv.apply(direction)
            low=b['origin']-b['pivot'];high=low+b['size'];near=-np.inf;far=np.inf
            for k in range(3):
                if abs(d[k])<1e-8:
                    if o[k]<low[k] or o[k]>high[k]:far=-np.inf
                else:
                    a,z=sorted([(low[k]-o[k])/d[k],(high[k]-o[k])/d[k]])
                    near=max(near,a);far=min(far,z)
            if far>=max(near,0):reach=max(reach,far)
        c['size']=np.array([.25,.5,.5]);c['rotation']=np.zeros(3)
        c['pivot']=start+direction*(reach+.04)
        c['origin']=c['pivot']-c['size']/2
    return native_cubes(s,out),members


def build(label):
    src=ROOT/'Creatures'/label; dest=ROOT/'Scratch'/label
    identifier,bbname=resource_names(label)
    paths=[src/f'geo/{identifier}.geo.json',src/bbname,src/f'animations/{identifier}.animation.json',src/'source/skeleton.json']
    before={str(p.relative_to(ROOT)):sha(p) for p in paths}
    s=Skeleton(SPECIES[label],src/'source')
    oldgeo=json.loads(paths[0].read_text());oldbb=json.loads(paths[1].read_text())
    cubes,members=fit(s)
    newgeo=geometry(s,cubes,identifier)
    assert projection(oldgeo)==projection(newgeo),'Skeleton metadata changed'
    # Preserve description/metadata, replace only cubes.
    finalgeo=copy.deepcopy(oldgeo)
    for a,b in zip(finalgeo['minecraft:geometry'][0]['bones'],newgeo['minecraft:geometry'][0]['bones']):
        a.pop('cubes',None)
        if b.get('cubes'):a['cubes']=b['cubes']
    write_json(dest/f'geo/{identifier}.geo.json',finalgeo,True)
    texture=src/f'textures/entity/{identifier}.png'
    (dest/'textures/entity').mkdir(parents=True,exist_ok=True)
    shutil.copy2(texture,dest/f'textures/entity/{identifier}.png')
    (dest/'animations').mkdir(exist_ok=True)
    shutil.copy2(paths[2],dest/f'animations/{identifier}.animation.json')
    generated=blockbench(s,cubes,{},label,texture)
    assert generated['groups']==oldbb['groups'],'Blockbench skeleton changed'
    bb=copy.deepcopy(oldbb);bb['elements']=generated['elements'];bb['outliner']=generated['outliner']
    write_json(dest/bbname,bb,True)
    report=dict(species=label,bones=len(s.names),cubes=len(cubes),previous_cubes=len(oldbb['elements']),animations=len(oldbb['animations']),source_hashes={},original_resource_hashes=before,members=members,method='Anatomical-frame rigid cuboid fitting; preserved joints; merged facial details',limitations=['Rigid skin approximation; facial micro motions intentionally have no separate visible cubes','Original animation preservation does not prove absence of visual gaps','No runtime Blockbench/Minecraft test'])
    write_json(dest/'build_report.json',report,True)
    # Reuse full source-frame validation while resolving the saved inputs read-only.
    import validate_dinosaurs as validator
    validator.CREATURES=ROOT/'Scratch'
    validator.Skeleton=lambda asset,source: Skeleton(asset,ROOT/'Creatures'/source.parent.name/'source')
    validator.validate(label)
    assert sha(paths[2])==sha(dest/f'animations/{identifier}.animation.json')
    assert bb['animations']==oldbb['animations']
    assert before=={str(p.relative_to(ROOT)):sha(p) for p in paths}
    animations=json.loads(paths[2].read_text())['animations']
    previews(dest,s,cubes,animations,label)
    decoded=decode_geometry(finalgeo)
    render(decoded,{},0,PALETTES[label],label,'Top view',camera=R.from_euler('z',90,degrees=True)).save(dest/'previews/top.png')
    (dest/'README.md').write_text(f'# {label} experimental cuboid model\n\nOpen {bbname} in Blockbench with GeckoLib. Textures and original animations are embedded.\n\n{len(oldbb["elements"])} -> {len(cubes)} cubes; {len(s.names)} original bones.\n\nSource: ../../Creatures/{label}/source (read-only). Rebuild from repository root: `python Scratch/scripts/build_members.py --species {label}`.\n\nSee ../METHOD.md for procedure, evidence and limitations.\n')
    print(label,len(oldbb['elements']),'->',len(cubes),flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--species',nargs='+',default=['Unicorn','Titanoboa','Argentavis','Tusoteuthis'])
    args=p.parse_args()
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore',message='Gimbal lock detected')
        for label in args.species:build(label)
