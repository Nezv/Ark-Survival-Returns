"""Source-topology-aware antlers; preserve tine gaps and the original head bone."""
import numpy as np
from scipy.sparse import coo_matrix
from scipy.sparse.csgraph import connected_components
from scipy.spatial import cKDTree


def antler_vertices(s):
    t=s.triangles
    edges=np.concatenate([t[:,[0,1]],t[:,[1,2]],t[:,[2,0]]])
    graph=coo_matrix((np.ones(len(edges)),(edges[:,0],edges[:,1])),shape=(len(s.source_vertices),)*2)
    count,labels=connected_components(graph,directed=False)
    mask=np.zeros(len(s.source_vertices),bool)
    for k in range(count):
        ix=np.flatnonzero(labels==k);p=s.source_vertices[ix]
        # Antlers are disconnected components with a broad lateral span,
        # completely weighted to the head; excludes face and ear components.
        if len(ix)>=3 and (np.abs(p[:,0]).max()>3 or p[:,1].max()>18) and np.mean(s.dominant[ix]==s.index['c_neck3'])>.95:
            mask[ix]=True
    assert mask.sum()>1000,'Could not identify Stag antlers'
    return mask


def antler_cubes(s,mask):
    tris=s.source_vertices[s.triangles[np.all(mask[s.triangles],axis=1)]]
    samples=[]
    for a in range(9):
        for b in range(9-a):
            samples.append(tris[:,0]+a/8*(tris[:,1]-tris[:,0])+b/8*(tris[:,2]-tris[:,0]))
    points=np.unique(np.round(np.concatenate(samples),3),axis=0)
    result=[];pitch=.3
    bands=np.floor(points[:,1]/pitch).astype(int)
    for band in np.unique(bands):
        p=points[bands==band]
        pairs=cKDTree(p).query_pairs(.3,output_type='ndarray')
        graph=coo_matrix((np.ones(len(pairs)),(pairs[:,0],pairs[:,1])),shape=(len(p),)*2)
        count,labels=connected_components(graph,directed=False)
        for k in range(count):
            q=p[labels==k]
            if len(q)<4:continue
            lo=q.min(0);hi=q.max(0)
            lo[1]=band*pitch;hi[1]=(band+1)*pitch
            size=np.maximum(hi-lo,.12);center=(lo+hi)/2
            result.append(dict(bone=s.index['c_neck3'],name=f'antler / branch band {len(result)+1}',origin=center-size/2,size=size,pivot=center,rotation=np.zeros(3),palette=4))
    assert result
    return result
