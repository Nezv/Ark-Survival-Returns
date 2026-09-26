"""Check saved wyvern geometry, contact motion and Blockbench/JSON parity.

Runs independently of the retargeter's IK and forward-kinematics code. Reads
the original and retargeted projects, writes retargeted/validation.json, and
exits nonzero on a failed check. Does not install or run Minecraft.
"""
from pathlib import Path
import base64
import collections
import hashlib
import json
import math
import numpy as np
from scipy.spatial.transform import Rotation as R

ROOT=Path(__file__).resolve().parents[1]
DRAGON=ROOT/'Creatures/Dragon'
OUT=DRAGON/'Dragon/retargeted'


def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))


def evaluate(geo,clip,times):
    """Homogeneous-matrix evaluator of saved Bedrock channels."""
    bones=geo['minecraft:geometry'][0]['bones']
    world={};pivots={}
    for b in bones:
        name=b['name'];pivot=np.array(b['pivot'])*[-1,1,1];pivots[name]=pivot
        channels=clip.get('bones',{}).get(name,{})
        vectors={}
        for channel,default in (('rotation',[0,0,0]),('position',[0,0,0])):
            track=channels.get(channel)
            if track:
                pairs=sorted((float(k),v) for k,v in track.items())
                vectors[channel]=np.array([np.interp(times,[x[0] for x in pairs],
                                                     [x[1][i] for x in pairs]) for i in range(3)]).T
            else:vectors[channel]=np.tile(default,(len(times),1)).astype(float)
        angles=(vectors['rotation']+np.array(b.get('rotation',[0,0,0])))*[-1,-1,1]
        local=np.tile(np.eye(4),(len(times),1,1))
        local[:,:3,:3]=R.from_euler('xyz',angles,degrees=True).as_matrix()
        position=pivot+vectors['position']*[-1,1,1]
        # T(pivot + offset) R T(-pivot), then compose with parent.
        local[:,:3,3]=position-np.einsum('fij,j->fi',local[:,:3,:3],pivot)
        world[name]=world[b['parent']]@local if 'parent' in b else local
    return world,pivots


def all_corners(cube,bone):
    from itertools import product
    size=np.array(cube['size']);o=np.array(cube['origin'],float);o[0]=-o[0]-size[0]
    inf=cube.get('inflate',0)
    pts=o-inf+np.array(list(product([0,1],repeat=3)))*(size+2*inf)
    pivot=np.array(cube.get('pivot',bone['pivot']))*[-1,1,1]
    rot=R.from_euler('xyz',np.array(cube.get('rotation',[0,0,0]))*[-1,-1,1],degrees=True)
    pts=rot.apply(pts-pivot)+pivot
    return np.column_stack([pts,np.ones(8)])


def main():
    geo=read(OUT/'dragon.geo.json');original=read(DRAGON/'Dragon/dragon.geo.json')
    anims=read(OUT/'dragon.animation.json')['animations']
    old=read(DRAGON/'Old/animations/dragon.animation.json')['animations']
    bb=read(OUT/'Dragon_Wyvern_Retargeted.bbmodel');metrics=read(OUT/'retarget_metrics.json')
    bones=geo['minecraft:geometry'][0]['bones'];by={b['name']:b for b in bones}
    assert set(anims)==set(old), 'Source clip inventory changed'
    assert len(by)==len(bones), 'Duplicate bones'
    assert (OUT/'dragon.png').read_bytes()==(DRAGON/'Dragon/dragon.png').read_bytes(), 'Texture changed'
    assert base64.b64decode(bb['textures'][0]['source'].split(',',1)[1])==(OUT/'dragon.png').read_bytes()
    canon=lambda c:json.dumps(c,sort_keys=True)
    assert collections.Counter(canon(c) for b in bones for c in b.get('cubes',[]))==collections.Counter(
        canon(c) for b in original['minecraft:geometry'][0]['bones'] for c in b.get('cubes',[])), 'Cube/UV artwork changed'
    # Rebinding must leave the authored bind silhouette unchanged.
    bw,_=evaluate(geo,{},[0]);ow,_=evaluate(original,{},[0]);original_world={}
    for b in original['minecraft:geometry'][0]['bones']:
        for c in b.get('cubes',[]):original_world.setdefault(canon(c),[]).append((ow[b['name']][0]@all_corners(c,b).T).T)
    bind_error=0
    for b in bones:
        for c in b.get('cubes',[]):
            actual=(bw[b['name']][0]@all_corners(c,b).T).T
            expected=original_world[canon(c)].pop()
            bind_error=max(bind_error,float(np.max(abs(actual-expected))))
    assert bind_error<1e-5, ('Changed bind silhouette',bind_error)
    groups={g['uuid']:g for g in bb['groups']}
    assert {g['name'] for g in groups.values()}==set(by)
    assert len(bb['elements'])==sum(len(b.get('cubes',[])) for b in bones)
    for g in groups.values():
        b=by[g['name']]
        assert np.allclose(g['origin'],np.array(b['pivot'])*[-1,1,1])
        assert np.allclose(g['rotation'],np.array(b.get('rotation',[0,0,0]))*[-1,-1,1])
    source_info=read(OUT/'retarget_map.json')
    for file,sha in source_info['source_hashes'].items():
        assert hashlib.sha256((ROOT/file).read_bytes()).hexdigest()==sha, ('Input changed',file)
    cubes=[(b,c) for b in bones for c in b.get('cubes',[])]
    for element,(b,c) in zip(bb['elements'],cubes):
        lo=np.array(c['origin'],float);sz=np.array(c['size']);lo[0]=-lo[0]-sz[0]
        assert np.allclose(element['from'],lo) and np.allclose(element['to'],lo+sz)
        assert np.allclose(element['origin'],np.array(c.get('pivot',b['pivot']))*[-1,1,1])
        assert np.allclose(element['rotation'],np.array(c.get('rotation',[0,0,0]))*[-1,-1,1])
        for face,data in c['uv'].items():
            x,y=data['uv'];w,h=data['uv_size'];editor_face={'east':'west','west':'east'}.get(face,face)
            assert np.allclose(element['faces'][editor_face]['uv'],[x,y,x+w,y+h])
    assert {a['name'] for a in bb['animations']}==set(anims)
    bba={a['name']:a for a in bb['animations']}
    results={};worst_contact=0;worst_ground=0;worst_seam=0;total=0
    for name,clip in anims.items():
        assert clip['animation_length']==old[name]['animation_length']
        expected_loop='hold_on_last_frame' if 'Die' in name else old[name]['loop']
        assert clip['loop']==expected_loop
        assert bba[name]['loop']==('hold' if 'Die' in name else 'loop' if expected_loop else 'once')
        assert set(clip['bones'])<=set(by)
        tracks={}
        for key,animator in bba[name]['animators'].items():
            assert key in groups and groups[key]['name']==animator['name']
            for f in animator['keyframes']:
                assert f['interpolation']=='linear'
                v=np.array([f['data_points'][0][a] for a in 'xyz'])
                tracks[(animator['name'],f['channel'],round(f['time'],8))]=v
        for bone,channels in clip['bones'].items():
            for channel,track in channels.items():
                ts=np.array([float(k) for k in track]);vs=np.array(list(track.values()))
                assert np.isfinite(vs).all() and np.all(np.diff(ts)>0)
                assert ts[0]==0 and abs(ts[-1]-clip['animation_length'])<1e-7
                for time,v in zip(ts,vs):
                    expected=v*([-1,-1,1] if channel=='rotation' else [-1,1,1])
                    assert np.max(abs(tracks[(bone,channel,round(time,8))]-expected))<1e-7
        times=np.array(metrics[name]['times'])
        world,pivots=evaluate(geo,clip,times)
        contact_error=0
        for j,goals in enumerate(metrics[name]['contact_goals']):
            for bone,goal in goals.items():
                # Independently recover the same physical sole corner from
                # the saved cube geometry's original bind orientation.
                b=by[bone];pts=np.concatenate([all_corners(c,b) for c in b.get('cubes',[])])
                bind=(bw[bone][0]@pts.T).T
                contact=pts[np.argmin(bind[:,1])]
                actual=(world[bone][j]@contact)[:3]
                contact_error=max(contact_error,float(np.linalg.norm(actual-goal)))
        # Check frames AND midpoints, since interpolation can penetrate even
        # when every baked contact key is exactly on the floor.
        dense=np.sort(np.concatenate([times,(times[:-1]+times[1:])/2]))
        dense_world,_=evaluate(geo,clip,dense)
        minimum=math.inf;minimum_bone=''
        if 'Ground' in name or name in ('Dragon-Land','Dragon-Take-Off'):
            for b in bones:
                for c in b.get('cubes',[]):
                    yy=np.einsum('fij,kj->fki',dense_world[b['name']],all_corners(c,b))[:,:,1]
                    bottom=float(yy.min())
                    if bottom<minimum:minimum=bottom;minimum_bone=b['name']
        seam=0
        if clip['loop'] is True:
            seam=max(float(np.max(abs(w[0]-w[-1]))) for w in world.values())
        assert seam<1e-5, ('Loop seam',name,seam)
        worst_contact=max(worst_contact,contact_error)
        worst_ground=min(worst_ground,minimum);worst_seam=max(worst_seam,seam);total+=len(dense)
        results[name]={'samples_checked':len(dense),'max_contact_error':contact_error,
                       'minimum_y':minimum if math.isfinite(minimum) else None,
                       'lowest_bone':minimum_bone,'loop_seam_error':seam}
    report={'passed':worst_contact<.12 and worst_ground>-.6,'clips':len(anims),'bones':len(bones),
            'cubes':len(bb['elements']),'samples_checked':total,'bind_preservation_error':bind_error,
            'max_contact_error':worst_contact,'lowest_ground_point':worst_ground,
            'max_loop_seam_error':worst_seam,'checks':['All 30 names and durations preserved',
            'Loop/one-shot/held-death semantics','Cube and UV identity; texture SHA-equivalence',
            'Bind silhouette before/after rebinding','Blockbench UUIDs and all timeline values',
            'Independent homogeneous-matrix contact evaluation','Baked keys and interpolation midpoints',
            'Ground clearance and seamless loops'],
            'runtime_test':'Not run in Minecraft. Offline retarget and textured previews; artistic review pending.',
            'clips_detail':results}
    (OUT/'validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='clips_detail'},indent=2))
    for name,r in results.items():
        if r['max_contact_error']>.12 or (r['minimum_y'] is not None and r['minimum_y']<-.6):print(name,r)
    if not report['passed']:raise SystemExit(1)


if __name__=='__main__':main()
