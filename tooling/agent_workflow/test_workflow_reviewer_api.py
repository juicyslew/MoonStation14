import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path
from types import SimpleNamespace

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_review_results
import workflow_review_transport
import workflow_store
import test_workflow_review as review_fixtures
from test_workflow_review_transport import FakeTransport


class ReviewerAPITests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        helper = review_fixtures.ReviewPlanningTests()
        self.store, self.run, check = helper.fixture(self.root)
        helper.run_check(self.root, self.store, self.run, check)
        self.planner = helper.coordinator(self.store, self.run)
        self.planner.artifact_dir = self.root / 'review-artifacts'
        self.planner.artifact_dir.mkdir(parents=True, exist_ok=True)
        self.task = self.planner.plan_reviews(self.run)[0]
        assignment = self.planner.build_assignment(self.task['task_id'])['body']['envelope']
        self.api = workflow_review_results.ReviewResults(self.store, self.planner)
        self.planner.reviewers['implementation'].pop('session_id',None)
        self.worktree=self.planner.reviewers['implementation']['worktree']
        self.binding = workflow_coordinator.ReviewerBinding('reviewer',self.worktree,'implementation')
        coordinator = workflow_coordinator.ExplicitCoordinator(self.store, bindings={'author-token': object()},
            review_bindings={'review-token': self.binding}, review_results=self.api, artifact_dir=self.root,
            identity_verifier=lambda _: True)
        self.fake_transport=FakeTransport()
        def fake_request(server,method,path,body=None): return self.fake_transport(server,method,path,body)
        transport=workflow_review_transport.ReviewDispatchBackend(self.planner,server='http://127.0.0.1:1234',
            agents={'implementation':'workflow-reviewer-implementation'},request=fake_request)
        self.assertEqual('APPLIED',transport.dispatch('fixture-dispatch',{'task_id':self.task['task_id']}))
        coordinator.review_pipeline=SimpleNamespace(transport=transport)
        self.client = TestClient(coordinator.app())
        self.assignment = assignment
        self.receipt = next(ref for ref in assignment['artifact_refs']
                            if self.store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT')

    def tearDown(self): self.temp.cleanup()

    def headers(self, token='review-token',session=None): return {'Authorization': 'Bearer ' + token,'X-MS14-Reviewer-Session':session or self.task['assigned_session_id']}
    def claim(self, token='review-token', **extra):
        payload = {'contract_version': 1, 'command_id': 'claim-' + self.task['task_id'],
                   **{k:self.task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')}}
        payload.update(extra)
        return self.client.post('/v1/review/claim', headers=self.headers(token), json=payload)
    def result(self, category='IMPLEMENTATION'):
        return {'contract_version':1, **{k:self.task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                'decision':'PASS','findings':[{'criterion_id':'criterion-1','category':category,'message':'Reviewed registered receipt',
                'location':'source tree','evidence_refs':[self.receipt]}], 'inspected_refs':[self.receipt]}

    def test_claim_complete_and_author_token_is_not_review_token(self):
        self.assertEqual(403, self.claim('author-token').status_code)
        claimed = self.claim(); self.assertTrue(claimed.json().get('accepted'), claimed.json())
        response = self.client.post('/v1/review/complete', headers=self.headers(), json=self.result())
        self.assertTrue(response.json()['accepted'], response.json())
        self.assertEqual('COMPLETED', self.store.get_task(self.task['task_id'])['state'])
        status=self.client.get('/v1/review/status',headers=self.headers(),params={'task_id':self.task['task_id']})
        body=status.json()
        self.assertEqual('not_checked',body['freshness'])
        self.assertEqual('COMPLETED',body['task']['state'])
        self.assertTrue(any(a['type']=='CHECK_RECEIPT' and a['path'] for a in body['artifacts']),body)
        self.assertEqual(403,self.client.get('/v1/review/status',headers=self.headers('author-token'),params={'task_id':self.task['task_id']}).status_code)
        self.assertEqual(400,self.client.get('/v1/review/status',headers=self.headers(),params={'task_id':self.task['task_id'],'extra':'x'}).status_code)

    def test_claim_preserves_deadline_and_frozen_assignment_identity_through_completion(self):
        before_task=self.store.get_task(self.task['task_id'])
        before_assignment=self.planner.build_assignment(self.task['task_id'])
        first=self.claim()
        self.assertTrue(first.json().get('accepted'),first.text)
        claimed=self.store.get_task(self.task['task_id'])
        after_assignment=self.planner.build_assignment(self.task['task_id'])
        self.assertEqual(before_task['deadline_ms'],claimed['deadline_ms'])
        self.assertEqual(before_assignment,after_assignment)
        replay=self.claim()
        self.assertTrue(replay.json().get('accepted'),replay.text)
        self.assertEqual(before_task['deadline_ms'],self.store.get_task(self.task['task_id'])['deadline_ms'])
        completed=self.client.post('/v1/review/complete',headers=self.headers(),json=self.result())
        self.assertTrue(completed.json().get('accepted'),completed.text)
        self.assertEqual('COMPLETED',self.store.get_task(self.task['task_id'])['state'])

    def test_claim_response_has_small_stable_guide_locator_on_replay(self):
        before=self.planner.build_assignment(self.task['task_id'])
        first=self.claim(); second=self.claim()
        self.assertTrue(first.json().get('accepted'),first.text)
        self.assertTrue(second.json().get('accepted'),second.text)
        self.assertLessEqual(len(first.content),4096)
        self.assertEqual(first.json()['review_guide'],second.json()['review_guide'])
        self.assertEqual(before,self.planner.build_assignment(self.task['task_id']))
        self.assertEqual(self.assignment['artifact_refs'],before['body']['envelope']['artifact_refs'])

    def test_claimed_bad_frozen_receipt_reports_guide_fault_without_verdict(self):
        from workflow_store import ProtocolError
        with patch.object(self.planner,'review_guide',side_effect=ProtocolError('CHECK_RECEIPT is unreadable: receipt-id')):
            response=self.claim()
        body=response.json()
        self.assertTrue(body['accepted'])
        self.assertEqual('FROZEN_INPUTS_UNAVAILABLE',body['guide_error']['code'])
        self.assertEqual('REPORT_ERROR',body['guide_error']['next_action'])
        self.assertIn('claim is accepted',body['guide_error']['recovery'])
        template=body['guide_error']['error_report_template']
        workflow_store.validate(template,'task_error')
        self.assertEqual({**{k:self.task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                          'contract_version':1,'error_code':'FROZEN_INPUTS_UNAVAILABLE',
                          'message':'CHECK_RECEIPT is unreadable: receipt-id','artifact_refs':[]},template)
        self.assertNotIn('session_id',template)
        self.assertEqual('CLAIMED',self.store.get_task(self.task['task_id'])['state'])
        self.assertIsNone(self.store.get_task(self.task['task_id'])['result'])
        replay=self.claim()
        self.assertTrue(replay.json()['accepted'])
        self.assertEqual('CLAIMED',self.store.get_task(self.task['task_id'])['state'])
        error_response=self.client.post('/v1/review/error',headers=self.headers(),json=template)
        self.assertTrue(error_response.json().get('accepted'),error_response.text)
        saved=self.store.get_task(self.task['task_id'])
        self.assertEqual('COMPLETED',saved['state'])
        self.assertEqual('ERROR',saved['result']['decision'])

    def test_large_status_externalizes_complete_bound_artifact_inventory(self):
        import test_workflow_native_execution
        original=test_workflow_native_execution.make_preflight_case
        def make_large_case(*args,**kwargs):
            case=original(*args,**kwargs)
            for index in range(14):
                data=(f'fixture evidence {index}\n').encode()
                path=case.evidence.parent/(f'long-Windows-style-assigned-evidence-{index:02d}-'+('x'*120)+'.xml')
                path.write_bytes(data)
                artifact=dict(case.artifact,artifact_id=f'artifact_extra_{index}',type='CHECK_RECEIPT',
                              path=str(path),sha256=__import__('hashlib').sha256(data).hexdigest(),size_bytes=len(data))
                case.context.artifacts.append(artifact)
                case.request['criteria'][0]['evidence_refs'].append(artifact['artifact_id'])
            return case
        self.tearDown()
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
        with patch.object(test_workflow_native_execution,'make_preflight_case',side_effect=make_large_case):
            self.setUp()
        before=self.planner.build_assignment(self.task['task_id'])
        response=self.client.get('/v1/review/status',headers=self.headers(),params={'task_id':self.task['task_id']})
        repeated=self.client.get('/v1/review/status',headers=self.headers(),params={'task_id':self.task['task_id']})
        self.assertEqual(200,response.status_code,response.text)
        self.assertLessEqual(len(response.content),4096)
        body=response.json()
        self.assertTrue(body['artifacts_externalized'])
        self.assertEqual(body['inputs']['guide'],repeated.json()['inputs']['guide'])
        self.assertEqual(before,self.planner.build_assignment(self.task['task_id']))
        with open(body['inputs']['guide']['path'],encoding='utf-8') as stream:
            guide=json.load(stream)
        refs=guide['assignment_artifact_refs']
        self.assertTrue(all(isinstance(ref,str) for ref in refs))
        self.assertEqual(set(self.assignment['artifact_refs']),set(refs))
        evidence={item['metadata']['artifact_id']:item for item in guide['evidence']}
        for artifact in refs:
            metadata=evidence[artifact]['metadata']
            self.assertTrue(metadata['sha256'])
            self.assertTrue(metadata['source_pointer'])

    def test_unbound_session_cannot_claim_or_spoof_session(self):
        self.assertEqual(400, self.claim(**{'session_id': self.task['assigned_session_id']}).status_code)
        # A foreign trusted binding does not gain access by claiming the assigned session in JSON.
        other = workflow_coordinator.ReviewerBinding('other',self.binding.worktree,'implementation')
        coordinator = workflow_coordinator.ExplicitCoordinator(self.store, bindings={'author-token': object()},
            review_bindings={'other-token': other}, review_results=self.api, artifact_dir=self.root,
            identity_verifier=lambda _: True)
        client = TestClient(coordinator.app())
        payload = {'contract_version':1, 'command_id':'wrong-session',
                   **{k:self.task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                   }
        self.assertEqual(403, client.post('/v1/review/claim', headers=self.headers('other-token',session='different-session'), json=payload).status_code)
        self.assertEqual('DISPATCHING', self.store.get_task(self.task['task_id'])['state'])

    def test_invalid_category_feedback_then_valid_result(self):
        self.assertTrue(self.claim().json().get('accepted'),self.claim().json())
        bad = self.client.post('/v1/review/complete', headers=self.headers(), json=self.result('PENDING_TESTS')).json()
        self.assertEqual(('INVALID_REVIEW_RESULT','CORRECT_RESULT'), (bad['code'],bad['next_action']))
        self.assertEqual('CLAIMED', self.store.get_task(self.task['task_id'])['state'])
        good = self.client.post('/v1/review/complete', headers=self.headers(), json=self.result()).json()
        self.assertTrue(good['accepted'], good)

    def test_fresh_session_header_is_required_on_all_four_routes(self):
        no_session={'Authorization':'Bearer review-token'}
        task_id=self.task['task_id']
        for path,payload in (
            ('/v1/review/claim',{'task_id':task_id}),
            ('/v1/review/complete',{'task_id':task_id}),
            ('/v1/review/error',{'task_id':task_id}),
        ):
            self.assertEqual(403,self.client.post(path,headers=no_session,json=payload).status_code,path)
            self.assertEqual(403,self.client.post(path,headers=self.headers(session='foreign-session'),json=payload).status_code,path)
        params={'task_id':task_id}
        self.assertEqual(403,self.client.get('/v1/review/status',headers=no_session,params=params).status_code)
        self.assertEqual(403,self.client.get('/v1/review/status',headers=self.headers(session='foreign-session'),params=params).status_code)

    def test_actual_session_identity_mismatch_blocks_all_four_routes(self):
        session=self.fake_transport.sessions[self.task['assigned_session_id']]
        original=dict(session)
        for field,value in (('agent','wrong-agent'),('directory',str(self.root/'wrong-tree')),
                            ('metadata',{'ms14_review_task_id':'different-task'})):
            with self.subTest(field=field):
                session.update(original); session[field]=value
                responses=(self.claim(), self.client.post('/v1/review/complete',headers=self.headers(),json=self.result()),
                    self.client.post('/v1/review/error',headers=self.headers(),json={'task_id':self.task['task_id']}),
                    self.client.get('/v1/review/status',headers=self.headers(),params={'task_id':self.task['task_id']}))
                self.assertEqual([403]*4,[r.status_code for r in responses])
                self.assertEqual('DISPATCHING',self.store.get_task(self.task['task_id'])['state'])

    def test_session_identity_http_unavailable_returns_503(self):
        def fail(server,method,path,body=None):
            if method=='GET' and path.endswith(self.task['assigned_session_id']): return 503,{}
            return self.fake_transport(server,method,path,body)
        self.planner # keep fixture ownership explicit
        self.client = self._client_with_request(fail)
        self.assertEqual(503,self.claim().status_code)

    def _client_with_request(self, request):
        transport=workflow_review_transport.ReviewDispatchBackend(self.planner,server='http://127.0.0.1:1234',
            agents={'implementation':'workflow-reviewer-implementation'},request=request)
        coordinator=workflow_coordinator.ExplicitCoordinator(self.store,bindings={'author-token':object()},
            review_bindings={'review-token':self.binding},review_results=self.api,artifact_dir=self.root,
            identity_verifier=lambda _:True)
        coordinator.review_pipeline=SimpleNamespace(transport=transport)
        return TestClient(coordinator.app())

    def test_raised_session_lookup_returns_503(self):
        def fail(server,method,path,body=None): raise OSError('identity endpoint unavailable')
        self.client=self._client_with_request(fail)
        self.assertEqual(503,self.claim().status_code)

    def test_body_session_id_is_rejected(self):
        self.assertEqual(400,self.claim(**{'session_id':self.task['assigned_session_id']}).status_code)

    def test_stale_evaluation_generation_blocks_all_four_routes(self):
        saved=self.store.get_run(self.task['run_id'])
        stale={'run':dict(saved.get('run',saved),current_attempt=self.task['evaluation_generation']+1)}
        with patch.object(self.store,'get_run',return_value=stale):
            responses=(self.claim(),self.client.post('/v1/review/complete',headers=self.headers(),json=self.result()),
                self.client.post('/v1/review/error',headers=self.headers(),json={'task_id':self.task['task_id']}),
                self.client.get('/v1/review/status',headers=self.headers(),params={'task_id':self.task['task_id']}))
        self.assertEqual([403]*4,[r.status_code for r in responses])
        self.assertEqual('DISPATCHING',self.store.get_task(self.task['task_id'])['state'])


if __name__ == '__main__': unittest.main()
