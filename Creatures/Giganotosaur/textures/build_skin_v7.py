"""Five vertical skin layers, five shades each, full mirrored 3x3 brush fill.
Run --help. Uses scripts/skin_studio.py for geometry/export; no v5/v6 imports.
"""
import argparse
import hashlib
import inspect
import json
import re
from pathlib import Path
import sys
import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT/'scripts'))
import skin_studio as st

BRUSH = 3
MIRROR = np.array([-1,1,1])
DEFAULTS = {'seed':7319, 'base_colors':['#ba7b40','#965529','#6c3b26','#4d2a1b','#361e14'],
            'shade_offsets':[-6,-3,0,3,6], 'layer_edges':[.20,.40,.60,.80],
            'pixels_per_unit':2.0, 'head_density_multiplier':2.2}
THEROPODS = ['Giganotosaur','Tyranosaur','Spinosaurus','Ceratosaurus','Dilophosaur',
             'Acrochantosaur','Allosaurus','Carnotaurus','Velociraptor']
VARIANTS = ['Ivory', 'Darken', 'Emerald', 'Midnight', 'Burgundy']
ORIGINAL_RENDER_SOURCE = inspect.getsource(st.render)

def linear_sum_assignment(cost):
    """Minimum-cost rectangular assignment without the heavyweight SciPy dependency."""
    matrix = np.asarray(cost, dtype=float)
    transposed = matrix.shape[0] > matrix.shape[1]
    if transposed:
        matrix = matrix.T
    rows, cols = matrix.shape
    u = np.zeros(rows + 1)
    v = np.zeros(cols + 1)
    matched_row = np.zeros(cols + 1, dtype=int)
    path = np.zeros(cols + 1, dtype=int)
    for row in range(1, rows + 1):
        matched_row[0] = row
        col = 0
        minimum = np.full(cols + 1, np.inf)
        used = np.zeros(cols + 1, dtype=bool)
        while True:
            used[col] = True
            current_row = matched_row[col]
            delta = np.inf
            next_col = 0
            for candidate in range(1, cols + 1):
                if used[candidate]:
                    continue
                reduced = matrix[current_row - 1, candidate - 1] - u[current_row] - v[candidate]
                if reduced < minimum[candidate]:
                    minimum[candidate] = reduced
                    path[candidate] = col
                if minimum[candidate] < delta:
                    delta = minimum[candidate]
                    next_col = candidate
            for candidate in range(cols + 1):
                if used[candidate]:
                    u[matched_row[candidate]] += delta
                    v[candidate] -= delta
                else:
                    minimum[candidate] -= delta
            col = next_col
            if matched_row[col] == 0:
                break
        while True:
            previous = path[col]
            matched_row[col] = matched_row[previous]
            col = previous
            if col == 0:
                break
    row_indices = np.array([matched_row[col] - 1 for col in range(1, cols + 1) if matched_row[col]], dtype=int)
    col_indices = np.array([col - 1 for col in range(1, cols + 1) if matched_row[col]], dtype=int)
    return (col_indices, row_indices) if transposed else (row_indices, col_indices)

def palette(colors, offsets):
    return np.clip(np.array([st.rgb(c) for c in colors])[:,None,:] +
                   np.array(offsets)[None,:,None],0,255).astype(np.uint8)

def random_brushes(height, width, seed, mirror_axis=None):
    """One uniform random shade per brush; no sparse dabs or rejection."""
    cells = np.random.default_rng(seed).integers(0,5,(height//3,width//3))
    if mirror_axis is not None:
        axis = np.arange(cells.shape[mirror_axis])
        cells = np.take(cells,np.minimum(axis,axis[::-1]),axis=mirror_axis)
    return cells

def transform(grid, op):
    transpose, flip_y, flip_x = op
    if transpose:
        grid = np.swapaxes(grid,0,1)
    if flip_y:
        grid = grid[::-1]
    if flip_x:
        grid = grid[:,::-1]
    return grid.copy()

def reflection_fit(a,b):
    ac = np.array(a['corner'])[[0,1,3,2]].reshape(2,2,3)*MIRROR
    bc = np.array(b['corner'])[[0,1,3,2]].reshape(2,2,3)
    options = [(float(np.mean((transform(ac,(t,y,x))-bc)**2)),(t,y,x))
               for t in (False,True) for y in (False,True) for x in (False,True)]
    return min(options,key=lambda v:v[0])

def face_pairs(cubes,faces):
    """Pair fitted cubes by bone family, then orient their reflected faces."""
    groups, cube_matches, unmatched = {}, [], []
    centers = [st.cube_corners(c).mean(0) for c in cubes]
    for i,c in enumerate(cubes):
        family = re.sub(r'^(l_|r_|lft_|rht_)','',c['bone'].lower())
        family = re.sub(r'_[lr]$','',family)
        groups.setdefault(family,[]).append(i)
    for indices in groups.values():
        left = [i for i in indices if centers[i][0]>.15]
        right = [i for i in indices if centers[i][0]<-.15]
        cube_matches.extend((i,i) for i in indices if abs(centers[i][0])<=.15)
        used = set()
        if left and right:
            cost = [[np.linalg.norm(centers[a]*MIRROR-centers[b]) for b in right] for a in left]
            aa,bb = linear_sum_assignment(cost)
            for a,b in zip(aa,bb):
                cube_matches.append((left[a],right[b]))
                used.update((left[a],right[b]))
        unmatched.extend(i for i in left+right if i not in used)
    cube_matches.extend((i,None) for i in unmatched)
    by_cube = {id(c):[f for f in faces if f['cube'] is c] for c in cubes}
    pairs = []
    for a,b in cube_matches:
        aa = by_cube[id(cubes[a])]
        if b is None:
            pairs.extend((f,None,(False,False,False)) for f in aa)
        elif a==b:
            remaining = list(range(6))
            while remaining:
                i = max(remaining,key=lambda k:abs(np.mean(aa[k]['corner'],axis=0)[0]))
                j = min(remaining,key=lambda k:reflection_fit(aa[i],aa[k])[0])
                pairs.append((aa[i],aa[j],reflection_fit(aa[i],aa[j])[1]))
                remaining.remove(i)
                if j!=i:
                    remaining.remove(j)
        else:
            bb = by_cube[id(cubes[b])]
            cost = [[reflection_fit(f,q)[0] for q in bb] for f in aa]
            rows,cols = linear_sum_assignment(cost)
            pairs.extend((aa[i],bb[j],reflection_fit(aa[i],bb[j])[1]) for i,j in zip(rows,cols))
    return pairs,unmatched

def layer_ids(points,part,field,head,bounds,config):
    _,height = field.dorsal_weight(points)
    part_index = st.PART_ID.get(part, st.PART_ID['other'])
    lo,hi = bounds[part_index,2:4]
    local = (points[:,1]-lo)/max(hi-lo,.01)
    if height is None:
        height = local
    if part in ('head','eye') and head.valid:
        _,up,_ = head.coords(points)
        height = (up+head.half_height)/(2*head.half_height)
    elif part.startswith(('leg','arm','foot')):
        height = .20+.55*local
    elif part=='jaw':
        height = .12+.18*local
    return np.digitize(np.clip(height,0,1),config['layer_edges'])

def pack(faces):
    """Independent two-pixel gutters, including atlas corners."""
    for size in (256,384,512,768,1024,2048):
        x = y = 2
        shelf = 0
        for f in sorted(faces,key=lambda f:(-f['height'],-f['width'])):
            if x+f['width']+2>size:
                y += shelf+4
                x,shelf = 2,0
            f['x'],f['y'] = x,y
            x += f['width']+4
            shelf = max(shelf,f['height'])
        if y+shelf+2<=size:
            return size
    raise ValueError('Atlas exceeds 2048px; lower pixels_per_unit.')

def find_nails(cubes,skeleton):
    result = set()
    for name in {c['bone'] for c in cubes}:
        if not (('toe' in name or 'finger' in name) and name.endswith('2')):
            continue
        terminal = name[:-1]+'3'
        if terminal not in skeleton['names']:
            continue
        group = [c for c in cubes if c['bone']==name]
        c = group[0]
        end = skeleton['pivots'][skeleton['names'].index(terminal)]
        tip = c['bone_world']+c['bone_matrix']@(end-c['bone_pivot'])
        result.add(id(min(group,key=lambda q:np.linalg.norm(st.cube_corners(q).mean(0)-tip))))
    return result

def make_texture(creature,config):
    geo,model = st.creature_files(creature)
    style = {**st.DEFAULT_STYLE,**st.SPECIES_STYLE.get(creature.name,{}),
             'base_scale':config['pixels_per_unit'],'min_island':{},
             'detail_scale':{'head':config['head_density_multiplier'],'eye':2.2,'jaw':2.0}}
    style['region_overrides'] = {**style.get('region_overrides',{}),'l_scapula':'torso','r_scapula':'torso'}
    _,cubes,skeleton = st.load_cubes(geo,style)
    faces = st.face_frames(cubes,style)
    for f in faces:
        for dim in ('width','height'):
            f[dim] = max(3,3*int(np.ceil(f[dim]/3)))
    field = st.BodyField(cubes,style.get('centerline_chain'))
    head,bounds = st.HeadFrame(cubes),st.part_bounds(cubes)
    nails = find_nails(cubes,skeleton)
    colors = palette(config['base_colors'],config['shade_offsets'])
    materials = palette(['#a86259','#655646','#ddb34b'],[-16,-8,0,8,16])
    pairs,unmatched = face_pairs(cubes,faces)
    brush_count = changed_count = mirrored = self_mirrors = 0
    nose = None
    for f,partner,op in pairs:
        h,w = f['height']//3,f['width']//3
        identity = f"{creature.name}:{f['cube']['bone']}:{f['cube']['cube_index']}:{f['key']}:{config['seed']}"
        seed = int.from_bytes(hashlib.blake2b(identity.encode(),digest_size=8).digest(),'little')
        axis = None
        if partner is f:
            corners = np.array(f['corner'])
            axis = 1 if abs(corners[1,0]-corners[0,0])>=abs(corners[3,0]-corners[0,0]) else 0
        shades = random_brushes(f['height'],f['width'],seed,axis)
        points = st.face_points({**f,'width':w,'height':h})[0]
        part = f['cube']['part']
        layers = layer_ids(points,part,field,head,bounds,config).reshape(h,w)
        if axis is not None:
            indices = np.arange(layers.shape[axis])
            layers = np.take(layers,np.minimum(indices,indices[::-1]),axis=axis)
        small = colors[layers,shades]
        flat = colors[layers,np.full_like(shades,2)]
        material = 0 if part=='tongue' else 1 if id(f['cube']) in nails or part=='claw' else 2 if part=='eye' else None
        if material is not None:
            small = materials[material,shades]
            flat = np.broadcast_to(materials[material,2],small.shape).copy()
        # A deliberate eye landmark, also painted only in whole brush cells.
        # Painting skin must not erase the eye hidden behind fitted head cubes.
        if part=='head' and head.valid:
            eyes = st.part_points(cubes,'eye')
            if len(eyes):
                center = eyes[eyes[:,0]>0].mean(0) if np.any(eyes[:,0]>0) else eyes.mean(0)
                ef,eu,_ = head.coords(center)
                fwd,up,lateral = head.coords(points)
                side = np.abs(lateral) > head.half_width*.45
                socket = (side & (abs(fwd-ef)<1.15) & (abs(up-eu)<1.0)).reshape(h,w)
                iris = (side & (abs(fwd-ef)<.80) & (abs(up-eu)<.65)).reshape(h,w)
                pupil = (side & (abs(fwd-ef)<.25) & (abs(up-eu)<.50)).reshape(h,w)
                small[socket] = [60,43,32]
                small[iris] = materials[2,shades[iris]]
                small[pupil] = [35,30,26]
        if axis is not None:
            # Facial landmarks must follow the same symmetry as skin cells.
            indices = np.arange(small.shape[axis])
            small = np.take(small,np.minimum(indices,indices[::-1]),axis=axis)
        brush_count += shades.size
        changed_count += int((shades!=2).sum())
        f['grid'] = small.repeat(3,0).repeat(3,1)
        f['flat'] = flat.repeat(3,0).repeat(3,1)
        if partner is not None and partner is not f:
            partner['grid'] = transform(f['grid'],op)
            partner['flat'] = transform(f['flat'],op)
            partner['height'],partner['width'] = partner['grid'].shape[:2]
            assert np.array_equal(partner['grid'],transform(f['grid'],op))
            mirrored += 1
        elif partner is f:
            assert np.array_equal(f['grid'],np.flip(f['grid'],axis))
            self_mirrors += 1
        if part=='head' and abs(f['normal'][0])>.5:
            score = np.mean(f['corner'],axis=0)[2]
            if nose is None or score>nose[0]:
                nose = score,f
    size = pack(faces)
    canvas = np.full((size,size,3),st.BACKGROUND,np.uint8)
    for f in faces:
        grid = f['grid']
        tiles = grid.reshape(f['height']//3,3,f['width']//3,3,3)
        assert np.all(tiles==tiles[:,:1,:,:1,:]), 'Nonuniform 3x3 brush cell'
        x,y,w,h = (f[k] for k in ('x','y','width','height'))
        canvas[y-2:y+h+2,x-2:x+w+2] = np.pad(grid,((2,2),(2,2),(0,0)),mode='edge')
    report = {'creature':creature.name,'brush_size_pixels':3,'skin_palette':colors.tolist(),
              'base_colors':config['base_colors'],'shade_offsets':config['shade_offsets'],'seed':config['seed'],
              'atlas_size':size,'cubes':len(cubes),'faces':len(faces),'symmetry_axis':'X=0',
              'mirrored_face_pairs':mirrored,'self_mirrored_faces':self_mirrors,
              'unpaired_cubes':[{'bone':cubes[i]['bone'],'cube_index':cubes[i]['cube_index']} for i in unmatched],
              'random_brush_cells':brush_count,'non_base_shade_fraction':round(changed_count/brush_count,4),
              'checks':{'all_faces_use_uniform_3x3_brushes':True,'paired_face_textures_identical_after_reflection':True},
              'materials':materials.tolist(),'method':'Full random fill in 3x3 cells; no sparse stamps, suppression or fragment rejection.'}
    report['metrics'] = st.validation(canvas,faces,size)
    assert report['metrics']['island_overlap_pixels']==0
    report['checks']['islands_disjoint'] = True
    return geo,model,cubes,skeleton,faces,style,canvas,colors,report,nose

def save_previews(out,geo,cubes,skeleton,style,report,nose):
    src = ORIGINAL_RENDER_SOURCE.replace('np.round(tu[visible])','np.floor(tu[visible])').replace('np.round(tv[visible])','np.floor(tv[visible])')
    exec(compile(src,'<v7 preview sampler>','exec'),st.__dict__)
    def title(image,title,subtitle):
        draw = ImageDraw.Draw(image)
        draw.text((30,20),title,fill='#eeeeee',font=st.font(27))
        draw.text((30,60),subtitle,fill='#bcc4c4',font=st.font(15))
        return image
    st.title_block = title
    animations = None
    files = sorted((out.parent/'animations').glob('*.animation.json'))
    if files:
        animations = json.loads(files[0].read_text())['animations']
    label = out.parent.parent.name if out.parent.name in ('skin_v7', 'textures') else out.parent.name
    st.previews(st.decode_geo(out/geo.name,style),cubes,skeleton,animations,
                out/'skin.png',out/'previews',label,
                f"3px brush | 5 layers x 5 shades | {report['atlas_size']}px atlas",style)
    if nose:
        f = nose[1]
        panels = []
        for label,grid in [('Five flat layers',f['flat']),('Random fill / 3px brush',f['grid'])]:
            im = Image.fromarray(grid).resize((grid.shape[1]*16,grid.shape[0]*16),Image.Resampling.NEAREST)
            panel = Image.new('RGB',(max(300,im.width),im.height+50),'#202a31')
            panel.paste(im,((panel.width-im.width)//2,50))
            ImageDraw.Draw(panel).text((12,15),label,fill='white',font=st.font(16))
            panels.append(panel)
        sheet = Image.new('RGB',(sum(x.width for x in panels)+12,max(x.height for x in panels)),'#202a31')
        sheet.paste(panels[0],(0,0))
        sheet.paste(panels[1],(panels[0].width+12,0))
        sheet.save(out/'previews/nose_comparison.png')
        Image.fromarray(f['grid']).save(out/'previews/nose_uv.png')

def build(creature,config,dry_run=False,previews=True,output_name='textures'):
    geo,model,cubes,skeleton,faces,style,canvas,colors,report,nose = make_texture(creature,config)
    if dry_run:
        return report
    out = creature/output_name
    out.mkdir(exist_ok=True)
    Image.fromarray(canvas).save(out/'skin.png')
    st.assign_uvs(faces)
    st.write_geo(geo,cubes,len(canvas),out/geo.name)
    st.write_bbmodel(model,cubes,out/'skin.png',len(canvas),out/f'{creature.name}_Textured.bbmodel')
    source = json.loads(model.read_text())
    result = json.loads((out/f'{creature.name}_Textured.bbmodel').read_text())
    assert source['outliner']==result['outliner'] and source['animations']==result['animations']
    assert [{k:v for k,v in e.items() if k!='faces'} for e in source['elements']]==[{k:v for k,v in e.items() if k!='faces'} for e in result['elements']]
    report['checks']['mesh_skeleton_animations_unchanged'] = True
    report['variant'] = output_name.split('/')[-1] if output_name != 'textures' else 'base'
    for filename,data in [('skin_report.json',report),
                          ('region_palettes.json',{'base_colors':config['base_colors'],'five_shades_per_layer':colors.tolist()}),
                          ('painting_rules.json',{**config,'brush_size_pixels':3,'symmetry_axis':'X=0'})]:
        (out/filename).write_text(json.dumps(data,indent=2)+'\n')
    sheet = Image.new('RGB',(620,240),'#202a31')
    draw = ImageDraw.Draw(sheet)
    for row,band in enumerate(colors):
        draw.text((10,12+row*46),['Belly','Lower flank','Flank','Upper flank','Dorsal'][row],fill='white')
        for col,color in enumerate(band):
            draw.rectangle((120+col*98,5+row*46,211+col*98,43+row*46),fill=tuple(color))
    sheet.save(out/'region_palettes.png')
    if previews:
        save_previews(out,geo,cubes,skeleton,style,report,nose)
    return report

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--creatures',nargs='+',default=['Giganotosaur'])
    parser.add_argument('--all-theropods',action='store_true',help='Use the nine listed theropod folders.')
    parser.add_argument('--config',type=Path,default=HERE/'batch_palettes.json')
    parser.add_argument('--seed',type=int)
    parser.add_argument('--dry-run',action='store_true',help='Build and validate in memory; write nothing.')
    parser.add_argument('--no-previews',action='store_true')
    parser.add_argument('--variants',nargs='*',metavar='NAME',help='Also build named palette variants into textures/<NAME>.')
    parser.add_argument('--all-variants',action='store_true',help='Build Ivory, Darken, Emerald, Midnight, and Burgundy.')
    args = parser.parse_args()
    settings = json.loads(args.config.read_text()) if args.config.exists() else {}
    failures = []
    for name in THEROPODS if args.all_theropods else args.creatures:
        try:
            if Path(name).name!=name or name in ('.','..'):
                raise ValueError('Use a creature folder name, not a path.')
            config = {**DEFAULTS,**settings.get('defaults',{}),**settings.get('creatures',{}).get(name,{})}
            if args.seed is not None:
                config['seed'] = args.seed
            assert len(config['base_colors'])==5 and len(config['shade_offsets'])==5
            assert len(config['layer_edges'])==4 and sorted(config['layer_edges'])==config['layer_edges']
            if config['pixels_per_unit']<=0 or config['head_density_multiplier']<=0:
                raise ValueError('Texture density must be positive.')
            ramp=palette(config['base_colors'],config['shade_offsets'])
            if any(len(np.unique(band,axis=0))!=5 for band in ramp):
                raise ValueError('Each layer needs five distinct shades; adjust colors or offsets.')
            r = build(ROOT/'Creatures'/name,config,args.dry_run,not args.no_previews)
            print(f"{name}: {r['atlas_size']}px, {r['random_brush_cells']} brush cells, {r['non_base_shade_fraction']:.1%} non-base shades, {len(r['unpaired_cubes'])} unpaired cubes"+(' [dry run]' if args.dry_run else ''))
            variant_names = VARIANTS if args.all_variants else (args.variants or [])
            unknown = [variant for variant in variant_names if variant not in VARIANTS]
            if unknown:
                raise ValueError(f"Unknown variants: {', '.join(unknown)}. Choose from {', '.join(VARIANTS)}.")
            for variant in variant_names:
                variant_config = {**config, **settings.get('variants',{}).get(variant,{})}
                if args.seed is not None:
                    variant_config['seed'] = args.seed
                if len(variant_config['base_colors']) != 5:
                    raise ValueError(f'{variant} needs exactly five base colors.')
                vr = build(ROOT/'Creatures'/name,variant_config,args.dry_run,not args.no_previews,
                            output_name=f'textures/{variant}')
                print(f"{name}/{variant}: {vr['atlas_size']}px, {vr['random_brush_cells']} brush cells, {vr['non_base_shade_fraction']:.1%} non-base shades"+(' [dry run]' if args.dry_run else ''))
        except Exception as error:
            failures.append(name)
            print(f'{name}: FAILED: {type(error).__name__}: {error}',file=sys.stderr)
    if failures:
        raise SystemExit(1)

if __name__=='__main__':
    main()
