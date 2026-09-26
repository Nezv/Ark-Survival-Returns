"""Export the approved 40-node progression, GUI art and FTB mirror chapters.

Source of truth: design/technology-tree/technology-tree.json (proposal 02).
Triggers come from each design node's optional 'trigger'; nodes without one stay future.
"""
import json
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
GUI=ROOT/'src/main/resources/assets/arksurvivalreturns/textures/gui/tech'
DESIGN=ROOT/'design/tech-menu'
SPEC=json.loads((ROOT/'design/technology-tree/technology-tree.json').read_text(encoding='utf-8'))
ORIGINS=[0,680,1250]
COLORS=['#b9c29a','#cbb184','#a6b6b6']


def quest_id(name):
    h=0
    for c in name:h=(31*h+ord(c))&0xffffffff
    return 0x4152000000000000 | (h<<1)


def write(path,obj):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')


def art():
    GUI.mkdir(parents=True,exist_ok=True);(GUI/'icons').mkdir(exist_ok=True)
    rng=random.Random(90223)
    im=Image.new('RGBA',(128,128));px=im.load()
    for y in range(128):
        for x in range(128):
            v=rng.choice([-3,-2,-1,0,0,1,2,3])+((x+y)%9==0)*2
            px[x,y]=(240+v,226+v,196+v,255)
    d=ImageDraw.Draw(im)
    for _ in range(60):
        x,y=rng.randrange(120),rng.randrange(128)
        d.line((x,y,x+rng.randrange(2,8),y),fill=(133,115,80,13))
    im.save(GUI/'parchment.png')
    # Rounded starfish badges (user feedback on F01): the same silhouette in every state, and the
    # hover highlight traces that silhouette. Drawn 4x and downsampled for smooth edges.
    import math
    def starfish(fill,outline,width,scale=1.0):
        S=256;big=Image.new('RGBA',(S,S));d=ImageDraw.Draw(big)
        pts=[]
        for i in range(360):
            t=math.radians(i)
            r=S*0.5*scale*(0.80+0.16*math.cos(5*(t+math.pi/2)))
            pts.append((S/2+r*math.cos(t),S/2+r*math.sin(t)))
        d.polygon(pts,fill=fill)
        d.line(pts+[pts[0]],fill=outline,width=width*4,joint='curve')
        return big.resize((64,64),Image.Resampling.LANCZOS)
    styles={'locked':((98,103,94,34),(98,103,94,120),2),'ready':((226,118,63,42),(184,72,27,235),2),
            'complete':((143,176,127,96),(75,102,65,245),2),'hover':((0,0,0,0),(226,118,63,255),3)}
    for name,(fill,outline,width) in styles.items():
        im=starfish(fill,outline,width,0.92 if name!='hover' else 0.98)
        if name=='complete':
            d=ImageDraw.Draw(im)
            d.polygon([(46,49),(50,46),(54,50),(61,41),(63,44),(54,56)],fill=(191,145,55,255))
        im.save(GUI/('node_'+name+'.png'))


def export():
    ages=[];nodes=[];ftbmap={}
    for index,age in enumerate(SPEC['ages']):
        ages.append(dict(id=age['id'],title=age['name'].title()+' Age',order=index,color=COLORS[index],
                         laneSummary=' / '.join(age['lanes']),lanes=[dict(index=i,title=t) for i,t in enumerate(age['lanes'])]))
    overrides={'mattress':'reed_bedroll','warmth':'stone_fire_lit','dish':'clay_pot','narcotics':'mortar_berry_whole'}
    for source in SPEC['nodes']:
        n={k:source[k] for k in ('id','title','task','age','lane','requires')}
        index=next(i for i,a in enumerate(ages) if a['id']==n['age'])
        n['box']=[ORIGINS[index]+60+source['column']*110,[66,142,218][n['lane']],40,40]
        n['kind']='side' if source['kind']=='bonus' else 'gate' if source['kind'] in ('starter','finale') else 'main'
        n['note']=source.get('note') or ''
        n['trigger']=source.get('trigger',{'type':'future'})
        # Food placement is cosmetic; actual prerequisites still apply.
        if n['id']=='dried':n['requires']=['lasting']
        icon=None
        if n['id'] in overrides:
            icon=ROOT/'design/prehistoric-camp/orthographic'/(overrides[n['id']]+'.png')
        elif source.get('icon'):
            icon=ROOT/'design/technology-tree/icons'/(source['icon']+'.png')
        if icon and icon.exists():
            Image.open(icon).convert('RGBA').resize((64,64),Image.Resampling.LANCZOS).save(GUI/'icons'/(n['id']+'.png'))
            n['icon']='arksurvivalreturns:textures/gui/tech/icons/'+n['id']+'.png'
        nodes.append(n)
        ftbmap[n['id']]=f'{quest_id(n["id"]):016X}'
    assert len(nodes)==40 and len(set(ftbmap.values()))==40
    tree=dict(schema_version=2,ages=ages,nodes=nodes)
    write(ROOT/'src/main/resources/data/arksurvivalreturns/tech_tree/tree.json',tree)
    write(DESIGN/'quest-id-map.json',ftbmap)
    for index,age in enumerate(ages):
        chapter_id=f'{0x4152FFFF00000100+index:016X}'
        filename='ark_tech_'+age['id'];quests=[]
        lang={f'chapter.{chapter_id}.title':age['title']+' - Chronicle'}
        for n in nodes:
            if n['age']!=age['id']:continue
            qid=ftbmap[n['id']];tid=f'{quest_id(n["id"])+1:016X}'
            quests.append(dict(id=qid,x=n['box'][0]/60,y=n['box'][1]/60,
                               dependencies=[ftbmap[r] for r in n['requires']],
                               tasks=[dict(id=tid,type='custom')],rewards=[],optional=n['kind']=='side'))
            lang[f'quest.{qid}.title']=n['title']
            # Never duplicate secret objectives into FTB's client-side quest description.
            lang[f'quest.{qid}.quest_desc']=['???' if n['kind']=='side' else n['task']]
            lang[f'task.{tid}.title']='Chronicle objective'
        chapter=dict(id=chapter_id,filename=filename,group='',order_index=20+index,always_invisible=True,quests=quests)
        write(ROOT/'config/ftbquests/quests/chapters'/(filename+'.json5'),chapter)
        write(ROOT/'config/ftbquests/quests/lang/en_us'/(filename+'.json5'),lang)
    return tree


def preview(tree):
    """Offline layout proof, not a client screenshot; same coordinates and art as the menu."""
    im=Image.new('RGBA',(720,400),'#27291f');d=ImageDraw.Draw(im)
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',11)
    bold=ImageFont.truetype('C:/Windows/Fonts/georgiab.ttf',17)
    d.text((20,12),'TRIBE CHRONICLE',font=bold,fill='#e0c58f')
    d.text((400,16),'PREHISTORIC    BRONZE    IRON',font=font,fill='#dac9a4')
    d.line((402,33,478,33),fill='#b99550',width=2)
    tile=Image.open(GUI/'parchment.png').convert('RGBA')
    for y in range(48,336,128):
        for x in range(16,704,128):im.alpha_composite(tile,(x,y))
    d=ImageDraw.Draw(im);d.rectangle((16,48,704,336),fill='#bac19d')
    for y in range(51,335,3):d.line((20,y,700,y),fill=(182+(y%3),190,152,255))
    d.text((34,61),'I   PREHISTORIC AGE',font=bold,fill='#485137')
    completed={'monkeys','rock','berries','mattress','warmth','fight'}
    shown=[n for n in tree['nodes'] if n['age']=='prehistoric'];byid={n['id']:n for n in tree['nodes']}
    for n in shown:
        if n['kind']=='side':continue
        x,y=n['box'][:2];x+=16;y+=68
        for req in n['requires']:
            a=byid[req];sx,sy=a['box'][:2];sx+=16;sy+=68
            mid=sx+55 if len(n['requires'])<3 else x-46
            d.line([(sx+22,sy),(mid,sy),(mid,y),(x-22,y)],fill='#777451',width=1)
    for n in shown:
        x,y=n['box'][:2];x+=16;y+=68
        if n['kind']=='side':d.text((x-8,y-7),'???',font=font,fill='#8c8464');continue
        status='complete' if n['id'] in completed else 'ready' if all(r in completed for r in n['requires']) else 'locked'
        halo=Image.open(GUI/('node_'+status+'.png')).resize((48,48));im.alpha_composite(halo,(x-24,y-24))
        if n.get('icon'):
            icon=Image.open(GUI/'icons'/(n['id']+'.png')).resize((40,40))
            if status=='locked':icon.putalpha(icon.getchannel('A').point(lambda v:v*145//255))
            im.alpha_composite(icon,(x-20,y-20))
    d=ImageDraw.Draw(im)
    d.rounded_rectangle((340,176,562,246),radius=5,fill='#f0e2c5',outline='#9c8153')
    d.text((351,183),'A little warmth',font=bold,fill='#483d29')
    d.text((351,207),'Light a campfire.',font=font,fill='#5d523b')
    d.text((351,227),'Complete',font=font,fill='#66733e')
    d.rectangle((0,337,720,400),fill='#27291f')
    d.line((24,352,696,352),fill='#666347',width=2);d.line((24,352,252,352),fill='#c6a15e',width=4)
    d.text((22,371),'Wheel / drag to travel     Arrow keys to pan',font=font,fill='#baa985')
    d.text((481,371),'Quest book     Esc / P close',font=font,fill='#baa985')
    im.convert('RGB').resize((1440,800),Image.Resampling.NEAREST).save(DESIGN/'menu-preview.png')


def main():
    DESIGN.mkdir(parents=True,exist_ok=True);art();tree=export();preview(tree)
    print('Exported 40 nodes, GUI art, three FTB mirror chapters and offline preview.')


if __name__=='__main__':main()
