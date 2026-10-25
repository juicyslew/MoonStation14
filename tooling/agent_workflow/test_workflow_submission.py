import hashlib
import json
import os
import subprocess
import sys
from unittest import mock
import tempfile
import unittest
import zipfile
from pathlib import Path

import workflow_preflight as preflight
import workflow_submission as submission
import workflow_store
from workflow_check_packs import resolve_check_packs
from preflight_test_support import init_repository, make_preflight_case


class SubmissionBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)
        case=make_preflight_case(self.root,repo_name='repo',caller='caller',key='key')
        for name in ('repo','author','caller','key','evidence','policy','request','receipt','context'):
            setattr(self,name,getattr(case,name))
        self.artifact_dir=None
        self.store=workflow_store.WorkflowStore(self.root/'db.sqlite'); self.artifacts=self.root/'evidence-store'
    def write_receipt(self):
        data={'main_root':str(self.repo),'shared_root':str(self.root),'source_hashes':{},'created_utc':'2026-01-01T00:00:00Z','files':[{'target':'main','path':'opencode.jsonc','new_sha256':hashlib.sha256((self.repo/'opencode.jsonc').read_bytes()).hexdigest()}]}
        self.receipt.write_text(json.dumps(data,separators=(',',':')))
    def submit(self,**kw): return submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=self.request,context=self.context,artifact_dir=self.artifacts,**kw)
    def tearDown(self): self.tmp.cleanup()

    def test_accepted_freezes_evidence_and_one_check(self):
        result=self.submit(); self.assertEqual(result['status'],'ACCEPTED')
        frozen=self.store.submission_artifacts(caller=self.caller,idempotency_key=self.key)
        self.assertEqual(len(frozen),4)
        by_name={Path(item['artifact']['path']).name:item for item in frozen}
        bundle=json.loads(Path(by_name['preflight-bundle.json']['artifact']['path']).read_text())
        self.assertEqual(workflow_store.digest(bundle['resolved_plan']),self.request['plan_digest'])
        for item in frozen:
            artifact=item['artifact']; data=Path(artifact['path']).read_bytes()
            self.assertEqual(hashlib.sha256(data).hexdigest(),artifact['sha256'])
        snapshot=Path(next(x['artifact']['path'] for x in frozen if Path(x['artifact']['path']).name=='source-snapshot.zip'))
        manifest=json.loads(Path(by_name['source-manifest.json']['artifact']['path']).read_text())
        with zipfile.ZipFile(snapshot) as archive:
            self.assertEqual(archive.namelist(),[x['path'] for x in manifest['files'] if x['state']=='FILE'])
            for row in manifest['files']:
                if row['state']=='FILE': self.assertEqual(archive.read(row['path']),(self.repo/row['path']).read_bytes())
        db=self.store._connect()
        try:
            self.assertEqual(db.execute('select count(*) from tasks').fetchone()[0],1)
            self.assertEqual(db.execute('select logical_key from tasks').fetchone()[0],'preflight:code_checks')
            self.assertEqual(db.execute('select count(*) from outbox').fetchone()[0],1)
        finally: db.close()

    def test_exact_retry_uses_saved_receipt_after_source_change(self):
        first=self.submit(); archives=[Path(x['artifact']['path']) for x in self.store.submission_artifacts(caller=self.caller,idempotency_key=self.key) if Path(x['artifact']['path']).name=='source-snapshot.zip']; (self.repo/'dirty.txt').write_text('changed'); (self.repo/'tracked.txt').unlink()
        self.assertEqual(self.submit(),first)
        with zipfile.ZipFile(archives[0]) as archive: self.assertEqual(archive.read('dirty.txt'),b'untracked'); self.assertEqual(archive.read('tracked.txt'),b'one')

    def test_changed_payload_same_key_conflicts(self):
        self.submit(); altered=dict(self.request,feature_label='Changed')
        with self.assertRaises(workflow_store.IdempotencyConflict): submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=altered,context=self.context,artifact_dir=self.artifacts)

    def test_incomplete_request_rejects_without_run_or_task(self):
        bad=dict(self.request,implementation_complete=False)
        result=submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=bad,context=self.context,artifact_dir=self.artifacts)
        self.assertEqual(result['result'],'FAIL'); db=self.store._connect()
        try: self.assertEqual(db.execute('select count(*) from runs').fetchone()[0],0); self.assertEqual(db.execute('select count(*) from tasks').fetchone()[0],0)
        finally: db.close()

    def test_untrusted_focused_selector_fails_before_admission(self):
        self.policy['coverage']['c1'].append('focused_gametest')
        self.policy['selector_requirements']={'focused_gametest':['ventilation']}
        self.request['check_packs'].append({'pack_id':'focused_gametest','kind':'focused_gametest','selectors':['unrelated']})
        self.request['policy_digest']=workflow_store.digest(self.policy)
        result=self.submit()
        self.assertEqual(result['result'],'FAIL')
        self.assertEqual(result['code'],'CHECK_PACK_PLAN')
        db=self.store._connect()
        try:
            self.assertEqual(db.execute('select count(*) from runs').fetchone()[0],0)
            self.assertEqual(db.execute('select count(*) from tasks').fetchone()[0],0)
        finally: db.close()

    def test_malformed_json_is_saved_as_diagnostic_wrapper(self):
        result=submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request='{"x":1,"x":2}',context=self.context,artifact_dir=self.artifacts)
        self.assertEqual(result['result'],'FAIL')
        self.assertEqual(self.store.lookup_submission_receipt(caller=self.caller,idempotency_key=self.key,request_digest=workflow_store.digest({'idempotency_key':self.key,'malformed_request':'{"x":1,"x":2}'})),result)

    def test_wrong_trusted_author_rejected_without_admission(self):
        bad=dict(self.request,author={'session_id':'other','worktree':str(self.repo),'role':'author'})
        result=submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=bad,context=self.context,artifact_dir=self.artifacts)
        self.assertEqual(result['result'],'FAIL'); self.assertIsNone(self.store.get_run('run_missing'))

    def test_check_cli_is_read_only(self):
        req=self.root/'request.json'; req.write_text(json.dumps(self.request)); ctx=self.root/'context.json'
        ctx.write_text(json.dumps({'repo':str(self.repo),'authenticated_author':self.author,'policy':self.policy,'artifacts':self.context.artifacts,'publication_receipt_path':str(self.receipt),'publication_receipt_sha256':self.context.publication_receipt_sha256}))
        env=dict(os.environ,PYTHONPATH=str(Path(__file__).parent))
        proc=subprocess.run([os.environ.get('WORKFLOW_PYTHON',sys.executable),str(Path(__file__).with_name('workflow_submission.py')),'check','--request',str(req),'--context',str(ctx),'--caller',self.caller,'--key',self.key],capture_output=True,text=True,env=env)
        self.assertEqual(proc.returncode,0,proc.stderr+proc.stdout); self.assertTrue(json.loads(proc.stdout)['ready_for_admission'])
        db=self.store._connect()
        try: self.assertEqual(db.execute('select count(*) from runs').fetchone()[0],0)
        finally: db.close()

    def test_administrative_submit_cli_is_explicit_and_idempotent(self):
        req=self.root/'request.json'; req.write_text(json.dumps(self.request)); ctx=self.root/'context.json'
        ctx.write_text(json.dumps({'repo':str(self.repo),'authenticated_author':self.author,'policy':self.policy,'artifacts':self.context.artifacts,'publication_receipt_path':str(self.receipt),'publication_receipt_sha256':self.context.publication_receipt_sha256}))
        dbpath=self.root/'cli.sqlite'; artifact_dir=self.root/'cli-evidence'; env=dict(os.environ,PYTHONPATH=str(Path(__file__).parent))
        command=[sys.executable,str(Path(__file__).with_name('workflow_submission.py')),'submit','--request',str(req),'--context',str(ctx),'--caller',self.caller,'--key',self.key,'--database',str(dbpath),'--artifact-dir',str(artifact_dir)]
        first=subprocess.run(command,capture_output=True,text=True,env=env); second=subprocess.run(command,capture_output=True,text=True,env=env)
        self.assertEqual(first.returncode,0,first.stdout+first.stderr); self.assertEqual(second.returncode,0,second.stdout+second.stderr)
        one,two=json.loads(first.stdout),json.loads(second.stdout)
        self.assertEqual(one['response']['status'],'ACCEPTED'); self.assertEqual(one['response'],two['response']); self.assertTrue(Path(one['bundle_path']).is_file())
        store=workflow_store.WorkflowStore(dbpath); conn=store._connect()
        try: self.assertEqual(conn.execute('select count(*) from tasks').fetchone()[0],1)
        finally: conn.close()

    def counts(self):
        db=self.store._connect()
        try: return tuple(db.execute(f'select count(*) from {table}').fetchone()[0] for table in ('runs','tasks','resources','incidents','outbox'))
        finally: db.close()

    def test_corrupt_pinned_receipt_is_durable_system_error_and_exact_retry(self):
        bad=preflight.PreflightContext(self.repo,self.author,self.policy,self.context.artifacts,self.receipt,'0'*64)
        first=submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=self.request,context=bad,artifact_dir=self.artifacts)
        self.assertEqual(first['result'],'ERROR'); self.assertEqual(self.counts(),(0,0,0,1,1))
        db=self.store._connect()
        try: self.assertEqual(db.execute("select count(*) from events where kind='INCIDENT_PAGE_PROJECTION'").fetchone()[0],1)
        finally: db.close()
        fixed=preflight.PreflightContext(self.repo,self.author,self.policy,self.context.artifacts,self.receipt,self.context.publication_receipt_sha256)
        self.assertEqual(submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=self.request,context=fixed,artifact_dir=self.artifacts),first)
        self.assertEqual(self.counts(),(0,0,0,1,1))

    def test_missing_referenced_proof_is_actionable_fail_without_jobs(self):
        self.request['criteria'][0]['evidence_refs']=['artifact_missing']
        result=self.submit()
        self.assertEqual(result['result'],'FAIL'); self.assertIn('required_change',result['findings'][0])
        self.assertEqual(self.counts()[:3],(0,0,0))

    def test_stage_time_source_change_is_error_without_admission(self):
        original=submission._write_immutable; called=[]
        def mutate(path,data):
            result=original(path,data)
            if not called:
                called.append(True); (self.repo/'tracked.txt').write_text('changed during staging')
            return result
        with mock.patch.object(submission,'_write_immutable',side_effect=mutate): result=self.submit()
        self.assertEqual(result['result'],'ERROR'); self.assertEqual(self.counts(),(0,0,0,1,1))

    def test_source_mutated_during_archive_copy_is_rejected_without_admission(self):
        original=zipfile.ZipFile.open; changed=[]
        def mutate_before_archive_write(archive,name,mode='r',*args,**kwargs):
            member=name.filename if isinstance(name,zipfile.ZipInfo) else name
            if mode=='w' and member=='tracked.txt' and not changed:
                changed.append(True)
                (self.repo/'tracked.txt').write_text('changed after manifest capture')
            return original(archive,name,mode,*args,**kwargs)
        with mock.patch.object(zipfile.ZipFile,'open',new=mutate_before_archive_write):
            result=self.submit()
        self.assertTrue(changed, 'mutation hook did not reach archive copy')
        self.assertEqual(result['status'],'REJECTED')
        self.assertEqual(result['result'],'ERROR')
        self.assertEqual(self.counts(),(0,0,0,1,1))
        self.assertEqual(len(self.store.submission_artifacts(caller=self.caller,idempotency_key=self.key)),0)
        self.assertEqual(len(list(self.artifacts.rglob('source-manifest.json'))),1)
        self.assertEqual(len(list(self.artifacts.rglob('preflight-bundle.json'))),1)

    def test_artifact_staging_failure_still_records_error_and_retry_receipt(self):
        with mock.patch.object(submission,'_write_immutable',side_effect=OSError('disk staging failure SECRET-TOKEN')):
            first=self.submit()
        self.assertEqual(first['result'],'ERROR'); self.assertEqual(first['next_action'],'WAIT_FOR_RECOVERY')
        self.assertIn('OSError',first['message'])
        self.assertNotIn('SECRET-TOKEN',first['message'])
        self.assertIn('could not be staged',first['message'])
        self.assertIn('retry',first['required_change'])
        self.assertEqual(self.counts(),(0,0,0,1,1))
        self.assertEqual(self.submit(),first); self.assertEqual(self.counts(),(0,0,0,1,1))

    def test_error_message_and_recovery_guidance_are_bounded_and_sanitized(self):
        result=submission._failure_response('ERROR','failure\n'+('x'*3000),code='FAULT',
            evidence={'incident_id':'incident_test'},required_change='retry\n'+('y'*3000))
        self.assertLessEqual(len(result['message']),2000)
        self.assertLessEqual(len(result['required_change']),2000)
        self.assertNotIn('\n',result['message']+result['required_change'])

    def test_legacy_error_receipt_shape_remains_schema_compatible(self):
        workflow_store.validate({'contract_version':1,'status':'REJECTED','result':'ERROR',
            'next_action':'WAIT_FOR_RECOVERY','code':'OLD_ERROR','incident_ref':'incident_old'})

    def test_fail_receipt_keeps_actionable_finding(self):
        self.request['criteria'][0]['evidence_refs']=['artifact_missing']
        result=self.submit()
        finding=result['findings'][0]
        self.assertIn('Required evidence artifact is missing',finding['message'])
        self.assertTrue(finding['required_change'])
        self.assertNotEqual(finding['message'],'artifact_missing')

    def test_malformed_retry_is_idempotent_and_changed_payload_conflicts(self):
        raw='{"x":1,"x":2}'
        first=submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=raw,context=self.context,artifact_dir=self.artifacts)
        artifacts_before=len(self.store.submission_artifacts(caller=self.caller,idempotency_key=self.key))
        self.assertEqual(submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=raw,context=self.context,artifact_dir=self.artifacts),first)
        self.assertEqual(artifacts_before,len(self.store.submission_artifacts(caller=self.caller,idempotency_key=self.key)))
        with self.assertRaises(workflow_store.IdempotencyConflict): submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request='{"x":1,"x":3}',context=self.context,artifact_dir=self.artifacts)

    def test_wrong_key_and_wrong_author_retries_do_not_duplicate_diagnostics(self):
        wrong_key=dict(self.request,idempotency_key='body-key')
        first=submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=wrong_key,context=self.context,artifact_dir=self.artifacts)
        self.assertEqual(submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=wrong_key,context=self.context,artifact_dir=self.artifacts),first)
        db=self.store._connect()
        try: self.assertEqual(db.execute('select count(*) from artifacts').fetchone()[0],1)
        finally: db.close()
        self.store=workflow_store.WorkflowStore(self.root/'other.sqlite'); wrong_author=dict(self.request,author={'session_id':'other','worktree':str(self.repo),'role':'author'})
        one=submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=wrong_author,context=self.context,artifact_dir=self.artifacts)
        self.assertEqual(submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=wrong_author,context=self.context,artifact_dir=self.artifacts),one)
        db=self.store._connect()
        try: self.assertEqual(db.execute('select count(*) from artifacts').fetchone()[0],1)
        finally: db.close()

    def test_postcommit_reply_failure_recovers_accepted_receipt(self):
        original=self.store.submit
        def commit_then_lose_reply(**kwargs): original(**kwargs); raise RuntimeError('lost response')
        with mock.patch.object(self.store,'submit',side_effect=commit_then_lose_reply): result=self.submit()
        self.assertEqual(result['status'],'ACCEPTED'); self.assertEqual(self.counts()[:2],(1,1))

    def test_identical_malformed_requests_have_caller_scoped_artifacts(self):
        raw='{"duplicate":1,"duplicate":2}'
        first=submission.submit_submission(self.store,caller='caller-one',idempotency_key='same-key',request=raw,context=self.context,artifact_dir=self.artifacts)
        second=submission.submit_submission(self.store,caller='caller-two',idempotency_key='same-key',request=raw,context=self.context,artifact_dir=self.artifacts)
        self.assertEqual((first['result'],second['result']),('FAIL','FAIL'))
        db=self.store._connect()
        try:
            rows=db.execute('select artifact_json from artifacts order by id').fetchall()
            artifacts=[json.loads(row[0])['artifact'] for row in rows]
        finally: db.close()
        self.assertEqual(len(artifacts),2); self.assertNotEqual(artifacts[0]['artifact_id'],artifacts[1]['artifact_id'])
        self.assertEqual({a['producer_id'] for a in artifacts},{self.store.submission_id_for('caller-one','same-key'),self.store.submission_id_for('caller-two','same-key')})

    def test_accepted_receipt_never_crosses_trusted_author_or_worktree_binding(self):
        accepted=self.submit(); other_author={'session_id':'other','worktree':str(self.repo),'role':'author'}
        bad_author=preflight.PreflightContext(self.repo,other_author,self.policy,self.context.artifacts,self.receipt,self.context.publication_receipt_sha256)
        with self.assertRaises(workflow_store.ProtocolError): submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=self.request,context=bad_author,artifact_dir=self.artifacts)
        other_repo=self.root/'elsewhere'; other_repo.mkdir()
        other_worktree_author={'session_id':'sess','worktree':str(other_repo),'role':'author'}
        bad_repo=preflight.PreflightContext(other_repo,other_worktree_author,self.policy,self.context.artifacts,self.receipt,self.context.publication_receipt_sha256)
        with self.assertRaises(workflow_store.ProtocolError): submission.submit_submission(self.store,caller=self.caller,idempotency_key=self.key,request=self.request,context=bad_repo,artifact_dir=self.artifacts)
        self.assertEqual(self.store.get_submission(self.caller,self.key),accepted); self.assertEqual(self.counts()[:2],(1,1))

    def test_atomic_artifact_write_cleans_failed_temporary(self):
        target=self.artifacts/'atomic'/'file.json'; self.artifacts.mkdir(exist_ok=True)
        with mock.patch.object(submission.os,'fsync',side_effect=OSError('injected')):
            with self.assertRaises(OSError): submission._write_immutable(target,b'bytes')
        self.assertEqual(list(target.parent.glob('.workflow-evidence-*')),[])
        submission._write_immutable(target,b'bytes'); self.assertEqual(target.read_bytes(),b'bytes')


if __name__=='__main__': unittest.main()
