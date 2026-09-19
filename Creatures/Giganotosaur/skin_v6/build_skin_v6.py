"""Giga paint experiment with isolated sampling diagnostics and padded UVs."""
import importlib.util
import inspect
import json
import sys
from pathlib import Path
import numpy as np
from PIL import Image
from scipy import ndimage

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT/'scripts'))
import skin_studio as st

spec = importlib.util.spec_from_file_location('v5', HERE.parent/'skin_v5/build_skin_v5.py')
v5 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v5)
ORIGINAL_RENDER = st.render
# UVs describe texel boundaries. Rounding samples the neighbour for the last
# half texel of every island; flooring samples the containing texel.
source = inspect.getsource(st.render).replace('np.round(tu[visible])', 'np.floor(tu[visible])').replace('np.round(tv[visible])', 'np.floor(tv[visible])')
exec(compile(source, '<v6 containing-texel renderer>', 'exec'), st.__dict__)
STYLE = {**v5.STYLE, 'detail_scale':{'head':2.2, 'jaw':2.0, 'eye':3.0}, 'base_scale':2.0}
STYLE['region_overrides']={**STYLE['region_overrides'], 'l_scapula':'torso', 'r_scapula':'torso'}
STATS = {'islands_cleaned':0, 'removed_small_components':0}
MODE = 'paint'
RULES = {'vertical_brush_units':1.6, 'longitudinal_brush_units':2.4,
         'stripe_spacing_units':12.0, 'stripe_width_units':3.2,
         'stripe_lower_limit':.48, 'minimum_body_patch_texels':12,
         'island_padding_texels':2, 'body_value_thresholds':[.20,.34,.48,.65,.83,.96]}
NAIL_CUBES=set()
REGION_COLORS={'torso':[185,90,75], 'neck':[220,135,75], 'tail':[210,185,65],
               'head':[80,165,180], 'eye':[250,230,125], 'jaw':[100,195,185],
               'tongue':[210,100,170], 'legl':[100,135,210], 'legr':[100,135,210],
               'arml':[160,120,215], 'armr':[160,120,215],
               'footl':[110,190,115], 'footr':[110,190,115]}


def pack(faces, width):
    # Reserve two texels on EACH side, including corners. Adjacent islands
    # never share padding, unlike the old one-texel shelf layout.
    x=y=2
    shelf=0
    for face in sorted(faces, key=lambda f:(-f['height'], -f['width'])):
        if x+face['width']+2 > width:
            y += shelf+4
            x=2
            shelf=0
        face['x'], face['y'] = x,y
        x += face['width']+4
        shelf=max(shelf, face['height'])
    return y+shelf+2


def compose(cubes, faces, bounds, features, palette, style, field, size):
    canvas=np.zeros((size,size,3),np.uint8)
    canvas[:]=st.BACKGROUND
    for face in faces:
        points,normals,ids=st.face_points(face)
        part=face['cube']['part']
        h,w=face['height'],face['width']
        if MODE=='flat':
            grid=np.broadcast_to([190,145,96],(h,w,3)).copy()
        elif MODE=='regions':
            role_color=REGION_COLORS.get(part,[160,160,160])
            if (face['cube']['bone'],face['cube']['cube_index']) in NAIL_CUBES:
                role_color=[60,60,60]
            grid=np.broadcast_to(role_color,(h,w,3)).copy()
        else:
            colors=paint(points,normals,ids,bounds,features,palette,style,field)
            grid=colors.reshape(h,w,3).astype(np.uint8)
            if (face['cube']['bone'],face['cube']['cube_index']) in NAIL_CUBES:
                grid[:]=v5.C[9]
            # Remove small high-contrast fragments on skin only. Eyes, mouth
            # and anatomical material masks get separate treatment.
            if part in ('torso','neck','tail','legl','legr','arml','armr','footl','footr'):
                before=grid.copy()
                labels=np.unique(grid.reshape(-1,3),axis=0)
                for col in labels:
                    mask=np.all(grid==col,axis=2)
                    components,n=ndimage.label(mask)
                    for k in range(1,n+1):
                        comp=components==k
                        count=int(comp.sum())
                        if count>=RULES['minimum_body_patch_texels'] or count==h*w:
                            continue
                        ring=ndimage.binary_dilation(comp)&~comp
                        candidates=grid[ring]
                        if not len(candidates):
                            continue
                        values,counts=np.unique(candidates,axis=0,return_counts=True)
                        grid[comp]=values[counts.argmax()]
                        STATS['removed_small_components']+=1
                STATS['islands_cleaned']+=int(np.any(grid!=before))
        x,y=face['x'],face['y']
        canvas[y-2:y+h+2,x-2:x+w+2]=np.pad(grid,((2,2),(2,2),(0,0)),mode='edge')
    return canvas


def paint(points,normals,ids,bounds,features,palette,style,field,shade=True):
    # Longitudinal + vertical painting plane: no lateral grid cuts that turn
    # cube corners into isolated marks. Symmetric on the two sides.
    p=points.copy()
    p[:,1]=v5.snap(p[:,1],RULES['vertical_brush_units'])
    p[:,2]=v5.snap(p[:,2],RULES['longitudinal_brush_units'])
    s,d=field.dorsal_weight(p)
    index=np.digitize(d,RULES['body_value_thresholds'])
    def part(*names):
        return np.isin(ids,[st.PART_ID[n] for n in names])
    body=part('torso','neck','tail')
    # Each stripe grows down from the dorsal area, no disconnected dots.
    phase=np.mod(s,RULES['stripe_spacing_units'])
    stripe=body&(phase<RULES['stripe_width_units'])&(d>RULES['stripe_lower_limit'])
    index[stripe]=np.minimum(index[stripe]+1,6)
    limb=part('legl','legr','arml','armr','footl','footr')
    local=np.clip((p[:,1]-bounds[ids,2])/np.maximum(bounds[ids,3]-bounds[ids,2],.01),0,1)
    index[limb]=np.digitize(local[limb],[.35,.75])+2
    index[part('jaw')]=1
    index[part('tongue')]=12
    # Head-oriented landmarks: no global lip-height interpolation crossing
    # the cheeks and no 3-D snapped cubes fragmenting a square eye.
    result=v5.C[index].copy()
    head=part('head','eye')
    if head.any():
        frame=features['head_frame']
        f,u,l=frame.coords(points)
        hd=(u+frame.half_height)/(2*frame.half_height)
        hi=np.digitize(hd,[.30,.52,.72,.9])+1
        result[head]=v5.C[np.clip(hi[head],0,6)]
        for center in features['eye_centers']:
            ef,eu,el=frame.coords(center)
            side=head & (l*np.sign(el)>abs(el)*.8)
            eye=side & (abs(f-ef)<.8) & (abs(u-eu)<.7)
            result[eye]=v5.C[7]
            iris=side & (abs(f-ef)<.5) & (abs(u-eu)<.45)
            result[iris]=v5.C[13]
            pupil=iris & (abs(f-ef)<.19)
            result[pupil]=v5.C[8]
    result[part('claw')]=v5.C[9]
    return result


def main():
    global MODE
    creature=HERE.parent
    diag=HERE/'diagnostics'
    diag.mkdir(parents=True,exist_ok=True)
    _,cubes,skeleton=st.load_cubes(creature/'geo/giganotosaur.geo.json',STYLE)
    # Select the distal fitted cube per digit, instead of cutting every
    # nearby surface with a black sphere. Kept in the region audit for review.
    for name in sorted({c['bone'] for c in cubes}):
        if not (('toe' in name or 'finger' in name) and name.endswith('2')):
            continue
        group=[c for c in cubes if c['bone']==name]
        terminal=name[:-1]+'3'
        if terminal not in skeleton['names']:
            continue
        c=group[0]
        end=skeleton['pivots'][skeleton['names'].index(terminal)]
        tip=c['bone_world']+c['bone_matrix']@(end-c['bone_pivot'])
        chosen=min(group,key=lambda q:np.linalg.norm(st.cube_corners(q).mean(0)-tip))
        NAIL_CUBES.add((name,chosen['cube_index']))
    cam=st.R.from_euler('yx',[-34,10],degrees=True).as_matrix()
    decoded=st.decode_geo(creature/'skin_v5/giganotosaur.geo.json',v5.STYLE)
    texture=Image.open(creature/'skin_v5/skin.png')
    a=ORIGINAL_RENDER(decoded,texture,cam,(1100,780))
    b=st.render(decoded,texture,cam,(1100,780))
    a.save(diag/'v5_original_sampling.png')
    b.save(diag/'v5_corrected_sampling.png')
    changed=int(np.any(np.asarray(a)!=np.asarray(b),axis=2).sum())
    st.pack_faces=pack
    st.compose=compose
    st.paint_points=paint
    MODE='flat'
    st.build_skin('Giganotosaur',creature,v5.COLORS[:8],style=STYLE,out_name='skin_v6/diagnostics/flat')
    MODE='regions'
    st.build_skin('Giganotosaur',creature,v5.COLORS[:8],style=STYLE,out_name='skin_v6/diagnostics/regions')
    MODE='paint'
    report,*_=st.build_skin('Giganotosaur',creature,
        ['#98532f','#ce9256','#572b22','#e3bd83','#f4ddb0','#38221e','#ecc04c','#ba6863'],
        style=STYLE,out_name='skin_v6')
    src=json.loads((creature/'Giganotosaur_GeckoLib.bbmodel').read_text())
    dst=json.loads((HERE/'Giganotosaur_Textured.bbmodel').read_text())
    assert src['animations']==dst['animations'] and src['outliner']==dst['outliner']
    assert [{k:v for k,v in e.items() if k!='faces'} for e in src['elements']]==[{k:v for k,v in e.items() if k!='faces'} for e in dst['elements']]
    assert all(report['checks'].values())
    STATS['v5_preview_pixels_changed_by_sampling_fix']=changed
    STATS['mesh_skeleton_animations_unchanged']=True
    (HERE/'diagnosis.json').write_text(json.dumps(STATS,indent=2)+'\n')
    (HERE/'painting_rules.json').write_text(json.dumps(RULES,indent=2)+'\n')
    (HERE/'paint_regions.json').write_text(json.dumps([
        {'bone':c['bone'],'cube_index':c['cube_index'],
         'paint_role':'nail' if (c['bone'],c['cube_index']) in NAIL_CUBES else c['part']}
        for c in cubes],indent=2)+'\n')
    print(json.dumps(STATS,indent=2))

if __name__=='__main__':
    main()
