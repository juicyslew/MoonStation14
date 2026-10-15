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
    if not isinstance(value,str) or not ID.fullmatch(value) or value.endswith(('.', ' ')) or value.split('.')[0].upper() in {'CON','PRN','AUX','NUL',*(f'COM{i}' for i in range(1,10)),*(f'LPT{i}' for i in range(1,10))}: raise ValueError('invalid id (use a portable 1-80 letter/digit/_/- ID; start alphanumeric)')
    return value

def safe(root,value):
    if not isinstance(value,str) or not value: raise ValueError('path must be a non-empty string')
    p=(Path(root).resolve()/value).resolve()
    if not p.is_relative_to(Path(root).resolve()): raise ValueError(f'path escapes root: {value}')
    return p

def author_report_dir(candidate):
    """Return the candidate-bound, ignored report folder in its author worktree."""
    repo=Path(candidate['worktree']['worktree']).resolve()
    sprint=validid(candidate.get('sprint_id'))
    folder=safe(repo, f'build/agent-workflow/{sprint}/reports')
    ignored=subprocess.run(['git','-C',str(repo),'check-ignore','-q',str(folder)],
        stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False).returncode==0
    if not ignored:
        raise ValueError(f'author report path is not gitignored: {folder}')
    tracked=subprocess.run(['git','-C',str(repo),'ls-files','--',str(folder.relative_to(repo))],capture_output=True,text=True,check=False)
    if tracked.returncode or tracked.stdout.strip(): raise ValueError(f'author report path contains tracked files: {folder}')
    return folder

def sprint_lock(root,repo,sprint):
    key=hashlib.sha256((str(Path(repo).resolve()).casefold()+'\0'+validid(sprint)).encode()).hexdigest()
    return gate_lock(Path(root)/'sprint-locks'/key)

def try_sprint_lock(root,repo,sprint):
    key=hashlib.sha256((str(Path(repo).resolve()).casefold()+'\0'+validid(sprint)).encode()).hexdigest()
    try: return gate_lock(Path(root)/'sprint-locks'/key,nonblocking=True)
    except (BlockingIOError,TimeoutError) as exc: raise ValueError('sprint cleanup or another mutation is in progress; retry later') from exc

def validate_review_report(report,candidate,reviewer):
    if not isinstance(report,dict): raise ValueError('review report must be a JSON object')
    if (report.get('candidate_id')!=candidate['id'] or report.get('snapshot_key')!=candidate['snapshot_key'] or
        report.get('reviewer')!=reviewer or report.get('decision') not in ('needs_changes','ready_for_owner')):
        raise ValueError('review report identity/decision mismatch')
    findings=report.get('findings')
    if not isinstance(findings,list) or not findings or len(findings)>100 or any(not isinstance(x,str) or not x.strip() or len(x)>2000 for x in findings):
        raise ValueError('review report findings must contain 1-100 bounded strings')
    if not isinstance(report.get('viewed_images',[]),list): raise ValueError('review report viewed_images must be an array')
    return report

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

def preflight_review_image_captures(records,originals,report_path,ident):
    base=Path(report_path).resolve().parent
    hashes={x['sha256'] for x in originals}
    for index,item in enumerate(records):
        digest=item.get('sha256') if isinstance(item,dict) else None
        if digest not in hashes: raise ValueError('each viewed image must bind a verified original_images SHA-256')
        target=base/'reviewed-images'/f'{ident}-{index+1}-{digest[:16]}.png'
        if target.exists() and sha(target)!=digest:
            raise ValueError(f'review image capture conflict: {target}')

def prepare_candidate_review(report,candidate,reviewer,result,report_path):
    report=validate_review_report(report,candidate,reviewer)
    if report.get('decision')!=result: raise ValueError('candidate review report decision mismatch')
    originals=verify_original_images(report.get('original_images',[]),candidate,Path(report_path).resolve().parent)
    if result=='ready_for_owner' and candidate['visual_review_required'] and not originals:
        raise ValueError('visual review must bind original_images in candidate/source evidence')
    images=optional_reviewed_images(report.get('viewed_images',[]),Path(report_path).resolve().parent)
    if result=='ready_for_owner' and candidate['visual_review_required'] and not images:
        raise ValueError('visual candidate ready report needs viewed PNG evidence')
    if result=='ready_for_owner' and candidate['visual_review_required'] and not originals:
        raise ValueError('visual review must bind original_images in candidate/source evidence')
    if originals: preflight_review_image_captures(report.get('viewed_images',[]),originals,report_path,candidate['id'])
    return report,originals,images

def save_candidate_review(path,candidate,result,report_path,report,images,originals):
    if not path.is_file(): raise ValueError('candidate was removed during review')
    current=readjson(path)
    if current.get('snapshot_key')!=candidate['snapshot_key']: raise ValueError('candidate changed during review')
    current['review']={'result':result,'report_path':str(Path(report_path).resolve()),'report':report,
        'report_sha256':sha(Path(report_path)),'viewed_images':images,'original_images':originals,
        'inspection_is_reviewer_assertion':True}
    writejson(path,current)

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
    try:
        from admission import maintenance
    except ModuleNotFoundError:
        from tooling.agent_workflow.admission import maintenance
    held=maintenance(root,a.id)
    if held:
        print(json.dumps(held,indent=2)); return
    rid=validid(a.id); repo=Path(a.repo).resolve()
    sprint=validid(a.sprint)
    if not (a.build_identity.strip() and a.steps.strip()): raise ValueError('candidate requires non-empty build identity and manual validation steps (or explicitly say blocked/not run)')
    lock=try_sprint_lock(root,repo,sprint)
    try:
        _cmd_submit_locked(a,root,rid,repo,sprint)
    finally: lock.close()

def _cmd_submit_locked(a,root,rid,repo,sprint):
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
    if p.exists(): raise ValueError('candidate id collision; use new revision id')
    prior=[]; revision=1
    for old_path in (root/'candidates').glob('*.json') if (root/'candidates').exists() else []:
        old=readjson(old_path)
        if old.get('sprint_id')==sprint and Path(old.get('worktree',{}).get('worktree','')).resolve()==repo:
            prior.append(old.get('id'))
            revision=max(revision,int(old.get('revision_sequence',0))+1)
    candidate={'id':rid,'sprint_id':sprint,'status':'candidate','agent':a.agent,'visual_review_required':a.visual_review_required,'worktree':gitid(repo),'base_ref':a.base_ref,'base_sha':base,'committed_changes':committed,'dirty_changes':dirty,'evidence':ev,'build_identity':a.build_identity,'steps':a.steps,'unverified':a.unverified,'gate_plan':plan,'submitted_at':time.time(),'revision_sequence':revision,'supersedes':prior}
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

def gate_lock(root,nonblocking=False):
    if os.name=='nt':
        import ctypes
        name='Local\\MoonStation14-AgentWorkflow-'+hashlib.sha256(str(root.resolve()).casefold().encode()).hexdigest()
        kernel=ctypes.WinDLL('kernel32',use_last_error=True); kernel.CreateMutexW.restype=ctypes.c_void_p
        kernel.CreateMutexW.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_wchar_p]
        handle=kernel.CreateMutexW(None,False,name)
        kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_uint]; kernel.WaitForSingleObject.restype=ctypes.c_uint
        kernel.ReleaseMutex.argtypes=[ctypes.c_void_p]; kernel.CloseHandle.argtypes=[ctypes.c_void_p]
        if not handle: raise OSError('CreateMutexW failed')
        wait=kernel.WaitForSingleObject(handle,0 if nonblocking else 0xFFFFFFFF)
        if wait not in (0,0x80):
            kernel.CloseHandle(handle)
            if nonblocking and wait==0x102: raise TimeoutError('shared gate mutex is busy')
            raise OSError('shared gate mutex wait failed')
        class Handle:
            def close(self): kernel.ReleaseMutex(handle); kernel.CloseHandle(handle)
        return Handle()
    path=root/'gate-validation.lock'; path.parent.mkdir(parents=True,exist_ok=True); f=path.open('a+b')
    import fcntl
    try: fcntl.flock(f,fcntl.LOCK_EX|(fcntl.LOCK_NB if nonblocking else 0))
    except BlockingIOError:
        f.close(); raise
    return f

def cmd_gate_validate(a,root):
    if a.actor!='integration-lead': raise ValueError('only integration-lead runs expensive gate checks')
    p=root/'candidates'/f'{validid(a.id)}.json'; c=readjson(p); repo,plan=verify_gate(c)
    _gate_progress(root,c,'queued',runner_pid=os.getpid(),started=time.time(),detail='Waiting for serialized validation slot')
    lock=gate_lock(root)
    report_written=False
    try:
        require_clear_gate(root)
        c=readjson(p); repo,plan=verify_gate(c); records=[]
        _gate_progress(root,c,'running',detail='Integration lead validation running')
        if c.get('gate_outcome',{}).get('result')=='ready_for_owner' or c.get('owner_decision'):
            raise ValueError('published/owner-disposition candidate validation is immutable; submit a new revision')
        folder=author_report_dir(c); folder.mkdir(parents=True,exist_ok=True)
        out=folder/f'{a.id}-gate-validation.json'; logdir=folder/f'{a.id}-logs'; logdir.mkdir(exist_ok=True)
        for i,check in enumerate(plan['checks']):
            verify_gate(readjson(p)); check_kind=check.get('kind','command')
            before=validation_evidence.snapshot_evidence(check,repo,hash_file=sha,safe_path=safe)
            started=time.time(); log=safe(repo,check['log']) if check_kind in ('gametest','launch-smoke') else logdir/f'{i+1}.log'
            log.parent.mkdir(parents=True,exist_ok=True)
            code=127; cleanup='not_needed'; blocker=None; smoke=None; smoke_started=time.monotonic()
            with log.open('w',encoding='utf-8',errors='replace') as stream:
                stream.write(f'GATE COMMAND STARTED: {started:.9f}\n'); stream.flush()
                try:
                    proc=subprocess.Popen(check['argv'],cwd=repo,stdout=stream,stderr=subprocess.STDOUT,shell=False,creationflags=(getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0)|getattr(subprocess,'CREATE_NO_WINDOW',0)) if os.name=='nt' else 0,start_new_session=os.name!='nt')
                    _gate_progress(root,c,'running',check_index=i+1,total=len(plan['checks']),argv=check['argv'],log=str(log),runner_pid=os.getpid(),child_pid=proc.pid,started=started,detail=f'Running check {i+1}/{len(plan["checks"])}')
                    if check_kind=='launch-smoke':
                        limit=min(a.timeout,check.get('timeout',120),120)
                        try: smoke=monitor_launch_smoke(proc,log,check,limit,stream,heartbeat=lambda: _gate_progress(root,c,'running',check_index=i+1,total=len(plan['checks']),argv=check['argv'],log=str(log),runner_pid=os.getpid(),child_pid=proc.pid,started=started,detail=f'Check {i+1}/{len(plan["checks"])} still running')); code=smoke['code']; cleanup='terminated' if smoke['controlled_stop'] else 'not_needed'
                        except GateTreeBlocked as exc:
                            code=124; cleanup='unverified'; blocker=exc
                            stream.write(f'\nLAUNCH SMOKE OWNED PROCESS TREE TERMINATION UNVERIFIED; PID {exc.pid}; {exc.detail}\n')
                        if smoke:
                            for pattern in smoke.get('matched_ready',[]): stream.write(f'\nGATE READY PATTERN MATCHED: {pattern}\n')
                            if smoke.get('error_pattern'): stream.write(f'\nGATE ERROR PATTERN MATCHED: {smoke["error_pattern"]}\n')
                            if smoke['controlled_stop']: stream.write('\nGATE CONTROLLED OWNED PROCESS TREE STOP: confirmed\n')
                    else:
                        deadline=time.monotonic()+a.timeout
                        while True:
                            remaining=deadline-time.monotonic()
                            if remaining<=0:
                                code=124
                                try:
                                    stop_owned_tree(proc); cleanup='terminated'
                                    stream.write('\nTIMEOUT: owned process tree termination command and root exit confirmed\n')
                                except GateTreeBlocked as exc:
                                    cleanup='unverified'; blocker=exc
                                    stream.write(f'\nTIMEOUT: OWNED PROCESS TREE TERMINATION UNVERIFIED; PID {exc.pid}; {exc.detail}\n')
                                break
                            try:
                                code=proc.wait(timeout=min(10,remaining)); break
                            except subprocess.TimeoutExpired:
                                _gate_progress(root,c,'running',check_index=i+1,total=len(plan['checks']),argv=check['argv'],log=str(log),runner_pid=os.getpid(),child_pid=proc.pid,started=started,detail=f'Check {i+1}/{len(plan["checks"])} still running')
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
        writejson(out,report); report_written=True; c=readjson(p); verify_gate(c); c['gate_validation']={'path':str(out),'sha256':sha(out)}; writejson(p,c); print(out)
        _gate_progress(root,c,'awaiting_review',detail='Validation complete; awaiting integration-lead verdict')
        if not report['passed']: raise ValueError(f'gate checks failed; actionable logs: {logdir}')
    except Exception as exc:
        if not report_written:
            _gate_progress(root,c,'blocked',detail=f'Validation could not complete: {str(exc)[:500]}')
        raise
    finally:
        lock.close()


def _gate_progress(root,candidate,phase,**fields):
    """Progress is informational; filesystem/board errors never affect verdicts."""
    try:
        try: import gate_progress
        except ModuleNotFoundError: from tooling.agent_workflow import gate_progress
        gate_progress.update(root,candidate,phase,**fields)
    except Exception as exc:
        print(f'progress update unavailable: {exc}',file=sys.stderr)

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
        _send_gate_completion(a,root,'needs_changes')
        print('needs_changes: return findings to author; submit a new candidate revision after fixes'); return
    if not plan['checks'] and (plan['kind']!='docs' or not plan.get('no_checks_rationale')): raise ValueError('only explicitly justified docs plans may publish without checks')
    gate=Path(c.get('gate_validation',{}).get('path',''))
    expected=author_report_dir(c)/f'{a.id}-gate-validation.json'
    if gate.resolve()!=expected.resolve(): raise ValueError('gate validation receipt is not in the candidate-owned report folder')
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
        print(f'manual testing already published: {md}')
        _send_gate_completion(a,root,'ready_for_owner')
        return
    item={'candidate_id':a.id,'title':plan['title'],'kind':plan['kind'],'status':'open','worktree':str(repo),'snapshot_key':c['snapshot_key'],'build_identity':c['build_identity'],'fixture':plan['fixture'],'manual_steps':plan['manual_steps'],'validation':str(gate),'boundaries':c.get('unverified','')}
    lock=_manual_publication_lock(root)
    try:
        c=readjson(p); verify_gate(c); verify_publication(c,root)
        if c.get('owner_decision'): raise ValueError('candidate has owner disposition')
        if c.get('gate_outcome',{}).get('result')!='ready_for_owner':
            md.parent.mkdir(parents=True,exist_ok=True)
            c['gate_outcome']={'result':'ready_for_owner','manual_entry':a.id,'notification':'pending'}; writejson(p,c)
            if c.get('gate_request',{}).get('origin_session_id'):
                try:
                    try: import owner_attention
                    except ModuleNotFoundError: from tooling.agent_workflow import owner_attention
                    owner_attention.register_request(root,c,requested_at=time.time(),origin_session_id=c['gate_request']['origin_session_id'],workflow=sys.modules.get(__name__) or types.SimpleNamespace(**globals()))
                    _ensure_owner_feedback(root, p, c)
                except (ValueError,OSError) as exc: print(f'owner-attention registration unavailable: {exc}',file=sys.stderr)
        render_manual_index(root)
    finally:
        lock.close()
    print(f'manual testing published: {md} (owner acceptance is not implied)')
    # Author wake is independent of the optional, potentially slow OS balloon.
    _send_gate_completion(a,root,'ready_for_owner')
    try:
        print(send_manual_notification(p,root))
    except (ValueError,OSError,subprocess.SubprocessError) as e:
        print(f'notification failed: {e}; retry with manual-notify {a.id}',file=sys.stderr)


def _send_gate_completion(a,root,phase):
    try:
        try: import gate_dispatch
        except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
        result=gate_dispatch.deliver_gate_completion(a,root,workflow=sys.modules.get(__name__) or types.SimpleNamespace(**globals()))
        print('author completion:',result)
    except (ValueError,OSError,subprocess.SubprocessError) as exc:
        result={'status':'failed','reason':str(exc)}
        print(f'author completion unavailable; retry gate-deliver {a.id}: {exc}',file=sys.stderr)
    try:
        state=readjson(root/'candidates'/f'{validid(a.id)}.json')
        receipt=state.get('author_completion',{})
        _gate_progress(root,state,phase,detail=f"Final {phase} verdict; author delivery {receipt.get('status','unavailable')} (API acknowledgement is not consumption)")
    except (ValueError,OSError) as exc: print(f'progress update unavailable: {exc}',file=sys.stderr)
    return result


def _ensure_owner_feedback(root, candidate_path, candidate):
    """Start the bounded feedback observer after owner-attention locks have been released."""
    try:
        try: import owner_feedback
        except ModuleNotFoundError: from tooling.agent_workflow import owner_feedback
        health = owner_feedback.ensure_started(root)
    except (ImportError, OSError, ValueError, RuntimeError) as exc:
        health = {'status':'unavailable','reason':str(exc)[:300]}
    lock = sprint_lock(root, candidate['worktree']['worktree'], candidate['sprint_id'])
    try:
        current = readjson(candidate_path)
        review = current.get('owner_review', {})
        if review.get('candidate_id') == candidate.get('id') and review.get('snapshot_key') == candidate.get('snapshot_key'):
            review['observer'] = health
            current['owner_review'] = review
            writejson(candidate_path, current)
    finally:
        lock.close()
    return health


def _manual_publication_lock(root):
    """A cheap shared namespace, separate from serialized gate validation."""
    return gate_lock(Path(root) / 'manual-publication')


def cmd_gate_deliver(a,root):
    if a.actor!='integration-lead': raise ValueError('only integration-lead retries author completion delivery')
    try: import gate_dispatch
    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
    result=gate_dispatch.deliver_gate_completion(a,root,workflow=sys.modules.get(__name__) or types.SimpleNamespace(**globals()))
    print(json.dumps(result,indent=2))

def native_notify(manual_path,title,message=None):
    if os.name!='nt': raise ValueError('native Windows notification unavailable on this platform')
    title=title.replace("'","''")[:63]
    text=(message or f'Manual testing requested. Open {manual_path}').replace("'","''")[:255]
    script=("Add-Type -AssemblyName System.Windows.Forms; $n=New-Object System.Windows.Forms.NotifyIcon; "+
        "$n.Icon=[System.Drawing.SystemIcons]::Information; $n.Visible=$true; "+
        f"$n.BalloonTipTitle='{title}'; $n.BalloonTipText='{text}'; $n.ShowBalloonTip(5000); "+
        "Start-Sleep -Seconds 6; $n.Visible=$false; $n.Dispose()")
    subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',script],check=True,timeout=15,capture_output=True,text=True,
                   creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    return 'delivered_to_os; user visibility is not guaranteed'

def manual_notification_title(candidate, plan=None):
    """Candidate-bound human title shared by automatic and explicit notification."""
    plan = plan or candidate.get('gate_plan', {})
    title = str(plan.get('title') or '').strip()
    folded = re.sub(r'[^a-z0-9]+', ' ', title.casefold()).strip()
    generic = folded in {'fixture', 'test fixture', 'visual testing fixture', 'visual test fixture', 'visual coding fixture', 'example fixture'}
    if generic or not title:
        title = str(candidate.get('sprint_id') or candidate.get('id') or 'candidate').replace('-', ' ').replace('_', ' ').title()
    worktree = Path(candidate.get('worktree', {}).get('worktree', '')).name
    agent_match = re.search(r'(agent[-_ ]?\d+)', worktree, re.I)
    author = agent_match.group(1).replace('_', '-').replace(' ', '-').lower() if agent_match else (worktree or 'author worktree')
    suffix=f' — ready ({author})'
    available=max(1,63-len(suffix))
    return title[:available]+suffix

def cmd_manual_notify(a,root):
    p=root/'candidates'/f'{validid(a.id)}.json'; print(send_manual_notification(p,root))

def send_manual_notification(p,root):
    lock=_manual_publication_lock(root)
    try:
        c=readjson(p); repo,plan=verify_gate(c)
        outcome=c.get('gate_outcome',{})
        review=c.get('owner_review',{})
        if c.get('owner_decision') or outcome.get('result')!='ready_for_owner': raise ValueError('manual request is no longer fresh/open')
        if (review.get('state')!='awaiting_owner' or review.get('candidate_id')!=c.get('id') or
            review.get('snapshot_key')!=c.get('snapshot_key') or not review.get('origin_session_id')):
            raise ValueError('no current owner-review handoff is registered; refusing a historical/generic notification')
        try:
            try: import owner_attention
            except ModuleNotFoundError: from tooling.agent_workflow import owner_attention
            heads=owner_attention.current_work(root,workflow=sys.modules.get(__name__) or types.SimpleNamespace(**globals()))
            if not any(x.get('head') and x['head'].get('id')==c.get('id') and x['head'].get('snapshot_key')==c.get('snapshot_key') for x in heads):
                raise ValueError('candidate is no longer the current worktree/sprint head')
        except (ImportError, OSError, KeyError, TypeError) as exc:
            raise ValueError(f'cannot verify current owner-review handoff: {exc}') from exc
        state=outcome.get('notification','pending')
        if state.startswith('delivered_to_os'): raise ValueError('notification already sent')
        if state=='sending' and time.time()-c['gate_outcome'].get('notification_started',time.time())<60: raise ValueError('notification send already in progress')
        verify_publication(c,root)
        title=manual_notification_title(c,plan)
        review=c.get('review',{})
        report_path=str(review.get('report_path') or outcome.get('manual_entry') or c['id'])
        report_hash=str(review.get('report_sha256') or 'unavailable')
        prior=outcome.get('notification_receipt') or {}
        identity=(c['id'],c['snapshot_key'],report_hash,outcome.get('manual_entry'))
        old_identity=(prior.get('candidate_id'),prior.get('snapshot_key'),prior.get('report_sha256'),prior.get('manual_entry'))
        if prior and old_identity[:2]==identity[:2] and old_identity!=identity:
            raise ValueError('saved notification body is bound to changed report/manual-entry identity')
        if prior and old_identity==identity and isinstance(prior.get('message'),str) and isinstance(prior.get('title'),str):
            title,message=prior['title'],prior['message']
            receipt=dict(prior,status='sending',retry_started_at=time.time())
        else:
            # NotifyIcon truncates BalloonTipText at 255 characters. Put all
            # proof identity first and keep the feature-specific step visible;
            # the durable receipt/board retain complete steps and report path.
            prefix=f'{c["id"]} s{c["snapshot_key"][:8]} review {report_hash[:8]}: '
            suffix='; full steps: manual-tests.md'
            steps='; '.join(str(x) for x in plan.get('manual_steps',[])) or 'Follow manual test entry'
            available=max(1,255-len(prefix.replace("'","''"))-len(suffix.replace("'","''")))
            step=steps[:available]
            while step and len((prefix+step+suffix).replace("'","''"))>255: step=step[:-1]
            message=prefix+step+suffix
            receipt={'candidate_id':c['id'],'snapshot_key':c['snapshot_key'],'report_sha256':report_hash,
                     'report_path':report_path,'manual_entry':outcome.get('manual_entry'),'title':title,
                     'message':message,'manual_steps':list(plan.get('manual_steps',[])),
                     'created_at':time.time(),'status':'sending'}
        c['gate_outcome']['notification']='sending'; c['gate_outcome']['notification_started']=receipt['created_at']
        c['gate_outcome']['notification_receipt']=receipt; writejson(p,c)
    finally: lock.close()
    try:
        result=native_notify(root/'manual-tests.md',title,message)
    except (ValueError,OSError,subprocess.SubprocessError) as e:
        lock=_manual_publication_lock(root)
        try:
            c=readjson(p); c['gate_outcome']['notification']=f'failed: {e}'
            saved=c['gate_outcome'].get('notification_receipt',receipt); saved.update(status='failed',error=str(e)[:500]); c['gate_outcome']['notification_receipt']=saved; writejson(p,c)
        finally: lock.close()
        raise
    lock=_manual_publication_lock(root)
    try:
        c=readjson(p); c['gate_outcome']['notification']=result
        saved=c['gate_outcome'].get('notification_receipt',receipt); saved.update(status='delivered_to_os',result=result); c['gate_outcome']['notification_receipt']=saved; writejson(p,c)
    finally: lock.close()
    return result

def verify_publication(c,root):
    p=Path(c['review']['report_path'])
    if not p.is_file() or sha(p)!=c['review']['report_sha256']: raise ValueError('review stale')
    gate=Path(c.get('gate_validation',{}).get('path',''))
    expected=author_report_dir(c)/f"{c['id']}-gate-validation.json"
    if gate.resolve()!=expected.resolve(): raise ValueError('gate validation receipt is not in the candidate-owned report folder')
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

def _owned_tree_files(repo,sprint):
    lexical=Path(repo).resolve()/ 'build'/'agent-workflow'/sprint
    cursor=Path(repo).resolve()
    for part in ('build','agent-workflow',sprint):
        cursor=cursor/part
        if cursor.is_symlink() or (cursor.exists() and getattr(cursor.stat(),'st_file_attributes',0)&getattr(__import__('stat'),'FILE_ATTRIBUTE_REPARSE_POINT',0)):
            raise ValueError(f'owned sprint path contains a link/reparse point: {cursor}')
    if not lexical.exists(): return []
    if not lexical.is_dir(): raise ValueError(f'owned sprint path is not a directory: {lexical}')
    result=[]
    for base,dirs,files in os.walk(lexical,followlinks=False):
        for name in dirs+files:
            item=Path(base)/name
            if item.is_symlink() or getattr(item.stat(),'st_file_attributes',0)&getattr(__import__('stat'),'FILE_ATTRIBUTE_REPARSE_POINT',0):
                raise ValueError(f'owned sprint path contains a link/reparse point: {item}')
        result.extend(Path(base)/name for name in files)
    return result

def _cleanup_candidates(root,repo,sprint):
    found=[]
    for path in sorted((root/'candidates').glob('*.json')) if (root/'candidates').exists() else []:
        c=readjson(path)
        if c.get('sprint_id')!=sprint: continue
        try: owner=Path(c['worktree']['worktree']).resolve()
        except (KeyError,TypeError,OSError): continue
        if owner!=repo: continue
        if validid(c.get('id'))!=path.stem: raise ValueError(f'candidate identity/path mismatch: {path}')
        req=c.get('gate_request',{})
        if req and req.get('snapshot_key')==c.get('snapshot_key') and (req.get('prompt_admitted') or req.get('admission_unknown')) and not c.get('gate_outcome'):
            raise ValueError(f"cleanup blocked: candidate {c['id']} has an admitted gate request without a final outcome")
        for reviewer,record in c.get('scoped_reviews',{}).items():
            if record.get('snapshot_key')==c.get('snapshot_key'):
                report_path=Path(record.get('report_path',''))
                if record.get('admission_unknown') or not record.get('report_path') or not report_path.is_file() or sha(report_path)!=record.get('report_sha256'):
                    raise ValueError(f"cleanup blocked: candidate {c['id']} has unfinished/stale scoped review {reviewer}")
                try: validate_review_report(read_report(report_path),c,reviewer)
                except (ValueError,OSError): raise ValueError(f"cleanup blocked: candidate {c['id']} has unfinished/stale scoped review {reviewer}")
        found.append((path,c))
    return found

def _candidate_managed_reports(candidate,report_root):
    """Resolve only files explicitly named by this candidate's current receipts."""
    owned={}; ident=validid(candidate['id'])
    def add(path,digest,relative_pattern=None):
        if not isinstance(path,str): return
        p=Path(path)
        if not p.is_absolute(): p=(report_root/p)
        try:
            resolved=p.resolve(); rel=resolved.relative_to(report_root.resolve()).as_posix()
        except (OSError,ValueError): return
        if relative_pattern and not re.fullmatch(relative_pattern,rel): return
        if resolved.is_file() and isinstance(digest,str) and re.fullmatch(r'[0-9a-f]{64}',digest) and sha(resolved)==digest:
            owned[resolved]=digest
    receipt=candidate.get('gate_validation',{})
    validation=report_root/f'{ident}-gate-validation.json'
    if receipt.get('path') and Path(receipt['path']).resolve()==validation.resolve():
        add(str(validation),receipt.get('sha256'),re.escape(validation.name))
        if validation.is_file() and sha(validation)==receipt.get('sha256'):
            try: checks=readjson(validation).get('checks',[])
            except (ValueError,OSError): checks=[]
            for item in checks:
                log=item.get('log'); log_path=Path(log) if isinstance(log,str) else Path()
                try: rel=log_path.resolve().relative_to(report_root.resolve()).as_posix()
                except (OSError,ValueError): continue
                if re.fullmatch(re.escape(f'{ident}-logs')+r'/[1-9][0-9]*\.log',rel):
                    add(str(log_path),item.get('log_sha256'),re.escape(f'{ident}-logs')+r'/[1-9][0-9]*\.log')
    review=candidate.get('review',{})
    lead=report_root/f'{ident}-integration-lead-review.json'
    if review.get('report_path') and Path(review['report_path']).resolve()==lead.resolve():
        add(str(lead),review.get('report_sha256'),re.escape(lead.name))
        body=review.get('report',{})
        for item in body.get('original_images',[]):
            if isinstance(item,dict):
                add(item.get('path'),item.get('sha256'),r'reviewed-images/'+re.escape(ident)+r'-[1-9][0-9]*-[0-9a-f]{16}\.png')
        for item in body.get('viewed_images',[]):
            if isinstance(item,dict):
                add(item.get('path'),item.get('sha256'),r'reviewed-images/'+re.escape(ident)+r'-[1-9][0-9]*-[0-9a-f]{16}\.png')
    for reviewer,record in candidate.get('scoped_reviews',{}).items():
        if reviewer not in ('review','fidelity-reviewer') or not isinstance(record,dict): continue
        scoped=report_root/f'{ident}-{reviewer}.json'
        if record.get('report_path') and Path(record['report_path']).resolve()==scoped.resolve():
            add(str(scoped),record.get('report_sha256'),re.escape(scoped.name))
            for item in record.get('original_images',[]):
                if isinstance(item,dict):
                    add(item.get('path'),item.get('sha256'),re.escape(ident)+r'/'+re.escape(reviewer)+r'/images/[1-9][0-9]*\.png')
            if scoped.is_file() and sha(scoped)==record.get('report_sha256'):
                try: body=readjson(scoped)
                except (ValueError,OSError): body={}
                for item in body.get('original_images',[]):
                    if isinstance(item,dict):
                        add(item.get('path'),item.get('sha256'),re.escape(ident)+r'/'+re.escape(reviewer)+r'/images/[1-9][0-9]*\.png')
    return set(owned)

def _remove_empty_tree(root):
    root=Path(root)
    if not root.exists(): return
    for base,_,_ in os.walk(root,topdown=False,followlinks=False):
        p=Path(base)
        if p.is_dir() and not any(p.iterdir()): p.rmdir()

def cmd_sprint_cleanup(a,root):
    repo=Path(a.repo).resolve(); sprint=validid(a.sprint)
    if a.apply and (not isinstance(a.owner_reference,str) or not a.owner_reference.strip()):
        raise ValueError('--apply requires a nonblank explicit --owner-reference confirming sprint closure')
    # Validation and dispatch locks serialize against active checks and already-admitted work.
    gate_handle=gate_lock(root)
    dispatch=[]; sprint_handle=None
    try:
        candidates=_cleanup_candidates(root,repo,sprint)
        for path,c in candidates:
            dlock=gate_lock(root/'dispatch-locks'/c['id']); dispatch.append(dlock)
        sprint_handle=sprint_lock(root,repo,sprint)
        candidates=_cleanup_candidates(root,repo,sprint)
        report_dir=safe(repo,f'build/agent-workflow/{sprint}')
        owned_files=_owned_tree_files(repo,sprint)
        # Ensure the exact report hierarchy remains within this author root before mutation.
        if report_dir.exists() and not report_dir.is_relative_to(repo): raise ValueError('owned sprint report path escapes author worktree')
        selected={c['id'] for _,c in candidates}
        blocker=blocker_path(root)
        if blocker.is_file():
            b=readjson(blocker); blocker_log=Path(b.get('log',''))
            log_owned=False
            for _,candidate in candidates:
                receipt=candidate.get('gate_validation',{}); validation=Path(receipt.get('path',''))
                if validation.is_file() and sha(validation)==receipt.get('sha256'):
                    try: records=readjson(validation).get('checks',[])
                    except (ValueError,OSError): records=[]
                    if any(Path(x.get('log','')).resolve()==blocker_log.resolve() for x in records): log_owned=True
            if b.get('candidate_id') in selected or log_owned:
                raise ValueError('cleanup blocked: selected candidate has quarantined/unverified process ownership; preserve candidate and log until explicitly cleared')
        report_root=report_dir/'reports'
        managed=set()
        for _,candidate in candidates: managed.update(_candidate_managed_reports(candidate,report_root))
        preserved=[p for p in owned_files if p.resolve() not in managed]
        plan={'author_worktree':str(repo),'sprint_id':sprint,'candidate_ids':[c['id'] for _,c in candidates],
              'candidate_records_removed':[str(p) for p,_ in candidates],
              'managed_files_removed':[str(p) for p in sorted(managed)],
              'preserved_paths':[str(p) for p in preserved],
              'manual_index':str(root/'manual-tests.md'),'apply':bool(a.apply)}
        if not a.apply:
            print(json.dumps(plan,indent=2)); return
        for path,c in candidates:
            path.unlink()
            lockfile=root/'dispatch-locks'/c['id']/'gate-validation.lock'
            if lockfile.is_file(): lockfile.unlink()
            lockdir=root/'dispatch-locks'/c['id']
            if lockdir.is_dir() and not any(lockdir.iterdir()): lockdir.rmdir()
        for path in sorted(managed,reverse=True):
            if path.is_file(): path.unlink()
        _remove_empty_tree(report_dir)
        render_manual_index(root)
        plan['owner_reference']=a.owner_reference.strip(); plan['applied']=True
        print(json.dumps(plan,indent=2))
    finally:
        if sprint_handle: sprint_handle.close()
        for handle in reversed(dispatch): handle.close()
        gate_handle.close()

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

def monitor_launch_smoke(proc,log,check,timeout,stream,heartbeat=None):
    ready=[re.compile(x) for x in check['ready_patterns']]
    errors=[re.compile(x) for x in check.get('error_patterns',[])]
    deadline=time.monotonic()+timeout; cursor=0; tail=''; matched=[]; last_heartbeat=time.monotonic()
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
        now=time.monotonic()
        if heartbeat and now-last_heartbeat>=10:
            heartbeat(); last_heartbeat=now
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
    report,originals,images=prepare_candidate_review(read_report(a.report),c,a.reviewer,a.result,a.report)
    lock=sprint_lock(root,c['worktree']['worktree'],c['sprint_id'])
    try:
        if originals:
            report['viewed_images']=capture_review_images(report.get('viewed_images',[]),originals,a.report,rid)
            writejson(a.report,report)
        save_candidate_review(p,c,a.result,a.report,report,images if not originals else optional_reviewed_images(report['viewed_images'],Path(a.report).resolve().parent),originals)
        print(a.result)
    finally: lock.close()

def cmd_gate_report(a,root):
    rid=validid(a.id); p=root/'candidates'/f'{rid}.json'
    if a.actor!='integration-lead' or not p.is_file(): raise ValueError('only integration-lead may report an existing candidate')
    c=verify_candidate(readjson(p)); folder=author_report_dir(c)
    lock=sprint_lock(root,c['worktree']['worktree'],c['sprint_id'])
    try:
        if not p.is_file(): raise ValueError('candidate was removed before report persistence')
        c=verify_candidate(readjson(p))
        if c.get('gate_outcome',{}).get('result')=='ready_for_owner' or c.get('owner_decision'):
            raise ValueError('published/owner-disposition candidate report is immutable; submit a new revision')
        folder=author_report_dir(c)
        target=folder/f'{rid}-integration-lead-review.json'
        report={'candidate_id':rid,'snapshot_key':c['snapshot_key'],'reviewer':'integration-lead',
                'decision':a.result,'findings':a.finding,'viewed_images':[]}
        if a.scoped_review:
            reviewer=a.scoped_review; receipt=c.get('scoped_reviews',{}).get(reviewer)
            if not receipt or receipt.get('snapshot_key')!=c['snapshot_key'] or receipt.get('admission_unknown'):
                raise ValueError('scoped review receipt missing, stale, or uncertain')
            scoped_path=Path(receipt.get('report_path',''))
            if not scoped_path.is_file() or sha(scoped_path)!=receipt.get('report_sha256'):
                raise ValueError('scoped review report missing or hash-stale')
            scoped=validate_review_report(read_report(scoped_path),c,reviewer)
            if scoped.get('decision')!=a.result: raise ValueError('scoped review decision does not match lead result')
            checked_originals=verify_original_images(scoped.get('original_images',[]),c,scoped_path.parent)
            receipt_originals=verify_original_images(receipt.get('original_images',[]),c,scoped_path.parent)
            if checked_originals!=receipt_originals: raise ValueError('scoped review image receipt binding mismatch')
            report['scoped_review']={'reviewer':reviewer,'report_sha256':receipt['report_sha256'],
                                     'inspection_by':reviewer,'integration_lead_personally_viewed':False}
            report['viewed_images']=scoped.get('viewed_images',[])
            report['original_images']=scoped.get('original_images',[])
        prepared,originals,images=prepare_candidate_review(report,c,'integration-lead',a.result,target)
        folder.mkdir(parents=True,exist_ok=True)
        if originals:
            prepared['viewed_images']=capture_review_images(prepared.get('viewed_images',[]),originals,target,rid)
        # Nothing is written until identity, decision, findings, scoped receipts,
        # original/copy hashes, and candidate mutability have all been checked.
        writejson(target,prepared)
        images=optional_reviewed_images(prepared.get('viewed_images',[]),target.parent)
        save_candidate_review(p,c,a.result,target,prepared,images,originals)
    finally: lock.close()
    print(target)

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

def _candidate_status_line(c, *, verify=False, root=None):
    outcome=c.get('gate_outcome') or {}
    recorded=outcome.get('result') or (c.get('review') or {}).get('result') or c.get('status','candidate')
    owner=c.get('owner_decision') or {}
    if owner.get('decision'): recorded='owner_'+str(owner['decision'])
    details=''
    if outcome.get('result')=='needs_changes':
        details+=f"; findings: {' | '.join(str(x) for x in outcome.get('findings',[]))}"
    report=outcome.get('report_path') or (c.get('review') or {}).get('report_path')
    if report: details+=f'; report: {report}'
    if not verify:
        detail=f'{recorded} (recorded; freshness not checked){details}'
        return f"code {c['id']}: {detail}"
    try:
        verify_candidate(c)
        if outcome.get('result')=='ready_for_owner':
            verify_gate(c); verify_publication(c,root)
        elif outcome.get('result')=='needs_changes':
            review=c.get('review') or {}; rp=Path(review.get('report_path',''))
            if (review.get('result')!='needs_changes' or outcome.get('report_sha256')!=review.get('report_sha256') or
                not rp.is_file() or sha(rp)!=review.get('report_sha256')):
                raise ValueError('needs_changes outcome is not bound to a current independent review report')
        if owner.get('decision'): verify_owner_decision(c)
        return f"code {c['id']}: {recorded} (verified current){details}"
    except (ValueError,OSError,subprocess.CalledProcessError,KeyError) as exc:
        historic=f' (owner_{owner["decision"]} recorded)' if owner.get('decision') else ''
        return f"code {c['id']}: STALE/BLOCKED{historic} (recorded {recorded}; {str(exc)[:240]}){details}"


def cmd_status(a,root):
    if not root.exists(): print('No agent exchange records.'); return
    selected=getattr(a,'candidate',None)
    if selected:
        p=root/'candidates'/f'{validid(selected)}.json'
        if not p.is_file(): raise ValueError(f'unknown candidate: {selected}')
        c=readjson(p)
        if c.get('id')!=selected: raise ValueError('candidate filename/identity mismatch')
        print(_candidate_status_line(c,verify=bool(getattr(a,'verify',False)),root=root)); return
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
        c=readjson(p); print(_candidate_status_line(c,verify=bool(getattr(a,'verify',False)),root=root))

def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--repo',default='.'); p.add_argument('--asset-workspace')
    s=p.add_subparsers(dest='command',required=True)
    q=s.add_parser('request'); q.add_argument('--from-json',required=True); q.add_argument('--context-root',default='.'); q.set_defaults(fn=cmd_request)
    q=s.add_parser('claim'); q.add_argument('id'); q.add_argument('--agent',required=True); q.set_defaults(fn=cmd_claim)
    q=s.add_parser('plan-manifest'); q.add_argument('--manifest',required=True); q.add_argument('--source-root',required=True); q.add_argument('--target',help='optional destination root for existing-file/conflict inspection'); q.set_defaults(fn=cmd_plan)
    q=s.add_parser('deliver'); q.add_argument('id'); q.add_argument('--manifest',required=True); q.add_argument('--source-root',required=True); q.add_argument('--revision',required=True); q.add_argument('--provenance',nargs='+',required=True); q.add_argument('--editable',nargs='+',required=True); q.add_argument('--review-images',nargs='+',required=True); q.set_defaults(fn=cmd_deliver)
    q=s.add_parser('review'); q.add_argument('id'); q.add_argument('--reviewer',required=True); q.add_argument('--result',choices=['needs_changes','ready_for_owner'],required=True); q.add_argument('--report',required=True); q.set_defaults(fn=cmd_review)
    q=s.add_parser('import-assets'); q.add_argument('id'); q.add_argument('--target',required=True); q.add_argument('--owner-receipt'); q.add_argument('--plan-out'); q.add_argument('--plan'); q.add_argument('--apply',action='store_true'); q.add_argument('--allow-overwrite',action='store_true'); q.set_defaults(fn=cmd_import)
    q=s.add_parser('submit'); q.add_argument('id'); q.add_argument('--agent',required=True); q.add_argument('--sprint',required=True); q.add_argument('--base-ref',default='main'); q.add_argument('--evidence',nargs='*',default=[]); q.add_argument('--build-identity',required=True); q.add_argument('--steps',required=True); q.add_argument('--unverified',default=''); q.add_argument('--gate-plan'); q.add_argument('--visual-review-required',action='store_true'); q.set_defaults(fn=cmd_submit)
    q=s.add_parser('candidate-review'); q.add_argument('id'); q.add_argument('--reviewer',required=True); q.add_argument('--report',required=True); q.add_argument('--result',choices=['needs_changes','ready_for_owner'],required=True); q.set_defaults(fn=cmd_candidate_review)
    q=s.add_parser('gate-report'); q.add_argument('id'); q.add_argument('--actor',required=True); q.add_argument('--result',choices=['needs_changes','ready_for_owner'],required=True); q.add_argument('--finding',action='append',required=True); q.add_argument('--scoped-review',choices=['review','fidelity-reviewer']); q.set_defaults(fn=cmd_gate_report)
    q=s.add_parser('candidate-decision'); q.add_argument('id'); q.add_argument('--owner-receipt',required=True); q.set_defaults(fn=cmd_candidate_decision)
    q=s.add_parser('gate-preflight'); q.add_argument('id'); q.set_defaults(fn=cmd_gate_preflight)
    q=s.add_parser('gate-validate'); q.add_argument('id'); q.add_argument('--actor',required=True); q.add_argument('--timeout',type=int,default=3600); q.set_defaults(fn=cmd_gate_validate)
    q=s.add_parser('gate-clear-blocker'); q.add_argument('--actor',required=True); q.add_argument('--pid',type=int,required=True); q.add_argument('--confirmed-stopped',action='store_true'); q.set_defaults(fn=cmd_gate_clear_blocker)
    q=s.add_parser('gate-finish'); q.add_argument('id'); q.add_argument('--result',choices=['needs_changes','ready_for_owner'],required=True); q.add_argument('--report',required=True); q.set_defaults(fn=cmd_gate_finish)
    q=s.add_parser('gate-deliver'); q.add_argument('id'); q.add_argument('--actor',required=True); q.set_defaults(fn=cmd_gate_deliver)
    q=s.add_parser('manual-notify'); q.add_argument('id'); q.set_defaults(fn=cmd_manual_notify)
    q=s.add_parser('manual-tests'); q.set_defaults(fn=cmd_manual_tests)
    q=s.add_parser('sprint-cleanup'); q.add_argument('--repo',required=True); q.add_argument('--sprint',required=True); q.add_argument('--apply',action='store_true'); q.add_argument('--owner-reference',default=''); q.set_defaults(fn=cmd_sprint_cleanup)
    q=s.add_parser('gate-request'); q.add_argument('id'); q.add_argument('--central-worktree'); q.add_argument('--origin-session'); q.add_argument('--server'); q.add_argument('--wait-timeout',type=int,default=7200); q.set_defaults(fn=cmd_gate_request)
    q=s.add_parser('gate-scope-review'); q.add_argument('id'); q.add_argument('--reviewer',choices=['review','fidelity-reviewer'],required=True); q.add_argument('--actor',required=True); q.add_argument('--server'); q.add_argument('--wait-timeout',type=int,default=7200); q.set_defaults(fn=cmd_gate_scope_review)
    q=s.add_parser('status'); q.add_argument('--candidate',help='show one code candidate without scanning history'); q.add_argument('--verify',action='store_true',help='explicitly verify source freshness and publication proofs (all candidates unless --candidate is supplied)'); q.set_defaults(fn=cmd_status)
    a=p.parse_args()
    try:
        root=queue(a,create=a.command not in ('status','plan-manifest')); a.fn(a,root)
    except WorkflowExit as e: return e.code
    except (ValueError,OSError,subprocess.CalledProcessError) as e: print(f'error: {e}',file=sys.stderr); return 2
    return 0
if __name__=='__main__': raise SystemExit(main())
