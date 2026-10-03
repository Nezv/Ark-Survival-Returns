"""Build original block structures, each with exactly one persistent airborne dragon.
Run from anywhere; no third-party assets or source structures are copied.
"""
from pathlib import Path
import gzip
import json
import math
import struct

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src/main/resources/data/arksurvivalreturns'

# Minimal big-endian NBT writer for native structure templates.
def string(s):
    b=s.encode(); return struct.pack('>H',len(b))+b
def payload(kind, v):
    if kind == 1: return struct.pack('>b',v)
    if kind == 3: return struct.pack('>i',v)
    if kind == 5: return struct.pack('>f',v)
    if kind == 6: return struct.pack('>d',v)
    if kind == 8: return string(v)
    if kind == 9:
        t,items=v
        return bytes([t])+struct.pack('>i',len(items))+b''.join(payload(t,i) for i in items)
    if kind == 10:
        return b''.join(bytes([t])+string(k)+payload(t,x) for k,(t,x) in v.items())+b'\0'
    raise ValueError(kind)
def ints(v): return (9,(3,list(v)))

def blocks(variant):
    out={}
    core={'red':'red_stained_glass','white':'light_blue_stained_glass','black':'purple_stained_glass'}[variant]
    def setb(x,y,z,block):
        if 0<=x<49 and 0<=y<97 and 0<=z<49: out[(x,y,z)]='minecraft:'+block
    # Tapered split tail, suspended above terrain. The centre slit remains luminous.
    for y in range(31):
        half=2+int(y*.39)
        for x in range(24-half,25+half):
            for z in range(21,28):
                if abs(x-24)<=1:
                    setb(x,y,z,core if z in (23,24,25) else 'air')
                else:
                    setb(x,y,z,'polished_blackstone' if (x+y+z)%9 else 'chiseled_polished_blackstone')
    # Broad oval landing terrace beneath the ring, including a safe central reward landing.
    for x in range(49):
        for z in range(49):
            r=((x-24)/24)**2+((z-24)/19)**2
            if r<=1:
                for y in (30,31,32):
                    setb(x,y,z,'polished_deepslate' if y<32 else
                         'polished_blackstone_bricks' if r<.84 else 'chiseled_polished_blackstone')
                if .77<r<.84: setb(x,32,z,'sea_lantern');setb(x,33,z,core)
    # Massive ring: 33 x 34 clear aperture, raised above the terrace.
    # Nested diamond armour and offset broken plates echo the reference silhouette.
    for x in range(2,47):
        for y in range(34,84):
            dx,dy=abs(x-24),abs(y-57)
            ellipse=((x-24)/22)**2+((y-57)/25)**2
            inner=((x-24)/17)**2+((y-57)/19)**2
            if ellipse<=1 and inner>=1:
                for z in range(20,29):
                    edge=z in (20,28)
                    setb(x,y,z,'chiseled_polished_blackstone' if edge and (dx+dy)%7==0
                         else 'polished_deepslate' if edge else 'deepslate_tiles')
                if 1.02<inner<1.16:
                    for z in (19,29): setb(x,y,z,core)
            # Offset slabs on alternating flanks, leaving the aperture untouched.
            if 24<=dx+dy<=29 and 9<=dx<=23 and 8<=dy<=25:
                for z in (18,19,29,30): setb(x,y,z,'polished_blackstone')
    # Forked, jagged crown and central glowing diamond above the aperture.
    for y in range(81,97):
        half=max(1,(97-y)//2)
        for x in range(24-half,25+half):
            for z in range(22,27):
                if abs(x-24)>1 or y>92: setb(x,y,z,'polished_deepslate')
                else: setb(x,y,z,core)
    for x in range(18,31):
        for y in range(77,90):
            d=abs(x-24)+abs(y-83)
            if 4<=d<=6:
                for z in (18,30): setb(x,y,z,'sea_lantern' if d==4 else 'polished_blackstone')
                for z in (17,31): setb(x,y,z,core if d==4 else 'polished_deepslate')
    # Three small detached plates, each physically part of the same saved structure piece.
    for cx,cy,cz in [(7,19,21),(42,72,24),(13,88,24)]:
        for dx in range(-3,4):
            for dy in range(-4,5):
                if abs(dx)+abs(dy)<=4:
                    for dz in range(-2,3):setb(cx+dx,cy+dy,cz+dz,'chiseled_polished_blackstone')
    return out

def build():
    dest=DATA/'structure/sky_beacon';dest.mkdir(parents=True,exist_ok=True)
    for i,variant in enumerate(('red','white','black')):
        shape=blocks(variant)
        palette=list(dict.fromkeys(shape.values()))
        entity={'id':(8,'arksurvivalreturns:guardian_dragon'),
                'Pos':(9,(6,[24.5,50.,24.5])), 'Rotation':(9,(5,[0.,0.])),
                'Motion':(9,(6,[0.,0.,0.])), 'PersistenceRequired':(1,1),
                'NoGravity':(1,1),'BeaconVariant':(3,i)}
        root={'DataVersion':(3,0),'size':ints([49,97,49]),
              'palette':(9,(10,[{'Name':(8,n)} for n in palette])),
              'blocks':(9,(10,[{'pos':ints(p),'state':(3,palette.index(n))} for p,n in sorted(shape.items())])),
              'entities':(9,(10,[{'pos':(9,(6,[24.5,50.,24.5])),
                                 'blockPos':ints([24,50,24]),'nbt':(10,entity)}]))}
        # Native current template; no historical data fixes needed on our authored block/entity ids.
        del root['DataVersion']
        (dest/f'{variant}.nbt').write_bytes(gzip.compress(b'\x0a\0\0'+payload(10,root),mtime=0))
        print(variant,len(shape),'blocks; one dragon')
    def write(rel,data):
        p=DATA/rel;p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(json.dumps(data,indent=2)+'\n')
    write('worldgen/structure/sky_beacon.json',{'type':'arksurvivalreturns:sky_beacon',
        'biomes':'#arksurvivalreturns:has_structure/sky_beacon',
        'step':'surface_structures','spawn_overrides':{},'terrain_adaptation':'none'})
    write('worldgen/structure_set/sky_beacons.json',{'structures':[{'structure':'arksurvivalreturns:sky_beacon','weight':1}],
        'placement':{'type':'minecraft:random_spread','spacing':32,'separation':30,'salt':50312010}})
    write('tags/worldgen/biome/has_structure/sky_beacon.json',{'replace':False,'values':['#minecraft:is_overworld']})

if __name__=='__main__': build()
