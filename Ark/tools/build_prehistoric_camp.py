"""Author the Prehistoric camp models: the design pack plus the in-game copies.

Run: python tools/build_prehistoric_camp.py
Requires Pillow and numpy. Geometry is native Minecraft / Blockbench JSON.
Writes the design pack (design/prehistoric-camp) and installs the in-game models (stone fire, Primitive
Bedroll, Primitive Forge) with their textures into src/main/resources. ArkData wires states and items.
"""
import shutil
import copy
import json
import math
import random
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from build_camp_assets import box, rot, lift, split, DISPLAY, TALL_DISPLAY
import render_camp_assets as art

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'design/prehistoric-camp'
ASSETS = OUT / 'assets/arksurvivalreturns'
MODELS = ASSETS / 'models/block/prehistoric'
TEXTURES = ASSETS / 'textures/block/prehistoric'
GAME = ROOT / 'src/main/resources/assets/arksurvivalreturns'
# Models used by the game; everything else stays a design asset.
INSTALL = ('stone_fire_', 'primitive_bedroll_', 'primitive_forge_')
# Two-block objects are rendered from their halves.
ASSEMBLIES = {'primitive_bedroll': [('primitive_bedroll_head', (0, 0, 0)), ('primitive_bedroll_foot', (0, 0, 16))],
              'primitive_forge': [('primitive_forge_lower', (0, 0, 0)), ('primitive_forge_upper', (0, 16, 0))],
              'primitive_forge_lit': [('primitive_forge_lit_lower', (0, 0, 0)), ('primitive_forge_lit_upper', (0, 16, 0))]}
COURSES = (0, 1.7, 3.4, 5.1)  # three stacked courses of hearth stones
SPIT_LIFT = 1.8  # the spit rests one course higher than the original two-course hearth
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


def save(name,e,display=DISPLAY):
    keys=sorted({f['texture'][1:] for el in e for f in el['faces'].values()})
    textures={k:'arksurvivalreturns:block/prehistoric/'+k for k in keys}
    textures['particle']=textures[keys[0]]
    data=dict(parent='minecraft:block/block',textures=textures,display=display,elements=e)
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
    """Primitive Bedroll: grass reeds, woven bindings and a fur cover, as long as a bed (two blocks) and flat on the ground."""
    e=[box([1,.08,.25],[15,.65,31.75],'woven','woven reed foundation')]
    for j in range(18):
        x=1.1+j*.765
        e.append(box([x,.3,.12+(j%3)*.12],[x+.58,1.12,31.85-(j%2)*.12],
                     'reed_light' if j%3==0 else 'reed','bundled grass reed'))
    for z in (1.05,5.4,10.2,15.0,19.8,24.6,29.2,30.9):
        e.append(box([1.06,.8,z],[14.96,1.26,z+.28],'cord','woven cross binding'))
    # The headrest is rolled grass; the hide is an irregular soft cover, never a frame.
    e += [box([1.65,1.12,.65],[14.35,2.95,4.3],'reed','rolled grass headrest'),
          box([2.25,2.95,1.05],[13.75,3.6,3.85],'reed_light','rounded reed crest'),
          box([1.35,1.2,.95],[1.65,2.7,4],'reed_dark','twisted headrest end'),
          box([14.35,1.2,.95],[14.65,2.7,4],'reed_dark','twisted headrest end'),
          box([2.05,1.22,5.3],[13.95,1.75,30.2],'hide','fur hide backing'),
          box([2.15,1.75,5.5],[13.7,2.12,29.4],'fur','warm fur cover'),
          box([2.55,2.12,6.1],[13.25,2.38,28.6],'fur_light','soft fur crown'),
          box([2.7,2.1,5.2],[13.3,2.75,7.1],'fur_light','folded fur cuff')]
    for j in range(17):
        z=6.9+j*1.35
        for x in (1.8+(j%3)*.2,13.5-(j%2)*.25):
            e.append(box([x,1.55,z],[x+.65,2.14,z+.56],'fur','uneven fur edge'))
    for j in range(12):
        x=1.4+j*1.12
        for z in (.02,31.63):e.append(box([x,.24,z],[x+.26,.9,min(32,z+.35)],'reed_light','frayed grass end'))
    head,foot=split(e,'z')
    save('primitive_bedroll_head',head)
    save('primitive_bedroll_foot',foot)
    # Inventory: the mat rolled up and tied with fiber.
    r=[box([2,4,5],[14,10,11],'reed'),box([2,5,4],[14,9,12],'reed'),box([2,3.6,6],[14,10.4,10],'reed_light')]
    for x in (1.95,13.9):
        r+=[box([x,5,5.4],[x+.15,9,10.6],'woven','rolled end'),
            box([x-.03,6,6.3],[x+.18,8.3,9.5],'fur','fur spiral'),
            box([x-.06,6.8,7],[x+.21,7.7,8.8],'hide','hide core')]
    for x in (4,10.8):
        r+=[box([x,3.4,5.8],[x+1.2,10.6,10.2],'cord','fiber tie'),box([x,4.8,3.8],[x+1.2,9.2,12.2],'cord','fiber tie')]
    for y,z in ((5.2,5.1),(8.1,4.6),(9.6,7.4),(6.7,11.2),(4.3,8.6)):
        for x in (1.35,14.1):r.append(box([x,y,z],[x+.55,y+.3,z+.3],'reed_light','frayed grass end'))
    r.append(box([5.5,10.6,7.4],[10.5,11.3,8.5],'cord','carrying cord'))
    save('primitive_bedroll_rolled',r)


def forge():
    """Primitive Forge: a two-block clay bloomery on a stone plinth. The mouth is the north (front) face."""
    body=[box([.5,0,.5],[15.5,2,15.5],'stone_dark','stone plinth'),
          box([2.5,2,2.5],[13.5,3.5,13.5],'stone_dark','plinth fill')]
    course=(([.5,2,.5],[8,3.5,2.5]),([8,2,.5],[15.5,3.5,2.5]),([.5,2,13.5],[7,3.5,15.5]),([7,2,13.5],[15.5,3.5,15.5]),
            ([.5,2,2.5],[2.5,3.5,8.5]),([.5,2,8.5],[2.5,3.5,13.5]),([13.5,2,2.5],[15.5,3.5,9]),([13.5,2,9],[15.5,3.5,13.5]))
    for i,(a,b) in enumerate(course):body.append(box(a,b,['stone','stone_light','stone'][i%3],'plinth course stone'))
    # Chamber walls around the open mouth; the inside is sooted.
    body += [box([1.5,3.5,12.5],[14.5,14,14.5],'clay','back wall'),
             box([1.5,3.5,1.5],[3.5,14,12.5],'clay','side wall'),
             box([12.5,3.5,1.5],[14.5,14,12.5],'clay','side wall'),
             box([3.5,3.5,1.5],[5,14,3.5],'clay','mouth jamb'),
             box([11,3.5,1.5],[12.5,14,3.5],'clay','mouth jamb'),
             box([5,10,1.5],[11,14,3.5],'clay','mouth crown'),
             box([4.5,9.6,1],[11.5,10.6,2],'stone_light','lintel stone'),
             box([4.4,3.5,.6],[11.6,4.1,1.6],'stone_dark','ash lip'),
             box([3.5,12.8,3.5],[12.5,14,12.5],'soot','chamber roof'),
             box([3.5,4,3.5],[3.7,12.8,12.2],'soot','sooted inner wall'),
             box([12.3,4,3.5],[12.5,12.8,12.2],'soot','sooted inner wall'),
             box([3.5,3.5,3.5],[12.5,4,12.5],'ash','hearth floor')]
    # The stack tapers into a short open chimney.
    body += [box([1,13.5,1],[15,15,15],'clay_light','rolled clay band'),
             box([2.5,15,2.5],[13.5,25,13.5],'clay','clay stack'),
             box([2,24.5,2],[14,26,14],'clay_light','rolled clay band'),
             box([3.8,26,3.8],[12.2,29,12.2],'clay','shoulder'),
             box([5,29,5],[11,32,6.3],'clay_light','chimney wall'),
             box([5,29,9.7],[11,32,11],'clay_light','chimney wall'),
             box([5,29,6.3],[6.3,32,9.7],'clay_light','chimney wall'),
             box([9.7,29,6.3],[11,32,9.7],'clay_light','chimney wall')]
    # Tuyere: the bellows pipe enters the east wall. Stones pressed into the clay keep it handmade.
    body += [box([14.5,5.5,6.9],[16,7,9.1],'clay_light','tuyere pipe'),
             box([14.9,5.3,6.7],[15.3,7.2,9.3],'cord','tuyere binding')]
    for x,y,z,axis in ((1.3,6,5,'x'),(1.3,10,9.3,'x'),(14.45,10.8,4.2,'x'),(3.5,18,2.3,'z'),(9.8,21,2.3,'z'),
                       (6,17.5,13.45,'z'),(2.3,20,8,'x'),(13.45,17,10,'x')):
        size=[.25,1.2,1.4] if axis=='x' else [1.4,1.2,.25]
        body.append(box([x,y,z],[x+size[0],y+size[1],z+size[2]],'stone_light','stone pressed into the clay'))
    for lit in (False,True):
        if lit:
            fuel=[box([3.7,4,12.2],[12.3,12.8,12.5],'ember','glowing back wall'),
                  box([4.6,4,5],[11.4,5,10.5],'ember','glowing charcoal'),
                  box([5.4,5,6],[6.8,7.4,6.8],'flame','flame'),box([8.6,5,7.2],[10,8.3,8],'flame','flame'),
                  box([7,5,5.2],[8.2,6.6,5.9],'flame_light','flame'),
                  box([5.7,7.4,6.1],[6.5,8.4,6.7],'flame_light','flame tip'),box([8.9,8.3,7.3],[9.7,9.2,7.9],'flame_light','flame tip'),
                  box([6.3,29,6.3],[9.7,29.2,9.7],'ember','glowing throat')]
        else:
            fuel=[box([3.7,4,12.2],[12.3,12.8,12.5],'soot','cold back wall'),
                  box([4.8,4,5.5],[11.2,5.2,7],'char','charcoal log'),box([5.3,4,8],[10.7,5.2,9.5],'wood','split log'),
                  box([6.3,29,6.3],[9.7,29.2,9.7],'soot','cold throat')]
        lower,upper=split(body+fuel)
        name='primitive_forge'+('_lit' if lit else '')
        save(name+'_lower',lower)
        save(name+'_upper',upper)
        if not lit:save('primitive_forge_item',lower+lift(upper,'y',16),TALL_DISPLAY)


def fire():
    e=[box([3.4,0,3.4],[12.6,.5,12.6],'ash','shallow hearth')]
    for level in range(3):
        low,top=COURSES[level],COURSES[level+1]
        for i in range(8):
            a=i*math.pi/4
            x=8+6*math.sin(a);z=8+6*math.cos(a)
            # Courses are staggered tangentially and lean in slightly as they rise.
            x+=.3*(level%2)*math.cos(a)-.25*level*math.sin(a);z+=-.3*(level%2)*math.sin(a)-.25*level*math.cos(a)
            tex=['stone','stone_light','stone_dark'][(i+level)%3]
            w,d=1.9-.1*level,1.2
            stone=box([x-w,low,z-d],[x+w,top,z+d],tex,'stacked hearth stone')
            if i%2:stone['rotation']=rot([x,0,z],'y',45 if i in (1,5) else -45)
            elif i in (2,6):stone=box([x-d,low,z-w],[x+d,top,z+w],tex,'stacked hearth stone')
            e.append(stone)
    logs=[]
    for z in (5.2,8.5):
        logs += [box([4.4,.55,z],[11.6,1.75,z+1.45],'wood','split fuel log'),
                 box([4.35,.65,z+.15],[4.5,1.62,z+1.28],'wood_cut','cut log end'),
                 box([5,1.7,z+.15],[11,1.94,z+1.22],'char','charred log crown')]
    coals=[box([5.6,.55,6.65],[10.4,1.15,8.4],'ember','glowing coal bed')]
    flames=[]
    for j,(x,z) in enumerate(((6,6.7),(8.6,7.4),(7.3,9),(9.3,5.8))):
        h=(3.1,3.5,2.9,2.6)[j]
        flames += [box([x,1.3,z],[x+1.3,1.3+h,z+.6],'flame','flame'),
                   box([x+.35,1.2+h,z+.06],[x+.96,2.3+h,z+.55],'flame_light','flame tip')]
    top=COURSES[-1];spit=[]
    for x in (1.85,13.5):
        spit += [box([x,top,7.65],[x+.65,7.4+SPIT_LIFT,8.35],'wood','forked spit upright'),
                 box([x-.2,6.1+SPIT_LIFT,7.35],[x+.86,6.8+SPIT_LIFT,8.6],'cord','support binding')]
    spit += [box([1.6,7.08+SPIT_LIFT,7.68],[14.4,7.58+SPIT_LIFT,8.18],'wood_cut','removable roasting spit'),
             box([14.25,6.35+SPIT_LIFT,7.65],[14.8,7.6+SPIT_LIFT,8.22],'wood','turning handle')]
    save('stone_fire_empty',e+spit)
    save('stone_fire_fueled',e+logs+spit)
    save('stone_fire_lit',e+logs+coals+flames+spit)
    save('stone_fire_pot_base',e+logs+coals+flames)
    for stage in ('raw','seared','cooked'):
        meat=[];shrink={'raw':0,'seared':.12,'cooked':.24}[stage];up=SPIT_LIFT
        for j,x in enumerate((5.0,8.7)):
            meat += [box([x+shrink,5.85+up+shrink,6.8+shrink],[x+2.5-shrink,7.5+up-shrink,9.1-shrink],
                         'meat_'+stage,'meat on spit'),
                     box([x+.3+shrink,5.45+up+shrink,7.05+shrink],[x+2.2-shrink,5.85+up+shrink,8.85-shrink],
                         'meat_'+stage,'rounded lower cut'),
                     box([x+.3+shrink,7.5+up-shrink,7.05+shrink],[x+2.2-shrink,7.85+up-shrink,8.85-shrink],
                         'meat_'+stage,'rounded upper cut')]
            tex='fat' if stage=='raw' else 'crust'
            for yy in (6.2+up,6.8+up):
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
        # Feet meet the top stone course; bowl floor clears all flame geometry.
        for el in mounted:
            el['from'][1]+=COURSES[-1];el['to'][1]+=COURSES[-1]
            if 'rotation' in el:el['rotation']['origin'][1]+=COURSES[-1]
        save(name,fire_base+mounted)


def render(name,size=512):
    parts=[('arksurvivalreturns:block/prehistoric/'+n,o) for n,o in ASSEMBLIES.get(name,[(name,(0,0,0))])]
    v=np.concatenate([f[0] for identifier,offset in parts for f in art.mesh(identifier,offset)])
    view=np.array([.65,.63,1.]);view/=np.linalg.norm(view)
    right=np.cross([0,1,0],view);right/=np.linalg.norm(right);up=np.cross(view,right)
    projected=v@np.array([right,up]).T;lo,hi=projected.min(0),projected.max(0)
    center=(lo+hi)/2;target=right*center[0]+up*center[1]
    scale=.85*size/max(hi-lo)
    return art.render(parts,(size,size),scale,target)


def turned(elements):
    """The same geometry turned 180 degrees about y, so previews can show the front of north-facing models."""
    out=[]
    for el in elements:
        a,b=el['from'],el['to'];r=copy.deepcopy(el.get('rotation'))
        if r:r['origin']=[16-r['origin'][0],r['origin'][1],16-r['origin'][2]]
        out.append(box([16-b[0],a[1],16-b[2]],[16-a[0],b[1],16-a[2]],next(iter(el['faces'].values()))['texture'][1:],el['name'],r))
    return out


def front_views():
    """Preview-only front renders of the forge; the temporary models never reach the pack."""
    views={}
    for name in ('primitive_forge','primitive_forge_lit'):
        parts=[]
        for half,offset in (('lower',(0,0,0)),('upper',(0,16,0))):
            temp=f'_front_{name}_{half}';data=copy.deepcopy(MODEL_DATA[f'{name}_{half}'])
            data['elements']=turned(data['elements'])
            (MODELS/(temp+'.json')).write_text(json.dumps(data),encoding='utf-8');parts.append((temp,offset))
        ASSEMBLIES[name+'_front']=parts
        views[name+'_front']=render(name+'_front')
        for temp,_ in parts:(MODELS/(temp+'.json')).unlink()
        del ASSEMBLIES[name+'_front']
    return views


def font(n,bold=False):return ImageFont.truetype('C:/Windows/Fonts/'+('georgiab.ttf' if bold else 'segoeui.ttf'),n)


def previews():
    art.ASSETS=ASSETS
    output=OUT/'orthographic';output.mkdir(exist_ok=True)
    rendered={}
    for stale in output.glob('*.png'):stale.unlink()
    for name in [*MODEL_DATA,*ASSEMBLIES]:
        im=render(name);im.save(output/(name+'.png'))
        rendered[name]=im
    for name,im in front_views().items():
        im.save(output/(name+'.png'));rendered[name]=im
    for name in ('mortar_empty','primitive_bedroll','stone_fire_lit','clay_pot'):
        im=render(name,256);im.resize((64,64),Image.Resampling.LANCZOS).save(output/(name+'_64.png'))
    sheet=Image.new('RGB',(2000,1580),'#eee9df');d=ImageDraw.Draw(sheet)
    d.text((65,32),'THE FIRST HEARTH',font=font(46,True),fill='#394635')
    d.text((68,94),'ARK SURVIVAL RETURNS  /  Four native models, made for the Prehistoric camp',font=font(23),fill='#776c57')
    panels=[(45,150,'01  NARCOTRAFFIC','Stone mortar, palm-worn pestle, visible grinding.','mortar_berry_whole'),
            (1020,150,'02  GOODNIGHT','Primitive Bedroll: grass reeds, woven bindings, a soft fur cover.','primitive_bedroll'),
            (45,740,'03  A LITTLE WARMTH','Three courses of stones, embers, a removable spit.','stone_fire_raw'),
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
        elif name=='primitive_bedroll':
            small=rendered['primitive_bedroll_rolled'].resize((170,170),Image.Resampling.LANCZOS);sheet.paste(small,(x+700,y+360),small)
            d.text((x+610,y+237),'Two blocks, like a bed',font=font(23,True),fill='#65704e')
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
    d.text((70,1515),'Orthographic renders of exported geometry. The stone fire, Primitive Bedroll and Primitive Forge are installed in game.',font=font(21),fill='#7e715b')
    d.text((70,1547),'The mortar and the clay pot remain design assets (B02).',font=font(18),fill='#7e715b')
    sheet.save(OUT/'prehistoric-camp.png')


def validate():
    for name,data in MODEL_DATA.items():
        for el in data['elements']:
            assert all(a<b for a,b in zip(el['from'],el['to'])),(name,el['name'])
            if 'rotation' in el:assert el['rotation']['angle'] in (-45,-22.5,0,22.5,45)
            for face in el['faces'].values():
                key=face['texture'][1:]
                assert (ASSETS/'textures'/(data['textures'][key].split(':')[1]+'.png')).exists()
                assert all(0<=c<=16 for c in face['uv']),(name,el['name'],'UV leaves the texture')
        verts=np.concatenate([f[0] for f in art.mesh('arksurvivalreturns:block/prehistoric/'+name,(0,0,0))])
        assert verts[:,1].min()>=-.001,(name,'below floor')
        assert verts[:,[0,2]].min()>=-.001 and verts[:,[0,2]].max()<=16.001,(name,'leaves its block')
        limit=32 if name=='primitive_forge_item' else 13 if name.startswith('pot_on_fire') else 10 if name.startswith('stone_fire') \
            else 16 if name.startswith('primitive_') else 8
        assert verts[:,1].max()<=limit,(name,verts[:,1].max())


def install():
    """Copy the in-game models and every texture they use into the mod resources."""
    models=GAME/'models/block/prehistoric';textures=GAME/'textures/block/prehistoric'
    models.mkdir(parents=True,exist_ok=True);textures.mkdir(parents=True,exist_ok=True)
    for stale in models.glob('*.json'):stale.unlink()
    used=set()
    for name,data in MODEL_DATA.items():
        if not name.startswith(INSTALL):continue
        shutil.copyfile(MODELS/(name+'.json'),models/(name+'.json'))
        used|={v.split('/')[-1] for v in data['textures'].values()}
    for key in sorted(used):shutil.copyfile(TEXTURES/(key+'.png'),textures/(key+'.png'))
    return len(list(models.glob('*.json')))


def main():
    for path in (MODELS,TEXTURES,ASSETS/'models/item',ASSETS/'items'):
        path.mkdir(parents=True,exist_ok=True)
        if path!=TEXTURES:
            for stale in path.glob('*.json'):stale.unlink()
    for name,(base,style) in PALETTE.items():material(name,base,style)
    mortar();bedroll();forge();base=fire();pot(base)
    art.ASSETS=ASSETS
    validate()
    for name in ('mortar_empty','primitive_bedroll_rolled','stone_fire_lit','clay_pot'):
        (ASSETS/'models/item'/(name+'.json')).write_text(json.dumps({'parent':'arksurvivalreturns:block/prehistoric/'+name},indent=2)+'\n',encoding='utf-8')
        (ASSETS/'items'/(name+'.json')).write_text(json.dumps({'model':{'type':'minecraft:model','model':'arksurvivalreturns:item/'+name}},indent=2)+'\n',encoding='utf-8')
    previews()
    manifest={'scope':'Design pack; the stone fire, Primitive Bedroll and Primitive Forge are installed in game','coordinates':'16 model units per block',
              'main_models':['mortar_empty','primitive_bedroll','stone_fire_lit','clay_pot','primitive_forge'],
              'two_block':{name:[part for part,_ in parts] for name,parts in ASSEMBLIES.items()},
              'all_models':list(MODEL_DATA),'materials':list(PALETTE),
              'grinding':{'berries':['mortar_berry_whole','mortar_berry_crushed','mortar_berry_paste'],
                          'meat':['mortar_meat_whole','mortar_meat_crushed','mortar_meat_paste']},
              'cooking':['stone_fire_empty','stone_fire_fueled','stone_fire_lit','stone_fire_raw','stone_fire_seared','stone_fire_cooked'],
              'pot':['clay_pot','clay_pot_stew','pot_on_fire','pot_on_fire_stew'],
              'placement':{'pot_mount_y':COURSES[-1],'remove_spit_when_pot_added':True,'combined_height':COURSES[-1]+7.65}}
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    with ZipFile(OUT/'prehistoric-camp-assets.zip','w',ZIP_DEFLATED) as z:
        for p in sorted(OUT.rglob('*')):
            if p.is_file() and p.suffix!='.zip':z.write(p,p.relative_to(OUT))
    installed=install()
    print(f'Validated/exported {len(MODEL_DATA)} models, {len(PALETTE)} textures and orthographic PNGs to {OUT}; installed {installed} in-game models.')


if __name__=='__main__':main()
