"""Public SDK regressions for bounded review envelopes and oversize admission."""
import asyncio
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import httpx
import workflow_coordinator
import workflow_store
import workflow_check_packs
import test_workflow_reviewer_api as api_fixture
import test_workflow_native_execution as native
import workflow_native_worker
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client
from workflow_coordinator_http_mcp import PROFILE_PATHS


class ReviewEnvelopeSDKTests(unittest.TestCase):
    def test_bounded_assignment_retains_inventory_for_public_paged_reads(self):
        async def scenario():
            fixture = api_fixture.ReviewerAPITests()
            original = native.make_preflight_case
            supplement_ids = []

            def large_case(*args, **kwargs):
                case = original(*args, **kwargs)
                for i in range(320):
                    data = (f'unreferenced artifact payload {i}\n').encode()
                    aid = 'artifact_supplement_' + str(i).zfill(3) + '_' + ('x' * 70)
                    path = case.repo.parent / ('supplement-' + str(i) + '.evidence')
                    path.write_bytes(data)
                    item = dict(case.artifact)
                    item.update(artifact_id=aid, path=str(path), sha256=hashlib.sha256(data).hexdigest(), size_bytes=len(data))
                    case.context.artifacts.append(item)
                    supplement_ids.append(aid)
                return case

            with patch.object(native, 'make_preflight_case', side_effect=large_case):
                fixture.setUp()
            try:
                assignment = fixture.planner.build_assignment(fixture.task['task_id'])
                body = assignment['body']
                raw = workflow_store.canonical_json(body).encode()
                self.assertLessEqual(len(raw), 16000)
                envrefs = body['envelope']['artifact_refs']
                self.assertIn(fixture.receipt, envrefs)
                request, _ = workflow_native_worker.load_native_inputs(fixture.store, fixture.run)
                primary = request['criteria'][0]['evidence_refs'][0]
                self.assertIn(primary, envrefs)
                self.assertFalse(set(supplement_ids) & set(envrefs))
                self.assertGreater(len(supplement_ids) * 90, 16000)
                digest = assignment['body_sha256']

                transport = __import__('workflow_review_transport').ReviewDispatchBackend(
                    fixture.planner, server='http://127.0.0.1:1234',
                    agents={'implementation': 'workflow-reviewer-implementation'},
                    request=lambda *args: fixture.fake_transport(*args))
                coordinator = workflow_coordinator.ExplicitCoordinator(
                    fixture.store, bindings={'author-token': object()}, review_bindings={'review-token': fixture.binding},
                    review_results=fixture.api, artifact_dir=fixture.root, identity_verifier=lambda _: True)
                coordinator.review_pipeline = SimpleNamespace(transport=transport)
                app = coordinator.app()

                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://127.0.0.1:18444', headers=headers)

                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444' + PROFILE_PATHS['reviewer'],
                            {'Authorization': 'Bearer review-token'}, httpx_client_factory=factory) as (r, w, _):
                        async with ClientSession(r, w) as client:
                            await client.initialize()
                            task = fixture.task
                            session = task['assigned_session_id']
                            async def result(call): return json.loads(call.content[0].text)
                            claimed = await result(await client.call_tool('claim_task', {'task_id':task['task_id'],'session_id':session}))
                            self.assertTrue(claimed.get('accepted'), claimed)
                            guideparts = []; offset = 0
                            while True:
                                guidepage = await result(await client.call_tool('status', {'task_id':task['task_id'],'session_id':session,'input_ref':'review_guide','input_offset':offset}))
                                ga = guidepage['input_access']; guideparts.append(ga['content'])
                                if ga['eof']:
                                    guide = json.loads(''.join(guideparts)); break
                                offset = ga['next_offset']
                            evidence = guide['evidence']
                            inventory = {row.get('metadata', {}).get('artifact_id') for row in evidence}
                            self.assertTrue(set(supplement_ids) <= inventory, (len(inventory), len(supplement_ids)))
                            target = supplement_ids[-1]
                            metadata = next(row['metadata'] for row in evidence if row.get('metadata', {}).get('artifact_id') == target)
                            self.assertNotIn(target, envrefs)
                            offset = 0; chunks = []
                            while True:
                                page = await result(await client.call_tool('status', {'task_id':task['task_id'],'session_id':session,'input_ref':target,'input_offset':offset}))
                                access = page['input_access']; chunks.append(access['content'].encode())
                                if access['eof']:
                                    raw_art = b''.join(chunks)
                                    self.assertEqual(access['sha256'], hashlib.sha256(raw_art).hexdigest())
                                    break
                                offset = access['next_offset']
                            self.assertEqual(raw_art, f'unreferenced artifact payload 319\n'.encode())
                            self.assertEqual(metadata['sha256'], hashlib.sha256(raw_art).hexdigest())
                            response = await result(await client.call_tool('status', {'task_id':task['task_id'],'session_id':session,'input_ref':'artifact_foreign_not_registered'}))
                            self.assertFalse(response.get('ok', True))
                            complete = {'task_id':task['task_id'],'decision':'PASS','findings':[],'owner_followups':[],
                                        'inspected_refs':[primary,target,fixture.receipt]}
                            done = await result(await client.call_tool('complete_review', {'result':complete,'session_id':session}))
                            self.assertTrue(done.get('accepted'), done)
                self.assertEqual(digest, fixture.planner.build_assignment(fixture.task['task_id'])['body_sha256'])
            finally:
                fixture.tearDown()

        asyncio.run(scenario())

    def test_public_author_submit_rejects_unfit_review_text_without_admission(self):
        from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission
        import workflow_coordinator as coordinator_module
        async def scenario():
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); case = make_preflight_case(root)
                base = case.policy
                floors = {'required_packs': sorted({p for packs in base['coverage'].values() for p in packs}),
                          'selectors': base['selector_requirements'], 'review_roles': base['review_roles'],
                          'owner_exceptions': base['owner_exceptions'], 'config_overrides': base['config_overrides'],
                          'review_envelope_limit': base['review_envelope_limit_bytes']}
                token = 'oversize-envelope-author'; sessions = {'oversize-session': {'id':'oversize-session','location':{'directory':str(case.repo)},'agent':'orchestrate-test'}}
                slot = coordinator_module.AuthorWorktreeBinding('slot', token, 'context', str(case.repo), floors, str(case.receipt), case.context.publication_receipt_sha256)
                verifier = coordinator_module.OpenCodeIdentityVerifier('local', session_lookup=lambda _s, sid:sessions.get(sid), role_lookup=lambda *_:{'mode':'primary'})
                store = workflow_store.WorkflowStore(root/'state.sqlite')
                coordinator = coordinator_module.ExplicitCoordinator(store, bindings={}, author_worktrees={token:slot}, artifact_dir=root/'artifacts', handlers={}, identity_verifier=verifier, reconcile_interval=30, admission_root=root/'admission')
                app = coordinator.app()
                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1:18445',headers={**(headers or {}),'Origin':'http://127.0.0.1:18445'})
                with patch.object(coordinator,'start',lambda:None):
                    async with app.router.lifespan_context(app):
                        async with streamablehttp_client('http://127.0.0.1:18445/mcp/author',{'Authorization':'Bearer '+token},httpx_client_factory=factory) as (r,w,_):
                            async with ClientSession(r,w) as client:
                                await client.initialize()
                                context = json.loads((await client.call_tool('status',{'session_id':'oversize-session'})).content[0].text)['author_context']
                                scope, request = prepare_dynamic_author_submission(context,base_policy=base,repo=case.repo,submit_key='oversize',criterion='c1',artifact_id='artifact_oversize',artifact_path=root/'evidence.txt',request_path=root/'request.json')
                                scope_path=root/'scope.json'; scope_path.write_text(json.dumps(scope),encoding='utf-8')
                                registered=json.loads((await client.call_tool('register_scope',{'scope_path':str(scope_path),'idempotency_key':'oversize-scope','session_id':'oversize-session'})).content[0].text)
                                self.assertEqual('REGISTERED',registered['response']['status'],registered)
                                request['manual_steps']=['x'*16000]
                                request['plan_digest']=workflow_store.digest(workflow_check_packs.resolve_check_packs(request,repo=case.repo,policy=scope['policy']))
                                reqpath=root/'oversize-request.json'; reqpath.write_text(json.dumps(request),encoding='utf-8')
                                submitted=json.loads((await client.call_tool('submit',{'request_path':str(reqpath),'idempotency_key':'oversize','session_id':'oversize-session'})).content[0].text)
                                response=submitted.get('response',{})
                                self.assertEqual('REJECTED',response.get('status'),submitted)
                                self.assertEqual('FAIL',response.get('result'),submitted)
                                self.assertIn('REVIEW_ENVELOPE_OVERSIZE',json.dumps(response),submitted)
                                db=store._connect()
                                try:
                                    self.assertEqual(0,db.execute('SELECT COUNT(*) FROM runs').fetchone()[0])
                                    self.assertEqual(0,db.execute('SELECT COUNT(*) FROM tasks').fetchone()[0])
                                finally: db.close()
        asyncio.run(scenario())


if __name__ == '__main__': unittest.main()
