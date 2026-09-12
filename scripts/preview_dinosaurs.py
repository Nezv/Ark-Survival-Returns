"""Offline software previews rendered from the actual exported GeckoLib JSON."""
import json
import math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.spatial.transform import Rotation as R

CORNERS=np.array([[0,0,0],[1,0,0],[1,1,0],[0,1,0],[0,0,1],[1,0,1],[1,1,1],[0,1,1]])
FACES=[[0,3,2,1],[4,5,6,7],[0,1,5,4],[3,7,6,2],[0,4,7,3],[1,2,6,5]]

class DecodedGeometry(tuple):
    def __new__(cls,names,pivots,parents,cubes,rotations):
        obj=super().__new__(cls,(names,pivots,parents,cubes));obj.rotations=rotations;return obj

def font(size):
    try:return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',size)
    except OSError:return ImageFont.load_default()

def decode_geometry(geo):
    bones=geo['minecraft:geometry'][0]['bones'];names=[b['name'] for b in bones]
    pivots=np.array([b['pivot'] for b in bones])*[-1,1,1]
    parents=[names.index(b['parent']) if 'parent' in b else -1 for b in bones]
    cubes=[]
    for i,b in enumerate(bones):
        for c in b.get('cubes',[]):
            size=np.array(c['size']);origin=np.array(c['origin']);origin[0]=-origin[0]-size[0]
            pivot=np.array(c.get('pivot',b['pivot']))*[-1,1,1]
            rot=R.from_euler('xyz',np.array(c.get('rotation',[0,0,0]))*[-1,-1,1],degrees=True)
            corners=rot.apply(origin+CORNERS*size-pivot)+pivot
            color=int(c['uv']['north']['uv'][0]//8)
            cubes.append((i,corners,color))
    rotations=np.array([b.get('rotation',[0,0,0]) for b in bones])*[-1,-1,1]
    return DecodedGeometry(names,pivots,parents,cubes,rotations)

def sample(track,time,default):
    if track is None:return np.array(default,dtype=float)
    times=np.array([float(t) for t in track]);values=np.array(list(track.values()),dtype=float)
    return np.array([np.interp(time,times,values[:,j]) for j in range(3)])

def pose(names,pivots,parents,clip,time,rotations=None):
    wp=[];matrices=[]
    if rotations is None:rotations=np.zeros((len(names),3))
    for i,p in enumerate(parents):
        tracks=clip.get('bones',{}).get(names[i],{})
        angle=sample(tracks.get('rotation'),time,[0,0,0])*[-1,-1,1]
        offset=sample(tracks.get('position'),time,[0,0,0])*[-1,1,1]
        scale=sample(tracks.get('scale'),time,[1,1,1])
        matrix=R.from_euler('xyz',angle+rotations[i],degrees=True).as_matrix()@np.diag(scale)
        if p<0:
            wp.append(pivots[i]+offset);matrices.append(matrix)
        else:
            wp.append(wp[p]+matrices[p]@(pivots[i]-pivots[p]+offset))
            matrices.append(matrices[p]@matrix)
    return np.array(wp),matrices

def render(decoded,clip,time,colors,label,subtitle='',size=(1100,780),camera=None):
    names,pivots,parents,cubes=decoded
    wp,matrices=pose(names,pivots,parents,clip,time,getattr(decoded,'rotations',None))
    camera=camera or R.from_euler('xy',[-14,-24],degrees=True)
    shapes=[];allpoints=[]
    for i,corners,color in cubes:
        pts=wp[i]+(corners-pivots[i])@matrices[i].T
        # View along X so the length of an ARK animal is horizontal.
        pts=camera.apply(pts)[:,[2,1,0]]
        allpoints.append(pts)
        for f in FACES:
            face=pts[f]
            normal=np.cross(face[1]-face[0],face[2]-face[0]);length=np.linalg.norm(normal)
            if length<1e-8:continue
            normal/=length
            brightness=.64+.3*abs(float(normal@np.array([-.35,.75,.56])))
            rgb=tuple(int(int(colors[color][j:j+2],16)*brightness) for j in (1,3,5))
            shapes.append((float(face[:,2].mean()),face,rgb))
    pts=np.concatenate(allpoints);lo=pts[:,:2].min(0);hi=pts[:,:2].max(0)
    w,h=size;scale=min((w-120)/max(hi[0]-lo[0],1),(h-200)/max(hi[1]-lo[1],1))
    center=(lo+hi)/2
    def project(p):return ((p[:,:2]-center)*[scale,-scale]+[w/2,h/2+30]).tolist()
    im=Image.new('RGB',size,'#131e27');d=ImageDraw.Draw(im)
    d.text((38,23),label,fill='#e9eddf',font=font(32))
    d.text((40,69),subtitle,fill='#95a9ac',font=font(17))
    ground=int((h/2+30)-(lo[1]-center[1])*scale)+6
    d.line((40,ground,w-40,ground),fill='#2c4148',width=2)
    from depth_preview import rasterize
    quads=[np.column_stack([project(face),face[:,2]]) for _,face,_ in shapes]
    pixels=rasterize(im,quads,[color for _,_,color in shapes])
    if pixels is not None:
        im=Image.fromarray(pixels);d=ImageDraw.Draw(im)
    else:
        for _,face,color in sorted(shapes,key=lambda item:item[0]):
            poly=project(face);d.polygon(poly,fill=color)
    d.text((40,h-40),'ARK skeleton  /  fitted cubes  /  original animation tracks',fill='#718c92',font=font(15))
    return im

def previews(out,s,cubes,animations,label):
    from build_dinosaurs import PALETTES
    from extract_dinosaurs import resource_names
    decoded=decode_geometry(json.loads((out/f'geo/{resource_names(label)[0]}.geo.json').read_text()))
    dest=out/'previews';dest.mkdir(exist_ok=True)
    render(decoded,{},0,PALETTES[label],label,f'{len(s.names)} bones  |  {len(cubes)} cubes  |  {len(animations)} clips  |  bind pose').save(dest/'model.png')
    # Include a side and front view for silhouette/joint review.
    render(decoded,{},0,PALETTES[label],label,'Side view',camera=R.identity()).save(dest/'side.png')
    desired=['fly-fwd','fly-flap','flight','move-fwd'] if label in ['Argentavis','Piterodon'] else ['move-fwd','walk','run','idle']
    clip_name=next((n for token in desired for n in animations if token in n.lower() and animations[n]['loop']),next(iter(animations)))
    clip=animations[clip_name];frames=[]
    for t in np.linspace(0,clip['animation_length'],18,endpoint=False):
        frames.append(render(decoded,clip,float(t),PALETTES[label],label,clip_name,size=(760,550)))
    frames[0].save(dest/'motion.gif',save_all=True,append_images=frames[1:],duration=max(35,int(clip['animation_length']*1000/len(frames))),loop=0)
    (dest/'motion_clip.txt').write_text(clip_name+'\n',encoding='utf-8')

def contact_sheet(root,labels,destination=None):
    sheet=Image.new('RGB',(1600,math.ceil(len(labels)/2)*560),'#131e27')
    for i,label in enumerate(labels):
        im=Image.open(root/'Creatures'/label/'previews/model.png').resize((800,560))
        sheet.paste(im,((i%2)*800,(i//2)*560))
    sheet.save(destination or root/'dinosaur_overview.png')

def refresh_static(root,labels):
    from build_dinosaurs import PALETTES
    from extract_dinosaurs import resource_names
    for label in labels:
        folder=root/'Creatures'/label
        report=json.loads((folder/'build_report.json').read_text())
        decoded=decode_geometry(json.loads((folder/f'geo/{resource_names(label)[0]}.geo.json').read_text()))
        subtitle=f"{report['bones']} bones  |  {report['cubes']} cubes  |  {report['animations']} clips  |  bind pose"
        render(decoded,{},0,PALETTES[label],label,subtitle).save(folder/'previews/model.png')
        render(decoded,{},0,PALETTES[label],label,'Side view',camera=R.identity()).save(folder/'previews/side.png')
        print(label+': static previews refreshed',flush=True)

if __name__=='__main__':
    import argparse
    from pathlib import Path
    from ark_geometry import ROOT
    from extract_dinosaurs import SPECIES
    parser=argparse.ArgumentParser(description='Render a contact sheet from generated creature previews.')
    parser.add_argument('--species',nargs='+',choices=SPECIES,default=list(SPECIES))
    parser.add_argument('--output',type=Path,default=ROOT/'dinosaur_overview.png')
    parser.add_argument('--refresh-static',action='store_true',help='Render model/side images again from the saved geometry JSON.')
    args=parser.parse_args()
    if args.refresh_static:refresh_static(ROOT,args.species)
    contact_sheet(ROOT,args.species,args.output)
