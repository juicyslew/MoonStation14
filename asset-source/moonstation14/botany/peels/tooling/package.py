"""Finished two-item package, literal bindings, actual saved-file review renders."""
import asset as a
import argparse,base64,copy,json,math,shutil,subprocess,sys
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw

def checkpoint():
    assert not (a.WORK/'checkpoint.json').exists()
    rows=[]
    for key in a.PALETTES:
        f=a.WORK/f'pilot02/{key}_comparison.png';rows.append({'path':f.relative_to(a.ROOT).as_posix(),'sha256':a.sha(f)})
    a.write(a.WORK/'checkpoint.json',{'decision':'proceed_finished_pair','actual_pilot_images':rows,'independent_review_session':'ses_e79ed9abfffetnQJhtAlvGV9Eb','reviewer_findings':'Proceed: actual banana/mimana comparisons and source contact visually inspected. Three distinct open curved skins around short neck/stalk, not whole fruit or worn remake; low flared form. Yellow/ochre vs muted gray-green distinct.66quads each. Final complete review still required.','author_findings':'Open three-lobe source silhouette, brown terminal, light inner/dark outer surfaces recognizable. Planar four-section ribbons avoid ambiguous twisted OBJ quads. No sprite extrusion. Source RGBs exact; hidden depth inferred.','limits':'No independent owner acceptance or app/game/runtime validation'})
    prior={}
    for root in (a.ROOT/'assets/Objects/Clothing/Hats/collection',a.ROOT/'assets/Objects/Clothing/Hats/candidates/source_reconstruction_r2'):
        if root.exists():
            for p in root.rglob('*'):
                if p.is_file() and ('banana' in p.as_posix() or 'mimana' in p.as_posix()):prior[p.relative_to(a.ROOT).as_posix()]=a.sha(p)
    for p in a.CTX.rglob('*'):
        if p.is_file():prior[p.relative_to(a.ROOT).as_posix()]=a.sha(p)
    # Preserve the just-closed predecessor, no unrelated botanical repaint.
    predecessor=a.ROOT/'assets/Structures/Botany/PlantCatalog/partitions/five_parts_r1/04_herbs_flowers/candidates/stem_gradient_r6'
    for p in predecessor.rglob('*'):
        if p.is_file():prior[p.relative_to(a.ROOT).as_posix()]=a.sha(p)
    a.write(a.WORK/'preservation_before.json',{'files':prior})

def item_matrix(rotation,left=False):
    x,y,z=rotation;sign=-1 if left else 1
    return a.static.matrix([x,0,0])@a.static.matrix([0,sign*y,0])@a.static.matrix([0,0,sign*z])

def displays(s):
    centre=(np.array(s['bounds']['min'])+s['bounds']['max'])/2-[0,8,0]
    def pose(rotation,scale,left=False):
        t=-item_matrix(rotation,left)@centre*scale
        if left:t[0]*=-1
        return {'rotation':rotation,'translation':t.tolist(),'scale':[scale]*3}
    return {'gui':pose([-30,225,0],1.7),'firstperson_righthand':pose([0,-35,0],.85),'firstperson_lefthand':pose([0,-35,0],.85,True),'thirdperson_righthand':pose([-25,0,0],.8),'thirdperson_lefthand':pose([-25,0,0],.8,True),'fixed':pose([0,180,0],1),'ground':{'rotation':[0,0,0],'translation':[0,4,0],'scale':[.5]*3}}

def build(out):
    assert a.read(a.WORK/'checkpoint.json')['decision']=='proceed_finished_pair'
    a.guard(out);out.mkdir(parents=True);runtime=out/'export/resourcepack';status=[]
    a.cp(a.WORK/'checkpoint.json',out/'source_fidelity_checkpoint.json');a.cp(a.WORK/'preservation_before.json',out/'source/preservation_before.json')
    request=a.read(a.ROOT/'work/agent_exchange/requests'/a.RID/'request.json');sources=[]
    for key in a.PALETTES:
        s,parts,tex,bb=a.product(key);aid=s['asset_id'];s['display']=displays(s);s['world_presentation']={'model_scale':.8,'model_floor_offset':.4,'appearance_scale_assumption':1,'derived_vertices':'(BB/16-[0,.5,0])*.8+[0,.4,0] = BB*.05','physical_width':.4,'physical_height':.08,'physical_bounds':'resolved existing values preserved, render geometry not collision'}
        a.write(out/f'specifications/{aid}.json',s);a.write(out/f'authoring/{aid}.bbmodel',bb);tp=out/f'textures/{aid}.png';tp.parent.mkdir(parents=True,exist_ok=True);tp.write_bytes(a.png(tex))
        modeldir=runtime/'assets/moonstation14/models/item/botany/peels';modeldir.mkdir(parents=True,exist_ok=True);obj,mtl=a.static.obj_mesh(parts,tex.size,aid);(modeldir/f'{aid}.obj').write_text(obj,encoding='utf-8');(modeldir/f'{aid}.mtl').write_text(mtl,encoding='utf-8')
        rid=f'moonstation14:item/botany/peels/{aid}';wrapper={'loader':'neoforge:obj','model':f'moonstation14:models/item/botany/peels/{aid}.obj','textures':{'atlas':rid,'particle':rid},'automatic_culling':False,'shade_quads':True,'flip_v':False,'emissive_ambient':False,'ambientocclusion':False,'render_type':'minecraft:solid','gui_light':'front','display':s['display']}
        a.write(modeldir/f'{aid}.json',wrapper);a.write(runtime/f'assets/moonstation14/models/item/{aid}.json',{'parent':f'moonstation14:item/botany/peels/{aid}'})
        a.cp(tp,runtime/f'assets/moonstation14/textures/item/botany/peels/{aid}.png')
        profile=a.read(a.CTX/f'src/main/resources/data/moonstation14/moonstation14/loose_object_profile/{aid}.json')
        if key=='banana':profile['render_kind']='block_model';profile['visual']={'model_scale':.8,'model_floor_offset':.4}
        # Mimana continues to inherit banana; all physical/slip/trigger fields exact.
        a.write(runtime/f'data/moonstation14/moonstation14/loose_object_profile/{aid}.json',profile)
        rsi=out/f'source/rsi/{key}.rsi';shutil.copytree(a.source(key),rsi)
        meta=a.read(rsi/'meta.json');sources.append({'family':key,'revision':request['source_pin'],'source_directory':str(a.source(key)),'copied_directory':rsi.relative_to(out).as_posix(),'license':meta['license'],'copyright':meta['copyright'],'files':{f.name:a.sha(f) for f in sorted(rsi.iterdir()) if f.is_file()},'state_coverage':[{'state':r['name'],'directions':r.get('directions',1),'delays':r.get('delays'),'disposition':'selected static loose/held/inventory3D' if r['name']=='peel' else 'four-direction pose reference;3D shared same volume' if r['name'] in ('peel-inhand-left','peel-inhand-right') else 'source-only sibling; existing worn/baked/growth/produce/alternative art untouched'} for r in meta['states']]})
        status.append({'id':aid,'prototype':'TrashBananaPeel' if key=='banana' else 'TrashMimanaPeel','master':f'authoring/{aid}.bbmodel','specification':f'specifications/{aid}.json','texture':f'textures/{aid}.png','runtime_model':f'moonstation14:item/{aid}','bounds':s['bounds'],'quad_count':s['quad_count'],'triangles':s['quad_count']*2,'persistent_states':['default'],'animations':[]})
    a.write(out/'collection_status.json',{'request_id':a.RID,'status':'complete pair awaiting independent final review and owner acceptance; not imported','states':status})
    a.write(out/'source/manifest.json',{'request_id':a.RID,'resources':sources,'prototype_chain':'TrashMimanaPeel -> TrashBananaPeel -> BaseItem','target':'Java1.21.1/NeoForge21.1.224','source_revision_verified':'c9df5ef5d675b0d1d226828bddf6b78c28502d91','context_snapshot':request['context_snapshot'],'wearable_policy':'separate equipment_item and fitted head meshes UNCHANGED, not copied into runtime exports','live_reference_repository':'READ ONLY; requester worktree direct access denied to parent, immutable queued context used'})
    for rel in ['Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml','Resources/Prototypes/Entities/Objects/base_item.yml']:a.cp(a.SS14/rel,out/'source/prototypes'/Path(rel).name)
    for rel in request['context']:
        src=a.CTX/rel
        if src.is_file():a.cp(src,out/'source/request_context'/rel)
    a.write(out/'export/binding_contract.json',{'request_id':a.RID,'same_volume':'One matching OBJ+PNG per identity for loose/held/inventory, no context-specific replacement body or wearable reuse','item_bindings':{'TrashBananaPeel':'moonstation14:item/trash_banana_peel','TrashMimanaPeel':'moonstation14:item/trash_mimana_peel'},'loose_profiles':{'trash_banana_peel':'KEEP generic parent, physical width.4, height.08 inherited and ALLtrigger/slip fields; add render_kind:block_model and visual model_scale.8/model_floor_offset.4 ONLY','trash_mimana_peel':'KEEP banana parent and exact sound override; inherit native visual presentation'},'world_transform':'ItemRenderer NONE centres block-space vertices by(-.5,-.5,-.5). OBJ=(BB+[8,0,8])/16. profile translatesY.4, scales.8; rendered floorBBY0 ->worldY0. ProduceWorldAppearance scale assumed1 for non-produce Trash identities; live consumer validation remains external.','held_inventory':'wrapper display transforms recentre same mesh about geometric bounds centre; world renderer uses NONE, not ground transform','physical':'Existing physical_width.4 /physical_height.08 preserved even though rendered stalk taller; no collider/mechanics changes','installation':'NO installation/import/owner approval supplied; data overrides are complete matching candidates only','not_touched':['equipment_item bindings','wearables/meshes/hats','jug renderer','slip/edibility/output/stack/network/mod code']})
    files=[{'file':f.relative_to(out).as_posix(),'destination':'src/main/resources/'+f.relative_to(runtime).as_posix(),'sha256':a.sha(f)} for f in sorted(runtime.rglob('*')) if f.is_file()];assert len(files)==12
    a.write(out/'export/manifest.json',{'path_base':'package root','files':files,'scope':'complete pair, ordinary model dependency closure plus native visual-only loose profile candidates'})
    print('Built complete pair',out)

def contact_source(out):
    im=Image.new('RGB',(1500,620),(28,37,49));d=ImageDraw.Draw(im)
    for i,key in enumerate(a.PALETTES):
        for j,name in enumerate(('peel','peel2','peel3')):
            fr=Image.open(out/f'source/rsi/{key}.rsi/{name}.png').convert('RGBA').resize((192,192),Image.Resampling.NEAREST);x=j*200;y=i*310+35;im.paste(fr,(x,y),fr);d.text((x,y-25),key+'/'+name+(' SELECTED' if name=='peel' else ' unselected'),fill='white')
        for j,name in enumerate(('peel-inhand-left','peel-inhand-right')):
            src=Image.open(out/f'source/rsi/{key}.rsi/{name}.png').convert('RGBA');nx=src.width//32
            for k,di in enumerate(('S','N','E','W')):
                fr=src.crop((k%nx*32,k//nx*32,k%nx*32+32,k//nx*32+32)).resize((128,128),Image.Resampling.NEAREST);x=620+j*440+k%2*210;y=i*310+25+k//2*140;im.paste(fr,(x,y),fr);d.text((x,y),name+' '+di,fill='white')
    im.save(out/'previews/source_contact.png')

def scene(parts,tex,reference=None,yaw=35,elev=25,size=(720,540),zoom=1):
    ps=copy.deepcopy(parts);at=Image.new('RGBA',(80,32));at.paste(tex,(0,0));at.paste((84,98,109,255),(66,1,70,5));at.paste((149,163,174,255),(72,1,76,5));at.paste((190,145,111,255),(66,8,70,12));at.paste((43,56,65,255),(72,8,76,12))
    def cube(name,lo,hi,uv):return {'name':name,'group':'shell','quads':[{'vertices':a.core.face_vertices(lo,hi,f),'normal':a.core.NORMALS[f],'uv':[[uv[0],uv[1]],[uv[2],uv[1]],[uv[2],uv[3]],[uv[0],uv[3]]]} for f in a.core.FACES]}
    if reference=='world':
        # Actual .8scale floor pose; single16unit floor cell and32unit player reference.
        for p in ps:
            for q in p['quads']:q['vertices']=(np.array(q['vertices'])*.8+[4,0,0]).tolist()
        ps.extend([cube('one_block_floor',[-8,-.25,-8],[8,0,8],[66,1,70,5]),cube('player_32u',[-13,0,0],[-9,32,4],[72,1,76,5]),cube('player_head',[-14,24,-1],[-8,32,5],[72,1,76,5])])
    elif reference=='hand':
        ps.extend([cube('illustrative_palm',[-1.9,-1.2,-1.6],[1.9,0,1.6],[66,8,70,12]),cube('illustrative_wrist',[-1.2,-4,-.8],[1.2,-1.2,1.1],[72,8,76,12])])
    points=np.array([v for p in ps for q in p['quads'] for v in q['vertices']]);cy=(points[:,1].max()+points[:,1].min())/2
    for p in ps:
        for q in p['quads']:q['vertices']=(np.array(q['vertices'])*zoom+[0,15-cy*zoom,0]).tolist()
    return a.core.render({'groups':[{'name':'door','pivot':[0,0,0]}],'dimensions':{'door_open_degrees':0}},ps,at,{'open':False,'locked':False,'welded':False,'label':'none'},yaw,elev,size=size)

def review(out):
    assert not a.OUT.exists() and not (out/'previews').exists();(out/'previews').mkdir();contact_source(out)
    status=a.read(out/'collection_status.json')['states']
    for r in status:
        aid=r['id'];_,parts,tex=a.static.read_bb(out/r['master']);sheet=Image.new('RGB',(1280,640),(28,37,49));d=ImageDraw.Draw(sheet)
        for i,(label,yaw,elev) in enumerate([('front',0,15),('rear',180,15),('east',90,15),('west',270,15),('top',0,90),('underside',0,-90),('front3q',35,30),('rear3q',215,30)]):
            x=i%4*320;y=i//4*320;sheet.paste(a.render(parts,tex,yaw,elev,size=(320,300),zoom=3.5),(x,y+20));d.text((x+4,y+3),label,fill='white')
        sheet.save(out/f'previews/{aid}_eight_views.png')
        key='banana' if aid=='trash_banana_peel' else 'mimana';sheet=Image.new('RGB',(1280,480),(28,37,49));d=ImageDraw.Draw(sheet);src=Image.open(out/f'source/rsi/{key}.rsi/peel.png').convert('RGBA').resize((256,256),Image.Resampling.NEAREST);sheet.paste(src,(0,90),src)
        sheet.paste(a.render(parts,tex,0,25,size=(500,440)),(280,30));sheet.paste(a.render(parts,tex,35,25,size=(500,440)),(780,30));d.text((5,5),key+' SOURCE peel | actual saved model front | threequarter;66quads, not wearable/fruit/flat extrusion',fill='white');sheet.save(out/f'previews/{aid}_source_model.png')
        scene(parts,tex,'world',yaw=25,elev=25,size=(800,600)).save(out/f'previews/{aid}_world.png');scene(parts,tex,'hand',yaw=25,elev=25,size=(600,500),zoom=3).save(out/f'previews/{aid}_palm.png')
        # Actual native display transform applied to saved mesh, with vanilla centring.
        s=a.read(out/r['specification']);contexts=Image.new('RGB',(1200,800),(28,37,49));dd=ImageDraw.Draw(contexts)
        for i,context in enumerate(('gui','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand','ground')):
            transformed=copy.deepcopy(parts);pose=s['display'][context];left=context.endswith('lefthand');R=item_matrix(pose['rotation'],left);scale=pose['scale'][0];t=np.array(pose['translation']);t[0]*=-1 if left else 1
            for pp in transformed:
                for q in pp['quads']:q['vertices']=((np.array(q['vertices'])-[0,8,0])@R.T*scale+t).tolist();q['normal']=(np.array(q['normal'])@R.T).tolist()
            image=scene(transformed,tex,yaw=0,elev=0,size=(400,370),zoom=2.5);x=i%3*400;y=i//3*400;contexts.paste(image,(x,y+25));dd.text((x+4,y+4),context+' (CPU contract preview)',fill='white')
        contexts.save(out/f'previews/{aid}_item_contexts.png')
    html='<!doctype html><meta charset="utf-8"><title>Banana + mimana peel — native3D pair</title><style>body{background:#1c2531;color:white;font:16px system-ui;margin:24px}a{color:#aeeaff}img{max-width:100%;image-rendering:pixelated}section{margin-top:48px}</style><h1>Finished banana / mimana peel pair</h1><p>Same low, open three-lobe skin volume for loose, held and inventory.66quads each, distinct native palettes. Pending independent final review/OWNER approval, NOT installed. <a href="README.md">Open / exports / bindings / limits</a></p><h2>Native selected and reference states</h2><img src="previews/source_contact.png">'
    for r in status:
        aid=r['id'];html+=f'<section><h2>{aid}</h2><p><a href="{r["master"]}">Editable BBModel</a> · <a href="{r["specification"]}">Spec / bounds / counts</a></p>'
        for suffix,label in [('source_model','Source / actual model'),('eight_views','All sides / top / underside'),('world','Floor pose /16unit block /32unit player'),('palm','Illustrative palm fit, not installed player renderer'),('item_contexts','Declared item display transforms, not game screenshots')]:html+=f'<h3>{label}</h3><img src="previews/{aid}_{suffix}.png">'
        html+='</section>'
    (out/'index.html').write_text(html,encoding='utf-8');print('Rendered finished pair allviews/world/palm/item contexts',out)

def verify(out,reproduce=True):
    counts=[];images=0;values=0
    for r in a.read(out/'collection_status.json')['states']:
        aid=r['id'];key=aid.split('_')[1];s=a.read(out/r['specification']);bb,ps,tex=a.static.read_bb(out/r['master']);assert len(bb['elements'])==5 and not bb['animations'];assert tex.tobytes()==Image.open(out/r['texture']).convert('RGBA').tobytes()
        ids=[e['uuid'] for e in bb['elements']];assert len(ids)==len(set(ids));refs=bb['outliner'][0]['children'][0]['children'];assert sorted(ids)==sorted(refs)
        count=sum(len(p['quads']) for p in ps);assert count==66<=96
        for p in ps:
            # Mesh is closed within each semantic editable part (neck/lobes may overlap internally).
            edges={}
            for q in p['quads']:
                vs=np.array(q['vertices']);uv=np.array(q['uv']);n=np.array(q['normal']);assert np.isfinite(vs).all() and np.isfinite(uv).all();assert np.all(uv>=0) and np.all(uv<=[64,32]);assert abs(np.dot(vs[3]-vs[0],n))<1e-8
                assert np.dot(np.cross(vs[1]-vs[0],vs[2]-vs[0]),n)>1e-8
                for i in range(4):
                    e=tuple(sorted([tuple(vs[i]),tuple(vs[(i+1)%4])]))
                    edges[e]=edges.get(e,0)+1
            assert all(v==2 for v in edges.values()),p['name']
        if reproduce:
            ns,np_,nt,nb=a.product(key);assert nb==bb and nt.tobytes()==tex.tobytes();assert ns['parts']==s['parts'] and ns['bounds']==s['bounds']
        root=out/'export/resourcepack/assets/moonstation14';wrapper=a.read(root/f'models/item/botany/peels/{aid}.json');assert wrapper['loader']=='neoforge:obj' and wrapper['flip_v'] is False and wrapper['display']==s['display'];assert a.read(root/f'models/item/{aid}.json')['parent']==f'moonstation14:item/botany/peels/{aid}'
        obj=root/f'models/item/botany/peels/{aid}.obj';qq=a.core.load_obj_quads(obj,tex.size,[8,0,8],False);original=[q for p in ps for q in p['quads']];assert len(qq)==len(original)
        for q,j in zip(original,qq):
            for field in ('vertices','uv','normal'):assert np.allclose(q[field],j[field],atol=1e-8,rtol=0);values+=np.array(q[field]).size
        op=[{'name':aid,'group':'shell','quads':qq}]
        for yaw in (0,35,180,215):assert a.render(ps,tex,yaw,25,size=(192,192)).tobytes()==a.render(op,tex,yaw,25,size=(192,192)).tobytes();images+=1
        source=np.array(Image.open(out/f'source/rsi/{key}.rsi/peel.png').convert('RGBA'));allowed=set(map(tuple,source[:,:,:3][source[:,:,3]>0].tolist()));ar=np.array(tex);assert set(map(tuple,ar[:,:,:3][ar[:,:,3]>0].tolist()))<=allowed
        vertices=np.array([v for p in ps for q in p['quads'] for v in q['vertices']]);assert abs(vertices[:,1].min())<1e-9;assert vertices[:,1].max()<4 and np.max(np.ptp(vertices,axis=0))<9;world=(vertices/16-[0,.5,0])*.8+[0,.4,0];assert abs(world[:,1].min())<1e-9
        profpath=f'data/moonstation14/moonstation14/loose_object_profile/{aid}.json';before=a.read(a.CTX/'src/main/resources'/profpath);after=a.read(out/'export/resourcepack'/profpath)
        if key=='banana':assert after.pop('render_kind')=='block_model' and after.pop('visual')=={'model_scale':.8,'model_floor_offset':.4}
        assert after==before # No physical/gameplay composition changes
        counts.append({'id':aid,'quads':count,'triangles':count*2,'bounds':s['bounds'],'world_bounds_min':world.min(0).tolist(),'world_bounds_max':world.max(0).tolist()})
    manifest=a.read(out/'export/manifest.json')['files'];assert len(manifest)==12 and len({r['destination'] for r in manifest})==12
    for r in manifest:assert a.sha(out/r['file'])==r['sha256'];assert not any(word in r['destination'] for word in ['equipment_item','wearables','jug'])
    for res in a.read(out/'source/manifest.json')['resources']:
        for f,h in res['files'].items():assert a.sha(out/res['copied_directory']/f)==h
    for f,h in a.read(out/'source/preservation_before.json')['files'].items():assert a.sha(a.ROOT/f)==h,f
    if (out/'frozen_hashes.json').exists():
        for f,h in a.read(out/'frozen_hashes.json')['files'].items():assert a.sha(out/f)==h,f
    return {'status':'PASS','items':counts,'numeric_vertex_uv_normal_values':values,'savedBB_OBJ_CPU_image_equalities':images,'runtime_dependencies':12,'source_palette_membership':True,'closed_semantic_mesh_edges':True,'quad_planarity_winding':True,'world_floor_alignment':True,'all_gameplay_and_wearable_bindings_unchanged':True,'preserved_files':len(a.read(out/'source/preservation_before.json')['files']),'not_validated':['Blockbench application','NeoForge native bake/Java-loader execution','Minecraft/GPU/production player/world rendering/lighting/mips/performance','independent owner acceptance/import']}

if __name__=='__main__':
    ap=argparse.ArgumentParser();ap.add_argument('action',choices=['checkpoint','build','review','verify']);ap.add_argument('--output',type=Path,default=a.OUT);args=ap.parse_args()
    if args.action=='checkpoint':checkpoint()
    elif args.action in ('build','review'):
        assert args.output.resolve().is_relative_to(a.WORK);{'build':build,'review':review}[args.action](args.output.resolve())
    else:print(json.dumps(verify(args.output),indent=2))
