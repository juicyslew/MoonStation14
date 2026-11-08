"""Read-only saved-asset, contract, source and renderer equivalence checks."""
import argparse, base64, hashlib, io, json, re, sys
from pathlib import Path
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[4];sys.path.insert(0,str(ROOT/'tooling'))
from build import products
from prepare import PACKAGE, COUNTS
from review import load, scene, image
from ss14_model_tools import face_vertices, json_write
from static_model_package import quads

def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def validate(out):
    manifest=json.loads((out/'export/plant-models.json').read_text());assert manifest['formatVersion']==1
    assert set(manifest['plants'])==set(COUNTS);assert 'empty' not in manifest['plants']
    for k,n in COUNTS.items():assert set(manifest['plants'][k])=={f'stage-{i}' for i in range(1,n+1)}|{'harvest','dead'}
    evidence=json.loads((out/'source/source_manifest.json').read_text());sourcechecks=0
    for r in evidence['rsi']:
        for name,h in r['hashes'].items():
            assert digest(out/'source/rsi'/Path(r['resource']).name/name)==h;sourcechecks+=1
    for r in evidence['code_and_prototypes']:
        assert digest(out/'source/upstream'/r['path'])==r['sha256'];sourcechecks+=1
    report={'projects':0,'exact_reproductions':0,'quads':0,'source_hash_checks':sourcechecks,'source_palette_checks':0,
       'bb_java_unlit_image_equalities':0,'runtime_destinations':0,'bounds_export':{},'known_source_states':0,
       'format':'native Java cuboid models','Blockbench_app':'NOT RUN','Minecraft':'NOT RUN','production_renderer':'NOT INTEGRATED',
       'visual_fidelity':'actual image review recorded separately; structural equality is not source fidelity'}
    for p in products(out):
        k,s=p['key'],p['state'];bpath=out/f'authoring/{k}/{s}.bbmodel'
        spec=json.loads((out/f'specifications/{k}/{s}.json').read_text());bb=json.loads(bpath.read_text())
        model=json.loads((out/'export/resourcepack/assets/moonstation14'/p['modelpath']).read_text())
        assert spec==json.loads(json.dumps(p['spec']));assert bb==p['bb'];assert model==p['model']
        assert (out/f'textures/{k}/{s}.png').read_bytes()==p['png']
        assert (out/'export/resourcepack/assets/moonstation14'/p['texturepath']).read_bytes()==p['png'];report['exact_reproductions']+=5
        assert bb['meta']['model_format']=='java_block';assert not bb['animations'];assert model['render_type']=='minecraft:cutout'
        ids={e['uuid'] for e in bb['elements']};assert len(ids)==len(bb['elements']);refs=[];groupids=[]
        def walk(nodes):
            for n in nodes:
                if isinstance(n,str):refs.append(n)
                else:groupids.append(n['uuid']);assert n['rotation']==[0,0,0];walk(n['children'])
        walk(bb['outliner']);assert set(refs)==ids and len(refs)==len(ids);assert not ids.intersection(groupids)
        png=base64.b64decode(bb['textures'][0]['source'].split(',')[1]);assert png==p['png']
        tex=np.asarray(Image.open(io.BytesIO(png)).convert('RGBA'));painted={tuple(int(v) for v in c) for c in tex[tex[:,:,3]>0,:3]}
        assert painted<={tuple(c) for c in spec['source_opaque_palette']};assert set(np.unique(tex[:,:,3]))<={0,255};report['source_palette_checks']+=1
        for e in model['elements']:
            assert np.isfinite(e['from']+e['to']).all();assert all(-16<=v<=32 for v in e['from']+e['to'])
            assert all(a<b for a,b in zip(e['from'],e['to']))
            if r:=e.get('rotation'):
                assert r['angle'] in [-45,-22.5,0,22.5,45] and r['axis'] in ['x','y','z'];assert r['rescale'] is False
                assert np.isfinite(r['origin']).all()
            for f in e['faces'].values():
                assert 'cullface' not in f;assert f['texture']=='#atlas';assert len(f['uv'])==4 and all(0<=v<=16 for v in f['uv'])
        bp,bt=load(out,k,s);jp,jt=load(out,k,s,'java');bq=quads(bp);jq=quads(jp)
        assert np.array_equal(np.asarray(bt),np.asarray(jt));assert len(bq)==len(jq)
        for a,b in zip(bq,jq):
            assert len(a['quads'])==len(b['quads'])
            for aq,bqq in zip(a['quads'],b['quads']):
                for field in ['vertices','uv','normal']:assert np.allclose(aq[field],bqq[field],atol=1e-9,rtol=0)
                report['quads']+=1
        pts=np.array([v for pp in bq for q in pp['quads'] for v in q['vertices']])+[8,0,8]
        lo,hi=pts.min(axis=0),pts.max(axis=0)
        report['bounds_export'][f'{k}/{s}']=[lo.tolist(),hi.tolist()]
        if k=='hydroponics_tray':assert np.all(lo>=[1-1e-6,0-1e-6,1-1e-6]) and np.all(hi<=[15+1e-6,5+1e-6,15+1e-6])
        else:assert np.all(lo>=[1-1e-4,5-1e-4,1-1e-4]) and np.all(hi<=[15+1e-4,32+1e-4,15+1e-4]),(k,s,lo,hi)
        for yaw,elev in [(0,0),(35,22),(0,90)]:
            a,ta=scene(out,k,s);b,tb=scene(out,k,s,'java')
            aa=image(a,ta,yaw,elev,size=(192,224),lighting=False);bbim=image(b,tb,yaw,elev,size=(192,224),lighting=False)
            assert np.array_equal(np.array(aa),np.array(bbim)),(k,s,'BB/Java pixels')
            report['bb_java_unlit_image_equalities']+=1
        report['projects']+=1
    for key,states in manifest['plants'].items():
        growing=[manifest['plants'][key][f'stage-{i}'] for i in range(1,COUNTS[key]+1)]
        assert states['dead'] not in growing and states['harvest'] not in growing and states['dead']!=states['harvest']
    destinations=json.loads((out/'export/manifest.json').read_text())['files'];assert len({f['destination'] for f in destinations})==len(destinations)
    for f in destinations:
        assert (out/f['source']).is_file();assert '..' not in Path(f['destination']).parts
        if f['destination'].startswith('src/main/resources/assets/'):
            tail=f['destination'].split('assets/',1)[1];assert re.fullmatch(r'[a-z0-9_./-]+',tail)
        report['runtime_destinations']+=f['destination'].startswith('src/main/resources/')
    coverage=json.loads((out/'source/state_coverage.json').read_text());assert set(coverage)=={'containers','overlays'}|set(COUNTS)
    for r in evidence['rsi']:
        key=Path(r['resource']).name.removesuffix('.rsi');assert {s['name'] for s in r['states']}=={s['state'] for s in coverage[key]};report['known_source_states']+=len(r['states'])
    if (out/'delivery_hashes.json').exists():
        for rel,h in json.loads((out/'delivery_hashes.json').read_text()).items():assert digest(out/rel)==h,rel
    report['result']='PASS'
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=PACKAGE);p.add_argument('--report',type=Path);a=p.parse_args()
    r=validate(a.output)
    if a.report:
        assert 'work' in a.report.resolve().parts,'Verification reports must not mutate saved delivery';json_write(a.report,r)
    print(json.dumps(r,indent=2))
