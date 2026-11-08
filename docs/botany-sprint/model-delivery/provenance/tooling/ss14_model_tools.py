"""Small shared cuboid asset pipeline: deterministic BB projects, Java parts, real UV renders.

Geometry is canonical right-handed, ground-centered, +Y up, -Z front, 16 units/block.
The artist supplies the spec and a surface painter. No game/runtime dependencies.
"""
import base64
import copy
import hashlib
import json
import math
from pathlib import Path
import uuid
import numpy as np
from PIL import Image, ImageDraw

FACES = ('north', 'east', 'south', 'west', 'up', 'down')
NORMALS = {'north': [0,0,-1], 'east': [1,0,0], 'south': [0,0,1], 'west': [-1,0,0], 'up': [0,1,0], 'down': [0,-1,0]}
# Each face goes UV top-left, top-right, bottom-right, bottom-left, matching Java.
def face_vertices(lo, hi, face):
    x,y,z = lo; X,Y,Z = hi
    return {
        'north': [[X,Y,z],[x,Y,z],[x,y,z],[X,y,z]],
        'south': [[x,Y,Z],[X,Y,Z],[X,y,Z],[x,y,Z]],
        'east': [[X,Y,Z],[X,Y,z],[X,y,z],[X,y,Z]],
        'west': [[x,Y,z],[x,Y,Z],[x,y,Z],[x,y,z]],
        'up': [[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]],
        'down': [[x,y,Z],[X,y,Z],[X,y,z],[x,y,z]],
    }[face]

def stable_id(asset, name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f'ms14-assets/{asset}/{name}'))

def json_write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, allow_nan=False)+'\n', encoding='utf-8')

class ProtectedOutput:
    """Fail closed on edited generated outputs. Manual edits belong in a separate candidate."""
    def __init__(self, root):
        self.root = Path(root)
        self.record = self.root / 'generated_hashes.json'
        if self.record.exists():
            prior = json.loads(self.record.read_text())
            for rel, digest in prior.items():
                p = self.root / rel
                if not p.exists() or hashlib.sha256(p.read_bytes()).hexdigest() != digest:
                    raise SystemExit(f'Refusing regeneration: edited or missing generated file {rel}. Preserve it as a candidate first.')
        elif any(self.root.glob('*.bbmodel')):
            raise SystemExit('Refusing generation over an existing Blockbench project without a protection manifest. Preserve it as a candidate first.')

    def finish(self, paths):
        json_write(self.record, {str(p.relative_to(self.root)).replace('\\','/'): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)})

def build_atlas(spec, paint):
    W,H = spec['texture']['size']; density = spec['texture']['texels_per_model_unit']; pad = spec['texture']['padding']
    atlas = Image.new('RGBA', (W,H), (0,0,0,0)); x = y = pad; row = 0
    regions = {}; parts = copy.deepcopy(spec['parts'])
    for part in parts:
        d = np.array(part['to']) - part['from']
        part['uv'] = {}
        for face in FACES:
            a,b = {'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}[face]
            w,h = max(1,round(d[a]*density)), max(1,round(d[b]*density))
            surface = part.get('faces', {}).get(face, part['surface'])
            # Direction-sensitive artwork can opt out of sharing by naming its surface.
            key = (surface,w,h)
            if key not in regions:
                if x+w+pad > W: x = pad; y += row+2*pad; row = 0
                if y+h+pad > H: raise ValueError('Atlas full')
                img = paint(surface,w,h)
                assert img.size == (w,h)
                atlas.paste(img,(x,y))
                # One pixel duplicated-edge padding; no texture bleed.
                atlas.paste(img.crop((0,0,w,1)),(x,y-1))
                atlas.paste(img.crop((0,h-1,w,h)),(x,y+h))
                atlas.paste(img.crop((0,0,1,h)),(x-1,y))
                atlas.paste(img.crop((w-1,0,w,h)),(x+w,y))
                for dx,dy,sx,sy in [(-1,-1,0,0),(w,-1,w-1,0),(-1,h,0,h-1),(w,h,w-1,h-1)]: atlas.putpixel((x+dx,y+dy),img.getpixel((sx,sy)))
                regions[key] = [x,y,x+w,y+h]
                x += w+2*pad; row = max(row,h)
            part['uv'][face] = regions[key]
    return atlas, parts, [{'surface': s, 'size': [w,h], 'uv': uv} for (s,w,h),uv in regions.items()]

def group_visibility(group, state):
    if group == 'lock_locked': return not state['open'] and state['locked']
    if group == 'lock_unlocked': return not state['open'] and not state['locked']
    if group == 'weld': return state['welded'] and not state['open']
    if group.startswith('label_'): return not state['open'] and state['label'] == group[6:]
    return True

def make_bbmodel(spec, parts, texture, state=None):
    aid = spec['asset_id']; defs = spec['groups']
    nodes = {'root': {'name':aid,'uuid':stable_id(aid,'root'),'origin':[0,0,0],'rotation':[0,0,0],'children':[],'visibility':True,'export':True}}
    for g in defs:
        nodes[g['name']] = {'name':g['name'],'uuid':stable_id(aid,g['name']),'origin':g['pivot'],'rotation':[0,spec['dimensions']['door_open_degrees'] if state and state['open'] and g['name']=='door' else 0,0],
                            'children':[],'visibility':group_visibility(g['name'], state) if state else g.get('visible',True),'export':True}
    for g in defs: nodes[g['parent']]['children'].append(nodes[g['name']])
    elements = []
    for p in parts:
        pid = stable_id(aid,p['name']); g = nodes[p['group']]
        visible = group_visibility(p['group'], state) if state else next(z.get('visible',True) for z in defs if z['name']==p['group'])
        elements.append({'name':p['name'],'uuid':pid,'type':'cube','from':p['from'],'to':p['to'],'origin':g['origin'],'rotation':[0,0,0],
                         'box_uv':False,'autouv':0,'shade':not p.get('unshaded',False),'visibility':visible,'export':True,
                         'faces':{f:{'uv':p['uv'][f],'texture':0} for f in FACES}})
        g['children'].append(pid)
    animations = []
    for clip in spec['clips']:
        animations.append({'uuid':stable_id(aid,clip['name']),'name':clip['name'],'loop':clip['loop'],'override':False,'length':clip['duration_seconds'],'snapping':20,
                           'animators':{nodes['door']['uuid']:{'name':'door','type':'bone','keyframes':[
                               {'uuid':stable_id(aid,clip['name']+str(t)),'channel':'rotation','time':t,'interpolation':'linear','data_points':[{'x':0,'y':-angle,'z':0}]} for t,angle in clip['geometric_y_keys']]}}})
    return {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},'name':aid,'model_identifier':aid,
            'resolution':{'width':spec['texture']['size'][0],'height':spec['texture']['size'][1]},'elements':elements,'outliner':[nodes['root']],
            'textures':[{'uuid':stable_id(aid,'atlas'),'name':aid+'.png','id':'0','mode':'bitmap','source':'data:image/png;base64,'+base64.b64encode(texture).decode(),
                         'relative_path':'textures/'+aid+'.png','internal':True,'width':spec['texture']['size'][0],'height':spec['texture']['size'][1],
                         'uv_width':spec['texture']['size'][0],'uv_height':spec['texture']['size'][1]}], 'animations':animations}

def load_master_bb(path):
    """Read back a generated unposed master, including its embedded texture.

    This intentionally rejects arbitrary hand-rotated cubes. A general BB importer is
    out of scope; callers must preserve an edited candidate, not flatten it silently.
    """
    import io
    bb = json.loads(Path(path).read_text(encoding='utf-8'))
    parent = {}
    def visit(nodes, group=None):
        for n in nodes:
            if isinstance(n, str): parent[n] = group
            else:
                assert n.get('rotation', [0,0,0]) == [0,0,0], 'Expected unposed master'
                visit(n['children'], n['name'])
    visit(bb['outliner'])
    parts = []
    for cube in bb['elements']:
        assert cube.get('type', 'cube') == 'cube'
        assert cube.get('rotation', [0,0,0]) == [0,0,0]
        parts.append({'name':cube['name'], 'group':parent[cube['uuid']], 'from':cube['from'], 'to':cube['to'],
                      'uv':{f:face['uv'] for f,face in cube['faces'].items()},
                      'uv_rotation':{f:face.get('rotation',0) for f,face in cube['faces'].items()},
                      'unshaded':not cube.get('shade',True)})
    source = bb['textures'][0]['source'].split(',', 1)[1]
    atlas = Image.open(io.BytesIO(base64.b64decode(source))).convert('RGBA')
    return bb, parts, atlas

def load_java_pose(resource_root, properties, blockstate='engineering_locker', namespace='moonstation14'):
    """Resolve north-facing multipart state and decode baked Java cubes for review."""
    resource_root = Path(resource_root)
    state = json.loads((resource_root/'assets'/namespace/'blockstates'/(blockstate+'.json')).read_text())
    assert properties['facing'] == 'north', 'For other facings use vanilla blockstate rotation'
    parts=[]
    for entry in state['multipart']:
        if not all(properties[k] == v for k,v in entry['when'].items()): continue
        namespace,name=entry['apply']['model'].split(':',1)
        model=json.loads((resource_root/'assets'/namespace/'models'/(name+'.json')).read_text())
        tex_namespace,tex_name=model['textures']['atlas'].split(':',1)
        with Image.open(resource_root/'assets'/tex_namespace/'textures'/(tex_name+'.png')) as texture:
            W,H=texture.size
        half=0 if properties['half']=='lower' else 1
        offset=np.array([8,-16*half,8])
        if model.get('loader') == 'neoforge:obj':
            obj_namespace,obj_name=model['model'].split(':',1)
            path=resource_root/'assets'/obj_namespace/obj_name
            quads=load_obj_quads(path,(W,H),offset,model.get('flip_v',False))
            parts.append({'name':path.stem,'group':'shell','quads':quads,'unshaded':not model.get('shade_quads',True)})
            continue
        for e in model['elements']:
            parts.append({'name':e['name'],'group':'shell', # Already transformed; no motion/state filtering.
                          'from':(np.array(e['from'])-offset).tolist(),'to':(np.array(e['to'])-offset).tolist(),
                          'uv':{f:[v*dim/16 for v,dim in zip(face['uv'],[W,H,W,H])] for f,face in e['faces'].items()},
                          'uv_rotation':{f:face.get('rotation',0) for f,face in e['faces'].items()},
                          'unshaded':not e.get('shade',True)})
    return parts

def rotate_y(points, angle, pivot):
    r = math.radians(angle); c,s = math.cos(r),math.sin(r)
    R = np.array([[c,0,s],[0,1,0],[-s,0,c]])
    return (np.asarray(points)-pivot) @ R.T + pivot

def moving_group(group):
    return group == 'door' or group.startswith('lock_') or group.startswith('label_')

def export_obj_part(spec, parts, angle, half, texture_id, model_id, name):
    """Exact arbitrary-Y cuboid pose as NeoForge's built-in OBJ quad model.

    Unlike vanilla element rotations, OBJ can preserve a true 120-degree pose.
    Coordinates are block-corner referenced in blocks; OBJ Vs here are top-down.
    Faces use outward CCW winding and explicit unit normals. No shaded sprite bake.
    """
    assert math.isfinite(angle)
    W,H=spec['texture']['size']
    pivot=next(g['pivot'] for g in spec['groups'] if g['name']=='door')
    lines=[f'# {spec["asset_id"]}: {angle:g} degree geometric Y pose, half {half}',f'mtllib {name}.mtl','usemtl atlas']
    index=0
    for p in parts:
        lo,hi=np.array(p['from'],float),np.array(p['to'],float)
        low,high=max(lo[1],half*16),min(hi[1],(half+1)*16)
        if high<=low: continue
        clipped_lo,clipped_hi=lo.copy(),hi.copy(); clipped_lo[1]=low; clipped_hi[1]=high
        turn=angle if moving_group(p['group']) else 0
        lines.append('g '+p['name'])
        for face in FACES:
            if face=='down' and low!=lo[1] or face=='up' and high!=hi[1]: continue
            vertices=rotate_y(face_vertices(clipped_lo,clipped_hi,face),turn,pivot)
            vertices=(vertices+np.array([8,-half*16,8]))/16
            normal=rotate_y([NORMALS[face]],turn,[0,0,0])[0]
            u,v,U,V=p['uv'][face]
            if face not in ('up','down'):
                span=V-v; base=v
                v=base+(hi[1]-high)/(hi[1]-lo[1])*span
                V=base+(hi[1]-low)/(hi[1]-lo[1])*span
            uv=np.array([[u/W,v/H],[U/W,v/H],[U/W,V/H],[u/W,V/H]])
            order=[0,1,2,3]
            cross=np.cross(vertices[1]-vertices[0],vertices[2]-vertices[0])
            if np.dot(cross,normal)<0: order=[0,3,2,1]
            for i in order:
                lines.append('v '+' '.join(format(float(x),'.17g') for x in vertices[i]))
                lines.append('vt '+' '.join(format(float(x),'.17g') for x in uv[i]))
                lines.append('vn '+' '.join(format(float(x),'.17g') for x in normal))
            lines.append('f '+' '.join(f'{i}/{i}/{i}' for i in range(index+1,index+5)))
            index+=4
    material='newmtl atlas\nKa 0 0 0\nKd 1 1 1\nd 1\nmap_Kd #atlas\n'
    model={'loader':'neoforge:obj','model':model_id,'textures':{'atlas':texture_id,'particle':texture_id},
           'automatic_culling':False,'shade_quads':True,'flip_v':False,'emissive_ambient':False,'ambientocclusion':True}
    return model,'\n'.join(lines)+'\n',material

def load_obj_quads(path, texture_size, offset, flip_v=False):
    """Read the bounded OBJ subset our exporter writes, for real-file validation/rendering."""
    positions=[]; uvs=[]; normals=[]; quads=[]; material=None; library=None
    for raw in Path(path).read_text().splitlines():
        line=raw.split()
        if not line or line[0].startswith('#'): continue
        kind=line[0]
        if kind=='v':
            position=np.array([float(x) for x in line[1:]])
            assert position.shape==(3,) and np.isfinite(position).all()
            positions.append(position*16-offset)
        elif kind=='vt':
            uv=np.array([float(x) for x in line[1:]])
            assert uv.shape==(2,) and np.isfinite(uv).all() and ((uv>=0)&(uv<=1)).all()
            if flip_v: uv[1]=1-uv[1]
            uvs.append((uv*texture_size).tolist())
        elif kind=='vn':
            normal=np.array([float(x) for x in line[1:]])
            assert np.isfinite(normal).all() and abs(np.linalg.norm(normal)-1)<1e-6
            normals.append(normal)
        elif kind=='mtllib': library=Path(path).parent/line[1]
        elif kind=='usemtl': material=line[1]
        elif kind=='f':
            assert material=='atlas' and len(line)==5
            indices=[[int(x)-1 for x in token.split('/')] for token in line[1:]]
            assert all(len(i)==3 and min(i)>=0 for i in indices)
            vs=[positions[i[0]].tolist() for i in indices]; ts=[uvs[i[1]] for i in indices]; ns=[normals[i[2]] for i in indices]
            assert all(np.allclose(n,ns[0]) for n in ns)
            assert np.dot(np.cross(np.array(vs[1])-vs[0],np.array(vs[2])-vs[0]),ns[0])>0, 'OBJ inward winding'
            quads.append({'vertices':vs,'uv':ts,'normal':ns[0].tolist()})
        else: assert kind in ('g','o'), f'Unsupported generated OBJ token {kind}'
    assert library and library.is_file() and 'map_Kd #atlas' in library.read_text()
    assert quads
    return quads

def export_java_part(spec, parts, angle, half, texture_id):
    """Bake axis-aligned 0/90 poses, remap face directions and exact UVs, split at Y=16."""
    assert angle in (0,90,-90)
    pivot = next(g['pivot'] for g in spec['groups'] if g['name']=='door')
    W,H = spec['texture']['size']; elements = []
    for p in parts:
        turn = angle if moving_group(p['group']) else 0
        lo,hi = np.array(p['from'],dtype=float),np.array(p['to'],dtype=float)
        low, high = max(lo[1], half*16),min(hi[1],(half+1)*16)
        if high <= low: continue
        clipped_lo = lo.copy(); clipped_hi = hi.copy(); clipped_lo[1]=low; clipped_hi[1]=high
        verts = np.array([v for f in FACES for v in face_vertices(clipped_lo,clipped_hi,f)])
        verts = rotate_y(verts,turn,pivot); outlo,outHi = verts.min(axis=0),verts.max(axis=0)
        offset = np.array([8,-half*16,8]); outlo += offset; outHi += offset
        faces = {}
        for face in FACES:
            if face=='down' and low != lo[1] or face=='up' and high != hi[1]: continue
            normal = rotate_y([NORMALS[face]],turn,[0,0,0])[0]
            newface = next(f for f in FACES if np.allclose(NORMALS[f],normal))
            src_uv = np.array(p['uv'][face],dtype=float)
            if face not in ('up','down'):
                vspan = src_uv[3]-src_uv[1]; fullH = hi[1]-lo[1]; oldtop=src_uv[1]
                src_uv[1]=oldtop+(hi[1]-high)/fullH*vspan
                src_uv[3]=oldtop+(hi[1]-low)/fullH*vspan
            # Track source texture corner correspondence through the rotation.
            rotated = rotate_y(face_vertices(clipped_lo,clipped_hi,face),turn,pivot)
            canonical = face_vertices(outlo-offset,outHi-offset,newface)
            ids = [next(i for i,v in enumerate(rotated) if np.allclose(v,c)) for c in canonical]
            # Rotation maps target face TL to this source corner (0/1/2/3).
            rotation = ids[0]*90
            uv = [src_uv[0]/W*16,src_uv[1]/H*16,src_uv[2]/W*16,src_uv[3]/H*16]
            faces[newface] = {'texture':'#atlas','uv':np.round(uv,6).tolist(), **({'rotation':rotation} if rotation else {})}
        elements.append({'name':p['name'],'from':np.round(outlo,6).tolist(),'to':np.round(outHi,6).tolist(),'shade':not p.get('unshaded',False),'faces':faces})
    return {'ambientocclusion':True,'textures':{'atlas':texture_id,'particle':texture_id},'elements':elements}

def render(spec, parts, atlas, state, yaw=0, elevation=0, angle=None, size=(360,480), reference=False, framing_angles=None, lighting=True):
    """Orthographic CPU z-buffer rasterizer of the actual BB cubes and per-face UVs.
    No smoothing. This validates geometry/UV/pose, not Minecraft light/loader behavior.
    """
    width,height = size
    bg = np.zeros((height,width,3), dtype=np.uint8); bg[:] = [28,37,49]
    zbuf = np.full((height,width),-np.inf); tex = np.array(atlas.convert('RGBA'))
    a,e = math.radians(yaw),math.radians(elevation)
    cam = np.array([math.sin(a)*math.cos(e),math.sin(e),-math.cos(a)*math.cos(e)])
    right = np.array([-math.cos(a),0,-math.sin(a)])
    up = np.cross(cam,right)
    basis = np.stack([right,up,cam],axis=1)
    scale = min(width/32,height/42); target=np.array([0,15,0])
    pivot=next(g['pivot'] for g in spec['groups'] if g['name']=='door')
    angle = (spec['dimensions']['door_open_degrees'] if state['open'] else 0) if angle is None else angle
    # Fit unusually wide door poses without cutting them off at the preview edge.
    visible=[]
    for p in parts:
        if not group_visibility(p['group'],state): continue
        if 'quads' in p: vertices=[v for q in p['quads'] for v in q['vertices']]
        else: vertices=[v for f in p['uv'] for v in face_vertices(p['from'],p['to'],f)]
        turns=(framing_angles if framing_angles is not None else [angle]) if moving_group(p['group']) else [0]
        for turn in turns: visible.extend(rotate_y(vertices,turn,pivot))
    if reference: visible.extend(v for f in FACES for v in face_vertices([-26,0,-8],[-10,16,8],f))
    if visible:
        extent=np.max(np.abs((np.array(visible)-target)@basis),axis=0)
        scale=min(scale,(width-24)/(2*max(extent[0],1)),(height-min(80,height*.15))/(2*max(extent[1],1)))
    light=np.array([-0.25,0.8,-0.5]); light/=np.linalg.norm(light)

    def draw_quad(v, uv, normal, shade, solid=None):
        if np.dot(normal,cam) <= 1e-6: return
        q=(np.array(v)-target)@basis
        screen=np.stack([width/2+q[:,0]*scale,height/2-q[:,1]*scale],axis=1)
        uv=np.array(uv)
        brightness=1.0 if not shade or not lighting else 0.72+0.28*max(0,np.dot(normal,light))
        for inds in ([0,1,2],[0,2,3]):
            ps=screen[inds]; depths=q[inds,2]; ts=uv[inds]
            xmin,xmax=max(0,math.floor(ps[:,0].min())),min(width-1,math.ceil(ps[:,0].max()))
            ymin,ymax=max(0,math.floor(ps[:,1].min())),min(height-1,math.ceil(ps[:,1].max()))
            if xmin>xmax or ymin>ymax: continue
            xx,yy=np.meshgrid(np.arange(xmin,xmax+1)+.5,np.arange(ymin,ymax+1)+.5)
            v0=ps[1]-ps[0]; v1=ps[2]-ps[0]; den=v0[0]*v1[1]-v1[0]*v0[1]
            if abs(den)<1e-8: continue
            dx,dy=xx-ps[0,0],yy-ps[0,1]
            b1=(dx*v1[1]-dy*v1[0])/den; b2=(v0[0]*dy-v0[1]*dx)/den; b0=1-b1-b2
            valid=(b0>=-1e-8)&(b1>=-1e-8)&(b2>=-1e-8)
            dep=b0*depths[0]+b1*depths[1]+b2*depths[2]
            if solid is None:
                tx=b0*ts[0,0]+b1*ts[1,0]+b2*ts[2,0]; ty=b0*ts[0,1]+b1*ts[1,1]+b2*ts[2,1]
                # Triangle interpolation and six-decimal Java UV export can put
                # an exact texel boundary a few millionths below its integer.
                # Plain floor then makes random neighbouring-colour notches.
                # Snap ONLY round-off-sized error, not real subpixel artwork.
                tx=np.where(np.abs(tx-np.rint(tx))<1e-5,np.rint(tx),tx)
                ty=np.where(np.abs(ty-np.rint(ty))<1e-5,np.rint(ty),ty)
                rgba=tex[np.clip(np.floor(ty).astype(int),0,tex.shape[0]-1),np.clip(np.floor(tx).astype(int),0,tex.shape[1]-1)]
                valid &= rgba[:,:,3] > 0; rgb=rgba[:,:,:3]
            else: rgb=np.broadcast_to(solid,(*xx.shape,3))
            region=zbuf[ymin:ymax+1,xmin:xmax+1]; valid &= dep>region+1e-6
            region[valid]=dep[valid]; bg[ymin:ymax+1,xmin:xmax+1][valid]=np.rint(np.clip(rgb*brightness,0,255)).astype('uint8')[valid]

    if reference:
        # One-block reference placed well to the right. Distinct desaturated gray.
        for f in FACES:
            draw_quad(face_vertices([-26,0,-8],[-10,16,8],f),[[0,0]]*4,np.array(NORMALS[f]),True,[85,104,118])
    for p in parts:
        if not group_visibility(p['group'],state): continue
        turn=angle if moving_group(p['group']) else 0
        if 'quads' in p:
            for q in p['quads']:
                draw_quad(rotate_y(q['vertices'],turn,pivot),q['uv'],rotate_y([q['normal']],turn,[0,0,0])[0],not p.get('unshaded',False))
            continue
        for f in p['uv']:
            v=rotate_y(face_vertices(p['from'],p['to'],f),turn,pivot)
            normal=rotate_y([NORMALS[f]],turn,[0,0,0])[0]
            u,vv,U,V=p['uv'][f]
            uv=[[u,vv],[U,vv],[U,V],[u,V]]
            shift=p.get('uv_rotation',{}).get(f,0)//90
            uv=uv[shift:]+uv[:shift]
            draw_quad(v,uv,normal,not p.get('unshaded',False))
    return Image.fromarray(bg)

def validate(spec, bb, parts):
    ids=[x['uuid'] for x in bb['elements']]; groups=[]; refs=[]
    def visit(nodes):
        for n in nodes:
            if isinstance(n,str): refs.append(n)
            else: groups.append(n['uuid']); visit(n['children'])
    visit(bb['outliner'])
    assert len(ids+groups)==len(set(ids+groups)) and sorted(ids)==sorted(refs)
    W,H=spec['texture']['size']; density=spec['texture']['texels_per_model_unit']
    for p in parts:
        assert all(math.isfinite(v) for v in p['from']+p['to'])
        dims=np.array(p['to'])-p['from']; assert (dims>0).all()
        for f,uv in p['uv'].items():
            u,v,U,V=uv; assert 0<=u<U<=W and 0<=v<V<=H
            a,b={'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}[f]
            assert U-u==max(1,round(dims[a]*density)) and V-v==max(1,round(dims[b]*density))
    for clip in bb['animations']:
        for gid,data in clip['animators'].items():
            assert gid in groups and data['keyframes']
            assert all(0<=k['time']<=clip['length'] for k in data['keyframes'])
    required=spec.get('required_source_states',['eng_secure','eng_secure_door','eng_secure_open','locked','unlocked','welded','paper','bounty','captains_paper','invoice'])
    assert set(spec['state_coverage']) >= set(required)
    return {'unique_ids_hierarchy':'passed','finite_geometry':'passed','uv_bounds_density':'passed (subpixel-thick trim sides use minimum one texel)',
            'animation_references':'passed','source_state_coverage':'passed','cuboids_all_options':len(parts),'cuboids_default':sum(group_visibility(p['group'],spec['states'][0]) for p in parts)}
