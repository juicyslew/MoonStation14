"""Shared real-Git fixture initialization for preflight/admission tests."""
import hashlib
import copy
import json
import subprocess
from types import SimpleNamespace

from workflow_store import digest
from workflow_check_packs import resolve_check_packs
import workflow_preflight as preflight


def prepare_dynamic_author_submission(context, *, base_policy, repo, submit_key, criterion, artifact_id, artifact_path, request_path):
    """Prepare strict scope/request files using only status-discovered author context."""
    from workflow_store import WorkflowStore
    caller = context['derived_caller']
    submission_id = WorkflowStore.submission_id_for(caller, submit_key)
    data = (criterion + ' evidence').encode()
    artifact_path.write_bytes(data)
    artifact = {'contract_version':1,'artifact_id':artifact_id,'run_id':None,'producer_task_id':None,
        'type':'SOURCE_MANIFEST','path':str(artifact_path),'sha256':hashlib.sha256(data).hexdigest(),
        'size_bytes':len(data),'immutable':True,'producer_kind':'SUBMISSION','producer_id':submission_id,
        'submission_id':submission_id}
    typed=context['typed_floors']; policy=copy.deepcopy(base_policy)
    policy.update(author=context['authenticated_author'],source_worktree=context['repo'],
        review_roles=typed['review_roles'],owner_exceptions=typed['owner_exceptions'],
        config_overrides=typed['config_overrides'],review_envelope_limit_bytes=typed['review_envelope_limit'],
        selector_requirements=typed['selectors'],sprint_id=criterion,criteria=[criterion],
        coverage={criterion:typed['required_packs']})
    policy['review_summaries']={role:'Review '+criterion for role in typed['review_roles']}
    request={'contract_version':1,'idempotency_key':submit_key,'sprint_id':criterion,'feature_label':'Example',
        'author':context['authenticated_author'],'source_worktree':context['repo'],
        'input_digest':digest(preflight.capture_source(repo)),'plan_digest':'0'*64,
        'policy_digest':digest(policy),'purpose':'OWNER_READY','implementation_complete':True,
        'criteria':[{'criterion_id':criterion,'status':'COMPLETE','evidence_refs':[artifact_id]}],
        'manual_steps':['Run the manual scenario.'],
        'check_packs':[{'pack_id':p,'kind':p,'selectors':[]} for p in typed['required_packs']]}
    request['plan_digest']=digest(resolve_check_packs(request,repo=repo,policy=policy))
    scope={'contract_version':1,'policy':policy,'artifacts':[artifact],
        'submission_idempotency_key':submit_key}
    request_path.write_text(json.dumps(request),encoding='utf-8')
    return scope, request


def init_repository(repo, *, extra_files=None):
    def git(*args): return subprocess.run(['git',*args],cwd=repo,check=True,capture_output=True)
    git('init','-q'); git('config','user.name','Test'); git('config','user.email','test@example.invalid')
    files={'tracked.txt':'one','opencode.jsonc':'{}','manual.md':'Run the focused check.'}
    files.update(extra_files or {})
    for name,content in files.items(): (repo/name).write_text(content)
    git('add','.'); git('commit','-qm','fixture')
    return git


def make_preflight_case(root, *, repo_name='main', caller='caller', key='key', evidence_name='evidence.txt', evidence=b'evidence'):
    """Build the shared, real-Git request/policy/artifact/context fixture."""
    root = __import__('pathlib').Path(root)
    repo = root / repo_name
    repo.mkdir(parents=True, exist_ok=True)
    init_repository(repo, extra_files={'manual.md': 'Run the focused check.'})
    (repo / 'dirty.txt').write_text('untracked')
    author = {'session_id':'sess','worktree':str(repo),'role':'author'}
    evidence_path = root / evidence_name
    evidence_path.write_bytes(evidence)
    submission_id = 'submit_' + hashlib.sha256((caller+'\0submit\0'+key).encode()).hexdigest()[:24]
    artifact = {'contract_version':1,'artifact_id':'artifact_evidence','run_id':None,'producer_task_id':None,
                'type':'SOURCE_MANIFEST','path':str(evidence_path),'sha256':hashlib.sha256(evidence).hexdigest(),
                'size_bytes':len(evidence),'immutable':True,'producer_kind':'SUBMISSION','producer_id':submission_id,
                'submission_id':submission_id}
    policy = {'contract_version':1,'sprint_id':'sprint','source_worktree':str(repo),'author':author,
              'criteria':['c1'],'coverage':{'c1':['package','provenance']},'selector_requirements':{},'fixture':'manual.md',
              'package_artifacts':['build/output.zip'],'owner_exceptions':[],'review_roles':['implementation'],
              'review_summaries':{'implementation':'Assess implementation against the agreed criterion and evidence.'},
              'review_envelope_limit_bytes':16000,'config_overrides':[]}
    request = {'contract_version':1,'idempotency_key':key,'sprint_id':'sprint','feature_label':'Example',
               'author':author,'source_worktree':str(repo),'input_digest':digest(preflight.capture_source(repo)),
               'plan_digest':'0'*64,'policy_digest':digest(policy),'purpose':'OWNER_READY','implementation_complete':True,
               'criteria':[{'criterion_id':'c1','status':'COMPLETE','evidence_refs':['artifact_evidence']}],
               'manual_steps':['Run the manual scenario.'],
               'check_packs':[{'pack_id':'package','kind':'package','selectors':[]},
                              {'pack_id':'provenance','kind':'provenance','selectors':[]}]}
    request['plan_digest'] = digest(resolve_check_packs(request, repo=repo, policy=policy))
    receipt = root / 'receipt.json'
    row = {'target':'main','path':'opencode.jsonc','new_sha256':hashlib.sha256((repo/'opencode.jsonc').read_bytes()).hexdigest()}
    receipt.write_text(__import__('json').dumps({'main_root':str(repo),'shared_root':str(root),'source_hashes':{},
                  'created_utc':'2026-01-01T00:00:00Z','files':[row]},separators=(',',':')))
    context = preflight.PreflightContext(repo,author,policy,[artifact],receipt,hashlib.sha256(receipt.read_bytes()).hexdigest())
    return SimpleNamespace(repo=repo,author=author,caller=caller,key=key,evidence=evidence_path,artifact_id='artifact_evidence',
                           submission_id=submission_id,artifact=artifact,policy=policy,request=request,receipt=receipt,context=context)
