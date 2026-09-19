"""Silhouette comparisons against ARK skin; pose strips from exported JSON.

Writes Scratch diagnostics only. LBS reference uses original vertex weights and
the validated exported bone transforms. These are visual approximation metrics,
not substitutes for original-PSA animation validation.
"""
import json,sys
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
from scipy.spatial.transform import Rotation as R
from build_members import ROOT, SPECIES, resource_names, Skeleton, PALETTES, write_json
from preview_dinosaurs import decode_geometry,pose,render,FACES,font


def masks(s,decoded,clip,time):
    names,pivots,parents,cubes=decoded
    bp,bm=pose(names,pivots,parents,{},0,decoded.rotations)
    wp,wm=pose(names,pivots,parents,clip,time,decoded.rotations)
    src=np.zeros_like(s.source_vertices);sums=np.zeros(len(src))
    for i in range(len(names)):
        weights=s.weights[s.weights['bone']==i]
        ix=weights['vertex'];w=weights['weight']
        if len(ix):
            local=(s.source_vertices[ix]-bp[i])@np.linalg.inv(bm[i]).T
            np.add.at(src,ix,(wp[i]+local@wm[i].T)*w[:,None]);np.add.at(sums,ix,w)
    for i in range(len(names)):
        ix=np.flatnonzero((sums==0)&(s.dominant==i))
        if len(ix):
            local=(s.source_vertices[ix]-bp[i])@np.linalg.inv(bm[i]).T
            src[ix]=wp[i]+local@wm[i].T;sums[ix]=1
    assert (sums>0).all()
    src/=sums[:,None]
    boxes=np.array([wp[i]+(p-pivots[i])@wm[i].T for i,p,c in cubes])
    pairs=[];scores={}
    for view,axes in [('side',(2,1)),('top',(2,0)),('front',(0,1))]:
        a=src[:,axes];b=boxes[:,:,axes]
        allp=np.concatenate([a,b.reshape(-1,2)]);lo=allp.min(0);hi=allp.max(0)
        scale=230/max(hi-lo);center=(lo+hi)/2
        a=(a-center)*[scale,-scale]+128;b=(b-center)*[scale,-scale]+128
        im1=Image.new('1',(256,256));im2=Image.new('1',(256,256));d1=ImageDraw.Draw(im1);d2=ImageDraw.Draw(im2)
        for tri in s.triangles:d1.polygon([tuple(x) for x in a[tri]],fill=1)
        for box in b:
            for face in FACES:d2.polygon([tuple(x) for x in box[face]],fill=1)
        m1=np.asarray(im1);m2=np.asarray(im2);intersection=(m1&m2).sum();union=(m1|m2).sum()
        scores[view]={'iou':round(float(intersection/max(union,1)),4),'source_coverage':round(float(intersection/max(m1.sum(),1)),4),'excess_fraction':round(float((m2&~m1).sum()/max(m2.sum(),1)),4)}
        pixels=np.full((256,256,3),[19,30,39],np.uint8)
        pixels[m1&~m2]=[70,160,220];pixels[m2&~m1]=[235,125,75];pixels[m1&m2]=[210,221,196]
        im=Image.fromarray(pixels);ImageDraw.Draw(im).text((8,8),f'{view}: IoU {scores[view]["iou"]:.2f}',font=font(14),fill='white');pairs.append(im)
    return pairs,scores


def inspect(label):
    dest=ROOT/'Scratch'/label;src=ROOT/'Creatures'/label
    identifier,_=resource_names(label);s=Skeleton(SPECIES[label],src/'source')
    decoded=decode_geometry(json.loads((dest/f'geo/{identifier}.geo.json').read_text()))
    anim=json.loads((dest/f'animations/{identifier}.animation.json').read_text())['animations']
    selected=[]
    for token in ['fly-flap','move-fwd','swim-fwd','attack','idle']:
        name=next((n for n in anim if token in n.lower() and n not in selected),None)
        if name:selected.append(name)
        if len(selected)==3:break
    cases=[('Bind pose',{},0)]+[(name,anim[name],anim[name]['animation_length']*f) for name in selected for f in [.25,.5,.75]]
    sheet=Image.new('RGB',(768,len(cases)*282+42),'#131e27');draw=ImageDraw.Draw(sheet)
    draw.text((8,8),'White: overlap | Blue: ARK only | Orange: cuboids only',font=font(17),fill='white')
    report=[]
    for k,(name,clip,t) in enumerate(cases):
        panels,scores=masks(s,decoded,clip,t)
        draw.text((8,42+k*282),f'{name} at {t:.3f}s',font=font(15),fill='white')
        for j,im in enumerate(panels):sheet.paste(im,(j*256,68+k*282))
        report.append(dict(clip=name,time=t,views=scores))
    sheet.save(dest/'previews/silhouette_comparison.png')
    # Actual geometry at several poses; retain file for human joint review.
    strip=Image.new('RGB',(1440,len(selected)*400),'#131e27')
    camera=R.from_euler('xy',[-35,-55],degrees=True) if label=='Argentavis' else None
    for row,name in enumerate(selected):
        clip=anim[name]
        for col,t in enumerate([.15,.45,.75]):
            im=render(decoded,clip,clip['animation_length']*t,PALETTES[label],label,name,size=(480,400),camera=camera)
            strip.paste(im,(col*480,row*400))
    strip.save(dest/'previews/poses.png')
    data=dict(reference='Original source triangles and vertex weights, linear blend skinning through validated exported transforms',samples=report,limitations=['Rasterized orthographic silhouettes at 256px; not a perceptual score','No deformation of cuboid vertices; original blend skinning becomes rigid parts','Sampled visual checks do not certify every pose'])
    write_json(dest/'silhouette_metrics.json',data,True)
    vals=[v['iou'] for sample in report for v in sample['views'].values()]
    print(label,'silhouette IoU mean',round(float(np.mean(vals)),3),'min',min(vals),flush=True)


if __name__=='__main__':
    for name in sys.argv[1:] or ['Unicorn','Titanoboa','Argentavis','Tusoteuthis']:inspect(name)
