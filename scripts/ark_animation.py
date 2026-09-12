"""PSA -> zero-rest-rotation bone tracks, with a source-FK round-trip check.

The rest skeleton is baked into world-space pivots/cubes. Each local animation
delta must therefore be conjugated into its parent's REST world axes. Copying
PSA Euler angles directly onto zero-rotation GeckoLib bones is incorrect.
"""
import configparser
import re
import warnings
import numpy as np
from scipy.spatial.transform import Rotation as R
from ark_geometry import chunks, bone_names, struct


def read_clips(skeleton):
    for path in sorted(skeleton.folder.rglob('*.psa')):
        c = chunks(path)
        names = bone_names(c['BONENAMES'])
        index = {n:i for i,n in enumerate(names)}
        config = configparser.ConfigParser(allow_no_value=True, strict=False)
        config.optionxform = str
        config.read(path.with_suffix('.config'))
        removed = dict(config['RemoveTracks']) if config.has_section('RemoveTracks') else {}
        rotations_only = config.getboolean('AnimSet', 'bAnimRotationOnly', fallback=False)
        translations = set(config['UseTranslationBoneNames']) if config.has_section('UseTranslationBoneNames') else set()
        all_keys = np.frombuffer(c['ANIMKEYS'][2], '<f4').reshape(-1,len(names),8)
        all_scales = None
        if c.get('SCALEKEYS', (0,0))[1]:
            all_scales = np.frombuffer(c['SCALEKEYS'][2], '<f4').reshape(-1,len(names),4)
        size,count,info = c['ANIMINFO']
        assert size == 168
        for seq in range(count):
            row = struct.unpack_from('<64s64s4i3f3i',info,seq*168)
            name = row[0].split(b'\0')[0].decode()
            n, fps, start, frames = row[2], row[8], row[10], row[11]
            source_fps = fps
            timing_note = None
            assert n == len(names) and frames > 0
            if fps == 0:
                # Some ARK pose/additive clips have RateScale=0. Preserve their
                # samples as an editable pose clip using the native duration;
                # explicitly report that frozen source playback was overridden.
                props = path.with_suffix('.props.txt')
                metadata = props.read_text() if props.exists() else ''
                duration = re.search(r'^SequenceLength\s*=\s*([0-9.eE+-]+)',metadata,re.M)
                rate = re.search(r'^RateScale\s*=\s*([0-9.eE+-]+)',metadata,re.M)
                assert count == 1 and duration and rate and float(rate[1]) == 0, f'No native timing for {path}'
                seconds = float(duration[1])
                assert np.isfinite(seconds) and seconds > 0, path
                fps = frames / seconds
                timing_note = f'Source RateScale=0; editable pose clip uses native SequenceLength={seconds}s.'
            assert np.isfinite(fps) and fps > 0, path
            keys = all_keys[start:start+frames]
            assert len(keys) == frames
            t = np.broadcast_to(skeleton.t,(frames,*skeleton.t.shape)).copy()
            q = np.broadcast_to(skeleton.r.as_quat(),(frames,len(skeleton.names),4)).copy()
            scale = np.ones((frames,len(skeleton.names),3))
            for i,bone in enumerate(skeleton.names):
                if bone not in index:
                    continue
                j = index[bone]
                omit = (removed.get(f'{name}.{j}') or '').lower()
                if omit == 'all':
                    continue
                if omit != 'trans' and (not rotations_only or bone in translations):
                    t[:,i] = keys[:,j,:3][:,[0,2,1]] * [.1,.1,-.1]
                if omit != 'rot':
                    q[:,i] = keys[:,j,3:7][:,[0,2,1,3]] * [1,1,-1,-1]
                    if skeleton.parents[i] < 0:
                        q[:,i,:3] *= -1
                if all_scales is not None:
                    scale[:,i] = all_scales[start:start+frames,j,:3][:,[0,2,1]]
            # Uniform scale commutes with the rest rotation. Nonuniform scale
            # would require shear and must not be silently approximated.
            assert skeleton.native_bind_axes or np.max(np.ptp(scale,axis=2)) < 1e-5, f'Nonuniform bone scale in {path}'
            yield {'name':name,'fps':fps,'source_fps':source_fps,'timing_note':timing_note,'frames':frames,'translation':t,'quaternion':q,
                   'scale':scale,'source':path,'extra_bones':sorted(set(names)-set(skeleton.names))}


def convert_clip(s, clip):
    frames, count = clip['frames'], len(s.names)
    if s.native_bind_axes:
        with warnings.catch_warnings():
            warnings.simplefilter('ignore',UserWarning)
            absolute=R.from_quat(clip['quaternion'].reshape(-1,4)).as_euler('xyz',degrees=True).reshape(frames,count,3)
        absolute=np.rad2deg(np.unwrap(np.deg2rad(absolute),axis=0))
        angles=absolute-s.model_rotations
        offsets=clip['translation']-s.t
        decoded=R.from_euler('xyz',(angles+s.model_rotations).reshape(-1,3),degrees=True)
        error=float((decoded.inv()*R.from_quat(clip['quaternion'].reshape(-1,4))).magnitude().max())
        assert error<1e-7
        return angles,offsets,{'source_fk_max_error':None,'rotation_roundtrip_radians':error,
                              'fk_validation':'All frames validated after JSON write using full affine matrices.'}
    angles = np.zeros((frames,count,3))
    offsets = np.zeros_like(angles)
    source_p, source_r, source_scale = [], [], []
    output_p, output_r, output_scale = [], [], []
    max_angle_error = 0.0
    for i,p in enumerate(s.parents):
        parent_rest = R.identity() if p < 0 else s.wr[p]
        local = R.from_quat(clip['quaternion'][:,i])
        delta = parent_rest * local * s.r[i].inv() * parent_rest.inv()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            euler = delta.as_euler('xyz',degrees=True)
        euler = np.rad2deg(np.unwrap(np.deg2rad(euler),axis=0))
        angles[:,i] = euler
        offsets[:,i] = parent_rest.apply(clip['translation'][:,i]-s.t[i])
        # Independent source hierarchy vs exported zero-rest bone hierarchy.
        decoded = R.from_euler('xyz', euler, degrees=True)
        max_angle_error = max(max_angle_error,float(np.max((decoded.inv()*delta).magnitude())))
        sc = clip['scale'][:,i,0]
        if p < 0:
            source_p.append(clip['translation'][:,i])
            source_r.append(local)
            source_scale.append(sc)
            output_p.append(s.wp[i]+offsets[:,i])
            output_r.append(decoded)
            output_scale.append(sc)
        else:
            source_p.append(source_p[p]+source_r[p].apply(clip['translation'][:,i]*source_scale[p][:,None]))
            source_r.append(source_r[p]*local)
            source_scale.append(source_scale[p]*sc)
            output_p.append(output_p[p]+output_r[p].apply((s.wp[i]-s.wp[p]+offsets[:,i])*output_scale[p][:,None]))
            output_r.append(output_r[p]*decoded)
            output_scale.append(output_scale[p]*sc)
    error = float(np.max(np.abs(np.array(source_p)-np.array(output_p))))
    # Also check an arbitrary rigid vertex attached to every bone.
    for i in range(count):
        probe = np.array([.3,.7,-.2])
        a = source_p[i]+source_r[i].apply(probe*source_scale[i][:,None])
        b = output_p[i]+output_r[i].apply(s.wr[i].apply(probe)*output_scale[i][:,None])
        error = max(error,float(np.max(np.abs(a-b))))
    assert error < 1e-7 and max_angle_error < 1e-7, (clip['name'],error,max_angle_error)
    return angles,offsets,{'source_fk_max_error':error,'rotation_roundtrip_radians':max_angle_error}


def reduce_linear(values,tolerance):
    """Bounded linear key reduction; all retained values remain source samples."""
    if len(values)==1 or np.max(abs(values-values[0])) <= tolerance:
        return [0]
    keep = {0,len(values)-1}
    stack = [(0,len(values)-1)]
    while stack:
        a,b = stack.pop()
        if b-a < 2:
            continue
        alpha = np.arange(1,b-a)[:,None]/(b-a)
        error = np.max(abs(values[a+1:b]-(values[a]+alpha*(values[b]-values[a]))),axis=1)
        j = int(np.argmax(error))
        if error[j] > tolerance:
            k = a+1+j
            keep.add(k)
            stack.extend([(a,k),(k,b)])
    return sorted(keep)


def is_loop(name):
    if re.search(r'[-_]loop$',name,re.I):
        return True
    return bool(re.search(r'(walk|run|sprint|move|charge|swim|fly|hover|idle|glide|torpid$)',name,re.I)) and not re.search(r'(aim|hurt|hit|start|stop|land|take[-_]?off|death|dead|attack|[-_](in|out)([-_]|$))',name,re.I)


def animation_tracks(s,clip,angles,offsets):
    bones = {}
    retained = 0
    for i,name in enumerate(s.names):
        tracks = {}
        # Bedrock/GeckoLib X coordinate is mirrored; rotations X/Y flip.
        for channel,values,tol,neutral in [
            ('rotation',angles[:,i]*[-1,-1,1],.03,0),
            ('position',offsets[:,i]*[-1,1,1],.0005,0),
            ('scale',clip['scale'][:,i],.00001,1),
        ]:
            if np.max(abs(values-neutral)) < 1e-7:
                continue
            indices = reduce_linear(values,tol)
            retained += len(indices)
            tracks[channel] = {format(k/clip['fps'],'.9f').rstrip('0').rstrip('.') or '0': np.round(values[k],6).tolist() for k in indices}
        if tracks:
            bones[name] = tracks
    return {'loop':is_loop(clip['name']),'animation_length':clip['frames']/clip['fps'],
            'override_previous_animation':True,'bones':bones},retained
