"""Author/export the standalone Prehistoric camp pack. No game integration.

Run: python tools/build_prehistoric_camp.py
Requires Pillow and numpy. Geometry is native Minecraft / Blockbench JSON.
"""
import copy
import json
import math
import random
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from build_camp_assets import box, rot, DISPLAY
import render_camp_assets as art

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'design/prehistoric-camp'
ASSETS = OUT / 'assets/arksurvivalreturns'
MODELS = ASSETS / 'models/block/prehistoric'
TEXTURES = ASSETS / 'textures/block/prehistoric'
MODEL_DATA = {}
PALETTE = {
    'stone': ((129, 125, 108), 'stone'),
    'stone_light': ((157, 151, 129), 'stone'),
    'stone_dark': ((101, 102, 87), 'stone'),
    'mortar': ((151, 150, 127), 'stone'),
    'mortar_rim': ((180, 174, 148), 'stone'),
    'reed': ((155, 150, 82), 'reed'),
    'reed_light': ((190, 178, 104), 'reed'),
    'reed_dark': ((113, 120, 65), 'reed'),
    'woven': ((158, 155, 95), 'woven'),
    'cord': ((178, 155, 103), 'cord'),
    'fur': ((184, 157, 119), 'fur'),
    'fur_light': ((213, 191, 152), 'fur'),
    'hide': ((121, 87, 57), 'fur'),
    'wood': ((128, 87, 48), 'wood'),
    'wood_cut': ((178, 136, 79), 'wood'),
    'char': ((48, 43, 34), 'wood'),
    'ash': ((87, 80, 68), 'stone'),
    'ember': ((210, 83, 28), 'ember'),
    'flame': ((239, 152, 46), 'ember'),
    'flame_light': ((255, 209, 99), 'ember'),
    'clay': ((163, 104, 69), 'clay'),
    'clay_light': ((191, 135, 89), 'clay'),
    'clay_inner': ((129, 83, 55), 'clay'),
    'soot': ((64, 51, 40), 'clay'),
    'berry_whole': ((77, 62, 99), 'food'),
    'berry_crush': ((110, 78, 108), 'food'),
    'berry_paste': ((96, 106, 65), 'food'),
    'leaf': ((100, 123, 64), 'reed'),
    'meat_raw': ((175, 82, 69), 'food'),
    'meat_crush': ((143, 87, 63), 'food'),
    'meat_paste': ((104, 92, 57), 'food'),
    'meat_seared': ((142, 85, 48), 'food'),
    'meat_cooked': ((98, 56, 31), 'food'),
    'fat': ((219, 174, 130), 'food'),
    'crust': ((61, 36, 24), 'food'),
    'broth': ((132, 96, 46), 'food'),
}


def material(name, base, style):
    rng = random.Random('prehistoric-' + name)
    im = Image.new('RGB', (32, 32)); pix = im.load()
    for y in range(32):
        for x in range(32):
            n = rng.choice([-3, -2, 0, 0, 1, 2, 3])
            if style == 'woven':
                n += (7 if (x//4+y//4) % 2 else -7) + (4 if x%4==1 or y%4==1 else 0)
            elif style == 'reed': n += [8, 2, -7, 0][x % 4] + (5 if y in (6, 22) else 0)
            elif style == 'fur': n += (5 if (x+y//3)%4==0 else 0)
            elif style == 'clay': n += 3*math.sin(y*1.4)
            elif style == 'wood': n += 5*math.sin(y*1.3)
            pix[x,y] = tuple(max(0,min(255,round(c+n))) for c in base)
    d=ImageDraw.Draw(im)
    dark=tuple(max(0,c-19) for c in base);light=tuple(min(255,c+17) for c in base)
    if style=='stone':
        for _ in range(16):
            x,y=rng.randrange(29),rng.randrange(29)
            d.rectangle((x,y,x+rng.choice([1,2,3]),y+1),fill=rng.choice([dark,light]))
    elif style=='wood':
        for y in (4,12,21,29):d.line([(2,y),(13,y),(17,y+1),(30,y+1)],fill=dark)
    elif style=='fur':
        for _ in range(38):
            x,y=rng.randrange(30),rng.randrange(28)
            d.line([(x,y),(x+1,y+2),(x,y+3)],fill=rng.choice([dark,light]))
    elif style=='cord':
        for x in range(-32,32,5):d.line((x,0,x+32,32),fill=dark);d.line((x+1,0,x+33,32),fill=light)
    elif style=='food':
        for _ in range(20):
            x,y=rng.randrange(30),rng.randrange(30)
            d.line((x,y,x+1,y),fill=rng.choice([light,dark]))
    elif style=='ember':
        for x in range(2,32,7):d.line([(x,0),(x+1,10),(x,21),(x+2,31)],fill=light,width=2)
    im.save(TEXTURES/(name+'.png'))


def save(name,e):
    keys=sorted({f['texture'][1:] for el in e for f in el['faces'].values()})
    textures={k:'arksurvivalreturns:block/prehistoric/'+k for k in keys}
    textures['particle']=textures[keys[0]]
    data=dict(parent='minecraft:block/block',textures=textures,display=DISPLAY,elements=e)
    MODEL_DATA[name]=data
    (MODELS/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
    return e


def ring(radius, thick, low, high, tex, name):
    """Eight overlapping tangent walls; native rotations only."""
    e=[];d=radius-thick/2;length=2*d*math.tan(math.pi/8)+thick*.48
    for i in range(8):
        angle=i*math.pi/4;x=8+d*math.sin(angle);z=8+d*math.cos(angle)
        if i%2:
            rotation=rot([x,low,z],'y',45 if i in (1,5) else -45)
            e.append(box([x-length/2,low,z-thick/2],[x+length/2,high,z+thick/2],tex,name,rotation))
        elif i in (0,4):e.append(box([x-length/2,low,z-thick/2],[x+length/2,high,z+thick/2],tex,name))
        else:e.append(box([x-thick/2,low,z-length/2],[x+thick/2,high,z+length/2],tex,name))
    return e


def mortar():
    base=[box([4.4,0,5.3],[11.6,.8,10.7],'mortar','rounded foot'),
          box([5.3,0,4.4],[10.7,.8,11.6],'mortar','rounded foot'),
          box([3.5,.8,5],[12.5,1.5,11],'mortar','lower bowl'),
          box([5,.8,3.5],[11,1.5,12.5],'mortar','lower bowl'),
          box([4.3,1.4,4.3],[11.7,2.2,11.7],'mortar','solid grinding floor')]
    base+=ring(4.9,1.2,1.3,4.3,'mortar','stone vessel wall')
    base+=ring(5.1,1.1,4.3,4.95,'mortar_rim','worn lip')
    base+=ring(5.02,.22,2.45,2.9,'cord','woven grip band')
    def pestle(stage):
        origin=[8.5,2.3,8.6];tilt=[-22.5,0,22.5][stage]
        return [box([7.75,2.3,7.85],[9.25,4.0,9.35],'stone_dark','pestle grinding head',rot(origin,'z',tilt)),
                box([8.1,3.6,8.2],[8.9,7.5,9.0],'wood','pestle handle',rot(origin,'z',tilt)),
                box([7.95,6.65,8.05],[9.05,7.7,9.15],'wood_cut','rounded palm grip',rot(origin,'z',tilt))]
    save('mortar_empty',base+pestle(0))
    for food in ('berry','meat'):
        for stage,label in enumerate(('whole','crushed','paste')):
            tex=(['berry_whole','berry_crush','berry_paste'] if food=='berry' else ['meat_raw','meat_crush','meat_paste'])[stage]
            fill=[]
            if stage==0:
                for j,(x,z) in enumerate(((5.25,6),(6.7,5.25),(9.4,5.8),(5.65,8.2),(6.7,9.7),(9.55,9.8))):
                    size=1.2 if food=='berry' else 1.55
                    fill.append(box([x,2.2,z],[x+size,3.4+j%2*.45,z+size],tex,'whole ingredient'))
                    if food=='berry':fill.append(box([x+.25,3.42+j%2*.45,z+.2],[x+.65,3.56+j%2*.45,z+.6],'leaf','berry stem'))
            else:
                fill.append(box([5.15,2.2,5.15],[10.85,2.65 if stage==1 else 2.45,10.85],tex,'ground ingredient bed'))
                for j,(x,z) in enumerate(((5.6,6),(7.7,5.5),(9.4,7),(6.3,9.6),(9,9.6))):
                    fill.append(box([x,2.45,z],[x+(.9 if stage==1 else .55),3.05 if stage==1 else 2.61,z+.65],tex,'remaining fragments'))
            save(f'mortar_{food}_{label}',base+fill+pestle(stage))


def bedroll():
    e=[box([2,.08,.25],[14,.65,15.75],'woven','woven reed foundation')]
    for j in range(15):
        x=2.1+j*.78
        e.append(box([x,.3,.12+(j%3)*.12],[x+.58,1.12,15.85-(j%2)*.12],
                     'reed_light' if j%3==0 else 'reed','bundled grass reed'))
    for z in (1.05,4.7,8.4,12.1,14.9):
        e.append(box([2.06,.8,z],[13.96,1.26,z+.28],'cord','woven cross binding'))
    # The headrest is rolled grass; the hide is an irregular soft cover, never a frame.
    e += [box([2.65,1.12,.65],[13.35,2.75,3.45],'reed','rolled grass headrest'),
          box([3.25,2.75,1.05],[12.75,3.35,3.02],'reed_light','rounded reed crest'),
          box([3.05,1.22,4.45],[12.95,1.75,14.4],'hide','fur hide backing'),
          box([3.15,1.75,4.6],[12.7,2.12,13.75],'fur','warm fur cover'),
          box([3.55,2.12,5.05],[12.25,2.38,12.9],'fur_light','soft fur crown'),
          box([3.7,2.1,4.35],[11.7,2.62,5.8],'fur_light','folded fur cuff')]
    for j in range(8):
        z=5.7+j*.95
        for x in (2.8+(j%3)*.2,12.5-(j%2)*.25):
            e.append(box([x,1.55,z],[x+.65,2.14,z+.56],'fur','uneven fur edge'))
    for j in range(10):
        x=2.4+j*1.12
        for z in (.02,15.65):e.append(box([x,.24,z],[x+.26,.9,min(16,z+.35)],'reed_light','frayed grass end'))
    save('reed_bedroll',e)


def fire():
    e=[box([3.6,0,3.6],[12.4,.5,12.4],'ash','shallow hearth')]
    for level in range(2):
        for i in range(8):
            a=i*math.pi/4
            x=8+5.85*math.sin(a);z=8+5.85*math.cos(a)
            # Stones are staggered tangentially in the second course.
            x+=.28*level*math.cos(a);z-=.28*level*math.sin(a)
            tex=['stone','stone_light','stone_dark'][(i+level)%3]
            top=1.65 if level==0 else 3.35
            stone=box([x-1.8,level*1.65,z-1.16],[x+1.8,top,z+1.16],tex,'stacked hearth stone')
            if i%2:stone['rotation']=rot([x,0,z],'y',45 if i in (1,5) else -45)
            elif i in (2,6):stone=box([x-1.16,level*1.65,z-1.8],[x+1.16,top,z+1.8],tex,'stacked hearth stone')
            e.append(stone)
    logs=[]
    for z in (5.2,8.5):
        logs += [box([4.4,.55,z],[11.6,1.75,z+1.45],'wood','split fuel log'),
                 box([4.35,.65,z+.15],[4.5,1.62,z+1.28],'wood_cut','cut log end'),
                 box([5,1.7,z+.15],[11,1.94,z+1.22],'char','charred log crown')]
    coals=[box([5.6,.55,6.65],[10.4,1.15,8.4],'ember','glowing coal bed')]
    flames=[]
    for j,(x,z) in enumerate(((6,6.7),(8.6,7.4),(7.5,9))):
        flames += [box([x,1.3,z],[x+1.3,2.55,z+.6],'flame','low flame'),
                   box([x+.35,2.5,z+.06],[x+.96,3.3-j*.15,z+.55],'flame_light','flame tip')]
    spit=[]
    for x in (1.85,13.5):
        spit += [box([x,2.3,7.65],[x+.65,7.4,8.35],'wood','forked spit upright'),
                 box([x-.2,6.1,7.35],[x+.86,6.8,8.6],'cord','support binding')]
    spit += [box([1.6,7.08,7.68],[14.4,7.58,8.18],'wood_cut','removable roasting spit'),
             box([14.25,6.35,7.65],[14.8,7.6,8.22],'wood','turning handle')]
    save('stone_fire_empty',e+spit)
    save('stone_fire_fueled',e+logs+spit)
    save('stone_fire_lit',e+logs+coals+flames+spit)
    save('stone_fire_pot_base',e+logs+coals+flames)
    for stage in ('raw','seared','cooked'):
        meat=[];shrink={'raw':0,'seared':.12,'cooked':.24}[stage]
        for j,x in enumerate((5.0,8.7)):
            meat += [box([x+shrink,5.85+shrink,6.8+shrink],[x+2.5-shrink,7.5-shrink,9.1-shrink],
                         'meat_'+stage,'meat on spit'),
                     box([x+.3+shrink,5.45+shrink,7.05+shrink],[x+2.2-shrink,5.85+shrink,8.85-shrink],
                         'meat_'+stage,'rounded lower cut'),
                     box([x+.3+shrink,7.5-shrink,7.05+shrink],[x+2.2-shrink,7.85-shrink,8.85-shrink],
                         'meat_'+stage,'rounded upper cut')]
            tex='fat' if stage=='raw' else 'crust'
            for yy in (6.2,6.8):
                meat.append(box([x+.35+shrink,yy,9.1-shrink],[x+2.1-shrink,yy+.13,9.15-shrink],tex,'fat / roasting marks'))
        save('stone_fire_'+stage,e+logs+coals+flames+spit+meat)
    return e+logs+coals+flames


def pot(fire_base):
    e=[]
    for x,z in ((3.9,4.3),(11.05,4.3),(7.45,12.1)):
        e.append(box([x,0,z],[x+1.05,2,z+1.05],'soot','short clay foot'))
    e += [box([4,1,5],[12,2,11],'soot','sooted base'),box([5,1,4],[11,2,12],'soot','sooted base')]
    e+=ring(4.7,1.15,1.5,3,'soot','heat blackened lower wall')
    e+=ring(5.1,1.05,3,6.65,'clay','hand built clay wall')
    e+=ring(5.28,1.15,6.65,7.65,'clay_light','rolled clay lip')
    # Visible inside floor and generous cavity; no solid cube interior.
    e.append(box([4.9,1.95,4.9],[11.1,2.08,11.1],'clay_inner','visible inner floor'))
    for x in (.5,14.3):
        e += [box([x,5.05,6.25],[x+1.2,5.85,9.75],'clay_light','open loop handle'),
              box([x+1.2 if x<8 else 12.25,5.05,6.25],[3.75 if x<8 else x,5.85,7.02],'clay','handle root'),
              box([x+1.2 if x<8 else 12.25,5.05,8.98],[3.75 if x<8 else x,5.85,9.75],'clay','handle root')]
    # Small stamped bands keep the earthenware warm and handmade.
    for z in (2.88,13.03):
        for x in (5.3,7.5,9.7):e.append(box([x,5.5,z],[x+.65,5.95,z+.1],'clay_light','pressed rim decoration'))
    save('clay_pot',e)
    stew=[box([4.15,5.5,4.15],[11.85,5.7,11.85],'broth','simmering broth')]
    for x,z in ((5.3,6),(9.25,8.2),(7.1,9.4)):
        stew += [box([x,5.7,z],[x+1.2,6.02,z+.9],'meat_cooked','stew portion'),
                 box([x-.2,5.71,z+1],[x+.7,5.84,z+1.4],'leaf','floating herbs')]
    save('clay_pot_stew',e+stew)
    for name,parts in [('pot_on_fire',e),('pot_on_fire_stew',e+stew)]:
        mounted=copy.deepcopy(parts)
        # Feet meet the stone course; bowl floor clears all flame geometry.
        for el in mounted:
            el['from'][1]+=3.35;el['to'][1]+=3.35
            if 'rotation' in el:el['rotation']['origin'][1]+=3.35
        save(name,fire_base+mounted)


def render(name,size=512):
    identifier='arksurvivalreturns:block/prehistoric/'+name
    mesh=art.mesh(identifier,(0,0,0));v=np.concatenate([f[0] for f in mesh])
    view=np.array([.65,.63,1.]);view/=np.linalg.norm(view)
    right=np.cross([0,1,0],view);right/=np.linalg.norm(right);up=np.cross(view,right)
    projected=v@np.array([right,up]).T;lo,hi=projected.min(0),projected.max(0)
    center=(lo+hi)/2;target=right*center[0]+up*center[1]
    scale=.85*size/max(hi-lo)
    return art.render([(identifier,(0,0,0))],(size,size),scale,target)


def font(n,bold=False):return ImageFont.truetype('C:/Windows/Fonts/'+('georgiab.ttf' if bold else 'segoeui.ttf'),n)


def previews():
    art.ASSETS=ASSETS
    output=OUT/'orthographic';output.mkdir(exist_ok=True)
    rendered={}
    for name in MODEL_DATA:
        im=render(name);im.save(output/(name+'.png'))
        rendered[name]=im
    for name in ('mortar_empty','reed_bedroll','stone_fire_lit','clay_pot'):
        im=render(name,256);im.resize((64,64),Image.Resampling.LANCZOS).save(output/(name+'_64.png'))
    sheet=Image.new('RGB',(2000,1580),'#eee9df');d=ImageDraw.Draw(sheet)
    d.text((65,32),'THE FIRST HEARTH',font=font(46,True),fill='#394635')
    d.text((68,94),'ARK SURVIVAL RETURNS  /  Four native models, made for the Prehistoric camp',font=font(23),fill='#776c57')
    panels=[(45,150,'01  NARCOTRAFFIC','Stone mortar, palm-worn pestle, visible grinding.','mortar_berry_whole'),
            (1020,150,'02  GOODNIGHT','Grass reeds, woven bindings, a soft fur cover.','reed_bedroll'),
            (45,740,'03  A LITTLE WARMTH','Stacked stones, low embers, a removable spit.','stone_fire_raw'),
            (1020,740,'04  DELICIOUS','Slab-height clay pot. Set it directly on the hearth.','pot_on_fire_stew')]
    for x,y,title,caption,name in panels:
        d.rounded_rectangle((x,y,x+935,y+560),radius=18,fill='#faf7ee')
        d.text((x+25,y+20),title,font=font(28,True),fill='#46503a')
        d.text((x+25,y+65),caption,font=font(21),fill='#7e715b')
        im=rendered[name].resize((445,445),Image.Resampling.LANCZOS)
        sheet.paste(im,(x+70,y+103),im)
        if name.startswith('mortar'):
            for j,n in enumerate(('mortar_berry_crushed','mortar_berry_paste')):
                small=rendered[n].resize((220,220),Image.Resampling.LANCZOS);sheet.paste(small,(x+640,y+103+j*210),small)
                d.text((x+750,y+298+j*210),('CRUSHED','GROUND')[j],font=font(17),fill='#7e715b',anchor='mm')
        elif name=='reed_bedroll':
            d.text((x+610,y+237),'12 x 16 footprint',font=font(23,True),fill='#65704e')
            d.text((x+610,y+281),'No frame. No cloth.',font=font(22),fill='#7e715b')
            d.text((x+610,y+319),'Rolled grass headrest.',font=font(22),fill='#7e715b')
        elif name=='stone_fire_raw':
            for j,n in enumerate(('stone_fire_seared','stone_fire_cooked')):
                small=rendered[n].resize((220,220),Image.Resampling.LANCZOS);sheet.paste(small,(x+640,y+103+j*210),small)
                d.text((x+750,y+298+j*210),('SEARING','COOKED')[j],font=font(17),fill='#7e715b',anchor='mm')
        else:
            im=rendered['clay_pot'].resize((300,300),Image.Resampling.LANCZOS);sheet.paste(im,(x+600,y+170),im)
            d.text((x+750,y+477),'POT / STANDALONE ITEM',font=font(17),fill='#7e715b',anchor='mm')
    d.text((70,1340),'GRINDING STATES  /  meat: whole > crushed > ground',font=font(24,True),fill='#596048')
    for j,n in enumerate(('mortar_meat_whole','mortar_meat_crushed','mortar_meat_paste')):
        im=rendered[n].resize((190,190),Image.Resampling.LANCZOS);sheet.paste(im,(735+j*300,1320),im)
    d.text((70,1515),'Orthographic renders of exported geometry. Individual items fit within half-block height; pot + fire is one compact assembly.',font=font(21),fill='#7e715b')
    d.text((70,1547),'Asset pack only. State swaps, recipes, fuel and cooking logic are supplied as an integration guide, not active gameplay.',font=font(18),fill='#7e715b')
    sheet.save(OUT/'prehistoric-camp.png')


def validate():
    for name,data in MODEL_DATA.items():
        for el in data['elements']:
            assert all(a<b for a,b in zip(el['from'],el['to'])),(name,el['name'])
            if 'rotation' in el:assert el['rotation']['angle'] in (-45,-22.5,0,22.5,45)
            for face in el['faces'].values():
                key=face['texture'][1:]
                assert (ASSETS/'textures'/(data['textures'][key].split(':')[1]+'.png')).exists()
        verts=np.concatenate([f[0] for f in art.mesh('arksurvivalreturns:block/prehistoric/'+name,(0,0,0))])
        assert verts[:,1].min()>=-.001,(name,'below floor')
        limit=12 if name.startswith('pot_on_fire') else 8
        assert verts[:,1].max()<=limit,(name,verts[:,1].max())
    bed=MODEL_DATA['reed_bedroll']['elements']
    assert min(e['from'][0] for e in bed)>=2 and max(e['to'][0] for e in bed)<=14
    assert min(e['from'][2] for e in bed)>=0 and max(e['to'][2] for e in bed)<=16


def main():
    for path in (MODELS,TEXTURES,ASSETS/'models/item',ASSETS/'items'):path.mkdir(parents=True,exist_ok=True)
    for name,(base,style) in PALETTE.items():material(name,base,style)
    mortar();bedroll();base=fire();pot(base)
    art.ASSETS=ASSETS
    validate()
    for name in ('mortar_empty','reed_bedroll','stone_fire_lit','clay_pot'):
        (ASSETS/'models/item'/(name+'.json')).write_text(json.dumps({'parent':'arksurvivalreturns:block/prehistoric/'+name},indent=2)+'\n',encoding='utf-8')
        (ASSETS/'items'/(name+'.json')).write_text(json.dumps({'model':{'type':'minecraft:model','model':'arksurvivalreturns:item/'+name}},indent=2)+'\n',encoding='utf-8')
    previews()
    manifest={'scope':'Assets only; no runtime integration','coordinates':'16 model units per block',
              'main_models':['mortar_empty','reed_bedroll','stone_fire_lit','clay_pot'],
              'all_models':list(MODEL_DATA),'materials':list(PALETTE),
              'grinding':{'berries':['mortar_berry_whole','mortar_berry_crushed','mortar_berry_paste'],
                          'meat':['mortar_meat_whole','mortar_meat_crushed','mortar_meat_paste']},
              'cooking':['stone_fire_empty','stone_fire_fueled','stone_fire_lit','stone_fire_raw','stone_fire_seared','stone_fire_cooked'],
              'pot':['clay_pot','clay_pot_stew','pot_on_fire','pot_on_fire_stew'],
              'placement':{'pot_mount_y':3.35,'remove_spit_when_pot_added':True,'combined_height':11.0},
              'recipe_intent':'New stone fire replaces the furnace role and uses the same furnace recipe; no recipe files installed.'}
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    with ZipFile(OUT/'prehistoric-camp-assets.zip','w',ZIP_DEFLATED) as z:
        for p in sorted(OUT.rglob('*')):
            if p.is_file() and p.suffix!='.zip':z.write(p,p.relative_to(OUT))
    print(f'Validated/exported {len(MODEL_DATA)} models, {len(PALETTE)} textures, 4 item definitions and orthographic PNGs. {OUT}')


if __name__=='__main__':main()
