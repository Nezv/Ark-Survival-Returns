"""Validate saved JSON/Blockbench resources against original ActorX animation."""
import argparse
import json
import hashlib
import numpy as np
from scipy.spatial.transform import Rotation as R
from ark_geometry import ROOT,Skeleton
from ark_animation import read_clips,convert_clip
from extract_dinosaurs import SPECIES, resource_names, CREATURES
from preview_dinosaurs import decode_geometry

def validate(label):
    out=CREATURES/label;identifier,bb_filename=resource_names(label);s=Skeleton(SPECIES[label],out/'source')
    geo=json.loads((out/f'geo/{identifier}.geo.json').read_text())
    anim=json.loads((out/f'animations/{identifier}.animation.json').read_text())['animations']
    bb=json.loads((out/bb_filename).read_text())
    report=json.loads((out/'build_report.json').read_text())
    for path,digest in report['source_hashes'].items():
        assert hashlib.sha256((out/'source'/path).read_bytes()).hexdigest()==digest, f'Source changed: {path}'
    decoded=decode_geometry(geo)
    names,pivots,parents,cubes=decoded
    assert names==s.names and len(set(names))==len(names)
    assert parents==s.parents and np.max(abs(pivots-s.model_pivots))<.000001
    assert np.max(abs(decoded.rotations-s.model_rotations))<.000001
    assert len(bb['elements'])==len(cubes)==report['cubes']
    assert [g['name'] for g in bb['groups']]==names
    assert set(a['name'] for a in bb['animations'])==set(anim)
    by_uuid={g['uuid']:g['name'] for g in bb['groups']}
    for a in bb['animations']:
        for key,track in a['animators'].items():
            assert key in by_uuid and track['name']==by_uuid[key]
    # Verify the separately generated Blockbench elements and geometry agree.
    from preview_dinosaurs import CORNERS
    for element,(_,corners,_) in zip(bb['elements'],[c for i in range(len(names)) for c in cubes if c[0]==i]):
        lo=np.array(element['from']);hi=np.array(element['to']);pivot=np.array(element['origin'])
        original=R.from_euler('xyz',element['rotation'],degrees=True).apply(lo+CORNERS*(hi-lo)-pivot)+pivot
        assert np.max(abs(original-corners))<.00001
    max_rotation=0.;max_position=0.;max_fk=0.;total_frames=0;clip_names=[]
    for clip in read_clips(s):
        name=clip['name'];clip_names.append(name);frames=clip['frames'];total_frames+=frames
        exported=anim[name]
        assert set(exported['bones'])<=set(names)
        assert abs(exported['animation_length']-frames/clip['fps'])<1e-8
        angles,offsets,_=convert_clip(s,clip)
        output_p=[];output_r=[];output_sc=[]
        expected_p=[];expected_r=[];expected_sc=[]
        for i,p in enumerate(parents):
            tracks=exported['bones'].get(names[i],{});channels={}
            for channel,ref,sign,neutral,tol in [
                ('rotation',angles[:,i],[-1,-1,1],0,.03001),
                ('position',offsets[:,i],[-1,1,1],0,.000502),
                ('scale',clip['scale'][:,i],[1,1,1],1,.00002),
            ]:
                if channel not in tracks:
                    values=np.full((frames,3),neutral,dtype=float)
                else:
                    track=tracks[channel];times=np.array([float(t) for t in track]);v=np.array(list(track.values()))*sign
                    assert np.all(np.diff(times)>0) and times[0]>=0 and times[-1]<exported['animation_length']+.000001
                    values=np.column_stack([np.interp(np.arange(frames)/clip['fps'],times,v[:,j]) for j in range(3)])
                assert np.isfinite(values).all()
                error=float(np.max(abs(ref-values)))
                assert error<tol,(label,name,names[i],channel,error)
                if channel=='rotation':max_rotation=max(max_rotation,error)
                if channel=='position':max_position=max(max_position,error)
                channels[channel]=values
            rot=R.from_euler('xyz',channels['rotation']+decoded.rotations[i],degrees=True).as_matrix()*channels['scale'][:,None,:]
            local=R.from_quat(clip['quaternion'][:,i]).as_matrix()*clip['scale'][:,i,None,:]
            if p<0:
                output_p.append(pivots[i]+channels['position']);output_r.append(rot)
                expected_p.append(clip['translation'][:,i]);expected_r.append(local)
            else:
                output_p.append(output_p[p]+np.einsum('fij,fj->fi',output_r[p],pivots[i]-pivots[p]+channels['position']))
                output_r.append(output_r[p]@rot)
                expected_p.append(expected_p[p]+np.einsum('fij,fj->fi',expected_r[p],clip['translation'][:,i]))
                expected_r.append(expected_r[p]@local)
        error=float(np.max(np.linalg.norm(np.array(output_p)-np.array(expected_p),axis=2)))
        max_fk=max(max_fk,error)
    assert set(clip_names)==set(anim)
    assert max_fk<np.ptp(s.source_vertices,axis=0).max()*.003,(label,max_fk)
    result={'passed':True,'clips':len(clip_names),'source_frames_checked':total_frames,
        'bones':len(names),'cubes':len(cubes),'max_rotation_key_error_degrees':max_rotation,
        'max_position_key_error_model_units':max_position,'max_world_bone_error_model_units':max_fk,
        'checks':['Source bone names and parent topology','All source clips present',
                  'Blockbench animation UUID bindings','GeckoLib/Blockbench cube equivalence',
                  'Every source frame vs reduced JSON tracks','Decoded output forward kinematics vs source'],
        'runtime_test':'Not run inside Minecraft; software validation and software-rendered previews only.'}
    (out/'validation.json').write_text(json.dumps(result,indent=2)+'\n')
    print(f'{label}: PASS, {len(clip_names)} clips / {total_frames} source frames, max world error {max_fk:.6g}',flush=True)
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--species',nargs='+',choices=SPECIES,default=list(SPECIES))
    args=p.parse_args()
    for label in args.species:validate(label)
