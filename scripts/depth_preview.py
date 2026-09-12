"""Optional accelerated depth-buffer rasterizer for dense model previews."""
import numpy as np
try:
    from numba import njit
except ImportError:
    njit=None

if njit:
    @njit(cache=True)
    def _draw(pixels,triangles,colors):
        height,width=pixels.shape[:2]
        depth=np.full((height,width),-np.inf)
        for i in range(len(triangles)):
            a,b,c=triangles[i]
            denom=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(denom)<1e-10:continue
            left=max(0,int(np.floor(min(a[0],b[0],c[0]))));right=min(width-1,int(np.ceil(max(a[0],b[0],c[0]))))
            top=max(0,int(np.floor(min(a[1],b[1],c[1]))));bottom=min(height-1,int(np.ceil(max(a[1],b[1],c[1]))))
            for y in range(top,bottom+1):
                for x in range(left,right+1):
                    px=x+.5;py=y+.5
                    u=((b[1]-c[1])*(px-c[0])+(c[0]-b[0])*(py-c[1]))/denom
                    v=((c[1]-a[1])*(px-c[0])+(a[0]-c[0])*(py-c[1]))/denom
                    w=1-u-v
                    if u>=-1e-9 and v>=-1e-9 and w>=-1e-9:
                        z=u*a[2]+v*b[2]+w*c[2]
                        if z>depth[y,x]:
                            depth[y,x]=z
                            pixels[y,x]=colors[i]
        return pixels

def rasterize(canvas,quads,colors):
    if njit is None:return None
    triangles=np.asarray(quads)[:,[[0,1,2],[0,2,3]],:].reshape(-1,3,3)
    return _draw(np.asarray(canvas).copy(),triangles,np.repeat(np.asarray(colors,dtype=np.uint8),2,axis=0))
