"""Surgical saved-tray correction. No plant/R1 regeneration or repo installation."""
import argparse,base64,copy,hashlib,io,json,re,shutil,sys
from functools import lru_cache
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[5]
sys.path[:0]=[str(ROOT/'tooling'),str(ROOT/'tooling/Structures/Botany/BasicBotany')]
from ss14_model_tools import json_write,stable_id,FACES,render
from static_model_package import java_model,quads,read_java
from rsi_reference import frames
from build import project
ORIGINAL=ROOT/'assets/Structures/Botany/BasicBotany/collection'
OUT=ROOT/'assets/Structures/Botany/BasicBotany/candidates/front_status_slots_r2'
WORK=ROOT/'work/Structures/Botany/BasicBotany/front_status_slots_r2'
TRAY='models/block/botany/hydroponics_tray.json'
TEXTURE='textures/block/botany/hydroponics_tray/default.png'
RESOURCE='export/resourcepack/assets/moonstation14'
FLAGS={'open':False,'locked':False,'welded':False,'label':'none'}
DUMMY={'groups':[{'name':'door','pivot':[0,0,0]}],'dimensions':{'door_open_degrees':0}}

def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def inventory(root):return {str(p.relative_to(root)).replace('\\','/'):sha(p) for p in sorted(root.rglob('*')) if p.is_file()}
def read(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def png(im):
    b=io.BytesIO();im.save(b,format='PNG',optimize=False);return b.getvalue()
def put(path,content):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(content)

@lru_cache(maxsize=4)
def data(out):
    spec=read(out/'model_spec.json');original=read(ORIGINAL/'authoring/hydroponics_tray/default.bbmodel')
    bb=copy.deepcopy(original);model=read(ORIGINAL/RESOURCE/TRAY)
    atlas=Image.open(ORIGINAL/'textures/hydroponics_tray/default.png').convert('RGBA')
    # Append tiny padded flat-color swatches ONLY in previously transparent space.
    colors={'backing':(15,13,10,255),'trim':(100,92,78,255),'reveal':(41,36,27,255),'pale':(187,188,185,255)};uv={}
    for i,(name,c) in enumerate(colors.items()):
        x=2+i*4;y=110
        assert not atlas.crop((x-1,y-1,x+2,y+2)).getchannel('A').getbbox()
        atlas.paste(c,(x-1,y-1,x+2,y+2));uv[name]=[x,y,x+1,y+1]
    new=[]
    def box(name,lo,hi,role):
        p={'name':name,'from':[round(v-8 if i in [0,2] else v,6) for i,v in enumerate(lo)],
          'to':[round(v-8 if i in [0,2] else v,6) for i,v in enumerate(hi)],'origin':[0,0,0],
          'rotation':[0,0,0],'group':'tray','uv':{f:uv[role] for f in FACES},'surface':role}
        new.append(p)
    # Source left/right order maps to +X/-X on a north-facing front.
    dividers=[(1.9,2.2),(4.2,4.6),(6.6,7.0),(9.0,9.4),(11.4,11.8),(13.8,14.1)]
    for i,(a,b) in enumerate(dividers):
        box(f'front_status_mullion_{i}',[a,1.3,1.9],[b,2.8,2.5],'trim')
        x=(a+b)/2
        box(f'front_divider_foot_highlight_{i}',[x-.08,1.1,1.82],[x+.08,1.5,1.9],'pale')
    box('front_status_lower_rail',[1.9,1.0,1.9],[14.1,1.3,2.5],'trim')
    box('front_status_upper_rail',[1.9,2.8,1.9],[14.1,3.6,2.5],'trim')
    for slot in spec['slots']['cells']:
        x=slot['center_x']
        box('slot_'+slot['id']+'_dark_backing',[x-1,1.3,2.5],[x+1,2.8,2.55],'backing')
    # Keep original cuboid bounds and all its other exterior surfaces/UVs EXACT.
    e=next(e for e in bb['elements'] if e['name']=='dark_undertray');e['faces']['north']['texture']=None
    e=next(e for e in model['elements'] if e['name']=='dark_undertray');del e['faces']['north']
    oldids=[e['uuid'] for e in bb['elements']]
    traygroup=bb['outliner'][0]['children'][0]
    assert traygroup['name']=='tray'
    for p,je in zip(new,java_model(new,'moonstation14:block/botany/hydroponics_tray/default',atlas.size)['elements']):
        model['elements'].append(je)
        eid=stable_id('hydroponics_tray_default',p['name'])
        bb['elements'].append({'name':p['name'],'uuid':eid,'type':'cube','from':je['from'],'to':je['to'],'origin':[8,0,8],
          'rotation':[0,0,0],'box_uv':False,'autouv':0,'shade':True,'visibility':True,'export':True,'rescale':False,
          'faces':{f:{'uv':p['uv'][f],'texture':0} for f in FACES}})
        traygroup['children'].append(eid)
    rawpng=png(atlas);bb['textures'][0]['source']='data:image/png;base64,'+base64.b64encode(rawpng).decode()
    model['render_type']='minecraft:cutout'
    loops=[]
    for slot in spec['slots']['cells']:
        crops=[]
        for f in frames(ORIGINAL/'source/rsi/overlays.rsi',slot['source']):
            assert f['seconds']==.1 and f['image'].getbbox()==tuple(slot['source_crop'])
            crop=f['image'].crop(tuple(slot['source_crop']));im=Image.new('RGBA',(8,8));im.paste(crop,(2,2))
            # Two-pixel padding nearest to the native status patch.
            for y in range(8):
                for x in range(8):im.putpixel((x,y),crop.getpixel((min(3,max(0,x-2)),min(2,max(0,y-2)))))
            crops.append(im)
        strip=Image.new('RGBA',(8,56))
        for i,im in enumerate(crops):strip.paste(im,(0,i*8))
        x=slot['center_x']-8;z=spec['slots']['indicator_plane_z']-8
        p={'name':'status_'+slot['id']+'_display','from':[x-1,1.3,z],'to':[x+1,2.8,z],'origin':[0,0,0],
           'rotation':[0,0,0],'group':'indicator','uv':{f:[2,2,6,5] for f in FACES}}
        cs={'asset_id':'hydroponics_tray_indicator_'+slot['id'],'visual_key':'indicators','state':slot['id'],
            'texture':{'size':[8,8]},'groups':[{'name':'indicator','parent':'root','pivot':[0,0,0]}],
            'dimensions':{'door_open_degrees':0},'clips':[]}
        cb=project(cs,[p],png(crops[0]));cb['textures'][0]['relative_path']='../../textures/indicators/'+slot['id']+'_frame0.png'
        for f in FACES:
            if f!='north':cb['elements'][0]['faces'][f]['texture']=None
        texture='moonstation14:block/botany/indicators/'+slot['id']
        cm=java_model([p],texture,(8,8));cm.pop('display',None);cm['render_type']='minecraft:cutout'
        cm['elements'][0]['faces']={'north':cm['elements'][0]['faces']['north']}
        loops.append({'slot':slot,'bb':cb,'model':cm,'strip':png(strip),'frames':crops,'part':p,
                      'mcmeta':{'animation':{'frametime':2,'interpolate':False}}})
    return {'bb':bb,'model':model,'png':rawpng,'parts_added':new,'swatches':uv,'original_uuids':oldids,'loops':loops}

def author_parts(bb):
    parents={}
    def walk(ns,parent='root'):
        for n in ns:
            if isinstance(n,str):parents[n]=parent
            else:assert n['rotation']==[0,0,0];walk(n['children'],n['name'])
    walk(bb['outliner']);parts=[]
    for e in bb['elements']:
        parts.append({'name':e['name'],'group':'tray','from':(np.array(e['from'])-[8,0,8]).tolist(),
          'to':(np.array(e['to'])-[8,0,8]).tolist(),'origin':(np.array(e['origin'])-[8,0,8]).tolist(),'rotation':e['rotation'],
          'uv':{f:v['uv'] for f,v in e['faces'].items() if v.get('texture') is not None}})
    im=Image.open(io.BytesIO(base64.b64decode(bb['textures'][0]['source'].split(',')[1]))).convert('RGBA')
    return parts,im

def scene(out,active=(),phase=0,runtime=False,old=False):
    p=ORIGINAL if old else out
    if runtime:
        atlas=Image.open(p/'textures/hydroponics_tray/default.png').convert('RGBA');_,parts=read_java(p/RESOURCE/TRAY,atlas)
    else:parts,atlas=author_parts(read(p/'authoring/hydroponics_tray/default.bbmodel'))
    qq=quads(parts)
    for key in active:
        loop=next(x for x in data(out)['loops'] if x['slot']['id']==key);im=loop['frames'][phase];W=atlas.width
        both=Image.new('RGBA',(W+8,128));both.paste(atlas,(0,0));both.paste(im,(W,0));atlas=both
        if runtime:_,parts=read_java(out/RESOURCE/f'models/block/botany/indicators/{key}.json',im)
        else:parts,_=author_parts(read(out/f'authoring/indicators/{key}.bbmodel'))
        qs=quads(parts)
        for q in qs:
            for face in q['quads']:face['uv']=(np.array(face['uv'])+[W,0]).tolist()
        qq+=qs
    return qq,atlas

def draw(out,active=(),yaw=0,elev=0,phase=0,runtime=False,old=False,lighting=True,scale_reference=False):
    ps,atlas=scene(out,active,phase,runtime,old)
    # Camera framing only: centre low tray on canonical renderer's Y15 target.
    if not scale_reference:
        for p in ps:
            for q in p['quads']:q['vertices']=(np.array(q['vertices'])+[0,12.5,0]).tolist()
    im=render(DUMMY,ps,atlas,FLAGS,yaw,elev,size=(768,512),reference=scale_reference,lighting=lighting)
    if scale_reference:return im
    # Fixed review crop for same before/current framing; nearest sampling remains intact.
    return im.crop((240,155,528,355)).resize((576,400),Image.Resampling.NEAREST)

def generate(out):
    out=out.resolve();assert out.is_relative_to(ROOT),'Output must remain in this modeling workspace'
    if (out/'delivery_hashes.json').exists() or (out/'authoring').exists():raise SystemExit('Refusing saved correction replacement; future revisions separate candidates.')
    d=data(out)
    json_write(out/'specifications/tray_geometry.json',{'spec':'../model_spec.json','base_revision':'saved collection, not regeneration',
      'parts_added':d['parts_added'],'swatches':d['swatches'],'disabled_face':'dark_undertray/north','source_order':[x['slot'] for x in d['loops']],
      'surface_bindings':'export/indicator_bindings.json','UUID_policy':f'all{len(d["original_uuids"])}original IDs retained; named new cuboids stable UUIDv5'})
    json_write(out/'authoring/hydroponics_tray/default.bbmodel',d['bb']);put(out/'textures/hydroponics_tray/default.png',d['png'])
    json_write(out/RESOURCE/TRAY,d['model']);put(out/RESOURCE/TEXTURE,d['png'])
    bindings={'formatVersion':1,'tray':'moonstation14:block/botany/hydroponics_tray','front':'north -Z','status_precedence':'independent booleans, NOT exclusive plant selection',
       'renderer_translation':[0,0,0],'renderer_scale':1,'off':'omit that component; base dark backing remains visible',
       'on':'draw component ONCE at block origin, behind physical slot front and in front of dark backing',
       'normal_lighting':True,'fullbright':False,'native_cycle':{'frames':7,'seconds_per_frame':.1,'ticks_per_frame':2,'seconds':.7,'ticks':14},'slots':[]}
    for loop in d['loops']:
        k=loop['slot']['id'];json_write(out/f'authoring/indicators/{k}.bbmodel',loop['bb'])
        put(out/f'textures/indicators/{k}_frame0.png',png(loop['frames'][0]))
        put(out/RESOURCE/f'textures/block/botany/indicators/{k}.png',loop['strip'])
        json_write(out/RESOURCE/f'textures/block/botany/indicators/{k}.png.mcmeta',loop['mcmeta'])
        json_write(out/RESOURCE/f'models/block/botany/indicators/{k}.json',loop['model'])
        bindings['slots'].append({**loop['slot'],'model':'moonstation14:block/botany/indicators/'+k,
          'quad_export':[loop['model']['elements'][0]['from'],loop['model']['elements'][0]['to']],
          'faces':['north'],'texture_uv_java':[4,4,12,10],'element_name':'status_'+k+'_display'})
    json_write(out/'export/indicator_bindings.json',bindings)
    # Preserve complete state mapping; original plants remain authoritative unchanged.
    put(out/'export/plant-models.json',(ORIGINAL/'export/plant-models.json').read_bytes())
    provenance=out/'source';shutil.copytree(ORIGINAL/'source',provenance)
    shutil.copyfile(Path(__file__),out/'source/front_status_slots_r2.py')
    json_write(out/'source/correction_manifest.json',{'original_package':str(ORIGINAL.relative_to(ROOT)).replace('\\','/'),
       'original_package_hashes':inventory(ORIGINAL),'saved_correction_spec_hash':sha(out/'model_spec.json'),
       'generator_sha256':sha(Path(__file__)),'full_R1_evidence':'source/ copied exact, old source/tooling is explicitly historical R1 code',
       'native_overlay_order':[(x['slot']['id'],x['slot']['source_crop']) for x in d['loops']],
       'licenses':'source/ATTRIBUTION.md + RSI meta.json; original source/codec/tooling licenses preserved'})
    # Complete current transfer map: original remainder plus partial replacement, no mixed tray atlas.
    orig=read(ORIGINAL/'export/manifest.json');files=[]
    r1=str(ORIGINAL.relative_to(ROOT)).replace('\\','/');r2=str(out.relative_to(ROOT)).replace('\\','/')
    replacements={RESOURCE+'/'+TRAY,RESOURCE+'/'+TEXTURE,'authoring/hydroponics_tray/default.bbmodel','export/plant-models.json'}
    for f in orig['files']:
        files.append({**f,'source':(r2 if f['source'] in replacements else r1)+'/'+f['source']})
    for loop in d['loops']:
        k=loop['slot']['id']
        for rel in [f'models/block/botany/indicators/{k}.json',f'textures/block/botany/indicators/{k}.png',f'textures/block/botany/indicators/{k}.png.mcmeta']:
            files.append({'source':r2+'/'+RESOURCE+'/'+rel,'destination':'src/main/resources/assets/moonstation14/'+rel})
        files.append({'source':r2+f'/authoring/indicators/{k}.bbmodel','destination':f'docs/botany-sprint/model-source/indicator_{k}.bbmodel'})
    files.append({'source':r2+'/export/indicator_bindings.json','destination':'docs/botany-sprint/model-delivery/indicator-bindings.json'})
    for rel in ['model_spec.json','specifications/tray_geometry.json','source/correction_manifest.json','source/front_status_slots_r2.py']:
        files.append({'source':r2+'/'+rel,'destination':'docs/botany-sprint/model-delivery/front-status-slots-r2/'+rel})
    json_write(out/'export/manifest.json',{'formatVersion':1,'source_path_base':'workspace root','installed':False,
       'files':files,'transfer_rule':'ONE corrected tray JSON/PNG/master replaces old tray. All34 saved R1 plants stay exact. Optional independent native indicator quads are selected by host booleans; no double drawing base/slots/components.'})
    print('Generated surgical tray revision: five physical apertures and five independently bindable native status loops; plants untouched.')

def reviews(out):
    out=out.resolve();assert out.is_relative_to(ROOT)
    if (out/'delivery_hashes.json').exists() or (out/'previews').exists():raise SystemExit('Refusing saved preview replacement.')
    pp=out/'previews';pp.mkdir();slots=[x['id'] for x in read(out/'model_spec.json')['slots']['cells']]
    for state,active in [('off',[]),('all_on',slots)]:
        sheet=Image.new('RGB',(4*576,2*424),'#1c2531');dr=ImageDraw.Draw(sheet)
        for i,(name,yaw,elev) in enumerate([('front',0,0),('back',180,0),('east',90,0),('west',270,0),('top',0,90),('bottom',0,-90),('front_3q',35,22),('back_3q',215,22)]):
            im=draw(out,active,yaw,elev);x=i%4*576;y=i//4*424;sheet.paste(im,(x,y+24));dr.text((x+4,y+4),state+' / '+name,fill='white')
            if name in ['front','front_3q']:im.save(pp/(state+'_'+name+'.png'))
        sheet.save(pp/(state+'_eightviews.png'))
    compare=Image.new('RGB',(1728,848),'#1c2531');dr=ImageDraw.Draw(compare)
    src=Image.open(ORIGINAL/'source/rsi/containers.rsi/hydrotray3.png').convert('RGBA').resize((512,512),Image.Resampling.NEAREST)
    compare.paste(src,(32,170),src)
    for i,(label,im) in enumerate([('R1 before: continuous painted face',draw(out,old=True)),('R2: five physical slots, all OFF',draw(out)),('R2: independent native indicators ON',draw(out,slots)),('R2: recessed openings, front 3q',draw(out,slots,35,15))]):
        x=576+(i%2)*576;y=(i//2)*424;compare.paste(im,(x,y+24));dr.text((x+4,y+4),label,fill='white')
    dr.text((8,8),'Original hydrotray3; original overlay order: harvest / health / alert / nutrition / water',fill='white')
    compare.save(pp/'source_before_after.png')
    sheet=Image.new('RGB',(3*576,2*424),'#1c2531');dr=ImageDraw.Draw(sheet)
    for i,key in enumerate(['off']+slots):
        im=draw(out,[] if key=='off' else [key]);x=i%3*576;y=i//3*424;sheet.paste(im,(x,y+24));dr.text((x+4,y+4),'Independent: '+key,fill='white')
    sheet.save(pp/'independent_slots.png')
    sheet=Image.new('RGB',(4*288,8*218),'#1c2531');dr=ImageDraw.Draw(sheet)
    for mask in range(32):
        active=[key for i,key in enumerate(slots) if mask&(1<<i)];x=mask%4*288;y=mask//4*218
        sheet.paste(draw(out,active).resize((288,200),Image.Resampling.NEAREST),(x,y+18));dr.text((x+3,y+3),f'{mask:05b} H/h/A/N/W',fill='white')
    sheet.save(pp/'all_32_combinations.png')
    ims=[draw(out,slots,phase=i) for i in range(7)]
    ims[0].save(pp/'native_indicators.gif',save_all=True,append_images=ims[1:],duration=[100]*7,loop=0,disposal=2,optimize=False)
    sheet=Image.new('RGB',(4*576,2*424),'#1c2531');dr=ImageDraw.Draw(sheet)
    for i,im in enumerate(ims):
        x=i%4*576;y=i//4*424;sheet.paste(im,(x,y+24));dr.text((x+4,y+4),f'native frame{i} / {i*.1:.1f}s / 100ms',fill='white')
    sheet.save(pp/'native_cycle_frames.png')
    draw(out,slots,35,22,scale_reference=True).save(pp/'block_scale.png')
    # Raw source crops are small flat displays, not complete perspective sprites.
    sheet=Image.new('RGB',(7*96,5*108),'#1c2531');dr=ImageDraw.Draw(sheet)
    for j,loop in enumerate(data(out)['loops']):
        for i,f in enumerate(frames(ORIGINAL/'source/rsi/overlays.rsi',loop['slot']['source'])):
            im=f['image'].crop(tuple(loop['slot']['source_crop'])).resize((64,48),Image.Resampling.NEAREST)
            sheet.paste(im,(i*96+8,j*108+32));dr.text((i*96+2,j*108+4),f'{loop["slot"]["id"]} f{i}',fill='white');dr.text((i*96+2,j*108+83),'100ms/2ticks',fill='white')
    sheet.save(pp/'native_source_crops.png')
    (out/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Hydroponics tray — five front status slots</title><style>body{background:#1c2531;color:white;font:16px system-ui;margin:24px}a{color:#8ed5ff}img{max-width:100%;image-rendering:pixelated}</style><h1>Five front information slots — tray-only R2</h1><p>Five PHYSICAL recessed openings; native order harvest / health / alert / nutrition / water. All34 plant projects stay original. New art awaits user review; controller/game integration pending.</p><p><a href="authoring/hydroponics_tray/default.bbmodel">Editable corrected tray</a> · <a href="README.md">Open / verify / transfer / limits</a> · <a href="export/indicator_bindings.json">Independent status bindings</a> · <a href="export/manifest.json">Complete current transfer map</a></p>'+''.join('<h2>'+name+'</h2><a href="previews/'+rel+'"><img src="previews/'+rel+'"></a>' for name,rel in [('Original / before / current','source_before_after.png'),('Off eight views','off_eightviews.png'),('All on eight views','all_on_eightviews.png'),('Five independent status channels','independent_slots.png'),('All32 combinations','all_32_combinations.png'),('Actual native optical loop','native_indicators.gif'),('All7 rendered phases','native_cycle_frames.png'),('Exact native source crops','native_source_crops.png'),('One-block reference','block_scale.png')]),encoding='utf-8')
    print('Rendered actual corrected geometry, independent statuses, all32 masks and native7-frame optical cycle.')

def verify(out):
    out=out.resolve();assert out.is_relative_to(ROOT)
    d=data(out);report={'result':'PASS','editable_projects':6,'native_loops':5,'native_frames':35,'independent_combinations':32,'animations':'no bone/mechanical motion',
      'Blockbench_app':'NOT RUN','Minecraft':'NOT RUN','production_controller':'NOT INTEGRATED'}
    assert read(out/'authoring/hydroponics_tray/default.bbmodel')==d['bb'];assert read(out/RESOURCE/TRAY)==d['model']
    assert (out/'textures/hydroponics_tray/default.png').read_bytes()==d['png']==(out/RESOURCE/TEXTURE).read_bytes()
    old=read(ORIGINAL/'authoring/hydroponics_tray/default.bbmodel');new=d['bb']
    for a,b in zip(old['elements'],new['elements']):
        aa=copy.deepcopy(a)
        if aa['name']=='dark_undertray':aa['faces']['north']['texture']=None
        assert aa==b,aa['name']
    oldtex=np.array(Image.open(ORIGINAL/'textures/hydroponics_tray/default.png'));newtex=np.array(Image.open(out/'textures/hydroponics_tray/default.png'))
    assert np.array_equal(oldtex[:109],newtex[:109]);assert not np.any(oldtex[109:,:,3])
    actual_palette={tuple(c) for c in newtex[newtex[:,:,3]>0,:3]};src=np.array(Image.open(ORIGINAL/'source/rsi/containers.rsi/hydrotray3.png').convert('RGBA'))
    assert actual_palette<={tuple(c) for c in src[src[:,:,3]>0,:3]}
    for loop in d['loops']:
        key=loop['slot']['id'];assert read(out/f'authoring/indicators/{key}.bbmodel')==loop['bb']
        assert read(out/RESOURCE/f'models/block/botany/indicators/{key}.json')==loop['model']
        assert read(out/RESOURCE/f'textures/block/botany/indicators/{key}.png.mcmeta')==loop['mcmeta']
        assert (out/RESOURCE/f'textures/block/botany/indicators/{key}.png').read_bytes()==loop['strip']
        for i,f in enumerate(frames(ORIGINAL/'source/rsi/overlays.rsi',loop['slot']['source'])):
            assert np.array_equal(np.array(loop['frames'][i].crop((2,2,6,5))),np.array(f['image'].crop(tuple(loop['slot']['source_crop']))))
    assert (out/'export/plant-models.json').read_bytes()==(ORIGINAL/'export/plant-models.json').read_bytes()
    # Front-ray checks: all five aperture centres hit a recessed backing, never fascia/body.
    base,_=author_parts(new)
    for slot in read(out/'model_spec.json')['slots']['cells']:
        x=slot['center_x']-8;y=2.05;hits=[]
        for p in base:
            if 'north' in p['uv'] and p['from'][0]<x<p['to'][0] and p['from'][1]<y<p['to'][1]:hits.append((p['from'][2],p['name']))
        assert min(hits)[1]=='slot_'+slot['id']+'_dark_backing',hits
        assert abs(min(hits)[0]+5.5)<1e-6
    for p in base:
        assert np.isfinite(p['from']+p['to']).all();assert all(0<=v<=128 for u in p['uv'].values() for v in u)
        assert np.all(np.array(p['from'])>=[-7,0,-7]) and np.all(np.array(p['to'])<=[7,5,7])
    for mask in range(32):
        active=[loop['slot']['id'] for i,loop in enumerate(d['loops']) if mask&(1<<i)]
        for yaw,elev in [(0,0),(35,22)]:
            a=draw(out,active,yaw,elev,lighting=False);b=draw(out,active,yaw,elev,runtime=True,lighting=False)
            assert np.array_equal(np.array(a),np.array(b)),(mask,yaw)
    # Source/old package hashes, current pinned correction code, gallery and portable resources.
    provenance=read(out/'source/correction_manifest.json')
    for rel,h in provenance['original_package_hashes'].items():assert sha(ORIGINAL/rel)==h,rel
    assert sha(Path(__file__))==provenance['generator_sha256']
    files=read(out/'export/manifest.json')['files'];assert len({f['destination'] for f in files})==len(files)
    for f in files:
        assert (ROOT/f['source']).is_file(),f
        if f['destination'].startswith('src/main/resources/'):
            assert re.fullmatch('[a-z0-9_./-]+',f['destination'])
    for model in [d['model']]+[x['model'] for x in d['loops']]:
        for e in model['elements']:
            assert all('cullface' not in face for face in e['faces'].values())
            for f in e['faces'].values():assert all(0<=v<=16 for v in f['uv'])
    report.update({'original_saved_files_preserved':len(provenance['original_package_hashes']),'unchanged_original_elements':len(old['elements'])-1,
      'modified_original_faces':1,'original_atlas_opaque_pixels_preserved':True,'physical_open_slot_front_ray_tests':5,
      'BB_Java_unlit_image_equalities':64,'complete_transfer_destinations':len(files),'runtime_destinations':sum(f['destination'].startswith('src/main/resources/') for f in files)})
    if (out/'delivery_hashes.json').exists():
        frozen=read(out/'delivery_hashes.json')
        for rel,h in frozen.items():assert sha(out/rel)==h,rel
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['build','review','verify']);p.add_argument('--output',type=Path,default=OUT);a=p.parse_args();a.output=a.output.resolve()
    if a.action=='build':generate(a.output)
    elif a.action=='review':reviews(a.output)
    else:print(json.dumps(verify(a.output),indent=2))
