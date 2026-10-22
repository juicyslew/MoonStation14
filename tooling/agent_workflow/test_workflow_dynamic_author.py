import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import workflow_coordinator_mcp as mcp
import workflow_scope
import workflow_coordinator
import workflow_store
import workflow_coordinator_bindings as bindings
from starlette.testclient import TestClient


class DynamicAuthorMcpTests(unittest.TestCase):
    def test_session_is_required_forwarded_only_as_header(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            request = root / 'request.json'
            request.write_text('{"contract_version":1}', encoding='utf-8')
            captured = {}

            def transport(url, data, headers, timeout):
                captured.update(url=url, data=data, headers=headers)
                return 200, b'{"response":{"status":"ACCEPTED"}}'

            client = mcp.CoordinatorClient('http://127.0.0.1:1', 'token', root,
                transport=transport, author_mode='worktree')
            tool = next(item for item in client.tools() if item['name'] == 'submit')
            self.assertIn('session_id', tool['inputSchema']['required'])
            missing = client.call('submit', {'request_path': str(request), 'idempotency_key': 'k'})
            self.assertFalse(missing['ok'])
            self.assertEqual(missing['code'], 'INVALID_TOOL_ARGUMENTS')
            self.assertEqual(missing['field'], 'session_id')
            self.assertEqual(captured, {})
            client.call('submit', {'request_path': str(request), 'idempotency_key': 'k', 'session_id': 'sid'})
            self.assertEqual(captured['headers']['X-MS14-Author-Session'], 'sid')
            self.assertNotIn('session_id', json.loads(captured['data']))
            ack={'contract_version':1,'command_id':'cmd','event_id':'event','run_id':'run',
                'evaluation_generation':1,'result_id':'result','result_digest':'a'*64,'session_id':'sid'}
            client.call('acknowledge_result',ack)
            self.assertEqual(captured['headers']['X-MS14-Author-Session'],'sid')
            self.assertNotIn('session_id',json.loads(captured['data']))

    def test_dynamic_status_without_run_or_key_is_allowed(self):
        captured = {}
        def transport(url, data, headers, timeout):
            captured.update(url=url, data=data, headers=headers)
            return 200, b'{"author_context":{}}'
        client = mcp.CoordinatorClient('http://127.0.0.1:1', 'token', '.', transport=transport, author_mode='worktree')
        client.call('status', {'session_id': 'sid'})
        self.assertTrue(captured['url'].endswith('/v1/status?'))
        self.assertEqual(captured['headers']['X-MS14-Author-Session'], 'sid')


class WorktreeFloorTests(unittest.TestCase):
    def test_floor_schema_is_strict_and_normalizes_existing_comparison_shape(self):
        floors = {'required_packs': ['unit'], 'selectors': {'server': ['focused']},
                  'review_roles': ['implementation'], 'owner_exceptions': [],
                  'config_overrides': [], 'review_envelope_limit': 100}
        parsed = workflow_scope.validate_worktree_floors(floors)
        self.assertEqual(parsed['required_packs'], {'unit'})
        self.assertEqual(parsed['selectors'], {'server': {'focused'}})
        self.assertEqual(workflow_scope.validate_worktree_floors({**floors,'required_packs':['package','provenance']})['required_packs'],
                         {'package','provenance'})
        with self.assertRaisesRegex(ValueError,'unique nonempty'):
            workflow_scope.validate_worktree_floors({**floors,'required_packs':[]})
        with self.assertRaisesRegex(ValueError,'unknown check-pack'):
            workflow_scope.validate_worktree_floors({**floors,'required_packs':['not-a-pack']})
        with self.assertRaises(ValueError):
            workflow_scope.validate_worktree_floors({**floors, 'policy': {}})
        with self.assertRaises(ValueError):
            workflow_scope.validate_worktree_floors({**floors, 'review_envelope_limit': True})

    def test_empty_configured_floor_rejects_before_token_receipt_or_database_access(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); context=root/'context.json'
            context.write_text(json.dumps({'repo':str(root),'floors':{'required_packs':[],'selectors':{},
                'review_roles':[],'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':1024},
                'publication_receipt_path':str(root/'missing-receipt'),'publication_receipt_sha256':'a'*64}))
            config=root/'coordinator.json'
            config.write_text(json.dumps({'database':str(root/'missing.sqlite'),'artifact_dir':str(root/'artifacts'),
                'opencode_server':'http://127.0.0.1:4096','bindings':[],
                'author_worktrees':[{'token_file':str(root/'missing-token'),'caller':'slot','context_file':str(context)}]}))
            with self.assertRaisesRegex(ValueError,'required_packs must contain unique nonempty strings'):
                workflow_coordinator.load_coordinator(config)
            self.assertFalse((root/'missing.sqlite').exists())

    def test_per_call_status_context_isolated_by_verified_session(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); repo=root/'repo'; repo.mkdir()
            floors={'required_packs':['unit'],'selectors':{'server':['focused']},'review_roles':['implementation'],
                'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':100}
            slot=workflow_coordinator.AuthorWorktreeBinding('slot','token','context',str(repo),floors,str(root/'receipt'),'a'*64)
            identities=[]
            def verify(context):
                identities.append(context.authenticated_author['session_id'])
                return context.authenticated_author['session_id'] in ('chat-a','chat-b')
            coordinator=workflow_coordinator.ExplicitCoordinator(workflow_store.WorkflowStore(root/'db.sqlite'),
                bindings={},author_worktrees={'token':slot},artifact_dir=root/'artifacts',handlers={},identity_verifier=verify)
            with TestClient(coordinator.app()) as client:
                for sid in ('chat-a','chat-b'):
                    response=client.get('/v1/status',headers={'Authorization':'Bearer token','X-MS14-Author-Session':sid})
                    self.assertEqual(response.status_code,200,response.text)
                    self.assertEqual(response.json()['author_context']['derived_caller'],'slot:'+sid)
                    self.assertEqual(response.json()['author_context']['typed_floors']['required_packs'],['unit'])
                denied=client.get('/v1/status',headers={'Authorization':'Bearer token','X-MS14-Author-Session':'chat-invalid'})
                self.assertEqual(denied.status_code,503)
            self.assertEqual(identities,['chat-a','chat-b','chat-invalid'])

    def test_existing_identity_verifier_rejects_wrong_directory_agent_and_role(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); repo=root/'repo'; repo.mkdir(); other=root/'other'; other.mkdir()
            sessions={
                'wrong-directory':{'id':'wrong-directory','directory':str(other),'agent':'orchestrate-ms14'},
                'wrong-agent':{'id':'wrong-agent','directory':str(repo),'agent':'workflow-reviewer-implementation'},
                'wrong-role':{'id':'wrong-role','directory':str(repo),'agent':'orchestrate-ms14'},
                'unknown':None,
            }
            verifier=workflow_coordinator.OpenCodeIdentityVerifier('loopback',
                session_lookup=lambda server,sid:sessions.get(sid),
                role_lookup=lambda server,agent,directory:{'mode':'subagent'})
            floors={'required_packs':['package','provenance'],'selectors':{},'review_roles':['implementation'],
                'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':100}
            slot=workflow_coordinator.AuthorWorktreeBinding('slot','token','context',str(repo),floors,str(root/'receipt'),'a'*64)
            store=workflow_store.WorkflowStore(root/'db.sqlite')
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={'token':slot},
                artifact_dir=root/'artifacts',handlers={},identity_verifier=verifier)
            with TestClient(coordinator.app()) as client:
                ack={'contract_version':1,'command_id':'cmd','event_id':'event','run_id':'run',
                    'evaluation_generation':1,'result_id':'result','result_digest':'a'*64}
                with mock.patch.object(store,'acknowledge_delivery') as acknowledge:
                    for sid in sessions:
                        headers={'Authorization':'Bearer token','X-MS14-Author-Session':sid}
                        denied=client.get('/v1/status',headers=headers)
                        self.assertEqual(denied.status_code,503,sid)
                        self.assertIsNone(store.get_submission('slot:'+sid,'key'))
                        ack_denied=client.post('/v1/acknowledge-result',headers=headers,json=ack)
                        self.assertEqual(ack_denied.status_code,503,sid)
                        self.assertEqual(ack_denied.json()['error']['code'],'IDENTITY_UNAVAILABLE',sid)
                    missing_session=client.post('/v1/acknowledge-result',
                        headers={'Authorization':'Bearer token'},json=ack)
                    self.assertEqual(missing_session.status_code,403)
                    self.assertEqual(missing_session.json()['error']['code'],'AUTHOR_SESSION_REQUIRED')
                    acknowledge.assert_not_called()

    def test_binding_plan_uses_worktree_slot_without_pinning_session(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); token=root/'author.token'; token.write_text('secret')
            receipt=root/'receipt'; receipt.write_text('{}')
            floors={'required_packs':['package','provenance'],'selectors':{},'review_roles':[],'owner_exceptions':[],
                'config_overrides':[],'review_envelope_limit':100}
            context=root/'context.json'; context.write_text(json.dumps({'repo':str(root),'floors':floors,
                'publication_receipt_path':str(receipt),'publication_receipt_sha256':__import__('hashlib').sha256(receipt.read_bytes()).hexdigest()}))
            coordinator={'bindings':[],'author_worktrees':[{'caller':'slot','token_file':str(token),'context_file':str(context)}]}
            roles=bindings._normalise_roles(coordinator,[{'role':'author','binding_mode':'worktree','caller':'slot',
                'token_file':str(token),'context_file':str(context)}],root,root)
            merged=bindings.merge_bindings({'agents':{'orchestrate-ms14':{'permissions':[]}},'mcp':{'servers':{}}},roles,
                repo=str(root),endpoint='http://127.0.0.1:1',python='python',adapter='adapter')
            command=merged['mcp']['servers']['ms14_workflow']['command']
            self.assertIn('--author-mode',command)
            self.assertEqual(command[command.index('--author-mode')+1],'worktree')
            self.assertNotIn('session_id',roles[0])

    def test_two_sessions_register_and_submit_same_keys_in_isolated_namespaces(self):
        import copy
        from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); case=make_preflight_case(root)
            base_policy=case.policy
            floors={'required_packs':sorted({p for packs in base_policy['coverage'].values() for p in packs}),
                'selectors':base_policy['selector_requirements'],'review_roles':base_policy['review_roles'],
                'owner_exceptions':base_policy['owner_exceptions'],'config_overrides':base_policy['config_overrides'],
                'review_envelope_limit':base_policy['review_envelope_limit_bytes']}
            slot=workflow_coordinator.AuthorWorktreeBinding('slot','token','context',str(case.repo),floors,
                str(case.receipt),case.context.publication_receipt_sha256)
            store=workflow_store.WorkflowStore(root/'isolated.sqlite')
            holdroot=root/'exchange'; holdroot.mkdir(); identity_calls=[]
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={'token':slot},
                artifact_dir=root/'artifacts',handlers={},identity_verifier=lambda context:identity_calls.append(context.authenticated_author['session_id']) or True,
                admission_root=holdroot)
            with TestClient(coordinator.app()) as client:
                runs=[]; replay_args=None; registrations={}; discovered={}; submit_key='same-submit-key'
                for sid,criterion,artifact_id in (('chat-a','criterion-a','artifact_a'),('chat-b','criterion-b','artifact_b')):
                    headers={'Authorization':'Bearer token','X-MS14-Author-Session':sid}
                    discovery=client.get('/v1/status',headers=headers)
                    self.assertEqual(discovery.status_code,200,discovery.text)
                    context=discovery.json()['author_context']
                    self.assertIsNone(discovery.json()['run']); self.assertIsNone(discovery.json()['submission'])
                    self.assertEqual(set(context),{'derived_caller','authenticated_author','repo','typed_floors',
                        'publication_receipt_path','publication_receipt_sha256'})
                    self.assertFalse({'token','artifacts','artifact_inventory','secrets'} & set(context))
                    self.assertEqual(context['authenticated_author']['session_id'],sid)
                    self.assertIsNone(store.get_submission(context['derived_caller'],submit_key))
                    discovered[sid]=context
                    key=submit_key
                    scope,request=prepare_dynamic_author_submission(context,base_policy=base_policy,repo=case.repo,
                        submit_key=key,criterion=criterion,artifact_id=artifact_id,artifact_path=root/(sid+'.txt'),
                        request_path=root/(sid+'-request.json'))
                    registered=client.post('/v1/scope/register',headers=headers,json={'idempotency_key':'same-registration-key',
                        'scope':scope})
                    self.assertEqual(registered.status_code,200,registered.text)
                    self.assertEqual(registered.json()['response']['status'],'REGISTERED',registered.text)
                    registrations[sid]=registered.json()['response']
                    accepted=client.post('/v1/submit',headers=headers,json={'idempotency_key':key,'request':request})
                    self.assertEqual(accepted.status_code,200,accepted.text)
                    self.assertEqual(accepted.json()['response']['status'],'ACCEPTED',accepted.text)
                    runs.append(accepted.json()['response']['run_id'])
                    replay_args=(headers,request,accepted.json()['response'])
                self.assertEqual(len(set(runs)),2)
                foreign_run=client.get('/v1/status',headers={'Authorization':'Bearer token','X-MS14-Author-Session':'chat-a'},
                    params={'run_id':runs[1]})
                self.assertEqual(foreign_run.status_code,403)
                own_scope=client.get('/v1/scope/status',headers={'Authorization':'Bearer token','X-MS14-Author-Session':'chat-a'},
                    params={'idempotency_key':'same-registration-key'})
                self.assertEqual(own_scope.status_code,200)
                self.assertEqual(own_scope.json()['response'],registrations['chat-a'])
                self.assertNotEqual(own_scope.json()['response'],registrations['chat-b'])
                ack_capture={}
                def acknowledge(ack,*,authenticated_session):
                    ack_capture.update(ack=ack,authenticated_session=authenticated_session)
                    return {'disposition':'ACKNOWLEDGED'}
                ack={'contract_version':1,'command_id':'ack-command','event_id':'event-a','run_id':runs[0],
                    'evaluation_generation':1,'result_id':'result-a','result_digest':'a'*64}
                with mock.patch.object(store,'acknowledge_delivery',side_effect=acknowledge):
                    ack_response=client.post('/v1/acknowledge-result',headers={'Authorization':'Bearer token',
                        'X-MS14-Author-Session':'chat-a'},json=ack)
                    self.assertEqual(ack_response.status_code,200,ack_response.text)
                    self.assertEqual(ack_capture['ack']['author_session_id'],'chat-a')
                    self.assertEqual(ack_capture['authenticated_session'],'chat-a')
                    spoof=client.post('/v1/acknowledge-result',headers={'Authorization':'Bearer token',
                        'X-MS14-Author-Session':'chat-a'},json={**ack,'author_session_id':'chat-b'})
                    self.assertEqual(spoof.status_code,400)
                headers,request,receipt=replay_args
                conflict_request=copy.deepcopy(request); conflict_request['feature_label']='different payload'
                before_conflict=len(identity_calls)
                conflict=client.post('/v1/submit',headers=headers,json={'idempotency_key':request['idempotency_key'],'request':conflict_request})
                self.assertEqual(conflict.status_code,409)
                self.assertEqual(len(identity_calls),before_conflict+1,'idempotency conflicts are also identity-verified before disclosure')
                (holdroot/'gate-maintenance.json').write_text('{')
                prior_identity_calls=len(identity_calls)
                replay=client.post('/v1/submit',headers=headers,json={'idempotency_key':request['idempotency_key'],'request':request})
                self.assertEqual(replay.json()['response'],receipt)
                self.assertEqual(len(identity_calls),prior_identity_calls+1,'cached receipt is returned only after re-verifying its author')
                new_request=copy.deepcopy(request); new_request['idempotency_key']='new-held-key'
                held=client.post('/v1/submit',headers=headers,json={'idempotency_key':'new-held-key','request':new_request})
                self.assertEqual(held.status_code,503)
                self.assertEqual(held.json()['error']['code'],'INTAKE_HELD')
                self.assertEqual(len(identity_calls),prior_identity_calls+1,'new held intake is rejected before OpenCode identity lookup')


if __name__ == '__main__':
    unittest.main()
