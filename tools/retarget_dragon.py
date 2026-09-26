"""Retarget Old/Dragon clips to the authored wing-walking wyvern.

Reads the two preserved GeckoLib projects; writes only Dragon/retargeted.
No game assets are installed. Run from any directory with Python + numpy,
scipy and Pillow. Geometry/UV/texture artwork is preserved; root torso cubes
are bound to body and each existing wing claw gets its own editable bone.
"""
from __future__ import annotations

import argparse
import base64
import copy
import hashlib
import json
import math
from pathlib import Path
import uuid
import warnings

import numpy as np
from scipy.spatial.transform import Rotation as R, Slerp

ROOT = Path(__file__).resolve().parents[1]
DRAGON = ROOT / "Creatures/Dragon"
POS_SIGN = np.array([-1., 1., 1.])
ROT_SIGN = np.array([-1., -1., 1.])
ALIGN = np.diag([1., 1., -1.])  # old faces +Z, authored wyvern faces -Z
CORNERS = np.array([[0,0,0],[1,0,0],[1,1,0],[0,1,0],
                    [0,0,1],[1,0,1],[1,1,1],[0,1,1]])
FACES = {"north":[0,3,2,1], "south":[4,5,6,7], "down":[0,1,5,4],
         "up":[3,7,6,2], "west":[0,4,7,3], "east":[1,2,6,5]}


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def write(path, data):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(json.dumps(data, indent=1, allow_nan=False)+"\n", encoding="utf-8")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "ark/wyvern-retarget/"+name))


def sample(track, times, default=(0,0,0)):
    if track is None:
        return np.tile(default, (len(times), 1)).astype(float)
    if not isinstance(track, dict):
        return np.tile(track, (len(times), 1)).astype(float)
    keys = sorted((float(k), np.array(v.get("post", v) if isinstance(v, dict) else v,
                                    dtype=float)) for k,v in track.items())
    ts, values = np.array([x[0] for x in keys]), np.array([x[1] for x in keys])
    return np.column_stack([np.interp(times, ts, values[:,j]) for j in range(3)])


class Rig:
    def __init__(self, doc):
        self.doc = doc
        self.bones = doc["minecraft:geometry"][0]["bones"]
        self.names = [b["name"] for b in self.bones]
        self.index = {n:i for i,n in enumerate(self.names)}
        assert len(self.index) == len(self.names), "Duplicate bone"
        self.parents = [self.index[b["parent"]] if "parent" in b else -1 for b in self.bones]
        assert all(p < i for i,p in enumerate(self.parents)), "Non-topological rig"
        self.pivots = np.array([b["pivot"] for b in self.bones])*POS_SIGN
        self.rest = np.array([b.get("rotation", [0,0,0]) for b in self.bones])*ROT_SIGN
        self.cubes = []
        for i,b in enumerate(self.bones):
            for c in b.get("cubes", []):
                size = np.array(c["size"], dtype=float)
                lo = np.array(c["origin"], dtype=float)
                lo[0] = -lo[0]-size[0]
                inflate = c.get("inflate", 0)
                cp = np.array(c.get("pivot", b["pivot"]))*POS_SIGN
                cr = R.from_euler("xyz", np.array(c.get("rotation", [0,0,0]))*ROT_SIGN, degrees=True)
                pts = cr.apply(lo-inflate+CORNERS*(size+2*inflate)-cp)+cp
                self.cubes.append((i, pts, c))
        self.refp, self.refr = self.fk(np.zeros((1,len(self.names),3)), np.zeros((1,len(self.names),3)))
        self.refp, self.refr = self.refp[0], self.refr[0]

    def fk(self, angles, offsets):
        n = len(angles)
        mat = R.from_euler("xyz", (angles+self.rest).reshape(-1,3), degrees=True).as_matrix().reshape(n,-1,3,3)
        wp, wr = np.zeros_like(angles), np.zeros_like(mat)
        for i,p in enumerate(self.parents):
            if p < 0:
                wp[:,i], wr[:,i] = self.pivots[i]+offsets[:,i], mat[:,i]
            else:
                wp[:,i] = wp[:,p]+np.einsum("fij,fj->fi",wr[:,p],self.pivots[i]-self.pivots[p]+offsets[:,i])
                wr[:,i] = wr[:,p]@mat[:,i]
        return wp,wr

    def clip(self, clip, times):
        angles = np.zeros((len(times),len(self.names),3))
        offsets = np.zeros_like(angles)
        for name, channels in clip.get("bones",{}).items():
            i = self.index[name]
            angles[:,i] = sample(channels.get("rotation"),times)*ROT_SIGN
            offsets[:,i] = sample(channels.get("position"),times)*POS_SIGN
            scale = sample(channels.get("scale"),times,(1,1,1))
            assert np.max(abs(scale-1)) < 1e-4, "Unsupported non-unit animated scale"
        return self.fk(angles, offsets)

    def vertices(self, wp,wr):
        return [(i, wp[i]+(pts-self.pivots[i])@wr[i].T,c) for i,pts,c in self.cubes]


def patched_geometry(original):
    doc = copy.deepcopy(original)
    bones = doc["minecraft:geometry"][0]["bones"]
    by = {b["name"]:b for b in bones}
    by["body"]["cubes"] = by["root"].pop("cubes", [])+by["body"].get("cubes",[])
    # Zero-rest child preserves the claw's exact bind placement and cube UVs.
    for side in ("l","r"):
        hand = by[f"wing_{side}_hand"]
        claw = {"name":f"wing_{side}_claw", "parent":hand["name"],
                "pivot":hand["pivot"].copy(), "cubes":hand["cubes"][1:]}
        hand["cubes"] = hand["cubes"][:1]
        bones.insert(bones.index(hand)+1, claw)
    return doc


def mean_pose(source, clip):
    p,r = source.clip(clip,np.linspace(0,clip["animation_length"],60,endpoint=False))
    return p.mean(0),np.array([R.from_matrix(r[:,i]).mean().as_matrix() for i in range(len(source.names))])


def fractional_rotation(rotations, names, fraction, index):
    at = np.clip(fraction,0,1)*(len(names)-1)
    lo,hi = int(np.floor(at)), int(np.ceil(at))
    if lo == hi:
        return rotations[index[names[lo]]]
    return Slerp([0,1],R.from_matrix([rotations[index[names[lo]]],rotations[index[names[hi]]]]))([at-lo]).as_matrix()[0]


def blend_rotation(a,b,weight):
    return a @ R.from_rotvec(R.from_matrix(a.T@b).as_rotvec()*weight).as_matrix()


def swing(a,b):
    a,b = a/np.linalg.norm(a),b/np.linalg.norm(b)
    cross = np.cross(a,b)
    dot = float(np.clip(a@b,-1,1))
    if np.linalg.norm(cross)<1e-8:
        if dot>0:return np.eye(3)
        axis=np.cross(a,[0,1,0] if abs(a[1])<.9 else [1,0,0])
        return R.from_rotvec(axis/np.linalg.norm(axis)*math.pi).as_matrix()
    return R.from_rotvec(cross/np.linalg.norm(cross)*math.acos(dot)).as_matrix()


def smooth(a,b,x):
    u=np.clip((x-a)/(b-a),0,1)
    return u*u*(3-2*u)


class Retarget:
    def __init__(self, source, target, clips):
        self.s,self.t,self.clips = source,target,clips
        self.gp,self.gr = mean_pose(source,clips["Dragon-Ground-Idle"])
        self.ap,self.ar = mean_pose(source,clips["Dragon-Fly-Fwd"])
        self.body_scale = (target.refp[target.index["body"],1] /
                           self.gp[source.index["c_back1"],1])
        # Anatomical correspondence: cumulative neck/tail orientation is
        # resampled by chain fraction, avoiding lost bends or double rotation.
        self.mapping = {"body":"c_back1", "head":"c_head", "jaw":"c_jaw"}
        for side in ("l","r"):
            self.mapping.update({f"wing_{side}_arm":f"{side}_wingShoulder",
                                 f"wing_{side}_fore":f"{side}_wingElbow",
                                 f"wing_{side}_hand":f"{side}_wingWrist",
                                 f"wing_{side}_claw":f"{side}_wingClaw",
                                 f"leg_{side}_thigh":f"{side}_leg",
                                 f"leg_{side}_shin":f"{side}_knee",
                                 f"leg_{side}_foot":f"{side}_ankle",
                                 f"leg_{side}_toes":f"{side}_toeB1"})
            for f,letter in enumerate("ABCD",1):
                for suffix,part in (("",1),("b",2),("c",3)):
                    self.mapping[f"wing_{side}_finger{f}{suffix}"]=f"{side}_wing{letter}{part}"
        self.mapping.update({f"neck{i}":("neck",(i-1)/3) for i in range(1,5)})
        self.mapping.update({f"tail{i}":("tail",(i-1)/6) for i in range(1,8)})
        self.mapping["tail_fin"]="c_tail9"
        self.air_rest=target.rest.copy()
        # An extended wing must cancel the large authored folded Euler rest.
        # Copying the seven earlier animation deltas would leave it folded.
        for side,sgn in (("l",1),("r",-1)):
            for part in ("arm","fore","hand"):
                self.air_rest[target.index[f"wing_{side}_{part}"]]=[0,0,sgn*3 if part=="arm" else 0]
            for part,delta in (("thigh",-80),("shin",24),("foot",-30),("toes",-40)):
                self.air_rest[target.index[f"leg_{side}_{part}"],0]+=delta
        self.airp,self.airr=target.fk((self.air_rest-target.rest)[None],np.zeros((1,len(target.names),3)))
        self.airp,self.airr=self.airp[0],self.airr[0]
        _,launch=source.clip(clips['Dragon-Fly-Fwd'],[0])
        self.launch=self.airr.copy()
        for bone,entry in self.mapping.items():
            i=target.index[bone]
            delta=ALIGN@self.source_rot(launch[0],entry)@self.source_rot(self.ar,entry).T@ALIGN
            self.launch[i]=delta@self.airr[i]
        # Ground contact probes are actual lowest corners, including the claw's
        # authored cube rotation, rather than an arbitrary wrist height.
        self.contacts={}
        for side in ("l","r"):
            for kind,bone in (("wing",f"wing_{side}_claw"),("hind",f"leg_{side}_toes")):
                i=target.index[bone]
                pts=np.concatenate([p for j,p,c in target.vertices(target.refp,target.refr) if j==i])
                contact=pts[np.argmin(pts[:,1])].copy()
                local=target.refr[i].T@(contact-target.refp[i])
                self.contacts[(side,kind)]=(bone,contact,local)
        self.metrics={}

    def source_rot(self, rotations, entry):
        if isinstance(entry,tuple):
            kind,f=entry
            chain=[f"c_{kind}{i}" for i in range(1,6 if kind=="neck" else 10)]
            return fractional_rotation(rotations,chain,f,self.s.index)
        return rotations[self.s.index[entry]]

    def ik(self, wp,wr,upper,lower,end,goal,bend_ref):
        t=self.t
        a,b,c=[t.index[n] for n in (upper,lower,end)]
        start=wp[a]
        l1=np.linalg.norm(t.pivots[b]-t.pivots[a])
        l2=np.linalg.norm(t.pivots[c]-t.pivots[b])
        ray=goal-start
        dist=np.linalg.norm(ray)
        d=float(np.clip(dist,abs(l1-l2)+.001,l1+l2-.001))
        axis=ray/max(dist,1e-9)
        bend=bend_ref-axis*(bend_ref@axis)
        if np.linalg.norm(bend)<1e-7:bend=np.cross(axis,[0,0,1])
        bend/=np.linalg.norm(bend)
        along=(l1*l1-l2*l2+d*d)/(2*d)
        joint=start+axis*along+bend*math.sqrt(max(0,l1*l1-along*along))
        actual=start+axis*d
        wr[a]=swing(t.refr[a]@(t.pivots[b]-t.pivots[a]),joint-start)@t.refr[a]
        wr[b]=swing(t.refr[b]@(t.pivots[c]-t.pivots[b]),actual-joint)@t.refr[b]
        wp[b],wp[c]=joint,actual
        return float(np.linalg.norm(actual-goal))

    def build_clip(self,name,clip,fps=30):
        s,t=self.s,self.t
        length=clip["animation_length"]
        times=np.linspace(0,length,max(2,math.ceil(length*fps)+1))
        sp,sr=s.clip(clip,times)
        airborne="Fly" in name
        transition=name in ("Dragon-Land","Dragon-Take-Off")
        death="Die" in name
        locomotion=any(x in name for x in ("Move","Charge","Turn"))
        charge="Charge" in name
        loop=clip.get("loop",False)
        angles=np.zeros((len(times),len(t.names),3));offsets=np.zeros_like(angles)
        contact_errors=[];contact_goals=[];transition_adjustments=[]
        # Determine the source hindfoot swing timing. Foreclaws take a quarter
        # cycle stagger, because the old independent arms cannot become wings.
        phases={}
        for side in ("l","r"):
            foot=sp[:,s.index[f"{side}_toeB3"]]
            phases[side]=float(times[np.argmax(foot[:,1])]/length)
        for frame,time in enumerate(times):
            u=time/length
            air=1. if airborne else 0.
            if name=="Dragon-Take-Off":air=float(smooth(.22,.78,u))
            if name=="Dragon-Land":air=1-float(smooth(.18,.76,u))
            source_ref=self.ar if airborne else self.gr
            target_ref=self.airr if airborne else t.refr
            desired=t.refr.copy()
            for bone,entry in self.mapping.items():
                i=t.index[bone]
                delta=ALIGN@self.source_rot(sr[frame],entry)@self.source_rot(source_ref,entry).T@ALIGN
                desired[i]=delta@target_ref[i]
            if transition:
                # Ground and flight reference states are matched independently;
                # smoothly join them while retaining source action timing.
                for bone,entry in self.mapping.items():
                    i=t.index[bone]
                    da=ALIGN@self.source_rot(sr[frame],entry)@self.source_rot(self.ar,entry).T@ALIGN
                    desired[i]=blend_rotation(desired[i],da@self.airr[i],air)
            # Hand/finger anatomy stays target-authored on the ground. Wings
            # are contact limbs; source wing flapping is applied only in air.
            for side in ("l","r"):
                for n in t.names:
                    if n.startswith(f"wing_{side}_"):
                        i=t.index[n]
                        # Unfold toward the raised flight pose. The old landing
                        # wing fold passes through the floor on these much
                        # longer spars, so its rotations cannot be reused here.
                        desired[i]=blend_rotation(t.refr[i],self.launch[i] if transition else desired[i],air)
            body=t.index["body"]
            if air<1:
                desired[body]=blend_rotation(t.refr[body],desired[body],.4+.6*air)
            src_body=s.index["c_back1"]
            refp=self.ap if airborne else self.gp
            delta_p=ALIGN@(sp[frame,src_body]-refp[src_body])*self.body_scale
            # Keep locomotion in-place; horizontal root travel belongs to the
            # entity controller. Vertical weight shifts and action dips survive.
            delta_p[0]*=.25;delta_p[2]=0
            delta_p[1]*=.40
            offsets[frame,body]=delta_p
            offsets[frame,body,1]-=12*(1-air)
            if death:
                # The old six-limbed fall rolls through the very long target
                # wing spars. Settle the wyvern between its braced wrists,
                # preserving the source head, jaw and tail recoil instead.
                offsets[frame,body,1]=-12-18*float(smooth(.10,.78,u))
            if airborne:offsets[frame,body,1]+=30
            if transition:offsets[frame,body,1]+=air*30
            wp=t.refp.copy();wr=t.refr.copy()
            # Reconstruct topologically, including authored helper bones.
            for i,p in enumerate(t.parents):
                if p<0:
                    wp[i]=t.pivots[i];wr[i]=R.from_euler("xyz",t.rest[i],degrees=True).as_matrix()
                else:
                    wp[i]=wp[p]+wr[p]@(t.pivots[i]-t.pivots[p]+offsets[frame,i])
                    wr[i]=desired[i] if t.names[i] in self.mapping else wr[p]@R.from_euler("xyz",t.rest[i],degrees=True).as_matrix()
            goals={}
            # Exact two-link solves against world-space contact trajectories.
            # Ground fingers keep their world orientation, so the membrane fan
            # remains upright as the supporting wrist moves underneath it.
            if air<.9999:
                for side in ("l","r"):
                    for kind in ("wing","hind"):
                        cb,base,local=self.contacts[(side,kind)]
                        goal=base.copy();goal[1]=.25
                        lift=0
                        if locomotion:
                            peak=phases[side]+(.25 if kind=="wing" else 0)
                            duty=.58 if charge else .68
                            phase=(u-peak+(1+duty)/2)%1
                            stride=(24 if charge else 17)*(1. if kind=="wing" else .75)
                            if phase<duty:
                                travel=-1+2*phase/duty
                            else:
                                v=(phase-duty)/(1-duty)
                                travel=1-2*smooth(0,1,v)
                                lift=(10 if charge else 7)*math.sin(math.pi*v)**2
                            envelope=1 if loop else math.sin(math.pi*u)**2
                            goal[2]+=travel*stride*envelope
                            goal[1]+=lift*envelope
                        if "Attack-Wing" in name and kind=="wing" and side=="l":
                            beat=float(smooth(.1,.35,u)*(1-smooth(.65,.9,u)))
                            goal+=np.array([12,24,-22])*beat
                        if air:
                            i=t.index[cb]
                            aerial=wp[i]+wr[i]@local
                            goal=goal*(1-air)+aerial*air
                            # During the tuck, a different toe/claw corner can
                            # become the sole. Clear the entire distal segment,
                            # not just the bind-pose contact probe.
                            family=([f'wing_{side}_hand',cb] if kind=='wing'
                                    else [f'leg_{side}_foot',cb])
                            ci=t.index[cb]
                            bottoms=[]
                            for bi,points,_ in t.cubes:
                                if t.names[bi] in family:
                                    relative=(points-t.pivots[ci])@wr[bi].T-wr[ci]@local
                                    bottoms.append(float(relative[:,1].min()))
                            goal[1]=max(goal[1],.35-min(bottoms))
                        if kind=="wing":
                            upper,lower,end=[f"wing_{side}_{p}" for p in ("arm","fore","hand")]
                            hand=t.index[end];ci=t.index[cb]
                            end_goal=goal-wr[ci]@local
                            # claw pivot is coincident with the wrist pivot
                            bend=np.array([0.,1.,.08])
                        else:
                            upper,lower,end=[f"leg_{side}_{p}" for p in ("thigh","shin","foot")]
                            foot=t.index[end];toe=t.index[cb]
                            wr[foot]=blend_rotation(t.refr[foot],wr[foot],air)
                            wr[toe]=blend_rotation(t.refr[toe],wr[toe],air)
                            end_goal=goal-wr[toe]@local-wr[foot]@(t.pivots[toe]-t.pivots[foot])
                            bend=np.array([0.,0.,-1.])
                        if air>.001:
                            # The linear handoff between folded and spread
                            # poses can leave the limb's reachable sphere.
                            # Adjust only that airborne arc, never a planted
                            # contact, before solving its joint rotations.
                            ia,ib,ic=[t.index[n] for n in (upper,lower,end)]
                            reach=np.linalg.norm(t.pivots[ib]-t.pivots[ia])+np.linalg.norm(t.pivots[ic]-t.pivots[ib])-.02
                            ray=end_goal-wp[ia];distance=np.linalg.norm(ray)
                            if distance>reach:
                                correction=ray*(reach/distance-1)
                                end_goal+=correction;goal+=correction
                                transition_adjustments.append(float(np.linalg.norm(correction)))
                        err=self.ik(wp,wr,upper,lower,end,end_goal,bend)
                        if air<.001:contact_errors.append(err)
                        goals[cb]=goal.tolist()
                # Desired child matrices must be reflected in the local tracks;
                # helper groups inherit the solved upper-wing orientation.
                for i,p in enumerate(t.parents):
                    if t.names[i] in ("bone1","bone2","bone1r","bone2r"):
                        wr[i]=wr[p]@R.from_euler("xyz",t.rest[i],degrees=True).as_matrix()
            for i,p in enumerate(t.parents):
                local_r=wr[i] if p<0 else wr[p].T@wr[i]
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore",UserWarning)
                    euler=R.from_matrix(local_r).as_euler("xyz",degrees=True)
                previous=t.rest[i]+(angles[frame-1,i] if frame else 0)
                candidates=[euler,np.array([euler[0]+180,180-euler[1],euler[2]+180])]
                candidates=[v+360*np.round((previous-v)/360) for v in candidates]
                euler=min(candidates,key=lambda v:np.linalg.norm(v-previous))
                angles[frame,i]=euler-t.rest[i]
            if air<.01 or transition:
                self.clear_chain(angles[frame],offsets[frame],"tail1",[f"tail{i}" for i in range(1,8)]+["tail_fin"],2.,-1)
                self.clear_chain(angles[frame],offsets[frame],"neck1",["neck1","neck2","neck3","neck4","head","jaw","snout","snout_tip"],2.,1,
                                 settle=death and u>.4)
            contact_goals.append(goals)
        # Make the full Euler branch continuous before linear baking.
        angles=np.rad2deg(np.unwrap(np.deg2rad(angles),axis=0))
        if loop:
            # Source clips often end one sample short of the cycle. Match the
            # seam, then verify the interpolated interval as well as keys.
            angles[-1]=angles[0];offsets[-1]=offsets[0]
            contact_goals[-1]=contact_goals[0]
        result={"loop":"hold_on_last_frame" if death else bool(loop),
                "animation_length":length,"bones":{}}
        for i,n in enumerate(t.names):
            channels={}
            for cname,values,sign in (("rotation",angles[:,i],ROT_SIGN),("position",offsets[:,i],POS_SIGN)):
                values=values*sign
                if np.max(abs(values))<1e-7:continue
                channels[cname]={f"{tt:.8f}":np.round(v,6).tolist() for tt,v in zip(times,values)}
            if channels:result["bones"][n]=channels
        self.metrics[name]={"sampled_frames":len(times),"ik_reach_error":max(contact_errors,default=0),
                            "airborne_arc_adjustment":max(transition_adjustments,default=0),
                            "contact_goals":contact_goals,"times":times.tolist()}
        return result

    def clear_chain(self,angles,offsets,base,names,height,direction,settle=False):
        """Ground-aware base bend; leaves the imported wave along the chain."""
        t=self.t;i=t.index[base];indices={t.index[n] for n in names}
        initial=float(angles[i,0])
        def lowest(amount):
            angles[i,0]=initial+direction*amount
            p,r=t.fk(angles[None],offsets[None])
            return min(float(v[:,1].min()) for j,v,c in t.vertices(p[0],r[0]) if j in indices)
        start=lowest(0)
        if start>=height and not settle:return
        lo,hi=(0.,65.) if start<height else (-65.,0.)
        # Search the first crossing to avoid folding a long neck backward.
        samples=np.linspace(lo,hi,27);values=[lowest(x)-height for x in samples]
        crossings=[j for j in range(len(samples)-1) if values[j]<=0<=values[j+1]]
        if not crossings:
            best=min(range(len(samples)),key=lambda j:abs(values[j]))
            lowest(samples[best]);return
        j=min(crossings,key=lambda j:abs(samples[j]))
        lo,hi=samples[j],samples[j+1]
        for _ in range(16):
            mid=(lo+hi)/2
            if lowest(mid)<height:lo=mid
            else:hi=mid
        lowest(hi)


def blockbench(rig, animations, texture):
    """Native Blockbench 5 project with stable UUID bindings and baked keys.

    Timeline rotations/positions use the editor's mirrored axes. The exported
    Bedrock JSON uses the inverse X/Y signs (as in the shared creature exporter).
    """
    groups=[];outlines=[];elements=[]
    for i,b in enumerate(rig.bones):
        groups.append({"name":b["name"],"uuid":uid("bone/"+b["name"]),
                       "origin":rig.pivots[i].tolist(),"rotation":rig.rest[i].tolist(),
                       "export":True,"visibility":True,"isOpen":False})
        outlines.append({"uuid":groups[-1]["uuid"],"isOpen":False,"children":[]})
    for i,p in enumerate(rig.parents):
        if p>=0:outlines[p]["children"].append(outlines[i])
    count=0
    for i,b in enumerate(rig.bones):
        for cube in b.get("cubes",[]):
            size=np.array(cube["size"]);origin=np.array(cube["origin"],float)
            origin[0]=-origin[0]-size[0]
            faces={}
            for face,uv in cube["uv"].items():
                x,y=uv["uv"];w,h=uv["uv_size"]
                editor_face={"east":"west","west":"east"}.get(face,face)
                faces[editor_face]={"uv":[x,y,x+w,y+h],"texture":0}
            eid=uid(f"cube/{count}");count+=1
            elements.append({"name":f'{b["name"]}_{count}',"type":"cube","uuid":eid,
                             "box_uv":False,"export":True,"autouv":0,"color":i%8,
                             "from":origin.tolist(),"to":(origin+size).tolist(),
                             "origin":(np.array(cube.get("pivot",b["pivot"]))*POS_SIGN).tolist(),
                             "rotation":(np.array(cube.get("rotation",[0,0,0]))*ROT_SIGN).tolist(),
                             "inflate":cube.get("inflate",0),"mirror_uv":cube.get("mirror",False),"faces":faces})
            outlines[i]["children"].append(eid)
    bb_anims=[]
    for name,clip in animations.items():
        animators={}
        for bone,channels in clip["bones"].items():
            frames=[]
            for channel,track in channels.items():
                sign=ROT_SIGN if channel=="rotation" else POS_SIGN
                for time,value in track.items():
                    values=np.array(value)*sign
                    frames.append({"uuid":uid(f'{name}/{bone}/{channel}/{time}'),"channel":channel,
                                   "time":float(time),"interpolation":"linear","color":-1,
                                   "data_points":[dict(zip("xyz",values.tolist()))]})
            animators[uid("bone/"+bone)]={"name":bone,"type":"bone","keyframes":frames,
                                        "rotation_global":False,"quaternion_interpolation":False}
        bb_anims.append({"name":name,"uuid":uid("animation/"+name),"length":clip["animation_length"],
                         "loop":"hold" if clip["loop"]=="hold_on_last_frame" else "loop" if clip["loop"] else "once",
                         "snapping":30,"override":True,"saved":True,"animators":animators})
    desc=rig.doc["minecraft:geometry"][0]["description"]
    return {"meta":{"format_version":"5.0","model_format":"geckolib_model","box_uv":False},
            "name":"Dragon Wyvern Retargeted","model_identifier":"dragon","geckolib_model_type":"entity",
            "resolution":{"width":desc["texture_width"],"height":desc["texture_height"]},
            "elements":elements,"groups":groups,"outliner":[outlines[i] for i,p in enumerate(rig.parents) if p<0],
            "textures":[{"name":"dragon.png","id":"0","uuid":uid("texture"),"internal":True,
                         "width":desc["texture_width"],"height":desc["texture_height"],
                         "uv_width":desc["texture_width"],"uv_height":desc["texture_height"],
                         "source":"data:image/png;base64,"+base64.b64encode(texture.read_bytes()).decode()}],
            "animations":bb_anims}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fps",type=int,default=30)
    args=parser.parse_args()
    if args.fps<30:parser.error('--fps must be at least 30 for contact accuracy')
    inputs=[DRAGON/'Dragon/dragon.geo.json',DRAGON/'Dragon/dragon.png',
            DRAGON/'Old/geo/dragon.geo.json',DRAGON/'Old/animations/dragon.animation.json']
    hashes={str(p.relative_to(ROOT)):digest(p) for p in inputs}
    original=read(DRAGON/"Dragon/dragon.geo.json")
    geo=patched_geometry(original)
    source=Rig(read(DRAGON/"Old/geo/dragon.geo.json"));target=Rig(geo)
    clips=read(DRAGON/"Old/animations/dragon.animation.json")["animations"]
    pipeline=Retarget(source,target,clips)
    output=DRAGON/"Dragon/retargeted"
    result={}
    for name,clip in clips.items():
        result[name]=pipeline.build_clip(name,clip,args.fps)
        print(name, pipeline.metrics[name]["sampled_frames"], "frames; reach error",
              round(pipeline.metrics[name]["ik_reach_error"],4),flush=True)
    # Bound every sampled pose, including the newly unfolded wings.
    low=np.full(3,np.inf);high=-low
    for name,clip in result.items():
        pp,rr=target.clip(clip,pipeline.metrics[name]['times'])
        for wp,wr in zip(pp,rr):
            points=np.concatenate([v for _,v,_ in target.vertices(wp,wr)])
            low=np.minimum(low,points.min(0));high=np.maximum(high,points.max(0))
    desc=geo['minecraft:geometry'][0]['description']
    desc['visible_bounds_width']=round(2*max(abs(low[[0,2]]).max(),abs(high[[0,2]]).max())/16+1,3)
    desc['visible_bounds_height']=round((high[1]-low[1])/16+1,3)
    desc['visible_bounds_offset']=[0,round((high[1]+low[1])/32,3),0]
    write(output/"dragon.geo.json",geo)
    write(output/"dragon.animation.json",{"format_version":"1.8.0","geckolib_format_version":2,"animations":result})
    write(output/"retarget_metrics.json",pipeline.metrics)
    write(output/"Dragon_Wyvern_Retargeted.bbmodel",blockbench(target,result,DRAGON/"Dragon/dragon.png"))
    (output/"dragon.png").write_bytes((DRAGON/"Dragon/dragon.png").read_bytes())
    assert hashes=={str(p.relative_to(ROOT)):digest(p) for p in inputs}, 'An input changed during retargeting'
    write(output/'retarget_map.json',{'source_hashes':hashes,'fps':args.fps,
          'source_facing':'+Z','target_facing':'-Z','rotation_basis':'mirrored Bedrock XYZ',
          'bone_mapping':pipeline.mapping,
          'rig_changes':['Moved root torso cubes into body with identical bind placement',
                         'Separated existing left and right hand claws into zero-rest child bones',
                         'Expanded visibility bounds to contain all sampled animation poses'],
          'adaptation':['Source world rotation deltas recalibrated against ground/flight reference poses',
                        'Five neck and nine tail joints resampled onto four neck and seven tail joints',
                        'Wing wrists replace old front feet; two-link IK; source hindfoot swing timing',
                        'Grounded claws/feet, tail and head clearance, folded/spread wing transitions',
                        'Legs tuck in flight; death settles between braced wrists and holds its final pose'],
          'integration':'Source project only; use the paired retargeted geometry and animation files. No runtime installation.',
          'rebuild':'python tools/retarget_dragon.py',
          'validate':'python tools/validate_dragon_retarget.py',
          'preview':'python tools/preview_dragon.py'})


if __name__=="__main__":
    main()
