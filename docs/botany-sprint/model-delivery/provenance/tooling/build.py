"""Deterministic native Java botany assets. Never replaces a saved delivery.

An authorized future revision must use a NEW package with copied saved sources.
"""
import argparse, base64, copy, hashlib, io, json, shutil, sys
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'tooling'))
from ss14_model_tools import build_atlas, make_bbmodel, json_write, FACES
from static_model_package import java_model
from prepare import PACKAGE, COUNTS
from shapes import tray, CONSTRUCTORS

TARGETS={
 'leaf':[(8,112,0),(11,129,2),(14,168,3)],
 'stem':[(6,78,1),(8,112,0),(11,150,1)],
 'wood':[(153,99,46),(163,111,60),(178,121,64)],
 'fruit':[(148,33,10),(190,34,4),(226,112,43)],
 'green_fruit':[(99,150,2),(112,168,3),(125,189,1)],
 'tuber':[(139,100,38),(189,144,72),(209,162,86)],
 'petal':[(121,20,24),(176,39,42),(239,85,49)],
 'eye':[(59,25,28),(71,35,38),(201,183,83)],
 'pale':[(160,162,158),(187,188,185),(218,219,215)],
 'pale_side':[(119,122,117),(173,174,172),(187,188,185)],
 'water':[(0,112,153),(0,140,199),(0,160,231)],
 'trough':[(15,13,10),(41,36,27),(100,92,78)]}

def palettes(key,state,source):
    pixels=np.asarray(Image.open(source).convert('RGBA')); colors=sorted(set(tuple(int(v) for v in row) for row in pixels[pixels[:,:,3]==255,:3]))
    assert colors
    desired=copy.deepcopy(TARGETS)
    if key=='wheat':
        if state=='harvest':a=[(183,120,27),(231,188,47),(253,243,135)]
        elif state=='dead':a=[(69,86,36),(95,104,53),(144,161,66)]
        elif int(state.split('-')[1])>=4:a=[(113,94,8),(172,157,1),(222,209,3)]
        else:a=[(23,99,0),(31,117,6),(90,109,0)]
        desired['stem']=desired['leaf']=desired['grain']=a
    if key=='potato':desired['leaf']=[(56,108,19),(79,122,16),(146,187,89)];desired['stem']=[(51,79,9),(69,108,12),(99,145,31)]
    if key=='poppy':
        desired['leaf']=desired['stem']=desired['bud']=[(0,108,0),(0,128,0),(101,158,58)]
    if key=='aloe':
        desired['leaf']=desired['stem']=desired['spear']=[(35,59,0),(63,107,0),(164,218,2)]
    if state=='dead':
        a={'tomato':[(89,57,37),(107,72,50),(136,97,73)],'potato':[(83,58,7),(109,78,14),(148,112,38)],
          'poppy':[(58,58,58),(135,135,135),(192,192,192)],'aloe':[(90,74,1),(128,112,1),(238,214,103)]}.get(key)
        if a:
            for role in ['leaf','stem','bud','spear']:desired[role]=a
    # Only roles used by current geometry will be painted, but preserve choices.
    result={}
    for name,wants in desired.items():
        result[name]=[min(colors,key=lambda c:sum((c[i]-w[i])**2 for i in range(3))) for w in wants]
    return result,colors

def painter(pal):
    def paint(surface,w,h):
        base=surface
        for suffix in ['_front','_top','_under','_tip']:
            if base.endswith(suffix):base=base[:-len(suffix)]
        a,b,c=[tuple(v)+(255,) for v in pal[base]]
        im=Image.new('RGBA',(w,h),b);d=ImageDraw.Draw(im)
        # Hand-built surface language, no procedural dirt/noise/randomness.
        if base in ['leaf','bud']:
            d.rectangle((0,max(0,h-1),w-1,h-1),fill=a)
            if surface.endswith('_top'):
                d.line((0,h//2,w-1,h//2),fill=c,width=1)
                for x in range(2,w-1,4):
                    d.line((x,h//2,min(w-1,x+1),max(0,h//2-2)),fill=a)
            elif surface.endswith('_under'):d.rectangle((0,0,w-1,h-1),fill=a)
            else:d.line((0,0,max(0,w-2),0),fill=c)
        elif base in ['stem','wood','spear']:
            d.rectangle((0,0,0,h-1),fill=a)
            d.line((max(0,w-1),0,max(0,w-1),h-1),fill=b if base=='spear' else c)
            if base=='spear':
                for y in range(2,h-1,4):d.point((max(0,w-2),y),fill=c)
                if surface.endswith('_front'):d.line((w//2,0,w//2,h-1),fill=b)
            if base=='wood' and h>7:
                d.line((w//2,h//3,w//2,h//3+2),fill=a)
        elif base=='grain':
            d.rectangle((0,0,0,h-1),fill=a)
            for y in range(0,h,2):
                d.line((w//2,y,w-1,y),fill=c)
                if y+1<h:d.point((max(0,w//2-1),y+1),fill=a)
        elif base in ['fruit','green_fruit','tuber','petal']:
            d.rectangle((0,max(0,h-2),w-1,h-1),fill=a)
            d.rectangle((0,0,0,h-1),fill=a)
            d.rectangle((max(0,w//4),0,max(0,w//2),max(0,h//3-1)),fill=c)
            if base=='tuber' and w>=3:d.point((w-2,max(0,h//2)),fill=a)
        elif base=='eye':
            im=Image.new('RGBA',(w,h),a);d=ImageDraw.Draw(im)
            if w>1 and h>1:d.point((w//2,h//2),fill=c)
        elif base in ['pale','pale_side']:
            d.line((0,0,w-1,0),fill=c);d.line((0,h-1,w-1,h-1),fill=a)
            if w>10:
                for x in range(4,w,8):d.line((x,0,x,max(0,h//3-1)),fill=c)
        elif base=='water':
            # Broad cyan field / directional source-like blue bands, no glow.
            d.rectangle((0,max(0,h-2),w-1,h-1),fill=a)
            d.line((0,0,w-1,0),fill=c)
            if surface.endswith('_top'):
                d.rectangle((1,1,max(1,w-2),max(1,h//4)),fill=c)
        elif base=='trough':
            im=Image.new('RGBA',(w,h),a);d=ImageDraw.Draw(im)
            d.line((0,0,w-1,0),fill=b)
            if surface.endswith('_front'):
                for x in range(4,w,8):d.line((x,1,x,h-1),fill=c)
        return im
    return paint

def project(spec,parts,png):
    bb=make_bbmodel(spec,parts,png)
    bb['meta']['model_format']='java_block'
    bb['ambientocclusion']=True
    for e,p in zip(bb['elements'],parts):
        e['from']=(np.array(p['from'])+[8,0,8]).tolist();e['to']=(np.array(p['to'])+[8,0,8]).tolist()
        e['origin']=(np.array(p.get('origin',[0,5,0]))+[8,0,8]).tolist();e['rotation']=p.get('rotation',[0,0,0]);e['rescale']=False
    def shift(nodes):
        for n in nodes:
            if isinstance(n,dict):n['origin']=(np.array(n['origin'])+[8,0,8]).tolist();shift(n['children'])
    shift(bb['outliner'])
    bb['textures'][0]['name']=spec['state']+'.png'
    bb['textures'][0]['relative_path']=f'../../textures/{spec["visual_key"]}/{spec["state"]}.png'
    return bb

def products(out):
    base=json.loads((out/'model_spec.json').read_text(encoding='utf-8'));allproducts=[]
    states=[('hydroponics_tray','default')]+[(k,s) for k,n in COUNTS.items() for s in [f'stage-{i}' for i in range(1,n+1)]+['harvest','dead']]
    for key,state in states:
        if key=='hydroponics_tray':
            raw=tray();src=out/'source/rsi/containers.rsi/hydrotray3.png';family='hydroponics_tray'
        else:
            raw=CONSTRUCTORS[key](base['families'][key],state);src=out/f'source/rsi/{key}.rsi/{state}.png';family=key
        pal,allcolors=palettes(family,state,src)
        spec={'asset_id':key+'_'+state,'visual_key':key,'state':state,'family':family,
          'source_reference':str(src.relative_to(out)).replace('\\','/'),'source_hash':hashlib.sha256(src.read_bytes()).hexdigest(),
          'texture':base['texture'],'groups':[{'name':'tray' if key=='hydroponics_tray' else 'plant','parent':'root','pivot':[0,0,0] if key=='hydroponics_tray' else [0,5,0]}],
          'dimensions':{'door_open_degrees':0},'clips':[],'parts':raw,'material_palettes':pal,'source_opaque_palette':allcolors,
          'coordinate_conversion':base['coordinates'],'animation':'none; authoritative discrete state',
          'collision':'host owned, not supplied','editor_format':'java_block / project 4.10 / BB4.12.6 reference'}
        atlas,parts,regions=build_atlas(spec,painter(pal));spec['uv_regions']=regions
        buf=io.BytesIO();atlas.save(buf,format='PNG',optimize=False);png=buf.getvalue()
        tex=f'moonstation14:block/botany/{key}/{state}'
        model=java_model(parts,tex,atlas.size);model.pop('display',None);model['render_type']='minecraft:cutout'
        # Every face has no cullface, including stems and leaves. Separate BER/host owner still required.
        bb=project(spec,parts,png)
        modelpath='models/block/botany/hydroponics_tray.json' if key=='hydroponics_tray' else f'models/block/botany/plants/{key}/{state}.json'
        rid='moonstation14:'+modelpath.removeprefix('models/').removesuffix('.json')
        allproducts.append({'key':key,'state':state,'spec':spec,'bb':bb,'png':png,'parts':parts,'atlas':atlas,'model':model,
                            'modelpath':modelpath,'resource_id':rid,'texturepath':f'textures/block/botany/{key}/{state}.png'})
    return allproducts

def build(out):
    if (out/'delivery_hashes.json').exists() or (out/'generated_hashes.json').exists():
        raise SystemExit('Refusing saved delivery regeneration. Preserve art/manual edits; use a separately authorized candidate.')
    if any((out/'authoring').rglob('*.bbmodel')): raise SystemExit('Refusing existing authoring overwrite; use a fresh candidate.')
    snapshots=[]
    paths=list(Path(__file__).parent.glob('*.py'))+[ROOT/'tooling'/n for n in ['ss14_model_tools.py','static_model_package.py','rsi_reference.py','requirements-models.txt']]
    for src in sorted(paths):
        dst=out/'source/tooling'/src.name;dst.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(src,dst);snapshots.append({'path':str(dst.relative_to(out)).replace('\\','/'),'sha256':hashlib.sha256(dst.read_bytes()).hexdigest()})
    json_write(out/'source/tooling/manifest.json',{'files':snapshots,'versions':{'python':'3.13','Pillow':'11.3.0','numpy':'2.3.3','PyYAML':'6.0.3'},
      'rebuild':'Use workspace tooling paths; saved snapshots pin generator code and shared behavior. No dependency installation required.'})
    manifest={'formatVersion':1,'tray':None,'plants':{}};dest=[]
    for p in products(out):
        key,state=p['key'],p['state'];state_dir=out/f'authoring/{key}'
        # Exact specification is saved before authoring/runtime model generation.
        json_write(out/f'specifications/{key}/{state}.json',p['spec'])
        json_write(state_dir/f'{state}.bbmodel',p['bb'])
        tex=out/f'textures/{key}/{state}.png';tex.parent.mkdir(parents=True,exist_ok=True);tex.write_bytes(p['png'])
        rr=out/'export/resourcepack/assets/moonstation14'
        json_write(rr/p['modelpath'],p['model']);tt=rr/p['texturepath'];tt.parent.mkdir(parents=True,exist_ok=True);tt.write_bytes(p['png'])
        if key=='hydroponics_tray':manifest['tray']=p['resource_id']
        else:manifest['plants'].setdefault(key,{})[state]=p['resource_id']
        for rel in [p['modelpath'],p['texturepath']]:
            dest.append({'source':f'export/resourcepack/assets/moonstation14/{rel}','destination':f'src/main/resources/assets/moonstation14/{rel}'})
        dest.append({'source':f'authoring/{key}/{state}.bbmodel','destination':f'docs/botany-sprint/model-source/{key}_{state}.bbmodel',
                     'note':'embedded texture is portable; authoring only, not runtime'})
    json_write(out/'export/plant-models.json',manifest)
    dest.append({'source':'export/plant-models.json','destination':'docs/botany-sprint/model-delivery/plant-models.json'})
    # Coverage is generated below; include its destination on the FIRST build,
    # not only when reproducing from a saved source directory that already has it.
    provenance={p for p in (out/'source').rglob('*') if p.is_file()}|{out/'source/state_coverage.json'}
    for p in sorted(provenance):
        rel=str(p.relative_to(out)).replace('\\','/')
        dest.append({'source':rel,'destination':'docs/botany-sprint/model-delivery/provenance/'+rel.removeprefix('source/')})
    json_write(out/'export/manifest.json',{'formatVersion':1,'target':'Java1.21.1/NeoForge21.1.224','installed':False,'files':dest,
       'transfer_rule':'All native models/PNGs plus plant-models.json and source/licenses. No new blockstates/registrations/gameplay/collisions are supplied.'})
    json_write(out/'export/binding_contract.json',{'contract_reference':'source/contracts/RENDER-CONTRACT.md','root':[8,5,8],
       'renderer_translation':[0,0,0],'renderer_scale':1,'empty':'render tray only','state_precedence':['dead','harvestable','authoritative stage'],
       'geometry_choice':'draw one tray and zero or one whole plant model. No full snapshot plus extra stem/fruit draw.',
       'late_join':'render authoritative current state directly; no timer, interpolated growth or stale previous model',
       'status':'authoring contract supplied; production baked-model owner registration/binding/in-game independent review pending',
       'lighting':'normal world lighting, cutout; no global fullbright; native warning indicators renderer-owned and not delivered as animated layers'})
    coverage={}
    for r in json.loads((out/'source/source_manifest.json').read_text())['rsi']:
        name=Path(r['resource']).name.removesuffix('.rsi');entries=[]
        for s in r['states']:
            if name in COUNTS and s['name'] in manifest['plants'][name]:status='implemented';mapping=manifest['plants'][name][s['name']]
            elif name=='containers' and s['name']=='hydrotray3':status='implemented';mapping=manifest['tray']
            elif name=='overlays':status='renderer_owned_not_commissioned';mapping=None
            else:status='source_only_other_style_or_UI_item_not_commissioned';mapping=None
            entries.append({'state':s['name'],'coverage':status,'model':mapping,'directions':s.get('directions',1),'native_delays':s.get('delays')})
        coverage[name]=entries
    json_write(out/'source/state_coverage.json',coverage)
    json_write(out/'collection_status.json',{'status':'awaiting_user_visual_review_not_integrated','projects':35,'plant_logical_states':34,'runtime_models':35,
       'families':['hydroponics_tray']+list(COUNTS),'animations':0,'no_empty_plant_model':True,
       'items':[{'key':p['key'],'state':p['state'],'parts':len(p['parts']),'master':f'authoring/{p["key"]}/{p["state"]}.bbmodel','runtime':p['resource_id']} for p in products(out)]})
    print('Generated 35 editable projects, 35 Java models, 70 PNG files and complete 34-state manifest.')

if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--output',type=Path,default=PACKAGE);args=a.parse_args();build(args.output)
