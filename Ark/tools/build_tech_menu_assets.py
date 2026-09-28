"""Export the approved progression, GUI art and FTB mirror chapters.

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
# Every age runs starter + four lane columns + finale (six columns); Iron's 4x4 lanes are mystery nodes
# ('???') until their logic is designed. Each origin leaves a 70-unit gap after the previous age's widest column.
ORIGINS=[0,680,1360]
# The map is 344 units tall (TechScreen.MAP_HEIGHT). Lanes are 76 apart and centred on the gate row, so a
# three-lane age and the four-lane Prehistoric/Bronze share the same gate height.
GATE_Y,LANE_STEP=180,76
# Node ids whose icon an art script (build_bronze_age_art.py, build_prehistoric_icons.py) paints directly at the runtime path below; the exporter must never
# generate, overwrite or delete that file, only point the node at it (the client tolerates a missing texture).
# tools/build_prehistoric_icons.py paints monkeys, rock, fight, london and sharp the same way.
FORCE_ICON={'monkeys','rock','fight','london','sharp','forge','shiny','home','coal','ironsmelt','minerals','sparklers','glass','ambulance','bandage',
            'vitamins','knight','tools','tincan','colossus','kaboom','steel','subdue'}
# Nodes removed by proposal 03; their stale per-node icon (if any) is deleted so no orphaned art ships.
DELETED_ICON_IDS={'prepare','rawr','greed','harder','faster','stronger','charcoal','prometheus'}


def lane_y(lane,lanes):
    return round(GATE_Y+(lane-(lanes-1)/2)*LANE_STEP)
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
    # Rounded eight-point star badges (user reference on F01): the same silhouette in every state, and
    # the hover highlight traces it. Geometry is shared with the showcase (tools/ark_shapes.py).
    from ark_shapes import star_polygon
    def starfish(fill,outline,width,scale=1.0):
        S=256;big=Image.new('RGBA',(S,S));d=ImageDraw.Draw(big)
        pts=star_polygon(S/2,S/2,S*0.5*scale)
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


# Portuguese (pt_br) title/task for every shipped node; the FTB mirror is the only place these strings are
# localized (the native TechScreen renders the tree's own text directly, English only, as raw data).
PT_TITLE={'monkeys':'Macacos','dried':'Carne Seca III','rock':'A pedra','berries':'Frutinhas!','london':'Londres',
    'lasting':'Longa duração','fight':'Devíamos lutar com eles','ride':'Devíamos montar neles',
    'companions':'Companheiros','scavenge':'Batedor','mattress':'Boa noite, enfim','warmth':'Prometeu',
    'dish':'Delicioso','narcotics':'Narcotráfico','sharp':'Pensamento afiado','horn':'Duro como chifre',
    'pointy':'Ponta afiada','armoured':'Pele grossa','forge':'Forja Infernal',
    'shiny':'Brilhante','golden':'Carne Dourada de Raptor','home':'Lar, doce lar','feed':'Alimente a fera',
    'theri':'Canivete suíço','slavery':'Escravidão','coal':'Ouro negro','ironsmelt':'Feito para durar',
    'minerals':'Segredos da Terra','sparklers':'Fogos de artifício','glass':'Invisível',
    'ambulance':'Chame a ambulância','bandage':'Mas não para mim','vitamins':'Tome suas vitaminas',
    'knight':'Cavaleiro do Reino','tools':'Ferramentas do ofício','tincan':'Lata de conserva','colossus':'Colosso',
    'kaboom':'Cabum!',
    'steel':'Juntos é melhor','cocaine':'Cocaína','subdue':'Dome a natureza'}
PT_TASK={'monkeys':'Pegue uma pedra solta.',
    'dried':'Coma Carne Seca III: carne curada no varal por três dias do jogo.',
    'rock':'Amarre uma pedra a um graveto: crie um Machado de Pedra.',
    'berries':'Colete as quatro frutas: Amora, Framboesa, Amarelinha e Mirtilo.',
    'london':'Crie seu primeiro equipamento: uma ferramenta de pedra ou a faca de sílex.',
    'lasting':'Seque carne em um varal.','fight':'Cause dano a um dinossauro.',
    'ride':'Crie uma corda de guia.','companions':'Domestique qualquer dinossauro.',
    'scavenge':'Tenha um dinossauro coletando recursos.','mattress':'Crie um Colchão.',
    'warmth':'Acenda uma fogueira de pedra.','dish':'Cozinhe qualquer prato em uma panela de barro.',
    'narcotics':'Produza narcóticos: moa Amoras no Pilão.',
    'sharp':'Crie uma Faca de Sílex: sílex, graveto e fibra. Carcaças abatidas com ela dão três queratinas a mais.',
    'horn':'Retire queratina de uma criatura com chifres, placas ou bico.',
    'pointy':'Crie uma Lança de Queratina.',
    'armoured':'Possua o conjunto completo de armadura de queratina: capacete, peitoral, calças e botas.',
    'forge':'Crie uma Forja Primitiva.',
    'shiny':'Funda Bronze: retire um Lingote de Bronze da Forja Primitiva.',
    'golden':'Crie Carne Dourada de Raptor.','home':'Crie um Saco de Dormir.',
    'feed':'Deixe seu tamed comer em uma cocheira.','theri':'Domestique um Therizinosaurus.',
    'slavery':'Tenha um tamed voltando de uma caçada e outro voltando com uma carga de recursos.',
    'coal':'Consiga carvão: minere minério de carvão ou produza carvão vegetal.',
    'ironsmelt':'Funda ferro: retire um Lingote de Ferro da Forja Primitiva.',
    'minerals':'Consiga enxofre.','sparklers':'Crie pólvora na Britadeira.','glass':'Consiga vidro.',
    'ambulance':'Coloque uma Bancada de Medicina.','bandage':'Consiga uma Atadura de Ervas.',
    'vitamins':'Crie vitaminas.','knight':'Consiga uma Espada Longa de Bronze ou um Martelo de Bronze.',
    'tools':'Crie qualquer ferramenta de bronze.','tincan':'Crie qualquer peça de armadura de bronze.',
    'colossus':'Possua o conjunto completo de armadura de bronze: capacete, peitoral, calças e botas.',
    'kaboom':'Crie Flechas Explosivas.',
    'steel':'Forje aço.','cocaine':'Pólvora + narcóticos + fruta amarela.','subdue':'Crie munição.'}


def export():
    for deleted in DELETED_ICON_IDS:
        stale=GUI/'icons'/(deleted+'.png')
        if stale.exists():stale.unlink()
    ages=[];nodes=[];ftbmap={}
    for index,age in enumerate(SPEC['ages']):
        ages.append(dict(id=age['id'],title=age['name'].title()+' Age',order=index,color=COLORS[index],
                         laneSummary=' / '.join(age['lanes']),lanes=[dict(index=i,title=t) for i,t in enumerate(age['lanes'])]))
    overrides={'mattress':'primitive_bedroll','warmth':'stone_fire_lit','dish':'clay_pot','narcotics':'mortar_berry_whole'}
    for source in SPEC['nodes']:
        n={k:source[k] for k in ('id','title','task','age','lane','requires')}
        index=next(i for i,a in enumerate(ages) if a['id']==n['age'])
        n['kind']='side' if source['kind']=='bonus' else 'gate' if source['kind'] in ('starter','finale') else 'main'
        lanes=len(ages[index]['lanes'])
        n['box']=[ORIGINS[index]+60+source['column']*110,GATE_Y if n['kind']=='gate' else lane_y(n['lane'],lanes),40,40]
        n['note']=source.get('note') or ''
        n['trigger']=source.get('trigger',{'type':'future'})
        # Food placement is cosmetic; actual prerequisites still apply.
        if n['id']=='dried':n['requires']=['lasting']
        if n['id'] in FORCE_ICON:
            # Owned by an art script: reference the runtime path without touching the
            # file, so a PNG dropped there later (or already there from a merge) is neither generated over
            # nor deleted by this exporter. The client shows an empty ring until the texture exists.
            n['icon']='arksurvivalreturns:textures/gui/tech/icons/'+n['id']+'.png'
        else:
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
    assert len(nodes)==len(SPEC['nodes']) and len(set(ftbmap.values()))==len(nodes)
    tree=dict(schema_version=2,ages=ages,nodes=nodes)
    write(ROOT/'src/main/resources/data/arksurvivalreturns/tech_tree/tree.json',tree)
    write(DESIGN/'quest-id-map.json',ftbmap)
    for index,age in enumerate(ages):
        chapter_id=f'{0x4152FFFF00000100+index:016X}'
        filename='ark_tech_'+age['id'];quests=[]
        lang={f'chapter.{chapter_id}.title':age['title']+' - Chronicle'}
        lang_pt={f'chapter.{chapter_id}.title':age['title']+' - Crônica'}
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
            lang_pt[f'quest.{qid}.title']=PT_TITLE.get(n['id'],n['title'])
            lang_pt[f'quest.{qid}.quest_desc']=['???' if n['kind']=='side' else PT_TASK.get(n['id'],n['task'])]
            lang_pt[f'task.{tid}.title']='Objetivo da crônica'
        chapter=dict(id=chapter_id,filename=filename,group='',order_index=20+index,always_invisible=True,quests=quests)
        write(ROOT/'config/ftbquests/quests/chapters'/(filename+'.json5'),chapter)
        write(ROOT/'config/ftbquests/quests/lang/en_us'/(filename+'.json5'),lang)
        write(ROOT/'config/ftbquests/quests/lang/pt_br'/(filename+'.json5'),lang_pt)
    return tree


def preview(tree):
    """Offline layout proof, not a client screenshot; same coordinates and art as the menu."""
    im=Image.new('RGBA',(720,476),'#27291f');d=ImageDraw.Draw(im)
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',11)
    bold=ImageFont.truetype('C:/Windows/Fonts/georgiab.ttf',17)
    d.text((20,12),'TRIBE CHRONICLE',font=bold,fill='#e0c58f')
    d.text((400,16),'PREHISTORIC    BRONZE    IRON',font=font,fill='#dac9a4')
    d.line((402,33,478,33),fill='#b99550',width=2)
    tile=Image.open(GUI/'parchment.png').convert('RGBA')
    for y in range(48,412,128):
        for x in range(16,704,128):im.alpha_composite(tile,(x,y))
    d=ImageDraw.Draw(im);d.rectangle((16,48,704,412),fill='#bac19d')
    for y in range(51,411,3):d.line((20,y,700,y),fill=(182+(y%3),190,152,255))
    d.text((34,61),'I   PREHISTORIC AGE',font=bold,fill='#485137')
    completed={'monkeys','rock','berries','mattress','warmth','fight'}
    shown=[n for n in tree['nodes'] if n['age']=='prehistoric'];byid={n['id']:n for n in tree['nodes']}
    for n in shown:
        if n['kind']=='side':continue
        x,y=n['box'][:2];x+=16;y+=58
        for req in n['requires']:
            a=byid[req];sx,sy=a['box'][:2];sx+=16;sy+=58
            mid=sx+55 if len(n['requires'])<3 else x-46
            d.line([(sx+22,sy),(mid,sy),(mid,y),(x-22,y)],fill='#777451',width=1)
    for n in shown:
        x,y=n['box'][:2];x+=16;y+=58
        if n['kind']=='side':d.text((x-8,y-7),'???',font=font,fill='#8c8464');continue
        status='complete' if n['id'] in completed else 'ready' if all(r in completed for r in n['requires']) else 'locked'
        halo=Image.open(GUI/('node_'+status+'.png')).resize((48,48));im.alpha_composite(halo,(x-24,y-24))
        icon_path=GUI/'icons'/(n['id']+'.png')
        if n.get('icon') and icon_path.exists():
            icon=Image.open(icon_path).resize((40,40))
            if status=='locked':icon.putalpha(icon.getchannel('A').point(lambda v:v*145//255))
            im.alpha_composite(icon,(x-20,y-20))
    d=ImageDraw.Draw(im)
    d.rounded_rectangle((340,176,562,246),radius=5,fill='#f0e2c5',outline='#9c8153')
    d.text((351,183),'A little warmth',font=bold,fill='#483d29')
    d.text((351,207),'Light a campfire.',font=font,fill='#5d523b')
    d.text((351,227),'Complete',font=font,fill='#66733e')
    d.rectangle((0,413,720,476),fill='#27291f')
    d.line((24,428,696,428),fill='#666347',width=2);d.line((24,428,252,428),fill='#c6a15e',width=4)
    d.text((22,447),'Wheel / drag to travel     Arrow keys to pan',font=font,fill='#baa985')
    d.text((481,447),'Quest book     Esc / P close',font=font,fill='#baa985')
    im.convert('RGB').resize((1440,952),Image.Resampling.NEAREST).save(DESIGN/'menu-preview.png')


def main():
    DESIGN.mkdir(parents=True,exist_ok=True);art();tree=export();preview(tree)
    print(f"Exported {len(tree['nodes'])} nodes, GUI art, three FTB mirror chapters and offline preview.")


if __name__=='__main__':main()
