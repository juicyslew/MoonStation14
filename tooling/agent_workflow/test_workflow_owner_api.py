import tempfile
import unittest
from pathlib import Path

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_store
import workflow_owner
import test_workflow_owner as owner_fixture
import json
from unittest.mock import patch


class OwnerApiBindingTests(unittest.TestCase):
    def make_coordinator(self, root):
        store=workflow_store.WorkflowStore(Path(root)/'db.sqlite')
        coordinator=workflow_coordinator.ExplicitCoordinator(store,
            bindings={'author-token':workflow_coordinator.CoordinatorBinding('author',object())},
            review_bindings={'review-token':workflow_coordinator.ReviewerBinding('reviewer','tree','implementation')},
            owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner','C:/work/repo')},
            artifact_dir=root,identity_verifier=lambda _: (_ for _ in ()).throw(AssertionError('identity must not be queried')))
        coordinator.owner_service=type('Owner',(),{'decide':lambda self,*a,**kw:{'disposition':'ACCEPTED'}})()
        return coordinator

    def pass_app(self, root):
        fixture=owner_fixture.OwnerSnapshotHappyPath()
        case,store,receipt,_,sequence,event,_=fixture._pass_case(root)
        owner={'session_id':None,'worktree':str(case.repo.resolve()),'role':'owner'}
        coordinator=workflow_coordinator.ExplicitCoordinator(store,
            bindings={'author-token':workflow_coordinator.CoordinatorBinding('author',object())},
            review_bindings={'review-token':workflow_coordinator.ReviewerBinding('reviewer','tree','implementation')},
            owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(case.repo.resolve()))},
            artifact_dir=root/'artifacts',identity_verifier=lambda _: (_ for _ in ()).throw(AssertionError('owner API queried OpenCode')),
            owner_service=workflow_owner.OwnerService(store,snapshot_dir=root/'snapshots'))
        return coordinator.app(),case,store,receipt,sequence,event

    @staticmethod
    def post_decision(client, event, receipt, case, seq, *, command, action='ACCEPT', **extra):
        decision={'contract_version':1,'command_id':command,'run_id':receipt['run_id'],
            'evaluation_generation':1,'input_digest':case.request['input_digest'],
            'decision':action,'findings':[],'expected_sequence':seq+2}
        if action=='ACCEPT': decision['acceptance_id']='accept_'+command
        if action=='REQUEST_CHANGES':
            decision['findings']=[{'criterion_id':'C1','category':'IMPLEMENTATION','message':'change required',
                'location':'tracked.txt','evidence_refs':['artifact_evidence'],'required_change':'fix tracked behavior'}]
        decision.update(extra)
        return client.post('/v1/owner/decision',headers={'Authorization':'Bearer owner-token'},json={
            'decision':decision,'displayed_result_id':event['result_id'],
            'displayed_result_digest':event['result_digest']})

    def test_real_snapshot_accept_and_exact_retry_create_one_regression_job(self):
        with tempfile.TemporaryDirectory() as tmp:
            app,case,store,receipt,seq,event=self.pass_app(Path(tmp))
            client=TestClient(app)
            shown=client.get('/v1/owner/snapshot',params={'run_id':receipt['run_id']},headers={'Authorization':'Bearer owner-token'})
            self.assertEqual(200,shown.status_code)
            view=shown.json()
            self.assertEqual(event['result_id'],view['displayed_result_id'])
            self.assertEqual(event['result_digest'],view['displayed_result_digest'])
            self.assertEqual(case.request['manual_steps'],view['manual_steps'])
            self.assertTrue(any(x['type']=='BUILD' and x['path'] and x['sha256'] for x in view['artifacts']))
            for token in ('author-token','review-token'):
                response=client.post('/v1/owner/decision',headers={'Authorization':f'Bearer {token}'},json={})
                self.assertEqual(403,response.status_code)
            response=self.post_decision(client,event,receipt,case,seq,command='owner-api-accept')
            self.assertEqual(200,response.status_code,response.text)
            self.assertTrue(response.json()['accepted'])
            (Path(tmp)/'artifacts'/'source-snapshot.zip').unlink(missing_ok=True)
            replay=self.post_decision(client,event,receipt,case,seq,command='owner-api-accept')
            self.assertEqual(200,replay.status_code,replay.text)
            self.assertEqual(1,len(store.get_owner_decisions(receipt['run_id'])))
            decision=store.get_owner_decisions(receipt['run_id'])[0]
            self.assertEqual('QUEUED',store.get_regression_job(decision['regression_id'])['status'])

    def test_stale_result_digest_is_rejected_without_acceptance(self):
        with tempfile.TemporaryDirectory() as tmp:
            app,case,store,receipt,seq,event=self.pass_app(Path(tmp))
            client=TestClient(app)
            bad=dict(event,result_digest='0'*64)
            response=self.post_decision(client,bad,receipt,case,seq,command='stale-view')
            self.assertEqual(409,response.status_code)
            self.assertEqual([],store.get_owner_decisions(receipt['run_id']))
            self.assertIsNone(store.get_regression_job('task_regression_stale-view'))

    def test_feedback_actions_do_not_change_feature_pass_or_queue_regression(self):
        for action in ('REQUEST_CHANGES','CANCEL'):
            with self.subTest(action=action),tempfile.TemporaryDirectory() as tmp:
                app,case,store,receipt,seq,event=self.pass_app(Path(tmp))
                client=TestClient(app)
                response=self.post_decision(client,event,receipt,case,seq,command='owner-api-'+action.lower(),action=action)
                self.assertEqual(200,response.status_code,response.text)
                self.assertEqual('PASS',store.get_run(receipt['run_id'])['attempt']['result'])
                saved=store.get_owner_decisions(receipt['run_id'])
                self.assertEqual(1,len(saved)); self.assertEqual(action,saved[0]['decision'])
                self.assertIsNone(saved[0].get('regression_id'))

    def test_optional_owner_config_is_inert_and_rejects_token_or_caller_collisions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case=__import__('preflight_test_support').make_preflight_case(root,caller='cfg-author',key='cfg')
            context=root/'context.json'
            context.write_text(json.dumps({'repo':str(case.repo),'authenticated_author':case.context.authenticated_author,
                'policy':case.context.policy,'artifacts':case.context.artifacts,
                'publication_receipt_path':str(case.context.publication_receipt_path),
                'publication_receipt_sha256':case.context.publication_receipt_sha256}))
            (root/'author.token').write_text('author-token'); (root/'owner.token').write_text('owner-token')
            config={'database':'db.sqlite','artifact_dir':'artifacts','opencode_server':'http://127.0.0.1:4096',
                'bindings':[{'token_file':'author.token','caller':'author','context_file':'context.json'}],
                'owners':[{'token_file':'owner.token','caller':'human-owner','worktree':str(case.repo)}]}
            config_path=root/'coordinator.json'; config_path.write_text(json.dumps(config))
            with patch.object(workflow_coordinator,'configure_review_pipeline'):
                coordinator=workflow_coordinator.load_coordinator(config_path)
            self.assertEqual({'owner-token'},set(coordinator.owner_bindings))
            self.assertIsInstance(coordinator.owner_service,workflow_owner.OwnerService)
            self.assertIsNone(coordinator.owner_bindings['owner-token'].session_id)
            self.assertIsNone(coordinator._thread)
            for duplicate in ({'token_file':'author.token','caller':'other-owner','worktree':str(case.repo)},
                              {'token_file':'owner.token','caller':'author','worktree':str(case.repo)}):
                config['owners']=[{'token_file':'owner.token','caller':'human-owner','worktree':str(case.repo)},duplicate]
                config_path.write_text(json.dumps(config))
                with patch.object(workflow_coordinator,'configure_review_pipeline'):
                    with self.assertRaises(ValueError): workflow_coordinator.load_coordinator(config_path)

    def test_owner_routes_require_owner_capability_and_server_bind_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            app=self.make_coordinator(Path(tmp)).app()
            with TestClient(app) as client:
                for token in ('author-token','review-token','bad'):
                    response=client.get('/v1/owner/snapshot?run_id=run',headers={'Authorization':f'Bearer {token}'})
                    self.assertEqual(403,response.status_code)
                response=client.post('/v1/owner/decision',headers={'Authorization':'Bearer owner-token'},json={
                    'decision':{'contract_version':1,'command_id':'x','acceptance_id':'accept_x','run_id':'run',
                    'evaluation_generation':1,'input_digest':'x','decision':'REQUEST_CHANGES','findings':[],
                    'expected_sequence':1,'owner':{'role':'owner'}},'displayed_result_id':'r','displayed_result_digest':'d'})
                self.assertEqual(400,response.status_code)

    def test_successor_route_rejects_self_and_cross_worktree_grants(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            class FakeStore:
                def get_run(self,run_id):
                    return {'run':{'author':{'session_id':'old-session','worktree':str(root),'role':'author'}}}
                def authorize_author_successor(self,**kwargs):
                    raise AssertionError('invalid grants must not reach durable store')
            slot=workflow_coordinator.AuthorWorktreeBinding('author','token','context',str(root),{},'receipt','a'*64)
            coordinator=workflow_coordinator.ExplicitCoordinator(FakeStore(),
                bindings={},author_worktrees={'author-token':slot},
                owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(root))},
                artifact_dir=root,identity_verifier=lambda _:True)
            client=TestClient(coordinator.app())
            headers={'Authorization':'Bearer owner-token'}
            payload={'run_id':'run','evaluation_generation':1,'expected_sequence':2,
                     'successor_session_id':'old-session','command_id':'grant'}
            self.assertEqual(403,client.post('/v1/owner/authorize-successor',headers=headers,json=payload).status_code)
            other=workflow_coordinator.ExplicitCoordinator(FakeStore(),bindings={},author_worktrees={'author-token':slot},
                owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(root/'other'))},
                artifact_dir=root,identity_verifier=lambda _:True)
            self.assertEqual(403,TestClient(other.app()).post('/v1/owner/authorize-successor',headers=headers,json={**payload,'successor_session_id':'new-session'}).status_code)

    def test_successor_grant_is_owner_verified_audited_and_idempotent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); app,case,store,receipt,sequence,event=self.pass_app(root)
            # Configure a worktree author slot; the identity check must run for the
            # requested successor, not for the owner or the historical author.
            slot=workflow_coordinator.AuthorWorktreeBinding('author','author-token','context',
                str(case.repo.resolve()),{'required_packs':['unit'],'selectors':{},'review_roles':[],
                    'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':100},'receipt','a'*64)
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},
                author_worktrees={'author-token':slot},
                owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(case.repo.resolve()))},
                artifact_dir=root/'artifacts',identity_verifier=lambda context:
                    context.authenticated_author['session_id'] in ('successor-session','changed'))
            client=TestClient(coordinator.app())
            headers={'Authorization':'Bearer owner-token'}
            author_headers={'Authorization':'Bearer author-token','X-MS14-Author-Session':'successor-session'}
            denied=client.get('/v1/status',headers=author_headers,params={'run_id':receipt['run_id']})
            self.assertEqual(403,denied.status_code)
            payload={'run_id':receipt['run_id'],'evaluation_generation':1,'expected_sequence':sequence+2,
                'successor_session_id':'successor-session','command_id':'grant-successor'}
            response=client.post('/v1/owner/authorize-successor',headers=headers,json=payload)
            self.assertEqual(200,response.status_code,response.text)
            grant=response.json()['grant']
            self.assertEqual('successor-session',grant['successor_session_id'])
            self.assertEqual('sess',store.get_run(receipt['run_id'])['run']['author']['session_id'])
            visible=client.get('/v1/status',headers=author_headers,params={'run_id':receipt['run_id'],
                'include_diagnostics':'true'})
            self.assertEqual(200,visible.status_code,visible.text)
            self.assertEqual(receipt['run_id'],visible.json()['run']['run_id'])
            self.assertIn('diagnostics',visible.json())
            paged=client.get('/v1/status',headers=author_headers,params={'run_id':receipt['run_id'],
                'include_diagnostics':'true','log_artifact_id':'missing-log','log_offset':'0'})
            self.assertEqual(200,paged.status_code,paged.text)
            self.assertEqual(grant,client.post('/v1/owner/authorize-successor',headers=headers,json=payload).json()['grant'])
            bound=client.get('/v1/status',headers=author_headers)
            self.assertEqual(200,bound.status_code,bound.text)
            self.assertEqual(receipt['run_id'],bound.json()['author_successions'][0]['run_id'])
            self.assertEqual('HISTORY_ONLY',bound.json()['author_successions'][0]['continuation'])
            conflict=client.post('/v1/owner/authorize-successor',headers=headers,json={**payload,'successor_session_id':'changed'})
            self.assertEqual(409,conflict.status_code)
            self.assertEqual(grant,store.get_author_successor_grant(run_id=receipt['run_id'],successor_session_id='successor-session'))

    def test_successor_grant_rejects_stale_sequence_and_generation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); app,case,store,receipt,sequence,event=self.pass_app(root)
            slot=workflow_coordinator.AuthorWorktreeBinding('author','author-token','context',
                str(case.repo.resolve()),{'required_packs':['unit'],'selectors':{},'review_roles':[],
                    'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':100},'receipt','a'*64)
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={'author-token':slot},
                owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(case.repo.resolve()))},
                artifact_dir=root/'artifacts',identity_verifier=lambda _:True)
            client=TestClient(coordinator.app()); headers={'Authorization':'Bearer owner-token'}
            payload={'run_id':receipt['run_id'],'evaluation_generation':1,'expected_sequence':sequence+1,
                'successor_session_id':'successor-session','command_id':'stale-sequence'}
            self.assertEqual(409,client.post('/v1/owner/authorize-successor',headers=headers,json=payload).status_code)
            stale_generation={**payload,'evaluation_generation':2,'expected_sequence':sequence+2,
                'command_id':'stale-generation'}
            self.assertEqual(409,client.post('/v1/owner/authorize-successor',headers=headers,json=stale_generation).status_code)
            self.assertIsNone(store.get_author_successor_grant(run_id=receipt['run_id'],successor_session_id='successor-session'))

    def test_status_distinguishes_unretired_and_retired_error_heads(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); app,case,store,receipt,sequence,event=self.pass_app(root)
            run_id=receipt['run_id']; worktree=str(case.repo.resolve())
            db=store._connect()
            try:
                attempt=store.get_run(run_id)['attempt']; attempt['result']='ERROR'
                db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',
                    (workflow_store.canonical_json(attempt),run_id)); db.commit()
            finally: db.close()
            slot=workflow_coordinator.AuthorWorktreeBinding('author','author-token','context',worktree,
                {'required_packs':['unit'],'selectors':{},'review_roles':[],'owner_exceptions':[],
                 'config_overrides':[],'review_envelope_limit':100},'receipt','a'*64)
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={'author-token':slot},
                artifact_dir=root/'artifacts',identity_verifier=lambda _:True)
            client=TestClient(coordinator.app()); headers={'Authorization':'Bearer author-token','X-MS14-Author-Session':'sess'}
            pending=client.get('/v1/status',headers=headers,params={'run_id':run_id})
            self.assertEqual(200,pending.status_code,pending.text)
            self.assertEqual({'retired_head':False,'next_action':'WAIT_FOR_RECOVERY'},pending.json()['continuation'])
            db=store._connect()
            try:
                db.execute('BEGIN IMMEDIATE')
                store._event(db,{'contract_version':1,'event_id':'event_fixture_retired_error',
                    'kind':'OWNER_ERROR_HEAD_RETIRED','run_id':run_id,'evaluation_generation':1,
                    'reason':'CANCELLED','historical_result':'ERROR'},run_id,1)
                db.execute('DELETE FROM feature_heads WHERE worktree=? AND sprint=? AND run_id=?',
                    (worktree,store.get_run(run_id)['run']['sprint_id'],run_id))
                db.commit()
            finally: db.close()
            retired=client.get('/v1/status',headers=headers,params={'run_id':run_id})
            self.assertEqual(200,retired.status_code,retired.text)
            self.assertEqual({'retired_head':True,'next_action':'PREPARE_FRESH_SUBMISSION'},retired.json()['continuation'])

    def test_capability_overlap_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                workflow_coordinator.ExplicitCoordinator(workflow_store.WorkflowStore(Path(tmp)/'db'),
                    bindings={'same':workflow_coordinator.CoordinatorBinding('a',object())},
                    owner_bindings={'same':workflow_coordinator.OwnerBinding('o','tree')},artifact_dir=tmp,
                    identity_verifier=lambda _:True)


if __name__=='__main__': unittest.main()
