#!/usr/bin/env python3
"""Filesystem-backed asset and coding handoff queue; no git/game mutation."""
import argparse, hashlib, json, os, re, shutil, subprocess, sys, time, types
from pathlib import Path
try:
    import validation_evidence
except ModuleNotFoundError:
    from tooling.agent_workflow import validation_evidence

ID = re.compile(r'^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$')
PNG = b'\x89PNG\r\n\x1a\n'


def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
    return h.hexdigest()

def readjson(path):
    try: return json.loads(Path(path).read_text(encoding='utf-8'))
    except (OSError,json.JSONDecodeError) as e: raise ValueError(f'{path}: {e}') from e

def read_report(path):
    path=Path(path)
    if path.stat().st_size>1024*1024: raise ValueError('review report exceeds 1 MiB limit')
    return readjson(path)

def writejson(path,obj):
    path=Path(path); path.parent.mkdir(parents=True,exist_ok=True)
    tmp=path.with_name(path.name+'.tmp'); tmp.write_text(json.dumps(obj,indent=2,sort_keys=True)+'\n',encoding='utf8'); os.replace(tmp,path)

def validid(value):
    if not isinstance(value,str) or not ID.fullmatch(value): raise ValueError('invalid id (use 1-80 letters, digits, _ or -; start alphanumeric)')
    return value

def safe(root,value):
    if not isinstance(value,str) or not value: raise ValueError('path must be a non-empty string')
    p=(Path(root).resolve()/value).resolve()
    if not p.is_relative_to(Path(root).resolve()): raise ValueError(f'path escapes root: {value}')
    return p

def git(repo,*args):
    env=os.environ.copy(); env['GIT_OPTIONAL_LOCKS']='0'
    return subprocess.run(['git','-C',str(repo),*args],check=True,capture_output=True,env=env).stdout

def gitid(repo):
    return {'worktree':str(Path(repo).resolve()),'head':git(repo,'rev-parse','HEAD').decode().strip(),'branch':git(repo,'branch','--show-current').decode().strip()}

def queue(a,create=False):
    if a.asset_workspace: root=Path(a.asset_workspace).resolve()/'work'/'agent_exchange'
    else:
        repo=Path(a.repo).resolve(); common=Path(git(repo,'rev-parse','--git-common-dir').decode().strip())
        if not common.is_absolute(): common=(repo/common).resolve()
        # common is <owning-repo>/.git; its parent is the repo, grandparent has sibling workspace.
        root=common.parent.parent/'Asset Building Workspace'/'work'/'agent_exchange'
    if create: root.mkdir(parents=True,exist_ok=True)
    return root

def getrequest(root,rid):
    validid(rid); folder=root/'requests'/rid
    if not (folder/'request.json').is_file(): raise ValueError(f'unknown request: {rid}')
    r=readjson(folder/'request.json'); verify_request(folder,r)
    return folder,r

def request_snapshot_material(r):
    mutable={'status','requester','context_snapshot','snapshot_key','delivery','review','import_receipt'}
    return {k:v for k,v in r.items() if k not in mutable}|{'context_snapshot':r.get('context_snapshot',[])}

def verify_request(folder,r):
    if snapshot_key(request_snapshot_material(r))!=r.get('snapshot_key'): raise ValueError('request snapshot key mismatch')
    for item in r.get('context_snapshot',[]):
        p=safe(folder/'context',item['path'])
        if not p.is_file() or sha(p)!=item['sha256']: raise ValueError(f'context snapshot stale: {item["path"]}')

def flatmanifest(path,source_root):
    """Deliberately narrow accepted format: complete flat files[] rows with file+destination."""
    path=Path(path).resolve(); m=readjson(path); rows=m.get('files')
    if not isinstance(rows,list):
        if 'items' in m: raise ValueError('unsupported manifest: nested items (wearable geometry/bone_mesh/nullable destinations) need an explicit reviewed adapter')
        raise ValueError('unsupported manifest: expected flat files[] format')
    if not rows: raise ValueError('manifest files[] is empty')
    base=Path(source_root).resolve(); output=[]; srcs=set(); dests=set()
    for i,row in enumerate(rows):
        if not isinstance(row,dict): raise ValueError(f'files[{i}] must be an object')
        src=row.get('file'); dest=row.get('destination')
        if not isinstance(src,str) or not isinstance(dest,str) or not src or not dest: raise ValueError(f'files[{i}] missing file or destination')
        status=row.get('status','')
        if isinstance(status,str) and any(word in status.casefold() for word in ('remap','transform','unsupported')):
            raise ValueError(f'unsupported manifest transform at files[{i}]: {status}')
        if Path(src).suffix.casefold()=='.mtl': raise ValueError(f'unsupported MTL remapping/transform: {src}')
        sp=safe(base,src); dp=Path(dest)
        if dp.is_absolute() or '..' in dp.parts or not dp.parts: raise ValueError(f'invalid destination: {dest}')
        sk=src.casefold(); dk=dp.as_posix().casefold()
        if sk in srcs: raise ValueError(f'duplicate source: {src}')
        if dk in dests: raise ValueError(f'duplicate destination: {dest}')
        srcs.add(sk); dests.add(dk)
        if not sp.is_file(): raise ValueError(f'missing source/dependency: {src}')
        output.append({'source':src,'destination':dp.as_posix(),'source_sha256':sha(sp),'size':sp.stat().st_size})
    return path,m,output

def context_snapshot(values,base,folder):
    prepared=[]; names=set()
    for value in values:
        src=safe(base,value)
        if not src.is_file(): raise ValueError(f'missing context: {value}')
        rel=Path(value)
        if rel.is_absolute() or '..' in rel.parts: raise ValueError(f'context path must be relative: {value}')
        name=rel.as_posix(); key=name.casefold()
        if key in names: raise ValueError(f'duplicate context path: {value}')
        names.add(key); prepared.append((src,name,sha(src)))
    return prepared

def snapshot_key(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def cmd_request(a,root):
    r=readjson(a.from_json); rid=validid(r.get('id'))
    if not isinstance(r.get('brief'),str) or not r['brief'].strip(): raise ValueError('request requires non-empty brief')
    if not isinstance(r.get('context',[]),list): raise ValueError('context must be a path list')
    folder=root/'requests'/rid
    if folder.exists(): raise ValueError(f'request id already exists: {rid}')
    prepared=context_snapshot(r.get('context',[]),Path(a.context_root or a.repo),folder)
    folder.mkdir(parents=True)
    try:
        ctx=[]
        for src,name,h in prepared:
            dst=folder/'context'/Path(name); dst.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(src,dst)
            ctx.append({'path':name,'sha256':h})
        r.update(status='open',requester=gitid(a.repo),context_snapshot=ctx)
        r['snapshot_key']=snapshot_key(request_snapshot_material(r))
        writejson(folder/'request.json',r)
    except Exception:
        shutil.rmtree(folder,ignore_errors=True); raise
    print(rid)

def cmd_claim(a,root):
    folder,r=getrequest(root,a.id); validid(a.id)
    try: fd=os.open(folder/'claim.json',os.O_CREAT|os.O_EXCL|os.O_WRONLY)
    except FileExistsError: raise ValueError('already claimed')
    with os.fdopen(fd,'w',encoding='utf8') as f: json.dump({'agent':a.agent,'worktree':str(Path(a.repo).resolve())},f)
    r['status']='claimed'; writejson(folder/'request.json',r); print('claimed')

def cmd_plan(a,root):
    path,m,rows=flatmanifest(a.manifest,a.source_root)
    target=Path(a.target).resolve() if a.target else None
    if target:
        for row in rows:
            dst,existing=inspect_destination(target,row['destination']); row['target_sha256']=existing
            row['action']='unchanged' if row['target_sha256']==row['source_sha256'] else ('conflict' if row['target_sha256'] else 'would-copy')
    print(json.dumps({'mode':'read-only-plan','manifest':str(path),'manifest_sha256':sha(path),'target':str(target) if target else None,'files':rows},indent=2))

def cmd_deliver(a,root):
    folder,r=getrequest(root,a.id)
    if not (folder/'claim.json').is_file(): raise ValueError('claim required')
    path,m,rows=flatmanifest(a.manifest,a.source_root)
    if (folder/'delivery').exists(): raise ValueError('delivery is immutable; create a new request/revision id')
    # Complete group only. Bind explicit lineage/provenance/editable/gallery records, not guessed sidecars.
    if not a.provenance or not a.editable or not a.review_images: raise ValueError('explicit --provenance, --editable and --review-images metadata required')
    sidecars=[]
    for label,values in [('provenance',a.provenance),('editable',a.editable),('review_image',a.review_images)]:
        for val in values:
            p=safe(a.source_root,val)
            if not p.is_file(): raise ValueError(f'missing {label}: {val}')
            sidecars.append({'kind':label,'path':val,'sha256':sha(p)})
    delivered=folder/'delivery'; deliverables=delivered/'files'
    # All validation precedes creating/copying delivery; once published immutable.
    delivered.mkdir(parents=True)
    try:
        for row in rows:
            src=safe(a.source_root,row['source']); dst=deliverables/row['destination']; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(src,dst)
        manifest_copy=delivered/'source_manifest.json'; shutil.copyfile(path,manifest_copy)
        manifest_copy_hash=sha(manifest_copy)
        r['delivery']={'revision':a.revision,'source_root':str(Path(a.source_root).resolve()),'manifest_path':str(path),'manifest_sha256':manifest_copy_hash,'files':rows,'metadata':sidecars,'digest':delivery_digest(manifest_copy_hash,rows,sidecars,a.revision)}
        r['status']='delivered'; writejson(folder/'request.json',r)
    except Exception:
        shutil.rmtree(delivered,ignore_errors=True); raise
    print(f'delivered complete group: {len(rows)} files; new revision {a.revision}')

def delivery_digest(manifest_hash,rows,metadata,revision):
    blob=json.dumps({'manifest':manifest_hash,'files':rows,'metadata':metadata,'revision':revision},sort_keys=True,separators=(',',':')).encode()
    return hashlib.sha256(blob).hexdigest()

def verify_delivery(folder,r):
    d=r.get('delivery')
    if not d: raise ValueError('no delivery')
    mp=folder/'delivery'/'source_manifest.json'
    if not mp.is_file() or sha(mp)!=d['manifest_sha256']: raise ValueError('captured manifest hash changed')
    srcroot=Path(d['source_root']); path=Path(d['manifest_path'])
    if not path.is_file() or sha(path)!=d['manifest_sha256']: raise ValueError('original source manifest stale; create a new revision')
    _,_,current=flatmanifest(path,srcroot)
    if current!=d['files']: raise ValueError('source files changed since delivery; create a new revision')
    if delivery_digest(d['manifest_sha256'],d['files'],d['metadata'],d['revision'])!=d['digest']: raise ValueError('delivery receipt digest mismatch')
    for row in d['files']:
        copied=folder/'delivery'/'files'/row['destination']
        if not copied.is_file() or sha(copied)!=row['source_sha256']: raise ValueError(f'delivered bytes changed: {row["source"]}')
    for x in d['metadata']:
        p=safe(srcroot,x['path'])
        if not p.is_file() or sha(p)!=x['sha256']: raise ValueError(f'delivery metadata stale: {x["path"]}')
    return d

def evidence_paths(paths,base):
    out=[]
    for value in paths:
        p=safe(base,value)
        if not p.is_file(): raise ValueError(f'missing evidence: {value}')
        if p.stat().st_size>64*1024*1024: raise ValueError(f'evidence exceeds 64 MiB limit: {value}')
        item={'path':str(p),'sha256':sha(p)}
        if p.suffix.lower()=='.png':
            with p.open('rb') as f: head=f.read(24)
            if len(head)<24 or head[:8]!=PNG: raise ValueError(f'not a valid PNG header: {value}')
            item['png_dimensions']=[int.from_bytes(head[16:20],'big'),int.from_bytes(head[20:24],'big')]
            if min(item['png_dimensions'])<1 or max(item['png_dimensions'])>8192: raise ValueError(f'invalid or excessive PNG dimensions: {value}')
        out.append(item)
    return out

def reviewed_images(records,base):
    if not isinstance(records,list) or not records: raise ValueError('review report requires viewed_images array')
    paths=[]
    for item in records:
        if not isinstance(item,dict) or not isinstance(item.get('path'),str) or not re.fullmatch(r'[0-9a-f]{64}',str(item.get('sha256',''))): raise ValueError('viewed_images entries require path and sha256')
        if Path(item['path']).suffix.lower()!='.png': raise ValueError('viewed image evidence must be PNG')
        paths.append(item['path'])
    actual=evidence_paths(paths,base)
    for given,current in zip(records,actual):
        if given['sha256']!=current['sha256']: raise ValueError(f"review image hash mismatch: {given['path']}")
    return actual

def verify_original_images(records,candidate,report_base=None):
    if not isinstance(records,list): raise ValueError('original_images must be an array')
    repo=Path(candidate['worktree']['worktree']).resolve(); evidence={Path(x['path']).resolve():x['sha256'] for x in candidate.get('evidence',[])}
    result=[]
    for item in records:
        if not isinstance(item,dict): raise ValueError('original_images entries must be objects')
        if 'original_path' in item:
            if report_base is None or not isinstance(item.get('path'),str) or not re.fullmatch(r'[0-9a-f]{64}',str(item.get('sha256',''))): raise ValueError('copied original_images require report path/hash binding')
            copied=safe(report_base,item['path']); source=Path(item.get('original_path','')).resolve(); digest=item.get('original_sha256')
            if not copied.is_file() or sha(copied)!=item['sha256']: raise ValueError(f'copied original review image stale: {copied}')
            if item['sha256']!=digest: raise ValueError('copied/original image hashes differ')
            path=source
        else:
            if not isinstance(item.get('path'),str): raise ValueError('original_images require path and sha256')
            raw=Path(item['path']); path=(raw if raw.is_absolute() else repo/raw).resolve(); digest=item.get('sha256')
        if not isinstance(digest,str) or not re.fullmatch(r'[0-9a-f]{64}',digest): raise ValueError('original_images require SHA-256')
        if path.suffix.lower()!='.png' or (not path.is_relative_to(repo) and path not in evidence): raise ValueError(f'original image outside candidate source/evidence scope: {path}')
        if not path.is_file() or sha(path)!=digest or (path in evidence and evidence[path]!=digest): raise ValueError(f'original image missing/stale: {path}')
        result.append({'path':str(path),'sha256':digest})
    return result

def capture_review_images(records,originals,report_path,ident):
    if not isinstance(records,list): raise ValueError('viewed_images must be an array')
    base=Path(report_path).resolve().parent; original_by_hash={x['sha256']:Path(x['path']) for x in originals}; copied=[]
    for index,item in enumerate(records):
        if not isinstance(item,dict) or item.get('sha256') not in original_by_hash: raise ValueError('each viewed image must bind a verified original_images SHA-256')
        source=original_by_hash[item['sha256']]
        target=base/'reviewed-images'/f'{ident}-{index+1}-{item["sha256"][:16]}.png'
        target.parent.mkdir(parents=True,exist_ok=True)
        if target.exists() and sha(target)!=item['sha256']: raise ValueError(f'review image capture conflict: {target}')
        if not target.exists(): shutil.copyfile(source,target)
        copied.append({'path':target.relative_to(base).as_posix(),'sha256':item['sha256']})
    return copied

def optional_reviewed_images(records,base):
    if not isinstance(records,list): raise ValueError('viewed_images must be an array')
    return reviewed_images(records,base) if records else []

def cmd_review(a,root):
    folder,r=getrequest(root,a.id); d=verify_delivery(folder,r)
    if a.reviewer==readjson(folder/'claim.json').get('agent'): raise ValueError('reviewer must differ from implementer/claim agent')
    report=read_report(a.report)
    required={'request_id':a.id,'snapshot_key':r['snapshot_key'],'delivery_digest':d['digest'],'reviewer':a.reviewer,'findings':report.get('findings'),'viewed_images':report.get('viewed_images')}
    if report.get('request_id')!=a.id or report.get('snapshot_key')!=r['snapshot_key'] or report.get('delivery_digest')!=d['digest'] or report.get('reviewer')!=a.reviewer: raise ValueError('review report request/snapshot/delivery/reviewer mismatch')
    if report.get('decision')!=a.result: raise ValueError('review report decision mismatch')
    if not isinstance(required['findings'],list) or not required['findings'] or len(required['findings'])>100 or any(not isinstance(x,str) or len(x)>2000 for x in required['findings']): raise ValueError('review report requires 1-100 bounded text findings')
    images=reviewed_images(required['viewed_images'],Path(a.report).resolve().parent)
    r['review']={'result':a.result,'report_path':str(Path(a.report).resolve()),'report_sha256':sha(Path(a.report)),'report':report,'viewed_images':images,'inspection_is_reviewer_assertion':True}
    r['status']=a.result; writejson(folder/'request.json',r); print(a.result)

def current_review(folder,r):
    d=verify_delivery(folder,r); review=r.get('review')
    if not review or review['result']!='ready_for_owner': raise ValueError('independent ready_for_owner review required')
    if not Path(review.get('report_path','')).is_file() or review['report_sha256']!=sha(Path(review['report_path'])): raise ValueError('review report changed')
    report=read_report(review['report_path'])
    if report.get('delivery_digest')!=d['digest'] or report.get('request_id')!=r['id'] or report.get('snapshot_key')!=r['snapshot_key']: raise ValueError('review report no longer matches delivery/request snapshot')
    for x in review['viewed_images']:
        p=Path(x['path'])
        if not p.is_file() or sha(p)!=x['sha256']: raise ValueError('review image changed')
    return d,review

def inspect_destination(target,destination):
    root=Path(target).resolve()
    if root.exists() and not root.is_dir(): raise ValueError(f'target root is not a directory: {root}')
    dst=safe(root,destination)
    parent=dst.parent
    while parent!=root and parent.is_relative_to(root):
        if parent.exists() and not parent.is_dir(): raise ValueError(f'destination parent exists but is not a directory: {parent}')
        parent=parent.parent
    if dst.exists() and not dst.is_file(): raise ValueError(f'destination exists but is not a regular file: {destination}')
    return dst,sha(dst) if dst.is_file() else None

def target_plan(target,d,overwrite=False):
    result=[]
    for row in d['files']:
        dst,existing=inspect_destination(target,row['destination'])
        if existing==row['source_sha256']: action='unchanged'
        elif existing and not overwrite: raise ValueError(f'overwrite conflict: {row["destination"]}; inspect fresh plan and explicitly authorize overwrite')
        else: action='copy'
        result.append({'destination':row['destination'],'expected_existing_sha256':existing,'new_sha256':row['source_sha256'],'action':action})
    return result

def cmd_import(a,root):
    folder,r=getrequest(root,a.id); d=verify_delivery(folder,r)
    owner_hash=None
    if a.owner_receipt:
        owner=readjson(a.owner_receipt)
        if owner.get('request_id')!=a.id or owner.get('decision')!='accepted' or owner.get('delivery_digest')!=d['digest']: raise ValueError('owner receipt must accept this exact request and delivery digest')
        owner_hash=sha(Path(a.owner_receipt))
    if a.apply:
        d,review=current_review(folder,r)
        if owner_hash is None: raise ValueError('owner receipt required for --apply')
        if (folder/'import_receipt.json').exists(): raise ValueError('this request already has a successful import receipt; use a new request/revision id')
    target=Path(a.target).resolve(); plan=target_plan(target,d,a.allow_overwrite)
    if a.plan_out:
        if Path(a.plan_out).exists(): raise ValueError(f'plan file already exists; choose a new output path: {a.plan_out}')
        writejson(a.plan_out,{'request_id':a.id,'delivery_digest':d['digest'],'owner_receipt_sha256':owner_hash,'target':str(target),'overwrite_acknowledged':a.allow_overwrite,'files':plan})
    if not a.apply:
        print(json.dumps({'dry_run':True,'target':str(target),'files':plan},indent=2)); return
    if not a.plan: raise ValueError('--apply requires --plan from the reviewed dry-run; target changes must be re-planned')
    saved=readjson(a.plan)
    if saved.get('request_id')!=a.id or saved.get('delivery_digest')!=d['digest'] or saved.get('owner_receipt_sha256')!=owner_hash or owner_hash is None or Path(saved.get('target','')).resolve()!=target or saved.get('files')!=plan: raise ValueError('target, acceptance receipt or plan changed; create a fresh post-acceptance dry-run')
    if saved.get('overwrite_acknowledged')!=a.allow_overwrite: raise ValueError('overwrite authorization differs from plan')
    for row in d['files']:
        dst=safe(target,row['destination']); src=folder/'delivery'/'files'/row['destination']
        if sha(src)!=row['source_sha256']: raise ValueError('delivery changed after plan')
        if sha(safe(d['source_root'],row['source']))!=row['source_sha256']: raise ValueError('source changed after plan')
    outcomes=[]
    for index,(row,p) in enumerate(zip(d['files'],plan)):
        dst=safe(target,row['destination'])
        if p['action']=='unchanged': outcomes.append({'destination':row['destination'],'status':'unchanged'}); continue
        try:
            dst.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(folder/'delivery'/'files'/row['destination'],dst)
            outcomes.append({'destination':row['destination'],'status':'copied'})
        except OSError as exc:
            outcomes.append({'destination':row['destination'],'status':'failed','error':str(exc)})
            outcomes.extend({'destination':future['destination'],'status':'not_attempted'} for future in d['files'][index+1:])
            attempt={'request_id':a.id,'delivery_digest':d['digest'],'plan':str(Path(a.plan).resolve()),'owner_receipt_sha256':owner_hash,'result':'partial_failure_no_rollback','paths':outcomes}
            writejson(folder/'import_attempt.json',attempt); r['status']='import_failed'; writejson(folder/'request.json',r)
            raise ValueError(f"import partially failed at {row['destination']}; earlier copies may remain; inspect {folder/'import_attempt.json'} (no rollback)") from exc
    receipt={'request_id':a.id,'delivery_digest':d['digest'],'plan':str(Path(a.plan).resolve()),'files':plan,'owner_receipt_sha256':owner_hash,'paths':outcomes,'result':'complete'}
    writejson(folder/'import_receipt.json',receipt); r['status']='imported'; writejson(folder/'request.json',r); print('imported')

def git_changes(repo,base):
    base_sha=git(repo,'rev-parse',base).decode().strip()
    committed=git(repo,'diff','--name-status','-z',base_sha,'--').decode(errors='surrogateescape').split('\0'); committed_rows=[]
    i=0
    while i<len(committed) and committed[i]:
        status=committed[i]; i+=1; name=committed[i]; i+=1
        if status.startswith('R') or status.startswith('C'):
            old=name; name=committed[i]; i+=1; committed_rows.append({'status':status,'old_path':old,'path':name})
        else: committed_rows.append({'status':status,'path':name})
    raw=git(repo,'status','--porcelain=v1','-z','--untracked-files=all').decode(errors='surrogateescape').split('\0'); dirty=[]; i=0
    while i<len(raw) and raw[i]:
        entry=raw[i]; i+=1; status=entry[:2]; name=entry[3:]
        item={'status':status,'path':name}
        if 'R' in status or 'C' in status:
            item['old_path']=raw[i]; i+=1
        p=safe(repo,name)
        item['sha256']=sha(p) if p.is_file() else None
        dirty.append(item)
    return base_sha,committed_rows,dirty

def cmd_submit(a,root):
    rid=validid(a.id); repo=Path(a.repo).resolve()
    if not (a.build_identity.strip() and a.steps.strip()): raise ValueError('candidate requires non-empty build identity and manual validation steps (or explicitly say blocked/not run)')
    base,committed,dirty=git_changes(repo,a.base_ref); ev=evidence_paths(a.evidence,repo)
    p=root/'candidates'/f'{rid}.json'
    if p.exists(): raise ValueError('candidate id collision; use new revision id')
    plan=None
    if a.gate_plan:
        plan=validate_gate_plan(readjson(a.gate_plan),repo)
        refs={}
        for ref in [plan['fixture']]+[x for x in plan.get('references',{})]:
            rp=safe(repo,ref)
            if not rp.exists(): raise ValueError(f'gate fixture/reference missing: {ref}')
            if rp.is_file(): refs[ref]=sha(rp)
        plan['references']=refs
    candidate={'id':rid,'status':'candidate','agent':a.agent,'visual_review_required':a.visual_review_required,'worktree':gitid(repo),'base_ref':a.base_ref,'base_sha':base,'committed_changes':committed,'dirty_changes':dirty,'evidence':ev,'build_identity':a.build_identity,'steps':a.steps,'unverified':a.unverified,'gate_plan':plan}
    candidate['snapshot_key']=snapshot_key(candidate); writejson(p,candidate)
    print(p)

def validate_gate_plan(plan,repo):
    if not isinstance(plan,dict): raise ValueError('gate plan must be a JSON object')
    for key in ('title','kind','acceptance_criteria','fixture','manual_steps','checks'):
        if key not in plan: raise ValueError(f'gate plan missing {key}')
    if plan['kind'] not in ('sprint','feature','bugfix','docs','art','other'): raise ValueError('invalid gate plan kind')
    for key in ('title','fixture'):
        if not isinstance(plan[key],str) or not plan[key].strip(): raise ValueError(f'gate plan {key} must be non-empty')
    for key in ('acceptance_criteria','manual_steps'):
        if not isinstance(plan[key],list) or not plan[key]: raise ValueError(f'gate plan {key} must be a non-empty array')
    if not isinstance(plan['checks'],list): raise ValueError('checks must be an array')
    if not plan['checks'] and (plan['kind']!='docs' or not isinstance(plan.get('no_checks_rationale'),str) or not plan['no_checks_rationale'].strip()): raise ValueError('empty checks require docs kind and explicit no_checks_rationale')
    fixture=safe(repo,plan['fixture'])
    if not fixture.is_file(): raise ValueError('gate fixture must identify an existing file')
    if any(not isinstance(x,str) or not x.strip() for x in plan['acceptance_criteria']+plan['manual_steps']): raise ValueError('criteria/manual steps must be non-empty text')
    for check in plan['checks']:
        if not isinstance(check,dict) or not isinstance(check.get('argv'),list) or not check['argv'] or any(not isinstance(x,str) or not x.strip() for x in check['argv']) or not isinstance(check.get('rationale'),str) or not check['rationale'].strip() or not check.get('covers'):
            raise ValueError('checks require argv, rationale and covered criteria')
        if not isinstance(check['covers'],list) or any(x not in plan['acceptance_criteria'] for x in check['covers']): raise ValueError('check covers must reference acceptance criteria')
        kind=check.get('kind','command')
        if kind not in ('build','junit','readiness','gametest','launch-smoke','command'): raise ValueError(f'unsupported evidence kind: {kind}')
        cmd=' '.join(check['argv']).casefold()
        if 'rungametestserver' in cmd and kind!='gametest': raise ValueError('runGameTestServer checks must declare kind gametest')
        if 'featurereadiness' in cmd and kind!='readiness': raise ValueError('featureReadiness checks must declare kind readiness')
        if (' test' in cmd or cmd.endswith('test')) and ('gradlew' in cmd or 'gradle' in cmd) and kind!='junit': raise ValueError('Gradle test checks must declare kind junit')
        if kind=='readiness' and (not isinstance(check.get('selectors'),list) or not check['selectors']): raise ValueError('readiness checks require exact selectors')
        if kind=='build' and (not isinstance(check.get('artifacts'),list) or not check['artifacts']): raise ValueError('build checks require explicit artifacts')
        if kind=='launch-smoke' and (not isinstance(check.get('ready_patterns'),list) or not check['ready_patterns']): raise ValueError('launch-smoke checks require ready_patterns')
        if kind in ('gametest','launch-smoke') and not isinstance(check.get('log'),str): raise ValueError(f'{kind} checks require a repo-relative log path')
        if kind=='launch-smoke':
            if not isinstance(check.get('ready_patterns'),list) or not isinstance(check.get('error_patterns',[]),list): raise ValueError('launch-smoke patterns must be arrays')
            patterns=check['ready_patterns']+check.get('error_patterns',[])
            if any(not isinstance(x,str) or not x or len(x)>300 for x in patterns): raise ValueError('launch-smoke regex patterns must be bounded text')
            try:
                for pattern in patterns: re.compile(pattern)
            except re.error as exc: raise ValueError(f'invalid launch-smoke regex: {exc}') from exc
            if not isinstance(check.get('timeout',120),int) or check.get('timeout',120)<1 or check.get('timeout',120)>120: raise ValueError('launch-smoke timeout must be 1..120 seconds')
    if plan['kind']!='docs' and not plan['checks']: raise ValueError('non-documentation plans require validation checks')
    return plan

def verify_gate(c):
    verify_candidate(c)
    plan=c.get('gate_plan')
    if not plan: raise ValueError('candidate has no structured gate plan; submit a new revision')
    repo=Path(c['worktree']['worktree'])
    validate_gate_plan(plan,repo)
    for key,record in plan.get('references',{}).items():
        p=safe(repo,key)
        if not p.is_file() or sha(p)!=record: raise ValueError(f'gate fixture/plan reference stale: {key}')
    return repo,plan

def cmd_gate_preflight(a,root):
    c=readjson(root/'candidates'/f'{validid(a.id)}.json'); repo,plan=verify_gate(c)
    print(json.dumps({'candidate':a.id,'worktree':str(repo),'kind':plan['kind'],'fixture':plan['fixture'],'checks':len(plan['checks']),'ready_to_run':bool(plan['checks'])},indent=2))

def gate_lock(root):
    if os.name=='nt':
        import ctypes
        name='Local\\MoonStation14-AgentWorkflow-'+hashlib.sha256(str(root.resolve()).casefold().encode()).hexdigest()
        kernel=ctypes.WinDLL('kernel32',use_last_error=True); kernel.CreateMutexW.restype=ctypes.c_void_p
        kernel.CreateMutexW.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_wchar_p]
        handle=kernel.CreateMutexW(None,False,name)
        kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_uint]; kernel.WaitForSingleObject.restype=ctypes.c_uint
        kernel.ReleaseMutex.argtypes=[ctypes.c_void_p]; kernel.CloseHandle.argtypes=[ctypes.c_void_p]
        if not handle: raise OSError('CreateMutexW failed')
        if kernel.WaitForSingleObject(handle,0xFFFFFFFF) not in (0,0x80):
            kernel.CloseHandle(handle); raise OSError('shared gate mutex wait failed')
        class Handle:
            def close(self): kernel.ReleaseMutex(handle); kernel.CloseHandle(handle)
        return Handle()
    path=root/'gate-validation.lock'; path.parent.mkdir(parents=True,exist_ok=True); f=path.open('a+b')
    import fcntl
    fcntl.flock(f,fcntl.LOCK_EX); return f

def cmd_gate_validate(a,root):
    if a.actor!='integration-lead': raise ValueError('only integration-lead runs expensive gate checks')
    p=root/'candidates'/f'{validid(a.id)}.json'; c=readjson(p); repo,plan=verify_gate(c)
    lock=gate_lock(root)
    try:
        require_clear_gate(root)
        c=readjson(p); repo,plan=verify_gate(c); records=[]
        if c.get('gate_outcome',{}).get('result')=='ready_for_owner' or c.get('owner_decision'):
            raise ValueError('published/owner-disposition candidate validation is immutable; submit a new revision')
        folder=root/'gate-reports'; folder.mkdir(parents=True,exist_ok=True)
        out=folder/f'{a.id}.json'; logdir=folder/a.id; logdir.mkdir(exist_ok=True)
        for i,check in enumerate(plan['checks']):
            verify_gate(readjson(p)); check_kind=check.get('kind','command')
            before=validation_evidence.snapshot_evidence(check,repo,hash_file=sha,safe_path=safe)
            started=time.time(); log=safe(repo,check['log']) if check_kind in ('gametest','launch-smoke') else logdir/f'{i+1}.log'
            log.parent.mkdir(parents=True,exist_ok=True)
            code=127; cleanup='not_needed'; blocker=None; smoke=None; smoke_started=time.monotonic()
            with log.open('w',encoding='utf-8',errors='replace') as stream:
                stream.write(f'GATE COMMAND STARTED: {started:.9f}\n'); stream.flush()
                try:
                    proc=subprocess.Popen(check['argv'],cwd=repo,stdout=stream,stderr=subprocess.STDOUT,shell=False,creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name=='nt' else 0,start_new_session=os.name!='nt')
                    if check_kind=='launch-smoke':
                        limit=min(a.timeout,check.get('timeout',120),120)
                        try: smoke=monitor_launch_smoke(proc,log,check,limit,stream); code=smoke['code']; cleanup='terminated' if smoke['controlled_stop'] else 'not_needed'
                        except GateTreeBlocked as exc:
                            code=124; cleanup='unverified'; blocker=exc
                            stream.write(f'\nLAUNCH SMOKE OWNED PROCESS TREE TERMINATION UNVERIFIED; PID {exc.pid}; {exc.detail}\n')
                        if smoke:
                            for pattern in smoke.get('matched_ready',[]): stream.write(f'\nGATE READY PATTERN MATCHED: {pattern}\n')
                            if smoke.get('error_pattern'): stream.write(f'\nGATE ERROR PATTERN MATCHED: {smoke["error_pattern"]}\n')
                            if smoke['controlled_stop']: stream.write('\nGATE CONTROLLED OWNED PROCESS TREE STOP: confirmed\n')
                    else:
                        try: code=proc.wait(timeout=a.timeout)
                        except subprocess.TimeoutExpired:
                            code=124
                            try:
                                stop_owned_tree(proc); cleanup='terminated'
                                stream.write('\nTIMEOUT: owned process tree termination command and root exit confirmed\n')
                            except GateTreeBlocked as exc:
                                cleanup='unverified'; blocker=exc
                                stream.write(f'\nTIMEOUT: OWNED PROCESS TREE TERMINATION UNVERIFIED; PID {exc.pid}; {exc.detail}\n')
                except OSError as e: stream.write(f'COMMAND START FAILED: {e}\n')
                finished=time.time(); stream.write(f'GATE COMMAND EXIT: {code}; finished={finished:.9f}\n')
            evidence_check=dict(check,exit_code=code)
            evidence=validation_evidence.collect_evidence(evidence_check,repo,started,finished,log,before,hash_file=sha,safe_path=safe)
            records.append({'argv':check['argv'],'kind':check_kind,'rationale':check['rationale'],'covers':check['covers'],'exit_code':code,'tree_cleanup':cleanup,'controlled_stop':bool(smoke and smoke['controlled_stop']),'startup_seconds':time.monotonic()-smoke_started if check_kind=='launch-smoke' else None,'started':started,'finished':finished,'log':str(log),'log_sha256':sha(log),'evidence':evidence})
            if blocker:
                writejson(blocker_path(root),{'candidate_id':a.id,'pid':blocker.pid,'created':time.time(),'reason':blocker.detail,'log':str(log),'log_sha256':sha(log)})
            if code: break
        verify_gate(readjson(p))
        report={'candidate_id':a.id,'snapshot_key':c['snapshot_key'],'plan_digest':snapshot_key(plan),'actor':a.actor,'checks':records,'passed':len(records)==len(plan['checks']) and all(x['exit_code']==0 and x['evidence']['status']=='PASS' for x in records),'created':time.time(),'no_checks_rationale':plan.get('no_checks_rationale')}
        writejson(out,report); c=readjson(p); verify_gate(c); c['gate_validation']={'path':str(out),'sha256':sha(out)}; writejson(p,c); print(out)
        if not report['passed']: raise ValueError(f'gate checks failed; actionable logs: {logdir}')
    finally:
        lock.close()

def cmd_gate_finish(a,root):
    p=root/'candidates'/f'{validid(a.id)}.json'; c=readjson(p); repo,plan=verify_gate(c)
    if not c.get('review') or c['review']['result']!=a.result: raise ValueError('fresh independent candidate-review with matching decision required')
    review=c['review']; rp=Path(review['report_path'])
    if Path(a.report).resolve()!=rp or not rp.is_file() or sha(rp)!=review['report_sha256']: raise ValueError('finish report must be the exact current independent review report')
    rr=read_report(rp)
    if a.result=='needs_changes':
        findings=rr.get('findings')
        if not findings: raise ValueError('actionable findings report required')
        c['gate_outcome']={'result':'needs_changes','findings':findings,'report_sha256':sha(Path(a.report))}; writejson(p,c)
        print('needs_changes: return findings to author; submit a new candidate revision after fixes'); return
    if not plan['checks'] and (plan['kind']!='docs' or not plan.get('no_checks_rationale')): raise ValueError('only explicitly justified docs plans may publish without checks')
    gate=root/'gate-reports'/f'{a.id}.json'
    if not gate.is_file(): raise ValueError('gate validation missing')
    g=readjson(gate)
    if not g.get('passed') or g.get('actor')!='integration-lead' or g.get('snapshot_key')!=c['snapshot_key'] or g.get('plan_digest')!=snapshot_key(plan) or len(g.get('checks',[]))!=len(plan['checks']): raise ValueError('passing validation for exact candidate plan required')
    if not c.get('gate_validation') or c['gate_validation']['path']!=str(gate) or c['gate_validation']['sha256']!=sha(gate): raise ValueError('validation receipt stale; rerun integration-lead gate')
    for check,item in zip(plan['checks'],g['checks']):
        if item.get('argv')!=check['argv'] or item.get('rationale')!=check['rationale'] or item.get('covers')!=check['covers'] or item.get('kind','command')!=check.get('kind','command'):
            raise ValueError('validation check record does not match submitted plan')
        lp=Path(item['log'])
        if not lp.is_file() or sha(lp)!=item['log_sha256']: raise ValueError('validation log stale')
        if item.get('exit_code')!=0 or item.get('evidence',{}).get('status')!='PASS': raise ValueError('check lacks fresh passing evidence')
        for artifact in item['evidence'].get('artifacts',[]):
            ap=Path(artifact['path'])
            if not ap.is_file() or sha(ap)!=artifact['sha256']: raise ValueError(f'validation artifact stale: {ap}')
    verify_gate(c)
    if c.get('owner_decision'): raise ValueError('candidate already has an explicit owner disposition; no new manual request')
    md=root/'manual-tests.md'
    if c.get('gate_outcome',{}).get('result')=='ready_for_owner':
        print(f'manual testing already published: {md}'); return
    item={'candidate_id':a.id,'title':plan['title'],'kind':plan['kind'],'status':'open','worktree':str(repo),'snapshot_key':c['snapshot_key'],'build_identity':c['build_identity'],'fixture':plan['fixture'],'manual_steps':plan['manual_steps'],'validation':str(gate),'boundaries':c.get('unverified','')}
    lock=gate_lock(root)
    try:
        c=readjson(p); verify_gate(c); verify_publication(c,root)
        if c.get('owner_decision'): raise ValueError('candidate has owner disposition')
        if c.get('gate_outcome',{}).get('result')=='ready_for_owner':
            print(f'manual testing already published: {md}'); return
        md.parent.mkdir(parents=True,exist_ok=True)
        c['gate_outcome']={'result':'ready_for_owner','manual_entry':a.id,'notification':'pending'}; writejson(p,c)
        render_manual_index(root)
    finally:
        lock.close()
    print(f'manual testing published: {md} (owner acceptance is not implied)')
    try:
        print(send_manual_notification(p,root))
    except (ValueError,OSError,subprocess.SubprocessError) as e:
        print(f'notification failed: {e}; retry with manual-notify {a.id}',file=sys.stderr)

def native_notify(manual_path,title):
    if os.name!='nt': raise ValueError('native Windows notification unavailable on this platform')
    title=title.replace("'","''")[:63]
    text=f'Manual testing requested. Open {manual_path}'.replace("'","''")[:255]
    script=("Add-Type -AssemblyName System.Windows.Forms; $n=New-Object System.Windows.Forms.NotifyIcon; "+
        "$n.Icon=[System.Drawing.SystemIcons]::Information; $n.Visible=$true; "+
        f"$n.BalloonTipTitle='{title}'; $n.BalloonTipText='{text}'; $n.ShowBalloonTip(5000); "+
        "Start-Sleep -Seconds 6; $n.Visible=$false; $n.Dispose()")
    subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',script],check=True,timeout=15,capture_output=True,text=True)
    return 'delivered_to_os; user visibility is not guaranteed'

def cmd_manual_notify(a,root):
    p=root/'candidates'/f'{validid(a.id)}.json'; print(send_manual_notification(p,root))

def send_manual_notification(p,root):
    lock=gate_lock(root)
    try:
        c=readjson(p); repo,plan=verify_gate(c)
        if c.get('owner_decision') or c.get('gate_outcome',{}).get('result')!='ready_for_owner': raise ValueError('manual request is no longer fresh/open')
        state=c['gate_outcome'].get('notification','pending')
        if state.startswith('delivered_to_os'): raise ValueError('notification already sent')
        if state=='sending' and time.time()-c['gate_outcome'].get('notification_started',time.time())<60: raise ValueError('notification send already in progress')
        verify_publication(c,root)
        c['gate_outcome']['notification']='sending'; c['gate_outcome']['notification_started']=time.time(); writejson(p,c)
    finally: lock.close()
    try:
        result=native_notify(root/'manual-tests.md',plan['title'])
    except (ValueError,OSError,subprocess.SubprocessError) as e:
        lock=gate_lock(root)
        try:
            c=readjson(p); c['gate_outcome']['notification']=f'failed: {e}'; writejson(p,c)
        finally: lock.close()
        raise
    lock=gate_lock(root)
    try:
        c=readjson(p); c['gate_outcome']['notification']=result; writejson(p,c)
    finally: lock.close()
    return result

def verify_publication(c,root):
    p=Path(c['review']['report_path'])
    if not p.is_file() or sha(p)!=c['review']['report_sha256']: raise ValueError('review stale')
    gate=Path(c.get('gate_validation',{}).get('path',''))
    if not gate.is_file() or sha(gate)!=c['gate_validation'].get('sha256'): raise ValueError('gate validation stale')
    g=readjson(gate)
    plan=c.get('gate_plan',{})
    if not g.get('passed') or g.get('actor')!='integration-lead' or g.get('snapshot_key')!=c['snapshot_key'] or g.get('plan_digest')!=snapshot_key(plan) or len(g.get('checks',[]))!=len(plan.get('checks',[])): raise ValueError('gate validation no longer passes')
    for check,x in zip(plan.get('checks',[]),g['checks']):
        if x.get('argv')!=check['argv'] or x.get('rationale')!=check['rationale'] or x.get('covers')!=check['covers'] or x.get('kind','command')!=check.get('kind','command'): raise ValueError('gate record differs from submitted plan')
        lp=Path(x['log'])
        if not lp.is_file() or sha(lp)!=x['log_sha256']: raise ValueError('gate log stale')
        ev=x.get('evidence',{})
        if x.get('exit_code')!=0 or ev.get('status')!='PASS': raise ValueError('gate evidence is not passing')
        for artifact in ev.get('artifacts',[]):
            ap=Path(artifact['path'])
            if not ap.is_file() or sha(ap)!=artifact['sha256']: raise ValueError(f'validation artifact stale: {ap}')

def render_manual_index(root):
    entries=[]
    for p in sorted((root/'candidates').glob('*.json')) if (root/'candidates').exists() else []:
        c=readjson(p)
        if c.get('gate_outcome',{}).get('result')!='ready_for_owner': continue
        plan=c.get('gate_plan',{}); state='open'
        try:
            verify_gate(c); verify_publication(c,root)
            if c.get('owner_decision'): state='owner_'+c['owner_decision']['decision']
        except (ValueError,OSError,KeyError,subprocess.CalledProcessError): state='STALE/BLOCKED'
        gate_path=Path(c.get('gate_validation',{}).get('path',''))
        checks=[]; counts_unknown=False
        if gate_path.is_file():
            try:
                g=readjson(gate_path)
                for x in g.get('checks',[]):
                    ev=x.get('evidence',{}); count=ev.get('counts',{}); test_count=count.get('tests')
                    if test_count is None: counts_unknown=True; summary='counts unknown'
                    else: summary=f"{test_count} tests ({count.get('passed')} passed, {count.get('failed')} failed, {count.get('errors')} errors, {count.get('skipped')} skipped)"
                    selection=ev.get('selection','unknown')
                    if count.get('selected') is not None: selection+=f"; {count['selected']}/{count.get('total')} selected/total"
                    checks.append(f"- {x.get('kind','command')}: {ev.get('status')} — {summary}; {selection}")
            except (ValueError,KeyError): pass
        notification=c.get('gate_outcome',{}).get('notification','')
        notify_line=f"\n**Notification:** {notification}\n" if str(notification).startswith('failed:') else ''
        validation_line=f"\nValidation: [detailed evidence report](<{gate_path.resolve().as_uri()}>)" if gate_path.is_file() else '\nValidation details unavailable.'
        if not checks and plan.get('no_checks_rationale'): checks=[f"- NOT APPLICABLE: {plan['no_checks_rationale']} (no automated tests run)"]
        entries.append((0 if state=='open' else 1 if state=='STALE/BLOCKED' else 2,
            f"## {plan.get('title',c['id'])} — {c['id']} ({state})\n\nBuild: {c.get('build_identity')}\n\nWorktree: `{c.get('worktree',{}).get('worktree')}`\n\nPreparation: {plan.get('fixture')}\n\nManual steps:\n"+'\n'.join(f'{i}. {s}' for i,s in enumerate(plan.get('manual_steps',[]),1))+f"\n\nValidation summary:\n"+'\n'.join(checks)+validation_line+f"\n\nBoundaries: {c.get('unverified','')}\n\nSnapshot: `{c.get('snapshot_key')}`\n"+notify_line))
    path=root/'manual-tests.md'; path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text('# Manual testing\n\n'+''.join(row for _,row in sorted(entries,key=lambda x:(x[0],x[1]))),encoding='utf-8')

def cmd_manual_tests(a,root):
    render_manual_index(root); print(root/'manual-tests.md')

class WorkflowExit(Exception):
    def __init__(self,code): self.code=code

class GateTreeBlocked(RuntimeError):
    def __init__(self,pid,detail): self.pid=pid; self.detail=detail

def stop_owned_tree(proc,timeout=5):
    """Bounded stop of this runner's process tree; never searches/kills by image name."""
    try:
        if os.name=='nt':
            result=subprocess.run(['taskkill','/PID',str(proc.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False,timeout=timeout)
            if result.returncode!=0: raise GateTreeBlocked(proc.pid,f'taskkill returned {result.returncode}')
        else: os.killpg(proc.pid,9)
        proc.wait(timeout=timeout)
    except GateTreeBlocked: raise
    except (OSError,subprocess.SubprocessError) as exc: raise GateTreeBlocked(proc.pid,str(exc)) from exc

def monitor_launch_smoke(proc,log,check,timeout,stream):
    ready=[re.compile(x) for x in check['ready_patterns']]
    errors=[re.compile(x) for x in check.get('error_patterns',[])]
    deadline=time.monotonic()+timeout; cursor=0; tail=''; matched=[]
    while time.monotonic()<deadline:
        with Path(log).open('r',encoding='utf-8',errors='replace') as source:
            source.seek(cursor); chunk=source.read(); cursor=source.tell()
        if chunk:
            tail=(tail+chunk)[-16384:]
            for pattern in errors:
                if pattern.search(tail):
                    if proc.poll() is None: stop_owned_tree(proc)
                    return {'code':1,'controlled_stop':False,'matched_ready':matched,'error_pattern':pattern.pattern}
            matched.extend(p.pattern for p in ready if p.search(tail) and p.pattern not in matched)
            if len(matched)==len(ready):
                if proc.poll() is None:
                    stop_owned_tree(proc)
                    return {'code':0,'controlled_stop':True,'matched_ready':matched}
                return {'code':1,'controlled_stop':False,'matched_ready':matched,'error_pattern':'process exited before controlled stop'}
        status=proc.poll()
        if status is not None:
            return {'code':status if status else 1,'controlled_stop':False,'matched_ready':matched,'error_pattern':'process exited before readiness marker'}
        time.sleep(.05)
    stop_owned_tree(proc)
    return {'code':124,'controlled_stop':False,'matched_ready':matched,'error_pattern':'readiness marker timeout'}

def blocker_path(root): return root/'gate-blocker.json'

def require_clear_gate(root):
    p=blocker_path(root)
    if p.is_file():
        b=readjson(p); raise ValueError(f"gate quarantined: owned process tree PID {b['pid']} may still run; integration-lead must confirm cleanup with gate-clear-blocker")

def cmd_gate_clear_blocker(a,root):
    if a.actor!='integration-lead' or not a.confirmed_stopped: raise ValueError('explicit integration-lead confirmation required')
    p=blocker_path(root)
    if not p.is_file(): raise ValueError('no gate blocker')
    record=readjson(p)
    if record.get('pid')!=a.pid: raise ValueError('PID does not match quarantined process')
    p.unlink(); print(f"cleared gate blocker for confirmed-stopped PID {a.pid}; artifacts: {record['log']}")

def cmd_gate_request(a,root):
    try: import gate_dispatch
    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
    result=gate_dispatch.request_gate(a,root,workflow=sys.modules.get(__name__) or types.SimpleNamespace(**globals()))
    print(json.dumps(result,indent=2))
    if result.get('status')=='blocked': raise WorkflowExit(2)
    if result.get('status')=='needs_changes': raise WorkflowExit(3)

def cmd_gate_scope_review(a,root):
    try: import gate_dispatch
    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
    result=gate_dispatch.scoped_review(a,root,workflow=sys.modules.get(__name__) or types.SimpleNamespace(**globals()))
    print(json.dumps(result,indent=2))

def verify_candidate(c):
    repo=Path(c['worktree']['worktree']); base,committed,dirty=git_changes(repo,c['base_ref'])
    if gitid(repo)!=c['worktree'] or base!=c['base_sha'] or committed!=c['committed_changes'] or dirty!=c['dirty_changes']: raise ValueError('candidate stale; submit a new candidate revision')
    for ev in c['evidence']:
        ep=Path(ev['path'])
        if not ep.is_file() or sha(ep)!=ev['sha256']: raise ValueError(f'candidate evidence stale: {ep}')
    review=c.get('review')
    if review:
        rp=Path(review['report_path'])
        if not rp.is_file() or sha(rp)!=review['report_sha256']: raise ValueError('review report stale')
        report=read_report(rp)
        if report.get('candidate_id')!=c['id'] or report.get('snapshot_key')!=c['snapshot_key'] or report.get('decision')!=review['result']: raise ValueError('review report binding stale')
        current=optional_reviewed_images(report.get('viewed_images',[]),rp.parent)
        if current!=review['viewed_images']: raise ValueError('review image stale')
        originals=verify_original_images(report.get('original_images',[]),c,rp.parent)
        if originals!=review.get('original_images',[]): raise ValueError('original review image stale')
        if review['result']=='ready_for_owner' and c['visual_review_required'] and not originals: raise ValueError('visual review must bind source original_images')
        if review['result']=='ready_for_owner' and c['visual_review_required'] and not current: raise ValueError('visual review image missing')
    return c

def verify_owner_decision(c):
    decision=c.get('owner_decision')
    if not decision: return None
    receipt=Path(decision['receipt_path'])
    if not receipt.is_file() or sha(receipt)!=decision['receipt_sha256']: raise ValueError('owner decision receipt stale')
    data=read_report(receipt)
    if data.get('candidate_id')!=c['id'] or data.get('snapshot_key')!=c['snapshot_key'] or data.get('decision')!=decision['decision']: raise ValueError('owner decision receipt binding stale')
    if data.get('owner_reference')!=decision['owner_reference'] or data.get('note')!=decision['note']: raise ValueError('owner decision reference stale')
    review_hash=c.get('review',{}).get('report_sha256')
    if review_hash!=decision.get('review_report_sha256'): raise ValueError('owner decision review reference stale')
    return decision

def cmd_candidate_review(a,root):
    rid=validid(a.id); p=root/'candidates'/f'{rid}.json'
    if not p.is_file(): raise ValueError('unknown candidate')
    c=verify_candidate(readjson(p))
    if a.reviewer==c['agent']: raise ValueError('reviewer must differ from candidate author')
    report=read_report(a.report)
    if report.get('candidate_id')!=rid or report.get('snapshot_key')!=c['snapshot_key'] or report.get('reviewer')!=a.reviewer or report.get('decision')!=a.result: raise ValueError('candidate review report identity/snapshot/decision mismatch')
    findings=report.get('findings')
    if not isinstance(findings,list) or not findings or len(findings)>100 or any(not isinstance(x,str) or len(x)>2000 for x in findings): raise ValueError('review report requires 1-100 bounded text findings')
    originals=verify_original_images(report.get('original_images',[]),c,Path(a.report).resolve().parent)
    if a.result=='ready_for_owner' and c['visual_review_required'] and not originals: raise ValueError('visual review must bind original_images in candidate/source evidence')
    if originals:
        captured=capture_review_images(report.get('viewed_images',[]),originals,a.report,rid)
        report['viewed_images']=captured
        writejson(a.report,report)
    images=optional_reviewed_images(report.get('viewed_images',[]),Path(a.report).resolve().parent)
    if a.result=='ready_for_owner' and c['visual_review_required'] and not images: raise ValueError('visual candidate ready report needs viewed PNG evidence')
    if a.result=='ready_for_owner' and c['visual_review_required'] and not originals: raise ValueError('visual review must bind original_images in candidate/source evidence')
    c['review']={'result':a.result,'report_path':str(Path(a.report).resolve()),'report':report,'report_sha256':sha(Path(a.report)),'viewed_images':images,'original_images':originals,'inspection_is_reviewer_assertion':True}; writejson(p,c); print(a.result)

def cmd_candidate_decision(a,root):
    rid=validid(a.id); p=root/'candidates'/f'{rid}.json'
    if not p.is_file(): raise ValueError('unknown candidate')
    c=verify_candidate(readjson(p)); receipt=Path(a.owner_receipt).resolve(); data=read_report(receipt)
    if data.get('candidate_id')!=rid or data.get('snapshot_key')!=c['snapshot_key'] or data.get('decision') not in ('accepted','deferred','needs_changes'):
        raise ValueError('owner receipt candidate/snapshot/decision mismatch')
    owner_reference=data.get('owner_reference'); note=data.get('note')
    if not isinstance(owner_reference,str) or not owner_reference.strip() or not isinstance(note,str) or not note.strip(): raise ValueError('receipt requires explicit owner_reference and note')
    if data['decision']=='accepted' and c.get('review',{}).get('result')!='ready_for_owner': raise ValueError('accepted decision requires a fresh independent ready_for_owner review')
    c['owner_decision']={'decision':data['decision'],'owner_reference':owner_reference,'note':note,'receipt_path':str(receipt),'receipt_sha256':sha(receipt),'review_report_sha256':c.get('review',{}).get('report_sha256')}
    writejson(p,c); render_manual_index(root); print(f"owner_{data['decision']}")

def cmd_status(a,root):
    if not root.exists(): print('No agent exchange records.'); return
    for p in sorted((root/'requests').glob('*/request.json')) if (root/'requests').exists() else []:
        r=readjson(p); state=r['status']
        try: verify_request(p.parent,r)
        except (ValueError,OSError): state='STALE/BLOCKED'
        if r.get('delivery'):
            try:
                verify_delivery(p.parent,r)
                if r.get('review',{}).get('result')=='ready_for_owner': current_review(p.parent,r)
            except (ValueError,OSError): state='STALE/BLOCKED'
        print(f"asset {r['id']}: {state} — {r.get('title','')}")
    for p in sorted((root/'candidates').glob('*.json')) if (root/'candidates').exists() else []:
        c=readjson(p); state=c.get('review',{}).get('result',c['status'])
        try:
            verify_candidate(c); decision=verify_owner_decision(c)
            if decision: state='owner_'+decision['decision']
        except (ValueError,OSError,subprocess.CalledProcessError,KeyError):
            historic=c.get('owner_decision',{}).get('decision')
            state=f'STALE/BLOCKED (owner_{historic} recorded)' if historic else 'STALE/BLOCKED'
        print(f"code {c['id']}: {state}")

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--repo',default='.'); p.add_argument('--asset-workspace')
    s=p.add_subparsers(dest='command',required=True)
    q=s.add_parser('request'); q.add_argument('--from-json',required=True); q.add_argument('--context-root',default='.'); q.set_defaults(fn=cmd_request)
    q=s.add_parser('claim'); q.add_argument('id'); q.add_argument('--agent',required=True); q.set_defaults(fn=cmd_claim)
    q=s.add_parser('plan-manifest'); q.add_argument('--manifest',required=True); q.add_argument('--source-root',required=True); q.add_argument('--target',help='optional destination root for existing-file/conflict inspection'); q.set_defaults(fn=cmd_plan)
    q=s.add_parser('deliver'); q.add_argument('id'); q.add_argument('--manifest',required=True); q.add_argument('--source-root',required=True); q.add_argument('--revision',required=True); q.add_argument('--provenance',nargs='+',required=True); q.add_argument('--editable',nargs='+',required=True); q.add_argument('--review-images',nargs='+',required=True); q.set_defaults(fn=cmd_deliver)
    q=s.add_parser('review'); q.add_argument('id'); q.add_argument('--reviewer',required=True); q.add_argument('--result',choices=['needs_changes','ready_for_owner'],required=True); q.add_argument('--report',required=True); q.set_defaults(fn=cmd_review)
    q=s.add_parser('import-assets'); q.add_argument('id'); q.add_argument('--target',required=True); q.add_argument('--owner-receipt'); q.add_argument('--plan-out'); q.add_argument('--plan'); q.add_argument('--apply',action='store_true'); q.add_argument('--allow-overwrite',action='store_true'); q.set_defaults(fn=cmd_import)
    q=s.add_parser('submit'); q.add_argument('id'); q.add_argument('--agent',required=True); q.add_argument('--base-ref',default='main'); q.add_argument('--evidence',nargs='*',default=[]); q.add_argument('--build-identity',required=True); q.add_argument('--steps',required=True); q.add_argument('--unverified',default=''); q.add_argument('--gate-plan'); q.add_argument('--visual-review-required',action='store_true'); q.set_defaults(fn=cmd_submit)
    q=s.add_parser('candidate-review'); q.add_argument('id'); q.add_argument('--reviewer',required=True); q.add_argument('--report',required=True); q.add_argument('--result',choices=['needs_changes','ready_for_owner'],required=True); q.set_defaults(fn=cmd_candidate_review)
    q=s.add_parser('candidate-decision'); q.add_argument('id'); q.add_argument('--owner-receipt',required=True); q.set_defaults(fn=cmd_candidate_decision)
    q=s.add_parser('gate-preflight'); q.add_argument('id'); q.set_defaults(fn=cmd_gate_preflight)
    q=s.add_parser('gate-validate'); q.add_argument('id'); q.add_argument('--actor',required=True); q.add_argument('--timeout',type=int,default=3600); q.set_defaults(fn=cmd_gate_validate)
    q=s.add_parser('gate-clear-blocker'); q.add_argument('--actor',required=True); q.add_argument('--pid',type=int,required=True); q.add_argument('--confirmed-stopped',action='store_true'); q.set_defaults(fn=cmd_gate_clear_blocker)
    q=s.add_parser('gate-finish'); q.add_argument('id'); q.add_argument('--result',choices=['needs_changes','ready_for_owner'],required=True); q.add_argument('--report',required=True); q.set_defaults(fn=cmd_gate_finish)
    q=s.add_parser('manual-notify'); q.add_argument('id'); q.set_defaults(fn=cmd_manual_notify)
    q=s.add_parser('manual-tests'); q.set_defaults(fn=cmd_manual_tests)
    q=s.add_parser('gate-request'); q.add_argument('id'); q.add_argument('--central-worktree'); q.add_argument('--origin-session'); q.add_argument('--server'); q.add_argument('--wait-timeout',type=int,default=7200); q.set_defaults(fn=cmd_gate_request)
    q=s.add_parser('gate-scope-review'); q.add_argument('id'); q.add_argument('--reviewer',choices=['review','fidelity-reviewer'],required=True); q.add_argument('--actor',required=True); q.add_argument('--server'); q.add_argument('--wait-timeout',type=int,default=7200); q.set_defaults(fn=cmd_gate_scope_review)
    q=s.add_parser('status'); q.set_defaults(fn=cmd_status)
    a=p.parse_args()
    try:
        root=queue(a,create=a.command not in ('status','plan-manifest')); a.fn(a,root)
    except WorkflowExit as e: return e.code
    except (ValueError,OSError,subprocess.CalledProcessError) as e: print(f'error: {e}',file=sys.stderr); return 2
    return 0
if __name__=='__main__': raise SystemExit(main())
