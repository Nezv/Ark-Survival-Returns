"""Render a geometry preview from the exact native beacon builder; not an in-game screenshot."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
from build_sky_beacons import blocks,ROOT
COLORS={'polished_blackstone':(56,59,65),'chiseled_polished_blackstone':(73,76,82),
'polished_deepslate':(87,92,98),'deepslate_tiles':(48,52,59),'polished_blackstone_bricks':(62,68,75),
'sea_lantern':(211,237,226),'red_stained_glass':(229,86,94),
'light_blue_stained_glass':(143,218,238),'purple_stained_glass':(156,112,223)}
def render():
    im=Image.new('RGB',(1560,1020),(18,28,40));d=ImageDraw.Draw(im)
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',22)
    title=ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf',34)
    d.text((55,32),'Floating beacons',font=title,fill=(235,241,246))
    d.text((55,80),'Native block geometry • 49 × 97 × 49 • colour variants share one generation rule',font=font,fill=(170,189,204))
    for i,v in enumerate(('red','white','black')):
        shape={p:b.split(':')[1] for p,b in blocks(v).items() if not b.endswith(':air')}
        ox,oy=255+i*510,895
        def proj(x,y,z):return (ox+(x-24)*6.2+(z-24)*2.4,oy-y*7+(z-24)*1.3-(x-24)*.65)
        for (x,y,z),b in sorted(shape.items(),key=lambda kv:kv[0][0]*.4-kv[0][2]*.9+kv[0][1]*.3):
            color=COLORS[b]
            for adj,face,light in [
                ((1,0,0),[(1,0,0),(1,0,1),(1,1,1),(1,1,0)],.8),
                ((0,0,-1),[(0,0,0),(1,0,0),(1,1,0),(0,1,0)],1),
                ((0,1,0),[(0,1,0),(1,1,0),(1,1,1),(0,1,1)],1.15)]:
                if (x+adj[0],y+adj[1],z+adj[2]) in shape:continue
                tint=tuple(min(255,int(c*light)) for c in color)
                d.polygon([proj(x+a,y+b,z+c) for a,b,c in face],fill=tint)
        d.text((ox-35,935),v.upper(),font=font,fill=(226,234,241))
    d.text((55,981),'Geometry preview; Minecraft textures, dragon animation and lighting require an in-game check.',font=font,fill=(155,176,194))
    path=ROOT/'docs/sky-beacons.png';im.save(path);print(path)
if __name__=='__main__':render()
