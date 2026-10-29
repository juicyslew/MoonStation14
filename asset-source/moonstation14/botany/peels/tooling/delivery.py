"""Immutable final pair, separate review/receipt sidecar, flat explicit handoff."""
import asset as a
import argparse,copy,importlib.util,json,re,shutil,subprocess,sys,zipfile
from pathlib import Path
ss=importlib.util.spec_from_file_location('peels_r1_package',Path(__file__).with_name('package.py'));p=importlib.util.module_from_spec(ss);ss.loader.exec_module(p)
SIDE=a.ROOT/'assets/Objects/Botany/Peels/deliveries/source_peels_r1'
def files(root):return {f.relative_to(root).as_posix():a.sha(f) for f in sorted(root.rglob('*')) if f.is_file()}
def save():
    assert not a.OUT.exists() and not SIDE.exists(),'Saved immutable revision; never rebuild to retrieve'
    src=a.WORK/'candidate03';ind=a.WORK/'independent02';exact=[]
    for f in sorted(ind.rglob('*')):
        if f.is_file():assert a.sha(f)==a.sha(src/f.relative_to(ind)),f;exact.append(f.relative_to(ind).as_posix())
    report=p.verify(src);shutil.copytree(src,a.OUT);SIDE.mkdir(parents=True)
    a.write(a.OUT/'validation.json',report);a.write(a.OUT/'independent_reproduction.json',{'status':'PASS','fresh_build':ind.relative_to(a.ROOT).as_posix(),'exact_files':exact,'count':len(exact),'normalization':'NONE: all generated files including manifests/source/specs/models equal','meaning':'Independent deterministic replay, NOT independent artistic acceptance'})
    images=[]
    for f in sorted((a.OUT/'previews').glob('*.png')):images.append({'path':f.relative_to(a.OUT).as_posix(),'sha256':a.sha(f)})
    a.write(a.OUT/'visual_review.json',{'role':'author ACTUAL image review, not owner approval','viewed_images':images,'finding':'Source comparisons and both allside sheets, source loose/held directions, palm/world contexts and final item contexts actually inspected.66quads/pair palette distinct,3separated curved tapered skins/lightinner/darkouter/brown stalk. No missing faces observed. World scale small relative16unit cell/32unit player.','iterations':'pilot01 preserved;pilot02planarfaces for robustOBJquads;candidate01outertexturechart ran crosswise, corrected to longitudinal in candidate02; candidate02GUI/thirdperson showed darkerouter, rotation corrected candidate03to show lightinner. All earlier inputs retained ownwork.','actual_review_evidence':'Earlier unchanged source/model/eightview/world/palm images inspected in candidate02; final source sheet/item contexts/mimana world/palm inspected candidate03, unchanged hashes checked.','limits':['Rendered actual saved BBmesh/PNG and decodedOBJ, nearest sampling; NOT application/game screenshots','Palm proxy is only fit illustration, not native player hand rendering','CPU item-transform/floor arithmetic is a proposal; exact native ItemRenderer centring/order and GPU visual behavior not executed','Low-poly linear facets and conservative unseenback; no soft skin physics or animation','No broad family approval; independent frozen final review in separate deliveries sidecar']})
    # Preserve actual local codec/loader evidence used, not an invented new dependency.
    for name in ('ObjLoader.java','ObjModel.java','ObjMaterialLibrary.java','UnbakedGeometryHelper.java'):
        path=a.ROOT/'work/_shared/neoforge-reference'/name
        if path.is_file():a.cp(path,a.OUT/'source/format_reference'/name)
    (a.OUT/'ATTRIBUTION.md').write_text('''# Attribution / license

Space Station14 reference checkout c9df5ef5d675b0d1d226828bddf6b78c28502d91, verified with read-only git rev-parse. Source: Resources/Textures/Objects/Specific/Hydroponics/{banana,mimana}.rsi, selected loose state `peel`; left/right four-direction inhands are pose reference only. Original meta.json files copied verbatim with original copyright and CC-BY-SA-3.0 declarations. They credit tgstation commit6be7633abca9f1a51cab1020500cf0776ce78e5c and named contributors; banana includes Chaoticaa, mubururu_, Prole0 and FlipBrooke; mimana credits FlipBrooke on-head art. See each actual metadata for exact wording and state attribution. https://creativecommons.org/licenses/by-sa/3.0/ .

New editable 3D ribbon geometry/reconstructed surface textures by OpenAI assistant for William,2026-10-28, derived from the above source references, distributed under CC-BY-SA-3.0. Adaptations: sprites reconstructed as closed three-dimensional skins, conservative hidden thickness/back, four curved linear segments per lobe, reused geometry between palette variants, native display transforms. Not a source sprite pasted onto a box. Existing fitted head/wearable geometry is NOT reused/replaced.

Local shared authoring/export/render utilities are referenced read-only; original sources/licenses remain with their repositories. Copied Java loader excerpts are NeoForge reference evidence, NOT executable runtime assets or new dependency. Their original headers/licenses remain in source/format_reference. Do not remove source attribution/share-alike obligations when transferring the models/textures.
''',encoding='utf-8')
    (a.OUT/'README.md').write_text('''# Banana and mimana peels — finished native3D pair R1

[Fully expanded source/model/eightview/world/palm/inventory gallery](index.html). Two complete editable models, distinct yellow/ochre and muted gray/green native palettes. **Owner art approval/import pending; not installed.** Frozen final independent review +receipt live in `assets/Objects/Botany/Peels/deliveries/source_peels_r1/` outside this immutable package.

## Model / open
Open `authoring/trash_banana_peel.bbmodel` or `trash_mimana_peel.bbmodel` in Blockbench Free format (4.10 schema, pinned4.12.6 codec conventions). Textures embedded plus portable `textures/` PNGs. Semantic parts:three independently editable closed curved/tapered skin lobes, joined short skin neck and dark terminal stalk. Light reconstructed inner faces, ochre/olive outer surfaces, dark thin edges.66planar quads /132triangles each, five mesh parts,64×32PNG,4texels/modelunit target density with minimum1texel edge. Under96quad target. No flat-icon extrusion, fitted-wearable reuse or full fruit.

One `open_peel_ribbon_r1` family reused for both variants. Authored dimensions7.5461×3.3900×4.4817units (X×Y×Z),16units/block. Ground-centred BB, +Yup andforward−Z. Default low floor pose. Proposed loose scale.8 yields0.3773×0.1695×0.2241blocks, not jug-sized. Root exactly minY0. Static persistent default only; no source mechanical animation, no invented clips. `peel2`/`peel3` are unselected source alternatives, held directions are pose references, all growth/produce/baked/primed/worn states explicitly source-only/out of scope.

## Native exports / integration boundary
`export/manifest.json` is the flat complete12file runtime group, **package-relative**:2item bindings,2OBJwrappers,2OBJmeshes,2MTLs,2PNGs and2visual profile candidates. All expected destinations are `src/main/resources/...`. Use ONE matching OBJ/MTL/JSON/PNGgroup per identity. OBJ is built-in `neoforge:obj`, not Java cube geometry or a newly installed animation library; tapered curved skins need noncuboid faces, cannot survive vanilla Java cuboid export unchanged. Existing project example +NeoForge reference inspected. `flip_v:false`, MTL `map_Kd #atlas`, exact `(BB+[8,0,8])/16` conversion once; no second recenter/scale. Ordinary item JSON parent binding, no jug route/custom renderer/code.

Proposed banana profile **keeps generic parent, exact physical_width.4, inherited height.08 and all gameplay/slip/trigger fields**; adds only `render_kind:block_model`, visual `model_scale:.8`, `model_floor_offset:.4`. Mimana keeps banana parent/sound override exact, inheriting the visual proposal. No generic/block_model base profile is replaced. The shared renderer requests ItemDisplayContext.NONE, so ground display is NOT the loose-world pose. Display GUI/first/thirdperson/ground/fixed transforms are separate contexts on the SAMEvolume, not representation switches. Profile centring arithmetic assumes native ItemRenderer subtracts.5; exact vanilla transform order/native bake not executed here, must be checked by integration owner. CPU world preview implements proposed floor arithmetic, not measured game placement. ProduceWorldAppearance assumed1forTrash identities; validate live native consumer before installation.

See `export/binding_contract.json` for composition and transform formula. **Equipment/head bindings and fitted wearable meshes entirely untouched.** Existing physical height deliberately unchanged despite taller rendered stalk, so artwork is not proof of selection/collision. No mechanics/slip/edibility/output/stack/network/renderer changes. Independent artwork review is not owner import permission; references remain READONLY.

## Validation / visual limits
`validation.json`:2reproductions,66quads each, closed2-use mesh edges perpart, finitegeometry, planarfaces/outwardwinding/UVbounds, exact native source RGB membership,3036numericvertexUVnormal comparisons and8savedBB/decodedOBJCPU image equalities. Full12dependency group and visual-only profile diffs verified. Fresh separate generation exact in `independent_reproduction.json`.11ACTUAL author-reviewed image hashes; final independent findings outside package. First pilot and crosswiseouter-UV/GUIorientation fixes preserved ownwork.

Nearest texels, opaque diffuse materials; no glow/transparency/emissive. Low-poly facets, three-lobe count and hidden back/thickness conservative. Semantic closed strips overlap at neck intentionally. Existing sprites are stylized perspective references, not exact orthographic blueprints. World/player floor cell and palm proxy are scale/fit illustrations. Declared item contexts are CPU transform previews, NOT Blockbench-app/nativeplayer/Minecraft screenshots. Blockbenchimport/export, nativeNeoForgebake, exactItemRendererordering, hand/GPUlighting/mips/culling and performance remain unverified. No game/integration tests requested or executed. Runtime imports/overwrite require separate ownerauthorization after independentreview.

## Reproduce / preserve
Tooling: `tooling/Objects/Botany/Peels/asset.py`, `package.py`, `delivery.py`; Python/Pillow/NumPy versions and hashes in toolchain.json. Source palettes +ribbon parameters/specifications drive deterministic generation, using existing BB/OBJ/CPUhelpers read-only.
```powershell
python tooling/Objects/Botany/Peels/package.py verify
python tooling/Objects/Botany/Peels/delivery.py verify
```
Prefreeze build example: `package.py build --output work/Objects/Botany/Peels/source_peels_r1/independent02`; review `package.py review --output <fresh-work-path>`. Saved writers refuse regeneration. Retrieve these saved models/dependencies, never work/pilots/wearable models, regenerate-to-retrieve or delete guards. Future authorized art changes go to a separate revision; preserve manual edits.

Sourcepin/metadata/statecoverage/hash provenance: source/manifest.json, verbatimRSIs, queuedcontext/prototypes, ATTRIBUTION.md. Request `botany-peels-3d-loose-production-20261028`. Owner source repository and both reference repositories unchanged.
''',encoding='utf-8')
    a.write(a.OUT/'approval.json',{'status':'complete asset pair frozen for independent review; OWNER_ART_APPROVAL_PENDING','accepted':False,'import_authorized':False,'installed':False,'request_id':a.RID})
    toolpaths=list(Path(__file__).parent.glob('*.py'))+[a.ROOT/'tooling/static_model_package.py',a.ROOT/'tooling/ss14_model_tools.py']
    a.write(a.OUT/'toolchain.json',{'python':sys.version,'pillow':a.Image.__version__,'numpy':a.np.__version__,'reference_format':'Blockbench4.12.6 free save4.10, existingNeoForge21.1.224OBJ','files':{f.relative_to(a.ROOT).as_posix():a.sha(f) for f in toolpaths}})
    a.write(a.OUT/'frozen_hashes.json',{'status':'immutable complete R1; future edits separate candidate','files':files(a.OUT)})
    # Deterministic archive outside immutable core, convenient review/handoff only.
    archive=SIDE/'source_peels_r1.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED) as z:
        for rel in files(a.OUT):
            info=zipfile.ZipInfo(rel,date_time=(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,(a.OUT/rel).read_bytes())
    a.write(SIDE/'package_identity.json',{'request_id':a.RID,'package':a.OUT.relative_to(a.ROOT).as_posix(),'frozen_hashes_sha256':a.sha(a.OUT/'frozen_hashes.json'),'archive':archive.name,'archive_sha256':a.sha(archive),'status':'frozen complete pair, independent review sidecar pending'})
    print('Saved/frozen complete pair',a.OUT,'files',len(files(a.OUT)))

def verify():
    report=p.verify(a.OUT)
    for rel,h in a.read(a.OUT/'toolchain.json')['files'].items():assert a.sha(a.ROOT/rel)==h,rel
    for r in a.read(a.OUT/'visual_review.json')['viewed_images']:assert a.sha(a.OUT/r['path'])==r['sha256']
    for rel in re.findall(r'(?:src|href)="([^"]+)"',(a.OUT/'index.html').read_text(encoding='utf-8')):assert (a.OUT/rel).is_file(),rel
    ident=a.read(SIDE/'package_identity.json');assert a.sha(a.OUT/'frozen_hashes.json')==ident['frozen_hashes_sha256'] and a.sha(SIDE/ident['archive'])==ident['archive_sha256']
    with zipfile.ZipFile(SIDE/ident['archive']) as z:
        for rel,h in files(a.OUT).items():assert a.hashlib.sha256(z.read(rel)).hexdigest()==h
    if (SIDE/'independent_final_review.json').exists():
        review=a.read(SIDE/'independent_final_review.json');assert review['recommendation']=='ready_for_owner'
        for r in review['viewed_images']:assert a.sha(a.OUT/r['path'])==r['sha256']
    print(json.dumps(report,indent=2));return report

def publish():
    assert (SIDE/'independent_final_review.json').is_file() and not (a.WORK/'publication_before').exists();verify()
    folder=a.WORK/'publication_before';folder.mkdir();path=a.ROOT/'asset_locations.json';prior=path.read_bytes();j=json.loads(prior);key='botany_peels_native3d_r1';assert key not in j['families'];prefix=a.OUT.relative_to(a.ROOT).as_posix();side=SIDE.relative_to(a.ROOT).as_posix()
    entry={'status':'complete_pair_independently_reviewed_ready_for_OWNER_art_review_NOT_imported','request_id':a.RID,'package_root':prefix,'browse':prefix+'/index.html','authoring_directory':prefix+'/authoring','runtime_manifest':prefix+'/export/manifest.json','manifest_path_base':'package_root','bindings':prefix+'/export/binding_contract.json','review_and_receipt':side,'scope':'TrashBananaPeel+TrashMimanaPeel;same3Dvolume loose/held/inventory;66planarquads each; sourcepalette reskins; five parts, noanimation.12runtime dependencies incl visualonlyprofile proposals; physical/slip/wearable art exact preserved.','validation':'source+independentearly/finalactualPNG+savedBB/OBJCPU+exactreplay; NOTapp/game/nativebake/runtime/importpermission. Floor/hand transforms proposal awaitingnativeverification.','read_only_verifier':'python tooling/Objects/Botany/Peels/delivery.py verify'}
    (folder/'asset_locations.json').write_bytes(prior);marker=b'"families": {';assert prior.count(marker)==1;addition=('\n    '+json.dumps(key)+': '+json.dumps(entry,indent=2).replace('\n','\n    ')+',').encode();path.write_bytes(prior.replace(marker,marker+addition,1));now=a.read(path);assert now['families'].pop(key)==entry and now==j
    note=f'## Banana / mimana native3D peel pair R1\n\n[Editable/source/allview/world/palm/item-context gallery]({prefix}/index.html) · [Open/manifest/bindings/attribution/limits]({prefix}/README.md). TWOcomplete peeledskins/one66quadsharedfamily, threecurvedseparatedlobes, nativeyellow vsgraygreen. Samevolumeloose/held/inventory;12flatnativeOBJ/MTL/JSON/PNG+visualprofilecandidates. Source/CPU/replay and actualindependentearly/finalreview; pending OWNERartapproval NOTimport/app/Minecraft/nativeGPU/runtime. Physical/slip/steptrigger and existingwearablemeshes/bindings EXACTunchanged; nojugroute/newrenderer/code. Scale.8/flooroffset.4proposal needsnativecheck. Preserveart/manualedits/guards, no mixeddependencies/regenerate-to-retrieve/deleteguards. Review/receiptoutsideimmutablecore: `{side}`.\n\n'
    for name in ('MODEL_HANDOFF.md','README.md'):
        f=a.ROOT/name;before=f.read_bytes();(folder/name).write_bytes(before);f.write_bytes(note.encode()+before)
    a.write(a.WORK/'publication.json',{'key':key,'entry':entry,'prefix':note,'prior_peer_entries_preserved':len(j['families']),'entire_prior_prose_preserved':True});print('Published only own peel retrieval')

def finish():
    assert (a.WORK/'publication.json').exists() and (SIDE/'independent_final_review.json').exists();report=verify();snapshot=files(a.OUT);refusals=[];tools=Path(__file__).parent
    for cmd in ([sys.executable,str(tools/'asset.py'),'pilot','--name','frozen_refusal'],[sys.executable,str(tools/'package.py'),'build','--output',str(a.WORK/'frozen_refusal')],[sys.executable,str(tools/'package.py'),'review','--output',str(a.WORK/'frozen_refusal')],[sys.executable,str(tools/'delivery.py'),'save']):
        r=subprocess.run(cmd,capture_output=True,text=True);assert r.returncode!=0 and files(a.OUT)==snapshot;refusals.append({'command':cmd,'returncode':r.returncode,'stderr':r.stderr,'all_saved_files_unchanged':True})
    a.write(a.WORK/'writer_refusals.json',refusals)
    runtime=a.read(a.OUT/'export/manifest.json')['files'];rows=[{**r,'file':(a.OUT/r['file']).relative_to(a.ROOT).as_posix()} for r in runtime];seen={r['file'] for r in rows}
    # Asset-source files live under asset-source, not docs binary dumps.
    for root in (a.OUT,SIDE):
        for f in sorted(root.rglob('*')):
            if f.is_file() and f.name not in ('delivery_receipt.json','final_delivery_audit.json'):
                rel=f.relative_to(a.ROOT).as_posix()
                if rel not in seen:rows.append({'file':rel,'destination':'asset-source/moonstation14/botany/peels/'+('core/' if root==a.OUT else 'review/')+f.relative_to(root).as_posix()});seen.add(rel)
    for f in tools.glob('*.py'):rows.append({'file':f.relative_to(a.ROOT).as_posix(),'destination':'asset-source/moonstation14/botany/peels/tooling/'+f.name})
    assert len({r['destination'] for r in rows})==len(rows);manifest=a.WORK/'delivery_manifest.json';a.write(manifest,{'request_id':a.RID,'files':rows})
    workflow=a.ROOT.parent/'MoonStation14-shared/tooling/agent_workflow/workflow.py';rq=a.ROOT/'work/agent_exchange/requests'/a.RID;prefix=a.OUT.relative_to(a.ROOT).as_posix()
    if not (rq/'delivery').exists():
        cmd=[sys.executable,str(workflow),'--repo',str(a.ROOT.parent/'MoonStation14'),'deliver',a.RID,'--manifest',str(manifest),'--source-root',str(a.ROOT),'--revision','source-peels-r1-complete-frozen','--provenance']+[prefix+'/'+n for n in ('source/manifest.json','export/binding_contract.json','validation.json','source_fidelity_checkpoint.json','visual_review.json','independent_reproduction.json')]+[(SIDE/'independent_final_review.json').relative_to(a.ROOT).as_posix()]+['--editable']+[prefix+'/'+r['master'] for r in a.read(a.OUT/'collection_status.json')['states']]+['--review-images']+[prefix+'/'+r['path'] for r in a.read(a.OUT/'visual_review.json')['viewed_images']]
        result=subprocess.run(cmd,capture_output=True,text=True);a.write(a.WORK/'delivery_cli_result.json',{'returncode':result.returncode,'stdout':result.stdout,'stderr':result.stderr});print(result.stdout,result.stderr);assert result.returncode==0
    sys.path.insert(0,str(workflow.parent));ss=importlib.util.spec_from_file_location('peel_queue_verifier',workflow);w=importlib.util.module_from_spec(ss);ss.loader.exec_module(w);request=a.read(rq/'request.json');w.verify_request(rq,request);delivery=w.verify_delivery(rq,request);a.write(SIDE/'delivery_receipt.json',{'request_id':a.RID,'status':request['status'],'delivery':delivery})
    pub=a.read(a.WORK/'publication.json');prior=a.read(a.WORK/'publication_before/asset_locations.json');now=a.read(a.ROOT/'asset_locations.json');assert now['families'].pop(pub['key'])==pub['entry'] and now==prior
    for name in ('MODEL_HANDOFF.md','README.md'):assert (a.ROOT/name).read_bytes()==pub['prefix'].encode()+(a.WORK/'publication_before'/name).read_bytes()
    assert snapshot==files(a.OUT);audit={'status':'PASS complete pair saved/frozen/independently reviewed/delivered, OWNERapproval pending','saved_files':len(snapshot),'freeze_entries':len(a.read(a.OUT/'frozen_hashes.json')['files']),'actual_reviewed_images':11,'writer_refusals':len(refusals),'independent_exact_files':a.read(a.OUT/'independent_reproduction.json')['count'],'prior_peer_entries_preserved':pub['prior_peer_entries_preserved'],'all_prior_root_prose_preserved':True,'validation':report,'delivery_files':len(delivery['files']),'delivery_digest':delivery['digest'],'not':'owner acceptance/import/Blockbench/Minecraft/nativeGPU/runtime/lighting/performance'};a.write(SIDE/'final_delivery_audit.json',audit);a.write(a.WORK/'final_saved_delivery_audit.json',audit)
    a.write(SIDE/'frozen_sidecar_hashes.json',{'files':files(SIDE)});print(json.dumps(audit,indent=2))

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('action',choices=['save','verify','publish','finish']);args=ap.parse_args();{'save':save,'verify':verify,'publish':publish,'finish':finish}[args.action]()
