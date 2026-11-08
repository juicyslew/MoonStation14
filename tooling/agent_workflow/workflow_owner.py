"""Owner acceptance service: verify frozen evidence and prepare regression input."""
import hashlib
import json
import os
import re
import shutil
import stat
import tempfile
import zipfile
from pathlib import Path, PurePosixPath

import workflow_runtime
import workflow_store
import workflow_regression


class OwnerService:
    def __init__(self, store, *, snapshot_dir):
        self.store = store
        self.snapshot_dir = Path(snapshot_dir).resolve()

    @staticmethod
    def _bytes(artifact):
        path = Path(artifact['path'])
        data = path.read_bytes()
        if len(data) != artifact['size_bytes'] or hashlib.sha256(data).hexdigest() != artifact['sha256']:
            raise workflow_store.ProtocolError(f"registered artifact bytes do not match: {artifact['artifact_id']}")
        return data

    def decide(self, decision, *, authenticated_owner, displayed_result_id, displayed_result_digest):
        decision = workflow_store.validate(decision, 'owner_decision')
        if authenticated_owner != decision['owner'] or authenticated_owner.get('role') != 'owner':
            raise workflow_store.ProtocolError('authenticated owner binding mismatch')
        run_id = decision['run_id']
        saved_run = self.store.get_run(run_id)
        if saved_run is None:
            raise workflow_store.StaleCommand('run not found')
        if saved_run['run'].get('author', {}).get('worktree') != authenticated_owner.get('worktree'):
            raise PermissionError('run is outside configured owner worktree')
        saved = self.store.get_owner_decision_receipt(decision['owner'], decision['command_id'])
        if saved is not None:
            if (saved['decision'] != decision or
                    saved['owner'] != authenticated_owner or saved['result_id'] != displayed_result_id or
                    saved['result_digest'] != displayed_result_digest):
                raise workflow_store.IdempotencyConflict('owner command ID reused with changed content or binding')
            return saved['response']
        submission = self.store.get_run_submission(run_id)
        if submission is None: raise workflow_store.ProtocolError('accepted submission evidence unavailable')
        request = submission['request']; run = self.store.get_run(run_id)['run']
        if decision['input_digest'] != run['input_digest']:
            raise workflow_store.ProtocolError('decision input does not match accepted run')
        registered = [x['artifact'] for x in submission['artifacts']]
        producer_id = submission.get('submission_id')
        manifests = [x for x in submission['artifacts'] if x['artifact']['type']=='SOURCE_MANIFEST' and Path(x['artifact']['path']).name=='source-manifest.json' and x['artifact'].get('producer_kind')=='SUBMISSION' and x['artifact'].get('producer_id')==producer_id and x['artifact'].get('submission_id')==producer_id]
        archives = [x for x in submission['artifacts'] if x['artifact']['type']=='PROVENANCE' and Path(x['artifact']['path']).name=='source-snapshot.zip' and x['artifact'].get('producer_kind')=='SUBMISSION' and x['artifact'].get('producer_id')==producer_id and x['artifact'].get('submission_id')==producer_id]
        if len(manifests)!=1: raise workflow_store.ProtocolError('frozen source manifest evidence is missing or ambiguous')
        if decision['decision']=='ACCEPT' and len(archives)!=1: raise workflow_store.ProtocolError('frozen source archive evidence is missing or ambiguous')
        manifest_binding=manifests[0]; manifest=manifest_binding['artifact']; archive=archives[0]['artifact'] if archives else None
        manifest_data = self._bytes(manifest); source = json.loads(manifest_data)
        if (workflow_store.canonical_json(source).encode() != manifest_data or
                manifest_binding.get('input_digest') != run['input_digest'] or
                workflow_store.digest(source) != run['input_digest']):
            raise workflow_store.ProtocolError('source manifest semantic identity does not match accepted input')
        builds=[]
        for task in self.store.list_tasks(run_id, decision['evaluation_generation']):
            if task.get('state')=='COMPLETED' and task.get('result',{}).get('decision')=='PASS':
                for ref in task.get('result',{}).get('artifact_refs',[]):
                    binding=self.store.get_artifact(ref)
                    if binding and binding['artifact'].get('type')=='BUILD': builds.append(binding['artifact'])
        if not builds: raise workflow_store.ProtocolError('accepted PASS has no registered BUILD evidence')
        for item in builds: self._bytes(item)
        if decision['decision'] != 'ACCEPT':
            return self.store.record_owner_decision(decision,authenticated_owner=authenticated_owner,
                displayed_result_id=displayed_result_id,displayed_result_digest=displayed_result_digest,
                source_artifact_ref=manifest['artifact_id'],build_artifact_refs=[x['artifact_id'] for x in builds],regression_inputs=None)
        if archive is None: raise workflow_store.ProtocolError('frozen source archive unavailable')
        if not re.fullmatch(r'accept_[A-Za-z0-9_-]+', decision['acceptance_id']):
            raise workflow_store.ProtocolError('unsafe acceptance ID')
        zip_data=self._bytes(archive)
        files={r['path']:r for r in source['files'] if r.get('state')=='FILE'}
        with tempfile.TemporaryDirectory(prefix='.owner-source-',dir=self.snapshot_dir.parent) as tmp:
            stage=Path(tmp)/'source'; stage.mkdir()
            with zipfile.ZipFile(__import__('io').BytesIO(zip_data)) as z:
                names=z.namelist()
                if len(names)!=len(set(names)) or set(names)!=set(files): raise workflow_store.ProtocolError('source archive entries differ from manifest FILE rows')
                for name in names:
                    p=PurePosixPath(name)
                    if p.is_absolute() or any(x in ('','..','.') for x in p.parts) or '\\' in name: raise workflow_store.ProtocolError('unsafe archive entry')
                    data=z.read(name); row=files[name]
                    if len(data)!=row['size_bytes'] or hashlib.sha256(data).hexdigest()!=row['sha256']: raise workflow_store.ProtocolError('source file differs from accepted manifest')
                    dest=stage.joinpath(*p.parts); dest.parent.mkdir(parents=True,exist_ok=True); dest.write_bytes(data)
            workflow_regression.verify_snapshot_files(stage, source)
            base=self.snapshot_dir/decision['acceptance_id']; target=base/'source'
            if not target.resolve().is_relative_to(self.snapshot_dir): raise workflow_store.ProtocolError('snapshot path escapes configured directory')
            base.mkdir(parents=True,exist_ok=True)
            if target.exists():
                for rel,row in files.items():
                    b=(target/rel).read_bytes()
                    if hashlib.sha256(b).hexdigest()!=row['sha256']: raise workflow_store.ProtocolError('existing accepted source snapshot conflicts')
                shutil.rmtree(stage)
            else: os.replace(stage,target)
        preflight=next((a for a in registered if a['type']=='PROVENANCE' and Path(a['path']).name=='preflight-bundle.json'),None)
        if preflight is None: raise workflow_store.ProtocolError('accepted preflight bundle missing')
        bundle_data=self._bytes(preflight); bundle=workflow_store.strict_loads(bundle_data.decode('utf-8'))
        plan={'title':'Post-acceptance full regression','kind':'feature','acceptance_criteria':['full-regression'],
              'fixture':bundle['review_policy']['fixture'],'manual_steps':request['manual_steps'],
              'checks':[{'argv':['./gradlew','runGameTestServer',f'-Pms14GameTestDir=build/workflow-regression/{decision["acceptance_id"]}','--no-daemon'],
                         'rationale':'Full post-acceptance regression','covers':['full-regression'],'kind':'gametest','log':f'build/workflow-regression/{decision["acceptance_id"]}/logs/latest.log'}]}
        resolved=workflow_runtime.resolve_gate_plan(plan,target)
        policy={'kind':'POST_ACCEPTANCE_REGRESSION'}
        inputs={'contract_version':1,'accepted_feature_run_id':run_id,'source_input_digest':run['input_digest'],'source_manifest':source,
                'source_manifest_ref':manifest['artifact_id'],
                'source_snapshot_ref':archive['artifact_id'],'snapshot_root':str(target),'plan':resolved,
                'plan_digest':workflow_store.digest(resolved),'policy':policy,'policy_digest':workflow_store.digest(policy),
                'author':run['author'],'sprint_id':run['sprint_id'],'acceptance_id':decision['acceptance_id']}
        return self.store.record_owner_decision(decision,authenticated_owner=authenticated_owner,
            displayed_result_id=displayed_result_id,displayed_result_digest=displayed_result_digest,
            source_artifact_ref=manifest['artifact_id'],build_artifact_refs=[x['artifact_id'] for x in builds],regression_inputs=inputs)
