"""Render the asset-only Prehistoric -> Bronze -> Iron progression proposal.
Run from Ark: python tools/build_technology_tree.py
Uses approved camp geometry and existing game sprites; no runtime edits.
"""
import io
import json
import math
from collections import Counter
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import render_camp_assets as art

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'design/technology-tree'
W, H = 8140, 1610
PAPER, INK, MUTED = '#f9f6ef', '#303e33', '#657060'
LANES = ['#698255', '#587b73', '#9b7453']
CARD_W, CARD_H, STEP = 380, 238, 455
ROWS = [445, 815, 1185]
AGES = [
    dict(id='prehistoric', name='PREHISTORIC', x=55, width=2800, start=110, cols=6, fill='#e8eedc', accent='#466652', shadow='#cbd2bd', lanes=['FOOD & PRESERVATION','COMPANIONS & TAMING','CAMP & COMFORT']),
    dict(id='bronze', name='BRONZE', x=2890, width=2345, start=2945, cols=5, fill='#f1dec6', accent='#875b38', shadow='#d6c1a7', lanes=['WORKING DINOSAURS','METAL & CRAFT','FIELD CHEMISTRY']),
    dict(id='iron', name='IRON', x=5270, width=2800, start=5325, cols=6, fill='#dfe8ed', accent='#485f72', shadow='#c0cdd5', lanes=['FORGE & PRECISION','HARDER / BETTER / FASTER / STRONGER','HOME & DEFENCE']),
]
NODES, EDGES = [], []

def add(age, col, lane, id, title, task, icon=None, kind='main', note=None):
    n=dict(id=id,title=title,task=task,age=age['id'],lane=lane,column=col,icon=icon,kind=kind,note=note,
           box=[age['start']+col*STEP,ROWS[lane],CARD_W,CARD_H])
    NODES.append(n)
    return id

# Tuples: identifier, exact achievement title, requirement, existing icon (if available).
PRE = [
 [('rock','Tha rock','Gather a rock.','stone'),('berries','Berries!','Collect all four berries.','berries'),('london','London','Craft a stone knife.',None),('lasting','Long-lasting','Dry your meat on a drying rack.','drying_rack')],
 [('fight','We should fight them','Deal damage to a dinosaur.',None),('ride','We should ride them','Craft a leash.','leash'),('companions','Companions','Tame any dinosaur.','parasaur'),('scavenge','Scavenger','Have a dinosaur gather resources.','harness')],
 [('mattress','Finally Goodnight','Craft a Mattress.',None),('warmth','A little warmth','Light a campfire.','campfire'),('prometheus','Prometheus','Light a torch in the campfire.',None),('dish','Delicious','Craft any dish in a cooking pot.','cooking_pot')],
]
BRONZE = [
 [('feed','Feed the Beast','Have your tame eat from a feeding trough.','trough'),('theri','Multi-tool','Tame a Therizinosaurus.','therizinosaurus'),('slavery','Slavery','Return with a hunt and a resource haul from your tames.','harness')],
 [('shiny','Shiny','Collect copper and tin.','copper'),('alloy','Better Together','Smelt your first bronze.',None),('weapon','Made to last','Craft your first bronze weapon.',None)],
 [('charcoal','Black Gold','Produce charcoal.','charcoal'),('minerals','Earths Secrets','Collect sulfur and saltpeter.',None),('sparklers','Sparklers','Craft gunpowder.','gunpowder')],
]
IRON = [
 [('forge','Hell Forge','Have a forging table.',None),('glass','I see you','Craft glass.',None),('trickshot','Trick-shot','Kill a carnivore with a scoped weapon.',None)],
 [('harder','Harder','Forge iron.','iron'),('better','Better','Craft your first iron weapon.',None),('faster','Faster','Have engineered boots.','boots'),('stronger','Stronger','Have a piece of iron armor.','armor')],
 [('home','Home Sweet Home','Sleep in a bed.','bedroll'),('vault','Vault it!','Store items in a reinforced vault.',None),('curtain','Iron-Curtain','Place 10 iron spikes.',None)],
]
STARTERS = [('monkeys','Monkeys','Gather stone.','stone'),('prepare','Prepare for it!','Embed an arrow with tranquilizer.','tranquilizer'),('greed','Greed','Have gunpowder.','gunpowder')]
FINALES = [('narcotics','Narcotraffic','Produce narcotics.','narcotics'),('rawr','Rawr!','Tame a Tyrannosaur.','tyrannosaur'),('subdue','Subdue Nature','Craft ammunition.',None)]
BONUSES = [('dried','Dried Meat III','Eat meat dried for three in-game days.','ration'),('golden','Golden Raptor Meat','Craft Golden Raptor Meat.',None),('cocaine','Cocaine','Gunpowder + narcotics + yellow berry.','white_dust')]
for i,(age,paths) in enumerate(zip(AGES,[PRE,BRONZE,IRON])):
    starter=add(age,0,1,*STARTERS[i],kind='starter',note='AGE STARTER')
    bonus=add(age,0,0,*BONUSES[i],kind='bonus',note='OPTIONAL BOOSTING FOOD')
    EDGES.append((starter,bonus))
    ends=[]
    for lane,path in enumerate(paths):
        previous=starter
        for col,entry in enumerate(path,1):
            current=add(age,col,lane,*entry)
            EDGES.append((previous,current));previous=current
        ends.append(previous)
    final=add(age,age['cols']-1,1,*FINALES[i],kind='finale',note='AGE FINALE / ALL 3 PATHS')
    EDGES.extend((end,final) for end in ends)
    if i: EDGES.append((FINALES[i-1][0],starter))
BY_ID={n['id']:n for n in NODES}
BY_ID['dried']['acquisition_requires']=['lasting']
BY_ID['golden']['acquisition_requires']=['prepare']
BY_ID['cocaine']['acquisition_requires']=['greed','narcotics','berries']

def font(size, bold=False, serif=False):
    name = ('georgiab.ttf' if bold else 'georgia.ttf') if serif else ('segoeuib.ttf' if bold else 'segoeui.ttf')
    return ImageFont.truetype('C:/Windows/Fonts/' + name, size)


def wrap(text, face, width):
    lines = []
    for paragraph in text.split('\n'):
        line = ''
        for word in paragraph.split():
            trial = (line + ' ' + word).strip()
            if face.getlength(trial) > width and line:
                lines.append(line); line = word
            else: line = trial
        lines.append(line)
    return lines


def block_icon(objects):
    view = np.array([.65, .63, 1.0]); view /= np.linalg.norm(view)
    right = np.cross([0, 1, 0], view); right /= np.linalg.norm(right)
    up = np.cross(view, right)
    vertices = np.concatenate([f[0] for name, offset in objects for f in art.mesh(name, offset)])
    projected = vertices @ np.array([right, up]).T
    lo, hi = projected.min(axis=0), projected.max(axis=0)
    center = (lo + hi) / 2
    target = right * center[0] + up * center[1]
    scale = .88 * min(320 / max(.01, hi[0]-lo[0]), 320 / max(.01, hi[1]-lo[1]))
    return art.render(objects, size=(320, 320), scale=scale, target=target)


def sprite(name, vanilla=True):
    if vanilla:
        with ZipFile(art.JAR) as z:
            image = Image.open(io.BytesIO(z.read(f'assets/minecraft/textures/item/{name}.png'))).convert('RGBA')
    else:
        image = Image.open(art.ASSETS / 'textures/item' / (name + '.png')).convert('RGBA')
    return image.crop(image.getbbox())


def tile(sprites, positions, size=(320, 320)):
    result = Image.new('RGBA', size)
    for im, (x, y, w, h) in zip(sprites, positions):
        factor = min(w / im.width, h / im.height)
        im = im.resize((max(1, round(im.width * factor)), max(1, round(im.height * factor))), Image.Resampling.NEAREST)
        result.alpha_composite(im, (x + (w-im.width)//2, y + (h-im.height)//2))
    return result


def icons():
    camp = lambda s: 'arksurvivalreturns:block/camp/' + s
    result = {}
    for name in ('bedroll', 'trough', 'drying_rack', 'cooking_pot'):
        model = 'trough_oak' if name == 'trough' else name
        objects = [(camp(model), (0, 0, 0))]
        if name == 'drying_rack': objects += [(camp(f'rack_meat_{i}'), (0, 0, 0)) for i in (1, 2, 3)]
        result[name] = block_icon(objects)
    result['ration'] = block_icon([(camp('rack_ready'), (0, 0, 0))])
    result['campfire'] = block_icon([('minecraft:block/campfire', (0, 0, 0))])
    result['stone'] = block_icon([('minecraft:block/cobblestone', (0, 0, 0))])
    result['camp'] = tile([result['bedroll'], result['campfire'], result['cooking_pot']],
                          [(0, 100, 180, 160), (90, 120, 180, 170), (190, 30, 120, 140)])
    result['berries'] = tile([sprite(b, False) for b in ('tintoberry', 'amarberry', 'azulberry', 'narcoberry')],
                            [(0,0,145,145),(165,0,145,145),(0,165,145,145),(165,165,145,145)])
    for key, name in [('parasaur','parasaur_spawn_egg'),('tyrannosaur','tyrannosaurus_spawn_egg'),('therizinosaurus','therizinosaurus_spawn_egg')]:
        result[key] = tile([sprite(name, False)], [(50, 20, 220, 280)])
    result['harness'] = tile([sprite('saddle')], [(20,30,280,260)])
    result['narcotics'] = tile([sprite('narcoberry',False),sprite('glass_bottle')], [(0,100,170,170),(130,20,160,240)])
    result['tranquilizer'] = tile([sprite('arrow'),sprite('narcoberry',False)], [(25,20,250,250),(180,190,120,120)])
    for key, name in [('copper','copper_ingot'),('charcoal','charcoal'),('gunpowder','gunpowder')]:
        result[key] = tile([sprite(name)], [(30,30,260,260)])
    for key, name in [('leash','lead'),('iron','iron_ingot'),('white_dust','sugar'),('boots','iron_boots'),('armor','iron_chestplate')]:
        result[key] = tile([sprite(name)], [(30,30,260,260)])
    for name, image in result.items(): image.save(OUT / 'icons' / (name + '.png'))
    return result


def arrow(draw, points, color, dashed=False, width=5, head=True):
    if not dashed: draw.line(points, fill=color, width=width, joint='curve')
    else:
        for a, b in zip(points, points[1:]):
            distance=math.dist(a,b)
            if not distance:continue
            for offset in range(0, math.ceil(distance), 22):
                end=min(distance,offset+12)
                p=tuple(a[i]+(b[i]-a[i])*offset/distance for i in (0,1))
                q=tuple(a[i]+(b[i]-a[i])*end/distance for i in (0,1))
                draw.line((p,q),fill=color,width=width)
    if head:
        a,b=points[-2:];angle=math.atan2(b[1]-a[1],b[0]-a[0]);length=14
        wings=[(b[0]-length*math.cos(angle+d),b[1]-length*math.sin(angle+d)) for d in (-.5,.5)]
        draw.polygon([b,*wings],fill=color)


def anchor(n, side):
    x,y,w,h=n['box']
    return {'left':(x,y+h/2),'right':(x+w,y+h/2),'top':(x+w/2,y),'bottom':(x+w/2,y+h)}[side]


def draw_card(sheet, d, n):
    age=next(a for a in AGES if a['id']==n['age'])
    x,y,w,h=n['box']; gate=n['kind'] in ('starter','finale'); bonus=n['kind']=='bonus'
    d.rounded_rectangle((x+2,y+6,x+w+2,y+h+6),radius=20,fill=age['shadow'])
    d.rounded_rectangle((x,y,x+w,y+h),radius=20,fill=age['accent'] if gate else '#fffaf1' if bonus else '#fdfcf8',outline='#b9935b' if bonus else None,width=2)
    color='#fff9e9' if gate else INK
    secondary='#eee9dc' if gate else MUTED
    has_icon=bool(n['icon'])
    if has_icon:
        im=ASSET_ICONS[n['icon']].copy();im.thumbnail((90,90),Image.Resampling.LANCZOS)
        sheet.alpha_composite(im,(x+18+(90-im.width)//2,y+23+(90-im.height)//2))
    tx=x+(122 if has_icon else 22); tw=w-(144 if has_icon else 44)
    f=font(30,True)
    title=wrap(n['title'],f,tw)
    assert len(title)<=3,(n['id'],title)
    for i,line in enumerate(title):d.text((tx,y+26+i*35),line,font=f,fill=color)
    body=wrap(n['task'],font(25),w-44)
    for i,line in enumerate(body):d.text((x+22,y+122+i*30),line,font=font(25),fill=secondary)
    assert 122+len(body)*30<=h-22,(n['id'],body)
    if n['note']:d.text((x+22,y+h-24),n['note'],font=font(15,True),fill='#f1deb5' if gate else '#947445')


def draw_tree():
    sheet=Image.new('RGBA',(W,H),PAPER);d=ImageDraw.Draw(sheet)
    d.text((75,32),'FROM CAMP TO CIVILIZATION',font=font(58,True,True),fill=INK)
    d.text((78,108),'ARK SURVIVAL RETURNS  /  Progression proposal 02  /  Prehistoric to Iron',font=font(27),fill=MUTED)
    d.text((W-75,56),'ONE STARTER  /  THREE PATHS  /  ONE FINALE',font=font(27,True),fill=INK,anchor='ra')
    d.text((W-75,103),'Solid: required progression    /    Dashed: optional food bonus',font=font(24),fill=MUTED,anchor='ra')
    for index,a in enumerate(AGES):
        x=a['x'];right=x+a['width']
        d.rounded_rectangle((x,200,right,1490),radius=30,fill=a['fill'])
        d.text((x+42,231),f"0{index+1}  {a['name']} AGE",font=font(42,True,True),fill=a['accent'])
        d.text((right-38,246),'START  >  3 PATHS  >  FINALE',font=font(21,True),fill=a['accent'],anchor='ra')
        for lane,label in enumerate(a['lanes']):
            d.text((a['start']+STEP,ROWS[lane]-46),f'0{lane+1}  {label}',font=font(23,True),fill=LANES[lane])
        d.text((a['start'],ROWS[0]-46),'AGE FOOD BONUS',font=font(23,True),fill='#947445')
        for y in (749,1119):d.line((a['start']+STEP,y,right-50,y),fill=a['shadow'],width=2)
    for src,dst in EDGES:
        a,b=BY_ID[src],BY_ID[dst]
        p,q=anchor(a,'right'),anchor(b,'left')
        color=LANES[b['lane']]
        if b['kind']=='bonus':
            p,q=anchor(a,'top'),anchor(b,'bottom')
            arrow(d,[p,q],'#a58752',True);continue
        if a['age']!=b['age']:
            arrow(d,[p,q],'#6c7066',width=6);continue
        if a['kind']=='starter':
            mid=p[0]+35
            arrow(d,[p,(mid,p[1]),(mid,q[1]),q],color);continue
        if b['kind']=='finale':
            mid=q[0]-35
            arrow(d,[p,(mid,p[1]),(mid,q[1]),q],color);continue
        arrow(d,[p,q],color)
    for n in NODES:draw_card(sheet,d,n)
    for a in AGES:
        final=next(n for n in NODES if n['age']==a['id'] and n['kind']=='finale')
        x,y=anchor(final,'left');x-=35
        d.ellipse((x-9,y-9,x+9,y+9),fill=INK,outline=PAPER,width=3)
        d.text((x,y-46),'ALL 3',font=font(18,True),fill=INK,anchor='mt')
    d.text((80,1520),'Food bonuses are optional; their placement is not an early unlock. Dried Meat III still requires drying.',font=font(24),fill=MUTED)
    d.text((80,1560),'Camp icons: approved models. Other icons: existing sprites / placeholders. New block and weapon artwork reserved.',font=font(22),fill=MUTED)
    d.text((W-75,1560),'DESIGN ASSETS ONLY  /  22 SEP 2026',font=font(22,True),fill=MUTED,anchor='ra')
    return sheet.convert('RGB')


def validate():
    assert len(BY_ID)==len(NODES)
    incoming={k:[] for k in BY_ID};outgoing={k:[] for k in BY_ID}
    for a,b in EDGES:
        incoming[b].append(a);outgoing[a].append(b)
    for age in AGES:
        members=[n for n in NODES if n['age']==age['id']]
        start=next(n for n in members if n['kind']=='starter')
        final=next(n for n in members if n['kind']=='finale')
        assert len([k for k in outgoing[start['id']] if BY_ID[k]['kind']=='main'])==3
        assert len(incoming[final['id']])==3
        bonus=next(n for n in members if n['kind']=='bonus')
        assert not outgoing[bonus['id']]
        age['column_counts']=[sum(n['column']==i for n in members) for i in range(age['cols'])]
    pending=set(BY_ID);done=set()
    while pending:
        ready={n for n in pending if set(incoming[n])<=done}
        assert ready,'Cycle in progression'
        pending-=ready;done|=ready
    for n in NODES:
        n['requires']=incoming[n['id']]
        n['optional']=n['kind']=='bonus'
        x,y,w,h=n['box'];assert 0<=x<x+w<=W and 0<=y<y+h<=H
    assert BY_ID['feed']['age']=='bronze'
    assert BY_ID['home']['age']=='iron' and BY_ID['home']['icon']=='bedroll'
    assert BY_ID['mattress']['icon'] is None
    assert BY_ID['greed']['kind']=='starter' and BY_ID['rawr']['kind']=='finale'
    assert not {'hearth','protein','powder'} & set(BY_ID)


def main():
    global ASSET_ICONS
    (OUT/'icons').mkdir(parents=True,exist_ok=True)
    validate();ASSET_ICONS=icons();sheet=draw_tree()
    sheet.save(OUT/'technology-tree.png')
    files=['technology-tree.png','technology-tree.json','README.md','block-ideas.txt']
    for age in AGES:
        name=age['id']+'-age.png'
        sheet.crop((age['x']-15,185,age['x']+age['width']+15,1500)).save(OUT/name)
        files.append(name)
    data=dict(status='Design proposal 02; no game patch',ages=AGES,nodes=NODES,edges=EDGES,
        notes=[
            'Monkeys retained as the Prehistoric starter; its gather-stone task overlaps Tha rock pending author refinement.',
            'Narcotraffic ends Prehistoric; Prepare for it! starts Bronze; Rawr! ends Bronze; Greed starts Iron; Subdue Nature ends Iron.',
            'Prehistoric columns 2-3-3-3-3-1. Bronze columns 2-3-3-3-1; Greed is the following singleton and also Iron starter, not a duplicate Bronze node.',
            'Iron columns 2-3-3-3-1-1 preserve all four middle-path achievements.',
            'Slavery inherits the previous Working Giants requirement; Multi-tool inherits Therizinosaurus taming.',
            'Food bonuses are optional. acquisition_requires describes later food availability, independently of diagram placement.',
            'Cocaine is a fictional humorous boosting food: gunpowder + narcotics + yellow berry. White dust uses the existing sugar sprite.',
            'Mattress, new blocks and weapons remain text-only. Approved bed icon moves to Iron. Boots and armor use vanilla placeholder sprites.'
        ])
    (OUT/'technology-tree.json').write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    used=sorted({n['icon'] for n in NODES if n['icon']})
    files += ['icons/'+name+'.png' for name in used]
    with ZipFile(OUT/'technology-tree-assets.zip','w',ZIP_DEFLATED) as z:
        for file in files:z.write(OUT/file,file)
    print(f'Validated {len(NODES)} nodes and {len(EDGES)} connections; exported {W} x {H} PNG, three age crops and asset pack.')
    for a in AGES:print(a['id'],a['column_counts'])


if __name__=='__main__':main()
