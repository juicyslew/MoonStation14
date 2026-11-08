import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import workflow_coordinator
import workflow_store
from preflight_test_support import make_preflight_case


class ScopeServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.root=Path(self.temp.name)
        self.case=make_preflight_case(self.root,caller='author-a',key='base')
        self.store=workflow_store.WorkflowStore(self.root/'scope.sqlite')
        self.binding=workflow_coordinator.CoordinatorBinding('author-a',self.case.context)
        self.coordinator=workflow_coordinator.ExplicitCoordinator(self.store,bindings={'token-a':self.binding},
            artifact_dir=self.root/'artifacts',handlers={},identity_verifier=lambda context:True,reconcile_interval=30)
        from starlette.testclient import TestClient
        self.client=TestClient(self.coordinator.app()); self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None,None,None); self.temp.cleanup()

    def feature(self,label,key,artifact_id):
        path=self.root/(label+'-evidence.txt'); data=(label+' fresh evidence').encode(); path.write_bytes(data)
        producer='submit_'+hashlib.sha256(('author-a\0submit\0'+key).encode()).hexdigest()[:24]
        artifact={'contract_version':1,'artifact_id':artifact_id,'run_id':None,'producer_task_id':None,'type':'SOURCE_MANIFEST',
            'path':str(path),'sha256':hashlib.sha256(data).hexdigest(),'size_bytes':len(data),'immutable':True,
            'producer_kind':'SUBMISSION','producer_id':producer,'submission_id':producer}
        coverage={label+' criterion':['package','provenance']}
        policy=copy.deepcopy(self.case.policy); policy.update(sprint_id=label,criteria=[label+' criterion'],coverage=coverage)
        policy['review_summaries']['implementation']='Review '+label
        request=copy.deepcopy(self.case.request); request.update(idempotency_key=key,sprint_id=label,policy_digest=workflow_store.digest(policy),
            criteria=[{'criterion_id':label+' criterion','status':'COMPLETE','evidence_refs':[artifact_id]}])
        from workflow_check_packs import resolve_check_packs
        request['plan_digest']=workflow_store.digest(resolve_check_packs(request,repo=self.case.repo,policy=policy))
        payload={'contract_version':1,'policy':policy,'artifacts':[artifact],'submission_idempotency_key':key}
        return payload,request,path

    def register(self,payload,key='registration'):
        return self.client.post('/v1/scope/register',headers={'Authorization':'Bearer token-a'},json={'idempotency_key':key,'scope':payload})

    def submit(self,request):
        return self.client.post('/v1/submit',headers={'Authorization':'Bearer token-a'},json={'idempotency_key':request['idempotency_key'],'request':request})

    def test_register_submit_two_features_without_context_reload(self):
        run_ids=[]
        for label,key,artifact_id in [('feature-a','submission-a','artifact_a'),('feature-b','submission-b','artifact_b')]:
            payload,request,source_path=self.feature(label,key,artifact_id)
            registered=self.register(payload,'register-'+label)
            self.assertEqual(registered.status_code,200,registered.text)
            self.assertEqual(registered.json()['response']['status'],'REGISTERED')
            self.assertEqual(self.register(payload,'register-'+label).json(),registered.json())
            response=self.submit(request)
            self.assertEqual(response.status_code,200,response.text)
            self.assertEqual(response.json()['response']['status'],'ACCEPTED')
            run_ids.append(response.json()['response']['run_id'])
            run=self.store.get_run(response.json()['response']['run_id'])
            self.assertEqual(run['run']['sprint_id'],label)
            self.assertEqual(run['run']['policy_digest'],payload['policy'] and workflow_store.digest(payload['policy']))
            accepted=self.store.get_run_submission(run['run']['run_id'])
            saved_artifact=next(row['artifact'] for row in accepted['artifacts'] if row['artifact']['artifact_id']==artifact_id)
            self.assertNotEqual(saved_artifact['path'],str(source_path))
            self.assertEqual(hashlib.sha256(Path(saved_artifact['path']).read_bytes()).hexdigest(),saved_artifact['sha256'])
            self.assertEqual(accepted['request']['criteria'][0]['criterion_id'],label+' criterion')
        self.assertEqual(len(set(run_ids)),2)
        self.assertTrue(all(self.store.list_tasks(run_id,1)[0]['kind']=='CHECK' and self.store.list_tasks(run_id,1)[0]['state']=='QUEUED' for run_id in run_ids))

    def test_missing_or_mismatched_scope_fails_without_run(self):
        payload,request,_=self.feature('feature-a','unregistered','artifact_missing')
        response=self.submit(request)
        self.assertEqual(response.status_code,200)
        self.assertEqual(response.json()['response']['result'],'FAIL')
        self.assertEqual(response.json()['response']['code'],'SCOPE_NOT_REGISTERED')
        self.assertIsNone(response.json()['response'].get('run_id'))
        original=response.json()['response']
        self.register(payload,'after-rejected-submit')
        with mock.patch('workflow_scope.effective_context',side_effect=AssertionError('cached rejection must not resolve scope')):
            self.assertEqual(self.submit(request).json()['response'],original)
        mismatch_payload,mismatch_request,_=self.feature('feature-b','mismatch-key','artifact_mismatch')
        self.register(mismatch_payload,'registration')
        changed=copy.deepcopy(mismatch_request); changed['sprint_id']='other-sprint'
        response=self.submit(changed)
        self.assertEqual(response.json()['response']['code'],'SCOPE_MISMATCH')
        self.assertIsNone(response.json()['response'].get('run_id'))

    def test_original_artifact_drift_after_register_does_not_change_frozen_evidence(self):
        payload,request,path=self.feature('feature-a','drift-key','artifact_drift')
        original=path.read_bytes()
        self.register(payload,'register-drift')
        registered=self.store.get_author_scope(caller='author-a',submission_key='drift-key')
        archived_path=Path(registered['artifacts'][0]['path'])
        path.write_text('tampered')
        response=self.submit(request)
        self.assertEqual(response.json()['response']['status'],'ACCEPTED')
        self.assertEqual(archived_path.read_bytes(),original)

    def test_registered_archive_tamper_is_rejected_without_run(self):
        payload,request,_=self.feature('feature-a','archive-tamper-key','artifact_archive_tamper')
        self.register(payload,'register-archive-tamper')
        registered=self.store.get_author_scope(caller='author-a',submission_key='archive-tamper-key')
        archived_path=Path(registered['artifacts'][0]['path'])
        archived_path.write_text('tampered archive')
        response=self.submit(request)
        self.assertEqual(response.json()['response']['result'],'FAIL')
        self.assertEqual(response.json()['response']['code'],'ARTIFACT_DRIFT')
        self.assertIsNone(response.json()['response'].get('run_id'))

    def test_scope_identity_status_hold_and_cached_submit_receipt(self):
        payload,request,_=self.feature('feature-a','cached-key','artifact_cached')
        registration=self.register(payload,'register-cached').json()['response']
        accepted=self.submit(request).json()['response']
        self.coordinator.admission_root=self.root/'admission'
        with mock.patch('admission.maintenance',return_value={'note':'held'}):
            fresh_payload,fresh_request,_=self.feature('feature-c','new-submit','artifact_c')
            self.assertEqual(self.client.post('/v1/scope/register',headers={'Authorization':'Bearer token-a'},json={'idempotency_key':'new','scope':fresh_payload}).status_code,503)
            self.assertEqual(self.submit(fresh_request).status_code,503)
            self.assertIsNone(self.store.get_submission('author-a','new-submit'))
            replay=self.register(payload,'register-cached')
            self.assertEqual(replay.json()['response'],registration)
            # Receipt replay is checked before the hold and before registered scope resolution.
            with mock.patch('workflow_scope.effective_context',side_effect=AssertionError('cached submit must not resolve scope')):
                self.assertEqual(self.submit(request).json()['response'],accepted)
            query=self.client.get('/v1/scope/status?idempotency_key=register-cached',headers={'Authorization':'Bearer token-a'})
            self.assertEqual(query.json()['response'],registration)
        self.coordinator.bindings['token-b']=workflow_coordinator.CoordinatorBinding('author-b',self.case.context)
        isolated=self.client.get('/v1/scope/status?idempotency_key=register-cached',headers={'Authorization':'Bearer token-b'})
        self.assertEqual(isolated.status_code,404)

    def test_scope_schema_and_identity_fail_before_write(self):
        bad=self.client.post('/v1/scope/register',headers={'Authorization':'Bearer token-a'},content='{"idempotency_key":"x","idempotency_key":"y","scope":{}}')
        self.assertEqual(bad.status_code,400)
        payload,_,_=self.feature('feature-a','scope-key','artifact_scope')
        self.coordinator.identity_verifier=lambda context:False
        denied=self.register(payload,'denied')
        self.assertEqual(denied.status_code,503)
        self.assertIsNone(self.store.get_author_scope(caller='author-a',submission_key='scope-key'))

    def test_validation_failure_receipt_is_recoverable_and_key_bound(self):
        payload,_,_=self.feature('feature-a','scope-key','artifact_scope')
        payload['artifacts'][0]['producer_id']='submit_'+'0'*24
        failed=self.register(payload,'same-registration')
        self.assertEqual(failed.status_code,200)
        receipt=failed.json()['response']
        self.assertEqual((receipt['status'],receipt['result'],receipt['code']),('REJECTED','FAIL','ARTIFACT_PROVENANCE'))
        self.assertEqual(receipt['payload_digest'],workflow_store.digest(payload))
        self.assertEqual(self.register(payload,'same-registration').json()['response'],receipt)
        status=self.client.get('/v1/scope/status?idempotency_key=same-registration',headers={'Authorization':'Bearer token-a'})
        self.assertEqual(status.json()['response'],receipt)
        changed=copy.deepcopy(payload); changed['policy']['criteria']=['changed criterion']
        conflict=self.register(changed,'same-registration')
        self.assertEqual(conflict.status_code,409)
        self.assertIsNone(self.store.get_author_scope(caller='author-a',submission_key='scope-key'))

    def test_ambiguous_registration_recovers_by_same_status_key(self):
        payload,_,_=self.feature('feature-a','scope-key','artifact_scope')
        original=workflow_coordinator.workflow_scope.register_author_scope
        def commit_then_lose_reply(*args,**kwargs):
            original(*args,**kwargs)
            raise RuntimeError('fixture lost response after registration commit')
        with mock.patch('workflow_coordinator.workflow_scope.register_author_scope',side_effect=commit_then_lose_reply):
            uncertain=self.register(payload,'same-key')
        self.assertEqual(uncertain.status_code,503)
        recovered=self.client.get('/v1/scope/status?idempotency_key=same-key',headers={'Authorization':'Bearer token-a'})
        self.assertEqual(recovered.status_code,200)
        self.assertEqual(recovered.json()['response']['status'],'REGISTERED')
        self.assertEqual(recovered.json()['response']['payload_digest'],workflow_store.digest(payload))

    def test_noninteger_scope_versions_fail_durably_and_integer_one_registers(self):
        invalid=[True,False,1.0,'1']
        for index,value in enumerate(invalid):
            with self.subTest(version=value):
                payload,_,_=self.feature('feature-version-'+str(index),'version-submit-'+str(index),'artifact_version_'+str(index))
                payload['contract_version']=value
                registration_key='version-registration-'+str(index)
                result=self.register(payload,registration_key)
                self.assertEqual(result.status_code,200,result.text)
                receipt=result.json()['response']
                self.assertEqual((receipt['status'],receipt['result'],receipt['code'],receipt['field']),
                    ('REJECTED','FAIL','SCOPE_INPUT','contract_version'))
                self.assertEqual(len(receipt['payload_digest']),64)
                status=self.client.get('/v1/scope/status',params={'idempotency_key':registration_key},headers={'Authorization':'Bearer token-a'})
                self.assertEqual(status.json()['response'],receipt)
                self.assertIsNone(self.store.get_author_scope(caller='author-a',submission_key='version-submit-'+str(index)))
        missing,_,_=self.feature('feature-version-missing','version-submit-missing','artifact_version_missing')
        missing.pop('contract_version')
        response=self.register(missing,'version-registration-missing')
        self.assertEqual(response.json()['response']['result'],'FAIL')
        self.assertEqual(response.json()['response']['code'],'SCOPE_INPUT')
        # A corrected value cannot reuse the durable FAIL command key, but a fresh
        # registration command for the same intended submission key can succeed.
        valid,_,_=self.feature('feature-version-valid','version-submit-valid','artifact_version_valid')
        invalid_copy=copy.deepcopy(valid); invalid_copy['contract_version']=True
        bad=self.register(invalid_copy,'version-registration-immutable')
        self.assertEqual(bad.json()['response']['result'],'FAIL')
        corrected=self.register(valid,'version-registration-immutable')
        self.assertEqual(corrected.status_code,409)
        accepted=self.register(valid,'version-registration-corrected')
        self.assertEqual(accepted.status_code,200,accepted.text)
        self.assertEqual(accepted.json()['response']['status'],'REGISTERED')
        snapshot=workflow_store.WorkflowStore.readonly_snapshot(self.store.path)
        self.assertEqual(snapshot['records'],[])


if __name__=='__main__': unittest.main()
