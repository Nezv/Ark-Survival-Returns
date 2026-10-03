"""Import the three authored wyverns without rewriting their source geometry, UVs or animation.
The seven supplied clips are used directly; legacy wildlife roles alias the closest supplied clip.
"""
import copy
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent/'Creatures/Dragon/Dragon/out'
ASSETS = ROOT/'src/main/resources/assets/arksurvivalreturns'
VARIANTS = ('red','white','black')
SCALE = .7
ALIASES = {
    'Dragon-Ground-Idle':'idle', 'Dragon-Ground-Move-Fwd':'walk',
    'Dragon-Ground-Charge-Fwd':'walk', 'Dragon-Ground-Attack-Bite':'fire',
    'Dragon-Ground-Attack-Fire':'fire', 'Dragon-Fly-Fwd':'fly',
    'Dragon-Fly-Idle':'fly_vertical','Dragon-Land':'fly_vertical','Dragon-Take-Off':'fly_vertical',
    'Dragon-Fly-Attack-Swoop-Out':'glide','Dragon-Fly-Attack-Fire':'fly_fire',
    'Dragon-Ground-Turn-Lft':'walk','Dragon-Ground-Turn-Rit':'walk',
    'Dragon-Fly-Lft':'fly','Dragon-Fly-Rit':'fly',
}
def scale_track(v):
    if isinstance(v,(int,float)):return round(v*SCALE,6)
    if isinstance(v,list):return [scale_track(x) for x in v]
    if isinstance(v,dict):return {k:x if k=='lerp_mode' else scale_track(x) for k,x in v.items()}
    raise ValueError(f'Unsupported position expression: {v}')
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,separators=(',',':')),encoding='utf-8')
def import_dragon():
    for variant in VARIANTS:
        source_geo=SOURCE/f'wyvern_{variant}.geo.json'
        geo=json.loads(source_geo.read_text())
        model=geo['minecraft:geometry'][0]
        for bone in model['bones']:
            for obj in [bone,*bone.get('cubes',[])]:
                for key in ('pivot','origin','size'):
                    if key in obj:obj[key]=[round(x*SCALE,6) for x in obj[key]]
                if 'inflate' in obj:obj['inflate']*=SCALE
        desc=model['description']
        desc.update(identifier='geometry.dragon_'+variant,visible_bounds_width=34,
                    visible_bounds_height=18,visible_bounds_offset=[0,3,0])
        anim=json.loads((SOURCE/f'wyvern_{variant}.animation.json').read_text())
        clips=anim['animations']
        for clip in clips.values():
            for tracks in clip.get('bones',{}).values():
                if 'position' in tracks:tracks['position']=scale_track(tracks['position'])
        for name,source in ALIASES.items():
            clips[name]=copy.deepcopy(clips['animation.wyvern.'+source])
            clips[name]['loop']=not any(part in name for part in ('Attack','Land','Take-Off'))
        anim['geckolib_format_version']=2
        write(ASSETS/f'geckolib/models/entity/dragon_{variant}.geo.json',geo)
        write(ASSETS/f'geckolib/animations/entity/dragon_{variant}.animation.json',anim)
        shutil.copyfile(SOURCE/f'wyvern_{variant}.png',ASSETS/f'textures/entity/dragon_{variant}.png')
    verify()
def verify():
    from PIL import Image
    for variant in VARIANTS:
        geo=json.loads((ASSETS/f'geckolib/models/entity/dragon_{variant}.geo.json').read_text())['minecraft:geometry'][0]
        anim=json.loads((ASSETS/f'geckolib/animations/entity/dragon_{variant}.animation.json').read_text())['animations']
        original=json.loads((SOURCE/f'wyvern_{variant}.geo.json').read_text())['minecraft:geometry'][0]
        bones={b['name'] for b in geo['bones']}
        assert len(bones)==len(geo['bones'])
        assert set(ALIASES)<=set(anim)
        assert all(set(c.get('bones',{}))<=bones for c in anim.values())
        assert all(b.get('parent') in bones for b in geo['bones'] if 'parent' in b)
        for b,source in zip(geo['bones'],original['bones']):
            for cube,orig in zip(b.get('cubes',[]),source.get('cubes',[])):
                assert cube.get('uv')==orig.get('uv')
                assert cube['size']==[round(v*SCALE,6) for v in orig['size']]
        tex=ASSETS/f'textures/entity/dragon_{variant}.png'
        assert tex.read_bytes()==(SOURCE/f'wyvern_{variant}.png').read_bytes()
        assert Image.open(tex).size==(geo['description']['texture_width'],geo['description']['texture_height'])
    print('Three authored dragon variants: geometry, clip bones, UVs and exact textures verified.')
if __name__=='__main__': import_dragon()
