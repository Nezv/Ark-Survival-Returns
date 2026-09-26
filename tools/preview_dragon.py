"""Render textured wyvern previews from the saved retargeted GeckoLib files.

Uses orthographic triangle rasterization with a depth buffer and atlas alpha;
fixed camera framing across each clip makes drift and contact errors visible.
Only writes retargeted/previews. No renderer or game installation is needed.
"""
from pathlib import Path
import argparse
import math
import numpy as np
from PIL import Image,ImageDraw,ImageFont
from scipy.spatial.transform import Rotation as R
from retarget_dragon import DRAGON, Rig, read, FACES, CORNERS


def font(size):
    try:return ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',size)
    except OSError:return ImageFont.load_default()


class Renderer:
    def __init__(self,rig,texture,size=(720,520),yaw=64):
        self.rig=rig;self.texture=np.array(Image.open(texture).convert('RGBA'))
        self.size=size
        self.camera=R.from_euler('xy',[12,yaw],degrees=True)

    def bounds(self,poses):
        points=[]
        for wp,wr in poses:
            points.extend([self.camera.apply(p) for _,p,c in self.rig.vertices(wp,wr)])
        pts=np.concatenate(points)
        lo,hi=pts[:,:2].min(0),pts[:,:2].max(0)
        lo[1]=min(lo[1],-5)
        w,h=self.size
        self.scale=min((w-72)/max(hi[0]-lo[0],1),(h-122)/max(hi[1]-lo[1],1))
        self.center=(lo+hi)/2

    def project(self,points):
        p=self.camera.apply(points)
        p[:,:2]=(p[:,:2]-self.center)*[self.scale,-self.scale]+np.array(self.size)/2+[0,18]
        return p

    def triangle(self,canvas,depth,points,uv,shade):
        w,h=self.size
        x0,y0=np.maximum(np.floor(points[:,:2].min(0)).astype(int),[0,0])
        x1,y1=np.minimum(np.ceil(points[:,:2].max(0)).astype(int),[w-1,h-1])
        if x1<x0 or y1<y0:return
        a,b,c=points
        det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(det)<1e-8:return
        yy,xx=np.mgrid[y0:y1+1,x0:x1+1]
        aa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/det
        bb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/det
        cc=1-aa-bb
        z=aa*a[2]+bb*b[2]+cc*c[2]
        mask=(aa>=-1e-6)&(bb>=-1e-6)&(cc>=-1e-6)&(z>depth[y0:y1+1,x0:x1+1])
        if not mask.any():return
        tu=np.clip((aa*uv[0,0]+bb*uv[1,0]+cc*uv[2,0]).astype(int),0,self.texture.shape[1]-1)
        tv=np.clip((aa*uv[0,1]+bb*uv[1,1]+cc*uv[2,1]).astype(int),0,self.texture.shape[0]-1)
        tex=self.texture[tv,tu]
        mask &= tex[:,:,3]>100
        region=canvas[y0:y1+1,x0:x1+1]
        region[mask]=np.clip(tex[:,:,:3][mask]*shade,0,255).astype(np.uint8)
        depth[y0:y1+1,x0:x1+1][mask]=z[mask]

    def render(self,wp,wr,label,time,ground=True):
        w,h=self.size
        bg=Image.new('RGB',self.size,'#17212b');d=ImageDraw.Draw(bg)
        if ground:
            for v in range(-300,301,25):
                for line in ([[v,0,-250],[v,0,250]],[[-300,0,v],[300,0,v]]):
                    q=self.project(np.array(line,dtype=float))
                    d.line([tuple(p[:2]) for p in q],fill='#293a45')
        canvas=np.array(bg);depth=np.full((h,w),-np.inf)
        for _,pts,cube in self.rig.vertices(wp,wr):
            projected=self.project(pts)
            for face,ids in FACES.items():
                # The geometry decoder mirrors X; swap the east/west atlas.
                uvface={'east':'west','west':'east'}.get(face,face)
                fd=cube.get('uv',{}).get(uvface)
                if not fd:continue
                x,y=fd['uv'];du,dv=fd['uv_size']
                if du==0 or dv==0:continue
                corners=CORNERS[ids]
                if face=='up':coords=np.column_stack([corners[:,0],1-corners[:,2]])
                elif face=='down':coords=np.column_stack([corners[:,0],corners[:,2]])
                elif face=='north':coords=np.column_stack([1-corners[:,0],1-corners[:,1]])
                elif face=='south':coords=np.column_stack([corners[:,0],1-corners[:,1]])
                elif face=='west':coords=np.column_stack([corners[:,2],1-corners[:,1]])
                else:coords=np.column_stack([1-corners[:,2],1-corners[:,1]])
                uv=np.array([x,y])+coords*np.array([du,dv])
                q=projected[ids]
                normal=np.cross(pts[ids[1]]-pts[ids[0]],pts[ids[2]]-pts[ids[0]])
                norm=np.linalg.norm(normal)
                if norm<1e-8:continue
                shade=.66+.34*abs(normal@np.array([.25,.8,.45])/norm)
                for tri in ([0,1,2],[0,2,3]):
                    self.triangle(canvas,depth,q[tri],uv[tri],shade)
        img=Image.fromarray(canvas);d=ImageDraw.Draw(img)
        d.rectangle((0,0,w,61),fill='#17212b')
        d.text((20,12),label,fill='#f1efe5',font=font(20))
        d.text((20,39),f'Authored wyvern / adapted ARK motion     {time:.2f}s',fill='#8fabb5',font=font(12))
        d.rectangle((0,h-25,w,h),fill='#17212b')
        d.text((20,h-21),'Offline preview from exported geometry, animation and original texture',fill='#829ba4',font=font(11))
        return img


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--clips',nargs='*')
    p.add_argument('--frames',type=int,default=20)
    p.add_argument('--overview',action='store_true',help='Also render a labeled contact sheet of all 30 clips')
    args=p.parse_args()
    folder=DRAGON/'Dragon/retargeted';out=folder/'previews';out.mkdir(exist_ok=True)
    rig=Rig(read(folder/'dragon.geo.json'));clips=read(folder/'dragon.animation.json')['animations']
    selected=args.clips or ['Dragon-Ground-Idle','Dragon-Ground-Move-Fwd','Dragon-Ground-Charge-Fwd',
                            'Dragon-Fly-Fwd','Dragon-Take-Off','Dragon-Land',
                            'Dragon-Ground-Attack-Bite','Dragon-Ground-Attack-Fire',
                            'Dragon-Ground-Attack-Wing','Dragon-Ground-Die']
    for name in selected:
        clip=clips[name];length=clip['animation_length']
        times=np.linspace(0,length,args.frames,endpoint=clip.get('loop') is not True)
        pp,rr=rig.clip(clip,times)
        ren=Renderer(rig,DRAGON/'Dragon/dragon.png',yaw=64)
        ren.bounds(zip(pp,rr))
        frames=[ren.render(wp,wr,name.replace('Dragon-',''),time,'Fly' not in name)
                for wp,wr,time in zip(pp,rr,times)]
        stem=name.replace('Dragon-','').lower()
        frames[0].save(out/f'{stem}.gif',save_all=True,append_images=frames[1:],
                       duration=max(20,round(1000*length/len(frames))),loop=0,disposal=2)
        sheet=Image.new('RGB',(1440,1040),'#17212b')
        for j,i in enumerate(np.linspace(0,len(frames)-1,4).astype(int)):
            sheet.paste(frames[i],((j%2)*720,(j//2)*520))
        sheet.save(out/f'{stem}.png')
        print('Preview',stem,flush=True)
    if args.overview:
        sheet=Image.new('RGB',(1800,1800),'#17212b')
        for j,(name,clip) in enumerate(clips.items()):
            time=clip['animation_length']*.5
            pp,rr=rig.clip(clip,[time])
            ren=Renderer(rig,DRAGON/'Dragon/dragon.png',size=(600,400),yaw=64)
            ren.bounds(zip(pp,rr))
            img=ren.render(pp[0],rr[0],name.replace('Dragon-',''),time,'Fly' not in name)
            sheet.paste(img.resize((360,300)),((j%5)*360,(j//5)*300))
        sheet.save(out/'all-clips.png')
        print('Preview all-clips',flush=True)


if __name__=='__main__':main()
