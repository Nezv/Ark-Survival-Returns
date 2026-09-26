"""Offline orthographic previews of the actual JSON meshes (not client screenshots).

Uses Pillow/numpy and the locally cached Minecraft source jar for the vanilla campfire.
Run after build_camp_assets.py. Preview output: docs/camp-assets.png.
"""
import io
import json
import math
from pathlib import Path
from zipfile import ZipFile
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from build_camp_assets import ROOT, ASSETS, WOODS

JAR = ROOT / 'build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar'


def model(identifier):
    ns, path = identifier.split(':') if ':' in identifier else ('minecraft', identifier)
    if ns == 'minecraft':
        with ZipFile(JAR) as z:
            data = json.loads(z.read(f'assets/{ns}/models/{path}.json'))
    else:
        data = json.loads((ASSETS / 'models' / (path + '.json')).read_text())
    parent = model(data['parent']) if 'parent' in data and data['parent'] not in ('minecraft:block/block', 'block/block') else {}
    return {**parent, **data, 'textures': {**parent.get('textures', {}), **data.get('textures', {})}}


def texture(name, textures):
    while name.startswith('#'):
        name = textures[name[1:]]
    ns, path = name.split(':') if ':' in name else ('minecraft', name)
    if ns == 'minecraft':
        with ZipFile(JAR) as z:
            im = Image.open(io.BytesIO(z.read(f'assets/{ns}/textures/{path}.png'))).convert('RGBA')
    else:
        im = Image.open(ASSETS / 'textures' / (path + '.png')).convert('RGBA')
    return np.array(im.crop((0, 0, im.width, im.width)))


def mesh(identifier, offset=(0, 0, 0)):
    data = model(identifier)
    faces = []
    for e in data.get('elements', []):
        x, y, z = e['from']; X, Y, Z = e['to']
        vertices = {
            'north': [(X,y,z),(x,y,z),(x,Y,z),(X,Y,z)],
            'south': [(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],
            'west': [(x,y,z),(x,y,Z),(x,Y,Z),(x,Y,z)],
            'east': [(X,y,Z),(X,y,z),(X,Y,z),(X,Y,Z)],
            'up': [(x,Y,Z),(X,Y,Z),(X,Y,z),(x,Y,z)],
            'down': [(x,y,z),(X,y,z),(X,y,Z),(x,y,Z)],
        }
        for face, f in e['faces'].items():
            v = np.array(vertices[face], dtype=float)
            if 'rotation' in e:
                r = e['rotation']; axis = 'xyz'.index(r['axis']); angle = math.radians(r['angle'])
                a, b = [i for i in range(3) if i != axis]
                if axis == 1: a, b = b, a
                m = np.eye(3); m[a,a] = m[b,b] = math.cos(angle); m[a,b] = -math.sin(angle); m[b,a] = math.sin(angle)
                origin = np.array(r['origin'])
                v = (v-origin) @ m.T + origin
            v += offset
            normal = np.cross(v[1]-v[0], v[2]-v[0]); normal /= max(.00001, np.linalg.norm(normal))
            # The face ordering above points outward for all six faces.
            uv = f.get('uv', [0, 0, 16, 16]); u, vv, U, V = uv
            texcoords = np.array([(u,V),(U,V),(U,vv),(u,vv)]) / 16
            texcoords = np.roll(texcoords, f.get('rotation', 0)//90, axis=0)
            faces.append((v, normal, texcoords, texture(f['texture'], data['textures'])))
    return faces


def render(objects, size=(600, 440), scale=17, target=(8, 6, 8)):
    width, height = size
    view = np.array([.65, .63, 1.0]); view /= np.linalg.norm(view)
    right = np.cross([0,1,0], view); right /= np.linalg.norm(right)
    up = np.cross(view, right)
    camera = np.array([right, up, view])
    light = np.array([-.4,.9,.6]); light /= np.linalg.norm(light)
    pixels = np.zeros((height, width, 4), dtype=np.uint8)
    depth = np.full((height, width), -np.inf)
    for identifier, offset in objects:
        for vertices, normal, uv, tex in mesh(identifier, offset):
            if normal @ view <= 0: continue
            projected = (vertices - target) @ camera.T
            p = projected.copy(); p[:,0] = p[:,0]*scale+width/2; p[:,1] = height/2-p[:,1]*scale
            shading = .73 + .27*max(0, normal @ light)
            for indices in ((0,1,2),(0,2,3)):
                tri=p[list(indices)]; tuv=uv[list(indices)]
                lo=np.maximum([0,0],np.floor(tri[:,:2].min(axis=0)).astype(int)); hi=np.minimum([width-1,height-1],np.ceil(tri[:,:2].max(axis=0)).astype(int))
                if (lo>hi).any():continue
                xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
                a,b,c=tri[:,:2]; denominator=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
                if abs(denominator)<1e-8:continue
                w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/denominator
                w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/denominator
                w2=1-w0-w1
                zz=w0*tri[0,2]+w1*tri[1,2]+w2*tri[2,2]
                region=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1]
                coords=w0[...,None]*tuv[0]+w1[...,None]*tuv[1]+w2[...,None]*tuv[2]
                tx=np.clip((coords[...,0]*tex.shape[1]).astype(int),0,tex.shape[1]-1)
                ty=np.clip((coords[...,1]*tex.shape[0]).astype(int),0,tex.shape[0]-1)
                color=tex[ty,tx].copy(); color[:,:,:3]=(color[:,:,:3]*shading).astype(np.uint8)
                mask=(w0>=-1e-5)&(w1>=-1e-5)&(w2>=-1e-5)&(zz>region)&(color[:,:,3]>100)
                region[mask]=zz[mask]
                pixels[lo[1]:hi[1]+1,lo[0]:hi[0]+1][mask]=color[mask]
    return Image.fromarray(pixels)


def camp(name): return 'arksurvivalreturns:block/camp/' + name


def font(size, bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/' + ('georgiab.ttf' if bold else 'segoeui.ttf'), size)


def main():
    sheet = Image.new('RGB', (1600, 1200), '#eee9df'); d=ImageDraw.Draw(sheet)
    d.text((60,36), 'THE FIELD CAMP', font=font(38,True), fill='#343e30')
    d.text((62,91), 'ARK SURVIVAL RETURNS   /   Authored Minecraft model collection', font=font(19), fill='#736a59')
    panels=[(40,140,'01  FIELD BEDROLL (IRON AGE)','Four blocks, 3/4 block tall: canvas bedding on an iron-bracketed frame.'),
            (820,140,'02  FEEDING TROUGH','Open timber basin, pegs & resin-sealed joins.'),
            (40,590,'03  DRYING RACK','Two blocks tall: hanging food up top, rations on the shelf.'),
            (820,590,'04  COOKING POT','Seated on the stone fire; the spit lifts while it cooks.')]
    for x,y,title,caption in panels:
        d.rounded_rectangle((x,y,x+740,y+430),radius=12,fill='#f8f5ed')
        d.text((x+24,y+19),title,font=font(23,True),fill='#414c39')
        d.text((x+24,y+55),caption,font=font(17),fill='#817561')
    images=[render([(camp('field_bedroll_head_west'),(0,0,0)),(camp('field_bedroll_head_east'),(16,0,0)),
                    (camp('field_bedroll_foot_west'),(0,0,16)),(camp('field_bedroll_foot_east'),(16,0,16))],(490,330),9.5,(16,5,16)),
            render([(camp('trough_oak'),(0,0,0))],(640,330),24,(8,3,8)),
            render([(camp('drying_rack_lower'),(0,0,0)),(camp('drying_rack_upper'),(0,16,0)),
                    *[(camp('rack_meat_'+str(i)),(0,0,0)) for i in (1,2,3)],(camp('rack_ready'),(0,0,0))],(640,340),9.0,(8,16,8)),
            render([('arksurvivalreturns:block/prehistoric/stone_fire_pot_base',(0,0,0)),(camp('cooking_pot_stones'),(0,16,0))],(640,340),17,(8,6,8))]
    for im,(x,y,_,_) in zip(images,panels):
        sheet.paste(im,(x+48,y+90),im)
    rolled=render([(camp('field_bedroll_rolled'),(0,0,0))],(220,170),10,(8,7,8));sheet.paste(rolled,(540,365),rolled)
    for i,wood in enumerate(WOODS):
        im=render([(camp('trough_'+wood),(0,0,0))],(148,103),6,(8,3,8));x=45+i*151
        sheet.paste(im,(x,1035),im);d.text((x+74,1145),wood.replace('_',' ').title(),font=font(15),fill='#655d4d',anchor='mm')
    d.text((60,1180),'Offline renders of the shipped geometry and textures. Lighting differs from the game client.',font=font(13),fill='#817561')
    target=ROOT/'docs/camp-assets.png';sheet.save(target);print(target)


if __name__ == '__main__': main()
