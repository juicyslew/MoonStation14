"""HTTP-level author succession and fresh-submission continuation coverage."""
import tempfile
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_run_control
import workflow_store
from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission
from starlette.testclient import TestClient


class AuthorSuccessorFlowTests(unittest.TestCase):
    def test_failed_head_successor_discovers_and_submits_replacement(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            case = make_preflight_case(root, caller='slot:old-session', key='first')
            base = case.policy
            floors = {'required_packs':['package','provenance'], 'selectors':{}, 'review_roles':['implementation'],
                      'owner_exceptions':[], 'config_overrides':[], 'review_envelope_limit':16000}
            store = workflow_store.WorkflowStore(root/'db.sqlite')
            slot = workflow_coordinator.AuthorWorktreeBinding('slot','author-token','context',str(case.repo.resolve()),
                floors,str(case.receipt),case.context.publication_receipt_sha256)
            coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={}, author_worktrees={'author-token':slot},
                owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(case.repo.resolve()))},
                artifact_dir=root/'artifacts', handlers={}, identity_verifier=lambda _: True)
            coordinator.run_control = workflow_run_control.ServiceRunControl(store, coordinator.notify_committed)
            client = TestClient(coordinator.app())
            old_headers = {'Authorization':'Bearer author-token','X-MS14-Author-Session':'old-session'}
            new_headers = {'Authorization':'Bearer author-token','X-MS14-Author-Session':'new-session'}
            scope, request = prepare_dynamic_author_submission(
                {'derived_caller':'slot:old-session','authenticated_author':{'session_id':'old-session','worktree':str(case.repo.resolve()),'role':'author'},
                 'repo':str(case.repo.resolve()),'typed_floors':floors}, base_policy=base, repo=case.repo,
                submit_key='first', criterion='first', artifact_id='artifact_first', artifact_path=root/'first.txt', request_path=root/'first.json')
            first_registration=client.post('/v1/scope/register',headers=old_headers,
                json={'idempotency_key':'scope-first','scope':scope})
            self.assertEqual('REGISTERED',first_registration.json()['response']['status'],first_registration.text)
            accepted=client.post('/v1/submit',headers=old_headers,json={'idempotency_key':'first','request':request}).json()['response']
            self.assertEqual('ACCEPTED',accepted['status']); old_run=accepted['run_id']
            # Seed a valid terminal result only in this isolated fixture (no live workflow).
            db=store._connect()
            try:
                attempt={'contract_version':1,'run_id':old_run,'generation':1,'phase':'DECIDED','result':'FAIL',
                    'closure':None,'proposed_result':None,'cleanup_state':'NOT_REQUIRED','pause_reason':None,'proposed_closure':None}
                db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),old_run))
                row=db.execute('SELECT id,task_json FROM tasks WHERE run_id=?',(old_run,)).fetchone()
                task=workflow_store.strict_loads(row[1]); task['state']='COMPLETED'; task['result']={'decision':'FAIL','findings':[],'artifact_refs':[]}
                db.execute('UPDATE tasks SET task_json=? WHERE id=?',(workflow_store.canonical_json(task),row[0])); db.commit()
            finally: db.close()
            self.assertEqual(403,client.get('/v1/status',headers=new_headers,params={'run_id':old_run}).status_code)
            before=store.get_run(old_run); run=before['run']
            grant=client.post('/v1/owner/authorize-successor',headers={'Authorization':'Bearer owner-token'},json={
                'run_id':old_run,'evaluation_generation':1,'expected_sequence':run['sequence'],
                'successor_session_id':'new-session','command_id':'grant-new'} )
            self.assertEqual(200,grant.status_code,grant.text)
            visible=client.get('/v1/status',headers=new_headers,params={'run_id':old_run,'include_diagnostics':'true'})
            self.assertEqual(200,visible.status_code,visible.text)
            self.assertEqual('old-session',visible.json()['run']['author']['session_id'])
            self.assertTrue(visible.json().get('author_successions'))
            self.assertIn('diagnostics',visible.json())
            self.assertEqual(404,client.get('/v1/scope/status',headers=new_headers,params={'idempotency_key':'scope-first'}).status_code)
            fresh, submission=prepare_dynamic_author_submission(
                {'derived_caller':'slot:new-session','authenticated_author':{'session_id':'new-session','worktree':str(case.repo.resolve()),'role':'author'},
                 'repo':str(case.repo.resolve()),'typed_floors':floors},base_policy=base,repo=case.repo,
                submit_key='successor-key',criterion='first',artifact_id='artifact_successor',
                artifact_path=root/'successor.txt',request_path=root/'successor.json')
            submission['replaces_run_id']=old_run
            registered=client.post('/v1/scope/register',headers=new_headers,json={'idempotency_key':'scope-successor','scope':fresh})
            self.assertEqual(200,registered.status_code,registered.text); self.assertEqual('REGISTERED',registered.json()['response']['status'])
            result=client.post('/v1/submit',headers=new_headers,json={'idempotency_key':'successor-key','request':submission})
            self.assertEqual(200,result.status_code,result.text); self.assertEqual('ACCEPTED',result.json()['response']['status'],result.text)
            self.assertNotEqual(old_run,result.json()['response']['run_id'])
            saved=store.get_run(old_run)['run']['author']
            self.assertEqual('old-session',saved['session_id'])

    def test_retired_error_successor_prepares_fresh_submission_without_replacement(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); case=make_preflight_case(root,caller='slot:old-session',key='first')
            floors={'required_packs':['package','provenance'],'selectors':{},'review_roles':['implementation'],
                'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':16000}
            store=workflow_store.WorkflowStore(root/'db.sqlite')
            slot=workflow_coordinator.AuthorWorktreeBinding('slot','author-token','context',str(case.repo.resolve()),
                floors,str(case.receipt),case.context.publication_receipt_sha256)
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={'author-token':slot},
                owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(case.repo.resolve()))},
                artifact_dir=root/'artifacts',handlers={},identity_verifier=lambda _:True)
            coordinator.run_control=workflow_run_control.ServiceRunControl(store,coordinator.notify_committed)
            client=TestClient(coordinator.app())
            old_headers={'Authorization':'Bearer author-token','X-MS14-Author-Session':'old-session'}
            new_headers={'Authorization':'Bearer author-token','X-MS14-Author-Session':'new-session'}
            old_context={'derived_caller':'slot:old-session','authenticated_author':{'session_id':'old-session',
                'worktree':str(case.repo.resolve()),'role':'author'},'repo':str(case.repo.resolve()),'typed_floors':floors}
            scope,request=prepare_dynamic_author_submission(old_context,base_policy=case.policy,repo=case.repo,
                submit_key='first',criterion='sprint',artifact_id='artifact_first',artifact_path=root/'first.txt',request_path=root/'first.json')
            self.assertEqual('REGISTERED',client.post('/v1/scope/register',headers=old_headers,
                json={'idempotency_key':'scope-first','scope':scope}).json()['response']['status'])
            accepted=client.post('/v1/submit',headers=old_headers,json={'idempotency_key':'first','request':request}).json()['response']
            self.assertEqual('ACCEPTED',accepted['status']); old_run=accepted['run_id']
            # This isolated fixture models a settled ERROR. Acknowledge its ready
            # dispatch so retirement's real outbox guard is exercised.
            db=store._connect()
            try:
                attempt={'contract_version':1,'run_id':old_run,'generation':1,'phase':'DECIDED','result':'ERROR',
                    'closure':None,'proposed_result':None,'cleanup_state':'CONFIRMED','pause_reason':None,'proposed_closure':None}
                db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',
                    (workflow_store.canonical_json(attempt),old_run))
                row=db.execute('SELECT id,task_json FROM tasks WHERE run_id=?',(old_run,)).fetchone()
                task=workflow_store.strict_loads(row[1]); task['state']='COMPLETED'
                task['result']={'decision':'ERROR','findings':[],'artifact_refs':[]}
                db.execute('UPDATE tasks SET task_json=? WHERE id=?',(workflow_store.canonical_json(task),row[0]))
                db.execute("UPDATE outbox SET state='ACKNOWLEDGED' WHERE event_id IN (SELECT id FROM events WHERE run_id=? AND kind='TASK_READY')",(old_run,))
                db.commit()
            finally: db.close()
            current=store.get_run(old_run)
            cancelled=client.post('/v1/cancel',headers={'Authorization':'Bearer owner-token'},json={
                'contract_version':1,'run_id':old_run,'evaluation_generation':1,'expected_sequence':current['run']['sequence'],
                'command_id':'retire-error','actor':'OWNER','reason':'CANCELLED'})
            self.assertEqual(200,cancelled.status_code,cancelled.text)
            self.assertEqual('RETIRED_HEAD',cancelled.json()['receipt']['state'],cancelled.text)
            current=store.get_run(old_run)
            grant=client.post('/v1/owner/authorize-successor',headers={'Authorization':'Bearer owner-token'},json={
                'run_id':old_run,'evaluation_generation':1,'expected_sequence':current['run']['sequence'],
                'successor_session_id':'new-session','command_id':'grant-error-successor'})
            self.assertEqual(200,grant.status_code,grant.text)
            status=client.get('/v1/status',headers=new_headers,params={'run_id':old_run})
            self.assertEqual(200,status.status_code,status.text)
            self.assertEqual('ERROR',status.json()['attempt']['result'])
            self.assertEqual('PREPARE_FRESH_SUBMISSION',status.json()['continuation']['next_action'])
            self.assertTrue(status.json()['continuation']['retired_head'])
            new_context={'derived_caller':'slot:new-session','authenticated_author':{'session_id':'new-session',
                'worktree':str(case.repo.resolve()),'role':'author'},'repo':str(case.repo.resolve()),'typed_floors':floors}
            fresh,new_request=prepare_dynamic_author_submission(new_context,base_policy=case.policy,repo=case.repo,
                submit_key='fresh-error-successor',criterion='sprint',artifact_id='artifact_error_successor',
                artifact_path=root/'error-successor.txt',request_path=root/'error-successor.json')
            self.assertNotIn('replaces_run_id',new_request)
            registered=client.post('/v1/scope/register',headers=new_headers,json={
                'idempotency_key':'scope-error-successor','scope':fresh})
            self.assertEqual(200,registered.status_code,registered.text)
            self.assertEqual('REGISTERED',registered.json()['response']['status'])
            submitted=client.post('/v1/submit',headers=new_headers,json={
                'idempotency_key':'fresh-error-successor','request':new_request})
            self.assertEqual(200,submitted.status_code,submitted.text)
            self.assertEqual('ACCEPTED',submitted.json()['response']['status'],submitted.text)
            self.assertNotEqual(old_run,submitted.json()['response']['run_id'])
            original=store.get_run(old_run)
            self.assertEqual('ERROR',original['attempt']['result'])
            self.assertEqual('old-session',original['run']['author']['session_id'])


if __name__ == '__main__': unittest.main()
