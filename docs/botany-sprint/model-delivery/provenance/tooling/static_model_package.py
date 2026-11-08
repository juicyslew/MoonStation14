"""Bounded static cuboid package helpers. Keeps established locker tools unchanged.

Reuses the known BB writer, atlas allocator and CPU rasterizer; adds vanilla
single-axis +/-45 cube rotations and matching saved-file readers/OBJ candidates.
"""
import copy,io,json,base64,math
from pathlib import Path
import numpy as np
from PIL import Image
from ss14_model_tools import FACES,NORMALS,face_vertices,make_bbmodel,render,stable_id

def matrix(rotation):
    x,y,z=np.radians(rotation);cx,sx=math.cos(x),math.sin(x);cy,sy=math.cos(y),math.sin(y);cz,sz=math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def visible(part,state):return state in part.get('only_states',[state])
def bb_project(spec,parts,png,state):
    flags=next(s for s in spec['states'] if s['id']==state);bb=make_bbmodel(spec,parts,png,flags)
    bb['name']=spec['asset_id']+' / '+state
    for p,e in zip(parts,bb['elements']):
        e['rotation']=p.get('rotation',[0,0,0]);e['origin']=p.get('origin',[0,0,0]);e['visibility']=visible(p,state)
    bb['textures'][0]['relative_path']='../textures/'+state+'.png'
    return bb

def read_bb(path):
    b=json.loads(Path(path).read_text());parents={}
    def walk(nodes,parent='root'):
        for n in nodes:
            if isinstance(n,str):parents[n]=parent
            else:
                assert n['rotation']==[0,0,0], 'Only unposed groups supported';walk(n['children'],n['name'])
    walk(b['outliner']);tex=Image.open(io.BytesIO(base64.b64decode(b['textures'][0]['source'].split(',')[1]))).convert('RGBA');parts=[]
    for e in b['elements']:
        if not e['visibility']:continue
        if e.get('type')=='mesh':
            qq=[]
            for f in e['faces'].values():
                ids=f['vertices'];vs=[e['vertices'][v] for v in ids];uv=[f['uv'][v] for v in ids]
                n=np.cross(np.array(vs[1])-vs[0],np.array(vs[2])-vs[0]);n/=np.linalg.norm(n)
                if len(ids)==3:vs.append(vs[-1]);uv.append(uv[-1])
                qq.append({'vertices':vs,'uv':uv,'normal':n.tolist()})
            parts.append({'name':e['name'],'group':parents[e['uuid']],'quads':qq,'unshaded':False});continue
        parts.append({'name':e['name'],'from':e['from'],'to':e['to'],'group':parents[e['uuid']],
            'rotation':e['rotation'],'origin':e['origin'],'uv':{f:v['uv'] for f,v in e['faces'].items()},'unshaded':not e['shade']})
    return b,parts,tex

def quads(parts):
    result=[]
    for p in parts:
        if 'quads' in p:result.append(copy.deepcopy(p));continue
        rotation=p.get('rotation',[0,0,0]);R=matrix(rotation);pivot=np.array(p.get('origin',[0,0,0]));qq=[]
        for f,uv in p['uv'].items():
            vs=(np.array(face_vertices(p['from'],p['to'],f))-pivot)@R.T+pivot;norm=np.array(NORMALS[f])@R.T;u,v,U,V=uv
            qq.append({'vertices':vs.tolist(),'normal':norm.tolist(),'uv':[[u,v],[U,v],[U,V],[u,V]]})
        result.append({'name':p['name'],'group':'shell','quads':qq,'unshaded':p.get('unshaded',False)})
    return result

def review_render(parts,atlas,yaw=0,elevation=0,reference=False,lighting=True):
    ps=quads(parts);points=np.array([v for p in ps for q in p['quads'] for v in q['vertices']])
    if not reference:
        shift=15-(points[:,1].min()+points[:,1].max())/2
        for p in ps:
            for q in p['quads']:
                # BB mesh vertices may be shared by adjacent faces. Never mutate
                # a shared vertex once per face while positioning the camera.
                q['vertices']=(np.array(q['vertices'])+np.array([0,shift,0])).tolist()
    dummy={'groups':[{'name':'door','pivot':[0,0,0]}],'dimensions':{'door_open_degrees':0}}
    image=render(dummy,ps,atlas,{'open':False,'locked':False,'welded':False,'label':'none'},yaw,elevation,size=(640,640) if reference else (512,512),reference=reference,lighting=lighting)
    return image if reference else image.crop((88,88,424,424))

DISPLAY={'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]},'ground':{'translation':[0,3,0],'scale':[.35,.35,.35]},'fixed':{'rotation':[0,180,0],'scale':[.5,.5,.5]},'firstperson_righthand':{'rotation':[0,225,0],'translation':[0,2,0],'scale':[.55,.55,.55]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]}}
def java_model(parts,texture,size):
    W,H=size;els=[];offset=np.array([8,0,8])
    for p in parts:
        e={'name':p['name'],'from':(np.array(p['from'])+offset).tolist(),'to':(np.array(p['to'])+offset).tolist(),'shade':not p.get('unshaded',False),
            'faces':{f:{'texture':'#atlas','uv':[v/d*16 for v,d in zip(uv,[W,H,W,H])]} for f,uv in p['uv'].items()}}
        rot=p.get('rotation',[0,0,0]);axis=[i for i,r in enumerate(rot) if r]
        if axis:
            assert len(axis)==1 and rot[axis[0]] in (-45,-22.5,22.5,45)
            e['rotation']={'origin':(np.array(p['origin'])+offset).tolist(),'axis':'xyz'[axis[0]],'angle':rot[axis[0]],'rescale':False}
        els.append(e)
    return {'textures':{'atlas':texture,'particle':texture},'elements':els,'display':DISPLAY,'ambientocclusion':True}

def read_java(path,texture):
    m=json.loads(Path(path).read_text());parts=[];W,H=texture.size;offset=np.array([8,0,8])
    for e in m['elements']:
        rotation=[0,0,0];origin=[0,0,0]
        if r:=e.get('rotation'):
            assert not r['rescale'];rotation['xyz'.index(r['axis'])]=r['angle'];origin=(np.array(r['origin'])-offset).tolist()
        parts.append({'name':e['name'],'from':(np.array(e['from'])-offset).tolist(),'to':(np.array(e['to'])-offset).tolist(),'rotation':rotation,'origin':origin,'group':'shell',
            'uv':{f:[v/16*d for v,d in zip(face['uv'],[W,H,W,H])] for f,face in e['faces'].items()},'unshaded':not e['shade']})
    return m,parts

def obj_mesh(parts,size,name):
    W,H=size;lines=[f'# Static reference mesh / block-corner units in blocks',f'mtllib {name}.mtl','usemtl atlas'];index=0
    for p in quads(parts):
        lines.append('g '+p['name'])
        for q in p['quads']:
            vs=(np.array(q['vertices'])+np.array([8,0,8]))/16;uv=np.array(q['uv'])/np.array([W,H]);n=np.array(q['normal']);order=[0,1,2,3]
            if np.dot(np.cross(vs[1]-vs[0],vs[2]-vs[0]),n)<0:order=[0,3,2,1]
            for i in order:
                lines.append('v '+' '.join(f'{v:.12g}' for v in vs[i]));lines.append('vt '+' '.join(f'{v:.12g}' for v in uv[i]));lines.append('vn '+' '.join(f'{v:.12g}' for v in n))
            lines.append('f '+' '.join(f'{i}/{i}/{i}' for i in range(index+1,index+5)));index+=4
    return '\n'.join(lines)+'\n','newmtl atlas\nKa 0 0 0\nKd 1 1 1\nd 1\nmap_Kd #atlas\n'

def exact_tick_schedule(seconds):
    """Repeat enough native loops for integer-total Minecraft ticks; cumulative rounding.

    Preserves average native duration, but not sub-tick individual source beats.
    Animated resource PNGs loop and are not an authoritative mechanical timeline.
    """
    from fractions import Fraction
    ticks=[Fraction(str(v))*20 for v in seconds];total=sum(ticks);repeat=total.denominator
    assert repeat<=100,'Unreasonably long strip'
    timeline=[];time=Fraction(0);prev=0
    for _ in range(repeat):
        for i,t in enumerate(ticks):
            time+=t;end=round(time);duration=end-prev;prev=end
            # 0.04-second source frames cannot all survive a 0.05-second tick.
            # Omit only zero-tick samples; do not lengthen the source cycle or
            # force an invalid time=0 into mcmeta. GIF retains all native frames.
            if duration:timeline.append({'index':i,'time':duration})
    assert prev==total*repeat
    return {'frames':timeline,'frametime':1,'interpolate':False},repeat
