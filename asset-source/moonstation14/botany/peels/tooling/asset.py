"""Small deterministic peel ribbon family, BB free + existing NeoForge OBJ codec."""
import sys
sys.dont_write_bytecode=True
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'tooling'))
import argparse,base64,copy,hashlib,io,json,math
import numpy as np
from PIL import Image,ImageDraw
import ss14_model_tools as core
import static_model_package as static
OUT=ROOT/'assets/Objects/Botany/Peels/candidates/source_peels_r1'
WORK=ROOT/'work/Objects/Botany/Peels/source_peels_r1'
RID='botany-peels-3d-loose-production-20261028'
CTX=ROOT/'work/agent_exchange/requests'/RID/'context'
SS14=Path('C:/Users/William/Documents/SS14-dev/space-station-14')
def read(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def write(p,j):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(j,indent=2,allow_nan=False,ensure_ascii=False)+'\n',encoding='utf-8')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def png(im):b=io.BytesIO();im.save(b,format='PNG',optimize=False);return b.getvalue()
def cp(src,dst):dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(src.read_bytes())
def guard(out):assert not OUT.exists() and not out.exists(),'Saved/present output protected; new authorized candidate only'
def source(key):return SS14/f'Resources/Textures/Objects/Specific/Hydroponics/{key}.rsi'
PALETTES={'banana':{'inner':[222,217,73],'inner_mid':[216,202,64],'inner_shadow':[201,167,44],'outer':[166,128,31],'outer_shadow':[121,95,31],'edge':[107,79,21],'tip':[76,64,56]},'mimana':{'inner':[199,198,153],'inner_mid':[175,175,130],'inner_shadow':[161,161,114],'outer':[150,150,102],'outer_shadow':[109,113,72],'edge':[87,94,56],'tip':[76,64,56]}}
REGIONS={'inner':[2,2,10,26],'outer':[14,2,22,26],'edge':[26,2,30,26],'neck':[34,2,42,18],'tip':[46,2,50,6]}
def spec(key):
    return {'asset_id':'trash_'+key+'_peel','family':'open_peel_ribbon_r1','variant':key,'source':{'rsi':str(source(key)),'state':'peel','held_states':['peel-inhand-left','peel-inhand-right'],'other_lobes':'peel2/peel3 reference only, not alternate live states','head':'separate existing wearable UNCHANGED'},'format':{'authoring':'Blockbench free4.10 / codec reference4.12.6','runtime':'NeoForge1.21.1/21.1.224 built-in neoforge:obj ordinary item model','why_mesh':'Three curved tapered skins with explicit inner/outer/edge surfaces, avoiding disconnected cuboid staircase/compound cube rotations','units_per_block':16,'authoring_axes':'+Yup, forward-Z, ground-centred origin','OBJ_conversion':'(BB+[8,0,8])/16 exactly once; flip_v:false'},'texture':{'size':[64,32],'texels_per_unit':4,'palette':PALETTES[key],'regions':REGIONS,'material_roles':'inner light fibrous face, ochre/olive outer, dark rim and stem; diffuse opaque only'},'lobes':[{'name':'long_left_skin','angle_degrees':-105,'length_scale':1.03,'bend':-.19,'width_scale':1.0},{'name':'right_skin','angle_degrees':76,'length_scale':.95,'bend':.18,'width_scale':.91},{'name':'front_skin','angle_degrees':-6,'length_scale':.78,'bend':.13,'width_scale':.87}],'sections':{'radius':[.40,1.05,2.03,3.05,3.90],'height':[2.50,1.40,.52,.23,.68],'half_width':[.35,.63,.72,.53,.16],'skin_thickness':.18},'neck':{'bottom_y':1.34,'top_y':3.18,'bottom_halfwidth':.46,'top_halfwidth':.25,'stem_top_y':3.62},'groups':[{'name':'shell','parent':'root','pivot':[0,0,0]}],'dimensions':{'door_open_degrees':0},'clips':[],'states':[{'id':'default','open':False,'locked':False,'welded':False,'label':'none'}],'state_coverage':{'peel':'default same 3D volume loose/held/inventory','peel-inhand-left/right':'pose references only, native display transforms','peel2/peel3':'unselected alternative source art','worn':'existing wearable retained separately','growth/produce/baked/primed':'out of scope; NOT peel states'},'collision':'preserve current authoritative profile/mechanics; proposed render envelope only','assumptions':['Source shows three separated flared lobes and dark terminal stalk; unseen back reconstructed conservatively.','Smooth curved skins become four low-poly facets, no simulated flex or invented animation.','Same source RGBs on reconstructed surfaces, no full perspective sprite pasted on box.']}
def texture(s):
    im=Image.new('RGBA',tuple(s['texture']['size']));pal=s['texture']['palette']
    for role,uv in REGIONS.items():
        x,y,X,Y=uv;w=X-x;h=Y-y;patch=Image.new('RGBA',(w,h));pp=patch.load()
        for v in range(h):
            for u in range(w):
                if role=='inner':col=pal['inner'] if 2<=u<w-2 else pal['inner_mid'];col=pal['inner_shadow'] if u==0 or u==w-1 else col
                elif role=='outer':col=pal['outer_shadow'] if u in (0,w-1) or u==w//2 and v>h//3 else pal['outer']
                elif role=='edge':col=pal['edge']
                elif role=='neck':col=pal['inner_mid'] if u<w//3 else pal['outer'];col=pal['outer_shadow'] if u==w-1 else col
                else:col=pal['tip']
                pp[u,v]=tuple(col)+(255,)
        im.paste(patch,(x,y))
        # 1pixel guard around every chart, including corners.
        for yy in range(y-1,Y+1):
            for xx in range(x-1,X+1):
                if xx<x or xx>=X or yy<y or yy>=Y:im.putpixel((xx,yy),patch.getpixel((min(w-1,max(0,xx-x)),min(h-1,max(0,yy-y)))))
    return im
def geometry(s):
    ps=[]
    def add(part,vs,role,uv=None,outward=None):
        vs=np.array(vs,dtype=float);n=np.cross(vs[1]-vs[0],vs[2]-vs[0]);n/=np.linalg.norm(n)
        if outward is not None and np.dot(n,outward)<0:vs=vs[[0,3,2,1]];n=-n
        u,v,U,V=REGIONS[role] if uv is None else uv
        part['quads'].append({'vertices':vs.tolist(),'normal':n.tolist(),'uv':[[u,v],[U,v],[U,V],[u,V]]})
    sec=s['sections']
    for lobe in s['lobes']:
        part={'name':lobe['name'],'group':'shell','quads':[]};sections=[];theta=math.radians(lobe['angle_degrees']);direction=np.array([math.sin(theta),0,-math.cos(theta)]);side=np.array([math.cos(theta),0,math.sin(theta)])
        for i,(r,y,w) in enumerate(zip(sec['radius'],sec['height'],sec['half_width'])):
            t=i/4;centre=direction*r*lobe['length_scale']+side*lobe['bend']*t*t;centre[1]=y;w*=lobe['width_scale'];twist=0.0 # planar ribbon facets: no ambiguous nonplanar OBJ quad triangulation
            left=centre-side*w;right=centre+side*w;left[1]+=twist;right[1]-=twist;thick=np.array([0,sec['skin_thickness'],0]);sections.append([left,right,left+thick,right+thick])
        for i in range(4):
            L,R,Li,Ri=sections[i];N,M,Ni,Mi=sections[i+1];v0=2+i*6;v1=v0+6
            add(part,[Li,Ri,Mi,Ni],'inner',[2,v0,10,v1],outward=[0,1,0]);add(part,[R,L,N,M],'outer',[14,v0,22,v1],outward=[0,-1,0]);add(part,[L,Li,Ni,N],'edge',[26,v0,30,v1],outward=-side);add(part,[R,M,Mi,Ri],'edge',[26,v0,30,v1],outward=side)
        for i,dirn in [(0,-direction),(-1,direction)]:
            L,R,Li,Ri=sections[i];add(part,[L,R,Ri,Li],'edge',outward=dirn)
        ps.append(part)
    # Short tapered neck and bent-looking dark terminal; no solid banana fruit inside.
    def tapered(name,y0,y1,w0,w1,zshift,role):
        p={'name':name,'group':'shell','quads':[]};bottom=[[-w0,y0,-w0],[w0,y0,-w0],[w0,y0,w0],[-w0,y0,w0]];top=[[-w1,y1,-w1+zshift],[w1,y1,-w1+zshift],[w1,y1,w1+zshift],[-w1,y1,w1+zshift]]
        for j in range(4):a0=np.array(bottom[j]);a1=np.array(bottom[(j+1)%4]);b0=np.array(top[j]);b1=np.array(top[(j+1)%4]);n=(a0+a1)/2;n[1]=0;add(p,[a0,a1,b1,b0],role,outward=n)
        add(p,bottom,role,outward=[0,-1,0]);add(p,top,role,outward=[0,1,0]);ps.append(p)
    tapered('joined_skin_neck',1.34,3.18,.46,.25,.13,'neck');tapered('dark_terminal_stalk',3.15,3.62,.16,.11,.28,'tip')
    vertices=np.array([v for p in ps for q in p['quads'] for v in q['vertices']]);floor=float(vertices[:,1].min())
    for p in ps:
        for q in p['quads']:q['vertices']=(np.array(q['vertices'])-[0,floor,0]).tolist()
    return ps
def bbmodel(s,ps,tex):
    bb=core.make_bbmodel(s,[],png(tex));node=bb['outliner'][0]['children'][0]
    for p in ps:
        vertices={};faces={};lookup={}
        for i,q in enumerate(p['quads']):
            ids=[]
            for v in q['vertices']:
                tup=tuple(v)
                if tup not in lookup:key=f'v{len(vertices):03}';vertices[key]=v;lookup[tup]=key
                ids.append(lookup[tup])
            faces[f'f{i:03}']={'vertices':ids,'uv':dict(zip(ids,q['uv'])),'texture':0}
        eid=core.stable_id(s['asset_id'],p['name']);bb['elements'].append({'name':p['name'],'uuid':eid,'type':'mesh','origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces,'visibility':True,'export':True});node['children'].append(eid)
    bb['textures'][0]['relative_path']='../textures/'+s['asset_id']+'.png';return bb
def product(key):
    s=spec(key);tex=texture(s);parts=geometry(s);s['parts']=parts;points=np.array([v for p in parts for q in p['quads'] for v in q['vertices']]);s['bounds']={'min':points.min(0).tolist(),'max':points.max(0).tolist(),'size':np.ptp(points,axis=0).tolist()};s['quad_count']=sum(len(p['quads']) for p in parts);assert s['quad_count']<=96
    return s,parts,tex,bbmodel(s,parts,tex)
def render(parts,tex,yaw=35,elev=25,size=(480,400),zoom=3):
    ps=copy.deepcopy(parts);points=np.array([v for p in ps for q in p['quads'] for v in q['vertices']]);mid=(points[:,1].min()+points[:,1].max())/2
    for p in ps:
        for q in p['quads']:q['vertices']=(np.array(q['vertices'])*zoom+[0,15-mid*zoom,0]).tolist()
    return core.render({'groups':[{'name':'door','pivot':[0,0,0]}],'dimensions':{'door_open_degrees':0}},ps,tex,{'open':False,'locked':False,'welded':False,'label':'none'},yaw,elev,size=size,lighting=True)
def pilot(name):
    out=WORK/name;guard(out);out.mkdir(parents=True)
    for key in PALETTES:
        s,parts,tex,bb=product(key);write(out/f'{key}.bbmodel',bb);write(out/f'{key}.spec.json',s);(out/f'{key}.png').write_bytes(png(tex));sheet=Image.new('RGB',(1280,480),(28,37,49));d=ImageDraw.Draw(sheet);im=Image.open(source(key)/'peel.png').convert('RGBA').resize((256,256),Image.Resampling.NEAREST);sheet.paste(im,(10,110),im)
        for x,yaw in ((280,0),(780,35)):sheet.paste(render(parts,tex,yaw,25,size=(500,440)),(x,30))
        d.text((5,5),f'{key}: native peel SOURCE | actual 3D front | 3quarter; {s["quad_count"]}quads; same family distinct source palette',fill='white');sheet.save(out/f'{key}_comparison.png')
    print(out)
if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('action',choices=['pilot']);ap.add_argument('--name',default='pilot01');args=ap.parse_args();pilot(args.name)
